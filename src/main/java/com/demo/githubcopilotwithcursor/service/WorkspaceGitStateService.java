package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceGitStateService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceGitStateService.class);

    private final WorkspaceGuard workspaceGuard;

    public WorkspaceGitStateService(WorkspaceGuard workspaceGuard) {
        this.workspaceGuard = workspaceGuard;
    }

    public boolean hasUncommittedChanges(RepositoryWorkspace workspace) {
        Path workspacePath = workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath());
        if (workspacePath == null) {
            return false;
        }
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

    public boolean hasUnpushedCommits(RepositoryWorkspace workspace) {
        String branchName = workspace.getBranchName();
        if (branchName == null || branchName.isBlank()) {
            return false;
        }
        Path workspacePath = workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath());
        if (workspacePath == null) {
            return false;
        }
        try (Git git = Git.open(workspacePath.toFile())) {
            Repository repository = git.getRepository();
            Ref local = repository.findRef("refs/heads/" + branchName);
            if (local == null || local.getObjectId() == null) {
                return false;
            }
            Ref remote = repository.findRef("refs/remotes/origin/" + branchName);
            ObjectId remoteId = remote != null ? remote.getObjectId() : null;
            if (remoteId == null) {
                return true;
            }
            return !local.getObjectId().equals(remoteId);
        } catch (java.io.IOException exception) {
            log.warn(
                "Could not compare local and remote branch for {}/{}: {}",
                workspace.getRepoOwner(),
                workspace.getRepoName(),
                exception.getMessage()
            );
            return false;
        }
    }
}
