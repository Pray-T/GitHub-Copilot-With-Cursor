package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.dto.LaunchIdeResponse;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LaunchIdeService {

    private static final Logger log = LoggerFactory.getLogger(LaunchIdeService.class);

    private final WorkspaceProperties workspaceProperties;
    private final WorkspaceGuard workspaceGuard;
    private final WorkspaceService workspaceService;
    private final IdeLauncher ideLauncher;

    public LaunchIdeService(
        WorkspaceProperties workspaceProperties,
        WorkspaceGuard workspaceGuard,
        WorkspaceService workspaceService,
        IdeLauncher ideLauncher
    ) {
        this.workspaceProperties = workspaceProperties;
        this.workspaceGuard = workspaceGuard;
        this.workspaceService = workspaceService;
        this.ideLauncher = ideLauncher;
    }

    @Transactional(readOnly = true)
    public LaunchIdeResponse launchIde(String repoOwner, String repoName) {
        RepositoryWorkspace workspace = workspaceService.requirePresentWorkspace(repoOwner, repoName);
        Path workspacePath = workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath());
        ideLauncher.launchAsync(workspacePath, success -> {
            if (!success) {
                log.warn("IDE launch failed asynchronously for {}/{}", repoOwner, repoName);
            }
            workspaceService.recordIdeLaunchResult(repoOwner, repoName, success);
        });
        log.info("IDE launch requested for {}/{}", repoOwner, repoName);
        return new LaunchIdeResponse(
            repoOwner,
            repoName,
            OffsetDateTime.now(),
            workspaceProperties.getIdeCommand(),
            workspace.getStatus().name(),
            true
        );
    }
}
