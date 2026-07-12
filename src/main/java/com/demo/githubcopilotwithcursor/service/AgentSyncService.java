package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.config.GitHubProperties;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import com.demo.githubcopilotwithcursor.github.GitHubApiClient;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AgentSyncService {

    private static final Logger log = LoggerFactory.getLogger(AgentSyncService.class);

    private final WorkspaceGuard workspaceGuard;
    private final WorkspaceService workspaceService;
    private final GitHubProperties gitHubProperties;
    private final GitHubApiClient gitHubApiClient;

    public AgentSyncService(
        WorkspaceGuard workspaceGuard,
        WorkspaceService workspaceService,
        GitHubProperties gitHubProperties,
        GitHubApiClient gitHubApiClient
    ) {
        this.workspaceGuard = workspaceGuard;
        this.workspaceService = workspaceService;
        this.gitHubProperties = gitHubProperties;
        this.gitHubApiClient = gitHubApiClient;
    }

    public String pullAgentBranch(RepositoryWorkspace workspace) {
        if (workspace.getBranchName() == null || workspace.getBranchName().isBlank()) {
            throw new AppException(ErrorCode.AGENT_SYNC_FAILED, "동기화할 feature branch가 없습니다.");
        }
        Path workspacePath = workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath());
        workspace.markAgentSyncing();
        workspaceService.saveWorkspace(workspace);

        try (Git git = Git.open(workspacePath.toFile())) {
            Repository repository = git.getRepository();
            UsernamePasswordCredentialsProvider credentials = credentialsProvider();
            git.fetch()
                .setRemote("origin")
                .setCredentialsProvider(credentials)
                .call();
            git.pull()
                .setRemote("origin")
                .setRemoteBranchName(workspace.getBranchName())
                .setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
                .setCredentialsProvider(credentials)
                .call();

            ObjectId head = repository.resolve("HEAD");
            String syncedSha = head != null ? head.name() : null;
            workspace.markAgentCompleted(java.time.OffsetDateTime.now());
            workspace.setLastDiffAt(null);
            workspaceService.saveWorkspace(workspace);
            log.info("Synced agent branch for {}/{} at {}", workspace.getRepoOwner(), workspace.getRepoName(), syncedSha);
            return syncedSha;
        } catch (Exception exception) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("jgitMessage", gitHubApiClient.maskToken(exception.getMessage()));
            throw new AppException(ErrorCode.AGENT_SYNC_FAILED, "Agent 브랜치 pull에 실패했습니다.", exception, details);
        }
    }

    private UsernamePasswordCredentialsProvider credentialsProvider() {
        return new UsernamePasswordCredentialsProvider("x-access-token", gitHubProperties.getToken());
    }
}
