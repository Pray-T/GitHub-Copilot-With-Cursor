package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import com.demo.githubcopilotwithcursor.cursor.CursorAuth;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.domain.WorkspaceMode;
import com.demo.githubcopilotwithcursor.dto.CloneRequest;
import com.demo.githubcopilotwithcursor.dto.CloneResponse;
import com.demo.githubcopilotwithcursor.github.GitHubAuth;
import com.demo.githubcopilotwithcursor.github.GitHubRepoRef;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class CloneService {

    private final WorkspaceProperties properties;
    private final WorkspaceGuard workspaceGuard;
    private final GitHubAuth gitHubAuth;
    private final CursorAuth cursorAuth;
    private final WorkspaceBootstrapService workspaceBootstrapService;
    private final WorkspaceService workspaceService;
    private final AgentOrchestratorService agentOrchestratorService;
    private final ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();

    public CloneService(
        WorkspaceProperties properties,
        WorkspaceGuard workspaceGuard,
        GitHubAuth gitHubAuth,
        CursorAuth cursorAuth,
        WorkspaceBootstrapService workspaceBootstrapService,
        WorkspaceService workspaceService,
        AgentOrchestratorService agentOrchestratorService
    ) {
        this.properties = properties;
        this.workspaceGuard = workspaceGuard;
        this.gitHubAuth = gitHubAuth;
        this.cursorAuth = cursorAuth;
        this.workspaceBootstrapService = workspaceBootstrapService;
        this.workspaceService = workspaceService;
        this.agentOrchestratorService = agentOrchestratorService;
    }

    public CloneResponse cloneRepository(CloneRequest request) {
        cursorAuth.requireApiKey();
        var user = gitHubAuth.requireAuthenticatedUser();

        URI uri = workspaceGuard.parseRepoUrl(request.repoUrl(), properties.getAllowedHosts());
        GitHubRepoRef repoRef = workspaceGuard.extractRepoRef(uri);
        WorkspaceMode mode = request.resolvedMode();
        String lockKey = repoRef.owner() + "/" + repoRef.repo();
        Object lock = locks.computeIfAbsent(lockKey, key -> new Object());

        synchronized (lock) {
            try {
                RepositoryWorkspace workspace = workspaceBootstrapService.bootstrap(
                    request.repoUrl(),
                    repoRef,
                    user,
                    mode,
                    request.agentPrompt()
                );
                agentOrchestratorService.startAgent(workspace);
                workspace = workspaceService.requirePresentWorkspace(workspace.getRepoOwner(), workspace.getRepoName());
                return toResponse(workspace);
            } finally {
                locks.remove(lockKey, lock);
            }
        }
    }

    private CloneResponse toResponse(RepositoryWorkspace workspace) {
        return new CloneResponse(
            workspace.getRepoOwner(),
            workspace.getRepoName(),
            workspace.getMode().name(),
            workspace.getUpstreamUrl(),
            workspace.getForkUrl(),
            workspace.isForkReused(),
            workspace.getBranchName(),
            workspace.getWorkspacePath(),
            workspace.getHeadCommitSha(),
            workspace.getStatus().name(),
            workspace.getCursorAgentId(),
            workspace.getCursorRunId(),
            workspace.getAgentStatus() != null ? workspace.getAgentStatus().name() : null,
            workspace.getAgentStartedAt(),
            workspace.getClonedAt()
        );
    }
}
