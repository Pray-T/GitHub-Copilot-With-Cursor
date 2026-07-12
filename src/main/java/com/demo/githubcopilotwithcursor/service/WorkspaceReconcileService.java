package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import com.demo.githubcopilotwithcursor.repository.RepositoryWorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceReconcileService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceReconcileService.class);

    private final WorkspaceGuard workspaceGuard;
    private final RepositoryWorkspaceRepository repository;

    public WorkspaceReconcileService(WorkspaceGuard workspaceGuard, RepositoryWorkspaceRepository repository) {
        this.workspaceGuard = workspaceGuard;
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WorkspaceDeletionSnapshot deleteWorkspaceRecord(String repoOwner, String repoName) {
        workspaceGuard.validateRepoOwner(repoOwner);
        workspaceGuard.validateRepoName(repoName);
        RepositoryWorkspace workspace = repository.findByRepoOwnerAndRepoName(repoOwner, repoName)
            .orElseThrow(() -> new AppException(
                ErrorCode.WORKSPACE_NOT_FOUND,
                "워크스페이스 '" + repoOwner + "/" + repoName + "'를 찾을 수 없습니다."
            ));
        Path savedPath = workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath());
        repository.delete(workspace);
        log.info("Deleted workspace DB record for '{}/{}'", repoOwner, repoName);
        return new WorkspaceDeletionSnapshot(repoOwner, repoName, savedPath);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileMissingDisk(String repoOwner, String repoName) {
        workspaceGuard.validateRepoOwner(repoOwner);
        workspaceGuard.validateRepoName(repoName);
        repository.findByRepoOwnerAndRepoName(repoOwner, repoName).ifPresent(workspace -> {
            Path path = workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath());
            if (!isWorkspacePresent(path)) {
                repository.delete(workspace);
                log.info(
                    "Reconciled workspace '{}/{}' — removed DB row because local git workspace is absent at {}",
                    repoOwner,
                    repoName,
                    path
                );
            }
        });
    }

    boolean isWorkspacePresent(Path path) {
        Path normalized = workspaceGuard.normalizePath(path);
        return normalized != null
            && Files.isDirectory(normalized)
            && Files.isDirectory(normalized.resolve(".git"));
    }
}
