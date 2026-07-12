package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.dto.CommitPushRequest;
import com.demo.githubcopilotwithcursor.dto.CommitPushResponse;
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

    public CommitPushService(
        WorkspaceGuard workspaceGuard,
        WorkspaceService workspaceService,
        GitHubAuth gitHubAuth,
        CommitService commitService,
        PushService pushService
    ) {
        this.workspaceGuard = workspaceGuard;
        this.workspaceService = workspaceService;
        this.gitHubAuth = gitHubAuth;
        this.commitService = commitService;
        this.pushService = pushService;
    }

    public CommitPushResponse commitAndPush(String repoOwner, String repoName, CommitPushRequest request) {
        gitHubAuth.requireAuthenticatedUser();
        var workspace = workspaceService.requireContributeWorkspace(repoOwner, repoName);

        Path workspacePath = workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath());
        String commitSha = commitService.commit(repoOwner, repoName, workspacePath, request);
        pushService.push(repoOwner, repoName, workspacePath, workspace.getBranchName());
        return new CommitPushResponse(
            workspace.getRepoOwner(),
            workspace.getRepoName(),
            workspace.getBranchName(),
            commitSha,
            workspace.getForkUrl(),
            OffsetDateTime.now()
        );
    }
}
