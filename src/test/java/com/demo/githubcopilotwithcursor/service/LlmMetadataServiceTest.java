package com.demo.githubcopilotwithcursor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.githubcopilotwithcursor.config.CursorProperties;
import com.demo.githubcopilotwithcursor.cursor.CloudAgentClient;
import com.demo.githubcopilotwithcursor.cursor.LlmMetadataException;
import com.demo.githubcopilotwithcursor.cursor.dto.ComposerResponse;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.dto.ChangedFileResponse;
import com.demo.githubcopilotwithcursor.dto.DiffResponse;
import com.demo.githubcopilotwithcursor.dto.PrPrepareResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmMetadataServiceTest {

    private static final String BASE_SHA = "0123456789012345678901234567890123456789";

    private CursorProperties cursorProperties;
    private CloudAgentClient cloudAgentClient;
    private WorkspaceService workspaceService;
    private WorkspaceGuard workspaceGuard;
    private DiffService diffService;
    private DiffFingerprintService diffFingerprintService;
    private PullRequestService pullRequestService;
    private LlmMetadataService service;

    @BeforeEach
    void setUp() {
        cursorProperties = new CursorProperties();
        cloudAgentClient = org.mockito.Mockito.mock(CloudAgentClient.class);
        workspaceService = org.mockito.Mockito.mock(WorkspaceService.class);
        workspaceGuard = org.mockito.Mockito.mock(WorkspaceGuard.class);
        diffService = org.mockito.Mockito.mock(DiffService.class);
        diffFingerprintService = new DiffFingerprintService();
        pullRequestService = org.mockito.Mockito.mock(PullRequestService.class);
        service = new LlmMetadataService(
            cursorProperties,
            cloudAgentClient,
            workspaceService,
            workspaceGuard,
            diffService,
            diffFingerprintService,
            pullRequestService
        );
    }

    @Test
    void usesCacheWhenFingerprintMatches() {
        DiffResponse diff = sampleDiff("README.md", "MODIFIED", "cached content");
        String fingerprint = diffFingerprintService.compute(diff);
        RepositoryWorkspace workspace = contributeWorkspace();
        workspace.cacheLlmMetadata("cached commit", "cached title", "cached body", OffsetDateTime.now(), fingerprint);
        when(workspaceService.requireContributeWorkspace("octocat", "demo")).thenReturn(workspace);
        when(workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath()))
            .thenReturn(java.nio.file.Path.of(workspace.getWorkspacePath()));
        when(diffService.diffWithoutPersist(eq("octocat"), eq("demo"), eq(true), eq(0))).thenReturn(diff);

        PrPrepareResponse response = service.prepareForPr("octocat", "demo");

        assertThat(response.commitMessage()).isEqualTo("cached commit");
        assertThat(response.fallbackUsed()).isFalse();
        assertThat(response.metadataRegeneratedDueToDiffChange()).isFalse();
        verify(cloudAgentClient, never()).requestPrMetadataFollowUp(any(), any());
    }

    @Test
    void regeneratesWhenFingerprintDiffers() {
        DiffResponse cachedDiff = sampleDiff("README.md", "MODIFIED", "before ide edit");
        DiffResponse currentDiff = sampleDiff("README.md", "MODIFIED", "after ide edit");
        RepositoryWorkspace workspace = contributeWorkspace();
        workspace.cacheLlmMetadata(
            "cached commit",
            "cached title",
            "cached body",
            OffsetDateTime.now(),
            diffFingerprintService.compute(cachedDiff)
        );
        when(workspaceService.requireContributeWorkspace("octocat", "demo")).thenReturn(workspace);
        when(workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath()))
            .thenReturn(java.nio.file.Path.of(workspace.getWorkspacePath()));
        when(diffService.diffWithoutPersist(eq("octocat"), eq("demo"), eq(true), eq(0))).thenReturn(currentDiff);
        when(cloudAgentClient.requestPrMetadataFollowUp(eq("bc-test-001"), any()))
            .thenReturn(new ComposerResponse("new commit", "new title", "new body"));

        PrPrepareResponse response = service.prepareForPr("octocat", "demo");

        assertThat(response.commitMessage()).isEqualTo("new commit");
        assertThat(response.metadataRegeneratedDueToDiffChange()).isTrue();
        assertThat(workspace.getLlmDiffFingerprint()).isEqualTo(diffFingerprintService.compute(currentDiff));
        verify(cloudAgentClient).requestPrMetadataFollowUp(eq("bc-test-001"), any());
        verify(workspaceService).saveWorkspace(workspace);
    }

    @Test
    void storesFingerprintWhenMetadataIsCached() {
        DiffResponse diff = sampleDiff("README.md", "MODIFIED", "fresh content");
        RepositoryWorkspace workspace = contributeWorkspace();
        when(workspaceService.requireContributeWorkspace("octocat", "demo")).thenReturn(workspace);
        when(workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath()))
            .thenReturn(java.nio.file.Path.of(workspace.getWorkspacePath()));
        when(diffService.diffWithoutPersist(eq("octocat"), eq("demo"), eq(true), eq(0))).thenReturn(diff);
        when(cloudAgentClient.requestPrMetadataFollowUp(eq("bc-test-001"), any()))
            .thenReturn(new ComposerResponse("commit", "title", "body"));

        service.prepareForPr("octocat", "demo");

        assertThat(workspace.getLlmDiffFingerprint()).isEqualTo(diffFingerprintService.compute(diff));
    }

    @Test
    void invalidateLlmCacheClearsFingerprint() {
        RepositoryWorkspace workspace = contributeWorkspace();
        workspace.cacheLlmMetadata("commit", "title", "body", OffsetDateTime.now(), "abc123");

        workspace.invalidateLlmCache();

        assertThat(workspace.getLlmDiffFingerprint()).isNull();
        assertThat(workspace.getLlmCommitMessage()).isNull();
    }

    @Test
    void fallsBackWhenFollowUpFails() {
        RepositoryWorkspace workspace = contributeWorkspace();
        when(workspaceService.requireContributeWorkspace("octocat", "demo")).thenReturn(workspace);
        when(workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath()))
            .thenReturn(java.nio.file.Path.of(workspace.getWorkspacePath()));
        when(diffService.diffWithoutPersist(eq("octocat"), eq("demo"), eq(true), eq(0)))
            .thenReturn(new DiffResponse("octocat", "demo", BASE_SHA, OffsetDateTime.now(), 0, List.of()));
        when(cloudAgentClient.requestPrMetadataFollowUp(eq("bc-test-001"), any()))
            .thenThrow(new LlmMetadataException("follow-up down"));
        when(pullRequestService.buildDefaultBody("octocat", "demo")).thenReturn("## 변경 파일\n\n- M README.md\n");

        PrPrepareResponse response = service.prepareForPr("octocat", "demo");

        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.commitMessage()).isEqualTo("Refactor by Cursor Cloud Agent");
        assertThat(response.prBody()).contains("README.md");
        verify(workspaceService).saveWorkspace(workspace);
    }

    @Test
    void detectsStaleCachedMetadataForDiffPage() {
        DiffResponse cachedDiff = sampleDiff("README.md", "MODIFIED", "before");
        DiffResponse currentDiff = sampleDiff("README.md", "MODIFIED", "after");
        RepositoryWorkspace workspace = contributeWorkspace();
        workspace.cacheLlmMetadata(
            "cached commit",
            "cached title",
            "cached body",
            OffsetDateTime.now(),
            diffFingerprintService.compute(cachedDiff)
        );

        assertThat(service.isCachedMetadataStale(workspace, currentDiff)).isTrue();
        assertThat(service.isCachedMetadataStale(workspace, cachedDiff)).isFalse();
    }

    private DiffResponse sampleDiff(String path, String changeType, String content) {
        ChangedFileResponse file = new ChangedFileResponse(
            path,
            null,
            changeType,
            false,
            false,
            content.length(),
            content.length(),
            null,
            content,
            false
        );
        return new DiffResponse("octocat", "demo", BASE_SHA, OffsetDateTime.now(), 1, List.of(file));
    }

    private RepositoryWorkspace contributeWorkspace() {
        RepositoryWorkspace workspace = new RepositoryWorkspace(
            "octocat",
            "demo",
            "https://github.com/octocat/demo",
            "build/tmp/llm-meta/octocat/demo",
            BASE_SHA,
            "https://github.com/octocat/demo",
            "https://github.com/octocat/demo-fork",
            false,
            "refactor/demo-202605281900"
        );
        workspace.setAgentPrompt("Refactor Owner to record");
        workspace.markAgentStarted("bc-test-001", "run-test-001", OffsetDateTime.now().minusHours(2));
        workspace.markAgentCompleted(OffsetDateTime.now().minusHours(1));
        return workspace;
    }
}
