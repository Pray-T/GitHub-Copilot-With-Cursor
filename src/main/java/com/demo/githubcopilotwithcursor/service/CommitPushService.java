package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.dto.CommitPushRequest;
import com.demo.githubcopilotwithcursor.dto.CommitPushResponse;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.github.GitHubAuth;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class CommitPushService {

    private final WorkspaceGuard workspaceGuard;
    private final WorkspaceService workspaceService;
    private final GitHubAuth gitHubAuth;
    private final CommitService commitService;
    private final PushService pushService;
    private final DiffService diffService;
    private final DiffFingerprintService diffFingerprintService;

    public CommitPushService(
        WorkspaceGuard workspaceGuard,
        WorkspaceService workspaceService,
        GitHubAuth gitHubAuth,
        CommitService commitService,
        PushService pushService,
        DiffService diffService,
        DiffFingerprintService diffFingerprintService
    ) {
        this.workspaceGuard = workspaceGuard;
        this.workspaceService = workspaceService;
        this.gitHubAuth = gitHubAuth;
        this.commitService = commitService;
        this.pushService = pushService;
        this.diffService = diffService;
        this.diffFingerprintService = diffFingerprintService;
    }

    public CommitPushResponse commitAndPush(String repoOwner, String repoName, CommitPushRequest request) {
        gitHubAuth.requireAuthenticatedUser();
        var workspace = workspaceService.requireContributeWorkspace(repoOwner, repoName);

        Path workspacePath = workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath());
        String commitSha = commitService.commit(repoOwner, repoName, workspacePath, request);
        pushService.push(repoOwner, repoName, workspacePath, workspace.getBranchName());
        syncLlmDiffFingerprint(repoOwner, repoName, workspace);
        return new CommitPushResponse(
            workspace.getRepoOwner(),
            workspace.getRepoName(),
            workspace.getBranchName(),
            commitSha,
            workspace.getForkUrl(),
            OffsetDateTime.now()
        );
    }

    private void syncLlmDiffFingerprint(String repoOwner, String repoName, RepositoryWorkspace workspace) {
        if (workspace.getLlmCachedAt() == null) {
            return;
        }
        var diff = diffService.diffWithoutPersist(repoOwner, repoName, true, 0);
        workspace.updateLlmDiffFingerprint(diffFingerprintService.compute(diff));
        workspaceService.saveWorkspace(workspace);
    }
}
