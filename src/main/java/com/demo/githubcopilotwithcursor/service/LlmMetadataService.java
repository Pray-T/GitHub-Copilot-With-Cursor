package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.config.CursorProperties;
import com.demo.githubcopilotwithcursor.cursor.CloudAgentClient;
import com.demo.githubcopilotwithcursor.cursor.LlmMetadataException;
import com.demo.githubcopilotwithcursor.cursor.dto.ComposerRequest;
import com.demo.githubcopilotwithcursor.cursor.dto.ComposerResponse;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.dto.ChangedFileResponse;
import com.demo.githubcopilotwithcursor.dto.DiffResponse;
import com.demo.githubcopilotwithcursor.dto.PrPrepareResponse;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LlmMetadataService {

    private static final Logger log = LoggerFactory.getLogger(LlmMetadataService.class);
    private static final String FALLBACK_MESSAGE = "Refactor by Cursor Cloud Agent";
    private static final String PR_METADATA_FOLLOW_UP_PROMPT = """
        You are a technical writer for GitHub pull requests.
        You already completed the code changes in this conversation. Based on those changes and any diff context below, \
        write accurate PR metadata that reflects what was actually changed — do not invent changes.
        Respond with ONLY valid JSON (no markdown prose outside the JSON) using exactly these keys:
        commitMessage (string, <=4096 chars), prTitle (string, <=256 chars), prBody (string, markdown, <=65535 chars).
        prBody must include sections "## 변경 요약" and "## 변경 파일".
        Do not modify any repository files. Do not include secrets or tokens.
        """;

    private final CursorProperties cursorProperties;
    private final CloudAgentClient cloudAgentClient;
    private final WorkspaceService workspaceService;
    private final WorkspaceGuard workspaceGuard;
    private final DiffService diffService;
    private final DiffFingerprintService diffFingerprintService;
    private final PullRequestService pullRequestService;

    public LlmMetadataService(
        CursorProperties cursorProperties,
        CloudAgentClient cloudAgentClient,
        WorkspaceService workspaceService,
        WorkspaceGuard workspaceGuard,
        DiffService diffService,
        DiffFingerprintService diffFingerprintService,
        PullRequestService pullRequestService
    ) {
        this.cursorProperties = cursorProperties;
        this.cloudAgentClient = cloudAgentClient;
        this.workspaceService = workspaceService;
        this.workspaceGuard = workspaceGuard;
        this.diffService = diffService;
        this.diffFingerprintService = diffFingerprintService;
        this.pullRequestService = pullRequestService;
    }

    @Transactional
    public PrPrepareResponse prepareForPr(String repoOwner, String repoName) {
        RepositoryWorkspace workspace = workspaceService.requireContributeWorkspace(repoOwner, repoName);
        DiffResponse diff = diffService.diffWithoutPersist(repoOwner, repoName, true, 0);
        String currentFingerprint = diffFingerprintService.compute(diff);
        boolean hasLocalUncommitted = hasLocalUncommittedChanges(workspace);
        boolean hadCachedMetadata = hasCompleteCachedMetadata(workspace);
        String previousFingerprint = workspace.getLlmDiffFingerprint();

        if (isCacheValid(workspace, currentFingerprint)) {
            return toResponse(workspace, hasLocalUncommitted, false, false);
        }

        boolean metadataRegeneratedDueToDiffChange = hadCachedMetadata
            && (previousFingerprint == null || !previousFingerprint.equals(currentFingerprint));

        PreparedMetadata prepared = resolveComposerMetadata(repoOwner, repoName, workspace, diff);
        OffsetDateTime cachedAt = OffsetDateTime.now();
        workspace.cacheLlmMetadata(
            prepared.composer().commitMessage(),
            prepared.composer().prTitle(),
            prepared.composer().prBody(),
            cachedAt,
            currentFingerprint
        );
        workspaceService.saveWorkspace(workspace);
        return toResponse(
            workspace,
            hasLocalUncommitted,
            prepared.fallbackUsed(),
            metadataRegeneratedDueToDiffChange
        );
    }

    @Transactional(readOnly = true)
    public boolean isCachedMetadataStale(RepositoryWorkspace workspace, DiffResponse diff) {
        if (!hasCompleteCachedMetadata(workspace)) {
            return false;
        }
        String storedFingerprint = workspace.getLlmDiffFingerprint();
        if (storedFingerprint == null || storedFingerprint.isBlank()) {
            return true;
        }
        return !storedFingerprint.equals(diffFingerprintService.compute(diff));
    }

    public boolean isCacheValid(RepositoryWorkspace workspace, String currentFingerprint) {
        if (workspace.getLlmCachedAt() == null) {
            return false;
        }
        if (workspace.getLlmDiffFingerprint() == null || workspace.getLlmDiffFingerprint().isBlank()) {
            return false;
        }
        if (!hasCompleteCachedMetadata(workspace)) {
            return false;
        }
        return workspace.getLlmDiffFingerprint().equals(currentFingerprint);
    }

    private boolean hasCompleteCachedMetadata(RepositoryWorkspace workspace) {
        return hasText(workspace.getLlmCommitMessage())
            && hasText(workspace.getLlmPrTitle())
            && hasText(workspace.getLlmPrBody());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private PreparedMetadata resolveComposerMetadata(
        String repoOwner,
        String repoName,
        RepositoryWorkspace workspace,
        DiffResponse diff
    ) {
        try {
            String diffPatch = buildDiffPatch(diff);
            String agentId = workspace.getCursorAgentId();
            if (agentId == null || agentId.isBlank()) {
                throw new LlmMetadataException("Cursor agent id is missing for PR metadata follow-up");
            }
            ComposerRequest request = new ComposerRequest(
                PR_METADATA_FOLLOW_UP_PROMPT,
                null,
                diffPatch,
                cursorProperties.getComposer().getMaxFiles(),
                cursorProperties.getComposer().getMaxPatchBytes()
            );
            return new PreparedMetadata(cloudAgentClient.requestPrMetadataFollowUp(agentId, request), false);
        } catch (LlmMetadataException exception) {
            log.error("LLM_METADATA_FAILED for {}/{}: {}", repoOwner, repoName, exception.getMessage());
            return new PreparedMetadata(buildFallback(repoOwner, repoName), true);
        }
    }

    private ComposerResponse buildFallback(String repoOwner, String repoName) {
        return ComposerResponse.fallback(FALLBACK_MESSAGE, pullRequestService.buildDefaultBody(repoOwner, repoName));
    }

    private String buildDiffPatch(DiffResponse diff) {
        List<ChangedFileResponse> files = diff.changedFiles();
        int maxFiles = cursorProperties.getComposer().getMaxFiles();
        int maxPatchBytes = cursorProperties.getComposer().getMaxPatchBytes();
        StringBuilder builder = new StringBuilder("## Diff context\n");
        builder.append("Base commit: ").append(diff.headCommitSha()).append('\n');
        builder.append("Changed files: ").append(diff.totalChangedFiles()).append('\n');
        int fileCount = 0;
        for (ChangedFileResponse file : files) {
            if (fileCount >= maxFiles) {
                builder.append("\n... (additional files omitted)\n");
                break;
            }
            if (builder.length() >= maxPatchBytes) {
                break;
            }
            builder.append("\n### ").append(shortChangeType(file.changeType())).append(' ').append(file.path()).append('\n');
            if (file.binary()) {
                builder.append("(binary file)\n");
            } else if (file.truncated()) {
                builder.append("(content truncated)\n");
            } else if (file.newContent() != null && !file.newContent().isBlank()) {
                appendLimited(builder, file.newContent(), maxPatchBytes);
            }
            fileCount++;
        }
        if (builder.length() > maxPatchBytes) {
            return builder.substring(0, maxPatchBytes);
        }
        return builder.toString();
    }

    private void appendLimited(StringBuilder builder, String content, int maxPatchBytes) {
        int remaining = maxPatchBytes - builder.length();
        if (remaining <= 0) {
            return;
        }
        if (content.length() <= remaining) {
            builder.append(content);
        } else {
            builder.append(content, 0, remaining);
        }
    }

    private String shortChangeType(String changeType) {
        return switch (changeType) {
            case "ADDED" -> "A";
            case "DELETED" -> "D";
            case "RENAMED" -> "R";
            default -> "M";
        };
    }

    private boolean hasLocalUncommittedChanges(RepositoryWorkspace workspace) {
        Path workspacePath = workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath());
        try (Git git = Git.open(workspacePath.toFile())) {
            Status status = git.status().call();
            return !status.isClean();
        } catch (GitAPIException | java.io.IOException exception) {
            log.warn(
                "Could not read git status for {}/{}: {}",
                workspace.getRepoOwner(),
                workspace.getRepoName(),
                exception.getMessage()
            );
            return false;
        }
    }

    private PrPrepareResponse toResponse(
        RepositoryWorkspace workspace,
        boolean hasLocalUncommitted,
        boolean fallbackUsed,
        boolean metadataRegeneratedDueToDiffChange
    ) {
        String nextStep = hasLocalUncommitted ? PrPrepareResponse.NEXT_COMMIT_FORM : PrPrepareResponse.NEXT_PR_FORM;
        return new PrPrepareResponse(
            workspace.getRepoOwner(),
            workspace.getRepoName(),
            workspace.getLlmCommitMessage(),
            workspace.getLlmPrTitle(),
            workspace.getLlmPrBody(),
            workspace.getLlmCachedAt(),
            hasLocalUncommitted,
            nextStep,
            fallbackUsed,
            metadataRegeneratedDueToDiffChange
        );
    }

    private record PreparedMetadata(ComposerResponse composer, boolean fallbackUsed) {
    }
}
