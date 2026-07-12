package com.demo.githubcopilotwithcursor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.repository.RepositoryWorkspaceRepository;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class WorkspaceServiceTest {

    private static final String REPO_OWNER = "demo";

    @TempDir
    Path tempDir;

    @Test
    void isRegisteredAndPresentReturnsFalseAndCleansOrphanDiskWhenDbRowIsAbsent() throws Exception {
        Path workspaceRoot = tempDir.resolve(REPO_OWNER).resolve("orphan-repo");
        Files.createDirectories(workspaceRoot);
        try (Git ignored = Git.init().setDirectory(workspaceRoot.toFile()).call()) {
            // initialized git workspace only
        }

        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setRoot(tempDir.toString());

        RepositoryWorkspaceRepository repository = Mockito.mock(RepositoryWorkspaceRepository.class);
        WorkspaceDiskCleanupService diskCleanupService = Mockito.mock(WorkspaceDiskCleanupService.class);
        WorkspaceGuard workspaceGuard = new WorkspaceGuard();

        when(repository.existsByRepoOwnerAndRepoName(REPO_OWNER, "orphan-repo")).thenReturn(false);

        WorkspaceService service = new WorkspaceService(
            properties,
            workspaceGuard,
            new WorkspaceReconcileService(workspaceGuard, repository),
            diskCleanupService,
            repository
        );

        Path resolvedPath = workspaceGuard.resolveWorkspace(properties.rootPath(), REPO_OWNER, "orphan-repo");
        assertThat(service.isRegisteredAndPresent(REPO_OWNER, "orphan-repo", resolvedPath)).isFalse();
        verify(diskCleanupService).deleteWorkspaceDirectory(resolvedPath, REPO_OWNER + "/orphan-repo");
    }

    @Test
    void isRegisteredAndPresentReturnsTrueWhenDbRowAndDiskBothExist() throws Exception {
        Path workspaceRoot = tempDir.resolve(REPO_OWNER).resolve("active-repo");
        Files.createDirectories(workspaceRoot);
        try (Git ignored = Git.init().setDirectory(workspaceRoot.toFile()).call()) {
            // initialized git workspace only
        }

        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setRoot(tempDir.toString());

        RepositoryWorkspaceRepository repository = Mockito.mock(RepositoryWorkspaceRepository.class);
        WorkspaceDiskCleanupService diskCleanupService = Mockito.mock(WorkspaceDiskCleanupService.class);
        WorkspaceGuard workspaceGuard = new WorkspaceGuard();

        when(repository.existsByRepoOwnerAndRepoName(REPO_OWNER, "active-repo")).thenReturn(true);

        WorkspaceService service = new WorkspaceService(
            properties,
            workspaceGuard,
            new WorkspaceReconcileService(workspaceGuard, repository),
            diskCleanupService,
            repository
        );

        Path resolvedPath = workspaceGuard.resolveWorkspace(properties.rootPath(), REPO_OWNER, "active-repo");
        assertThat(service.isRegisteredAndPresent(REPO_OWNER, "active-repo", resolvedPath)).isTrue();
        verifyNoInteractions(diskCleanupService);
    }

    @Test
    void isRegisteredAndPresentReturnsFalseWhenNeitherDbNorDiskExists() {
        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setRoot(tempDir.toString());

        RepositoryWorkspaceRepository repository = Mockito.mock(RepositoryWorkspaceRepository.class);
        WorkspaceDiskCleanupService diskCleanupService = Mockito.mock(WorkspaceDiskCleanupService.class);
        WorkspaceGuard workspaceGuard = new WorkspaceGuard();

        when(repository.existsByRepoOwnerAndRepoName(REPO_OWNER, "fresh-repo")).thenReturn(false);

        WorkspaceService service = new WorkspaceService(
            properties,
            workspaceGuard,
            new WorkspaceReconcileService(workspaceGuard, repository),
            diskCleanupService,
            repository
        );

        Path resolvedPath = workspaceGuard.resolveWorkspace(properties.rootPath(), REPO_OWNER, "fresh-repo");
        assertThat(service.isRegisteredAndPresent(REPO_OWNER, "fresh-repo", resolvedPath)).isFalse();
        verifyNoInteractions(diskCleanupService);
    }

    @Test
    void assertCloneTargetClearThrowsWhenTargetDirectoryStillExists() throws Exception {
        Path workspaceRoot = tempDir.resolve(REPO_OWNER).resolve("blocked-repo");
        Files.createDirectories(workspaceRoot);
        try (Git ignored = Git.init().setDirectory(workspaceRoot.toFile()).call()) {
            // initialized git workspace only
        }

        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setRoot(tempDir.toString());

        RepositoryWorkspaceRepository repository = Mockito.mock(RepositoryWorkspaceRepository.class);
        WorkspaceDiskCleanupService diskCleanupService = Mockito.mock(WorkspaceDiskCleanupService.class);
        WorkspaceGuard workspaceGuard = new WorkspaceGuard();

        WorkspaceService service = new WorkspaceService(
            properties,
            workspaceGuard,
            new WorkspaceReconcileService(workspaceGuard, repository),
            diskCleanupService,
            repository
        );

        Path resolvedPath = workspaceGuard.resolveWorkspace(properties.rootPath(), REPO_OWNER, "blocked-repo");
        assertThatThrownBy(() -> service.assertCloneTargetClear(resolvedPath))
            .isInstanceOf(AppException.class)
            .satisfies(thrown -> {
                AppException exception = (AppException) thrown;
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CLONE_FAILED);
                assertThat(exception.getMessage()).isEqualTo(WorkspaceService.CLONE_TARGET_LOCKED_MESSAGE);
            });
    }

    @Test
    void listWorkspacesReconcilesMissingDiskViaReconcileService() throws Exception {
        Path presentRoot = tempDir.resolve(REPO_OWNER).resolve("present-repo");
        Files.createDirectories(presentRoot);
        try (Git ignored = Git.init().setDirectory(presentRoot.toFile()).call()) {
            // initialized git workspace only
        }

        Path missingRoot = tempDir.resolve(REPO_OWNER).resolve("missing-repo");

        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setRoot(tempDir.toString());

        RepositoryWorkspaceRepository repository = Mockito.mock(RepositoryWorkspaceRepository.class);
        WorkspaceReconcileService reconcileService = Mockito.mock(WorkspaceReconcileService.class);
        WorkspaceDiskCleanupService diskCleanupService = Mockito.mock(WorkspaceDiskCleanupService.class);
        WorkspaceGuard workspaceGuard = new WorkspaceGuard();

        RepositoryWorkspace present = new RepositoryWorkspace(
            REPO_OWNER,
            "present-repo",
            "https://github.com/demo/present-repo",
            presentRoot.toString(),
            "abc123"
        );
        RepositoryWorkspace missing = new RepositoryWorkspace(
            REPO_OWNER,
            "missing-repo",
            "https://github.com/demo/missing-repo",
            missingRoot.toString(),
            "def456"
        );
        when(repository.findAllByOrderByClonedAtDesc()).thenReturn(List.of(present, missing));
        Path presentPath = workspaceGuard.normalizeStoredPath(present.getWorkspacePath());
        Path missingPath = workspaceGuard.normalizeStoredPath(missing.getWorkspacePath());
        when(reconcileService.isWorkspacePresent(presentPath)).thenReturn(true);
        when(reconcileService.isWorkspacePresent(missingPath)).thenReturn(false);

        WorkspaceService service = new WorkspaceService(
            properties,
            workspaceGuard,
            reconcileService,
            diskCleanupService,
            repository
        );

        var response = service.listWorkspaces();

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).repoName()).isEqualTo("present-repo");
        verify(reconcileService).reconcileMissingDisk(REPO_OWNER, "missing-repo");
        verifyNoInteractions(diskCleanupService);
    }

    @Test
    void deleteWorkspaceUsesSavedPathWhenItIsUnderWorkspaceRoot() throws Exception {
        Path workspaceRoot = tempDir.resolve("workspaces");
        Path savedPath = workspaceRoot.resolve(REPO_OWNER).resolve("demo-repo");
        Files.createDirectories(savedPath);

        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setRoot(workspaceRoot.toString());

        RepositoryWorkspaceRepository repository = Mockito.mock(RepositoryWorkspaceRepository.class);
        WorkspaceReconcileService reconcileService = Mockito.mock(WorkspaceReconcileService.class);
        WorkspaceDiskCleanupService diskCleanupService = Mockito.mock(WorkspaceDiskCleanupService.class);
        WorkspaceGuard workspaceGuard = new WorkspaceGuard();

        when(reconcileService.deleteWorkspaceRecord(REPO_OWNER, "demo-repo"))
            .thenReturn(new WorkspaceDeletionSnapshot(REPO_OWNER, "demo-repo", savedPath));

        WorkspaceService service = new WorkspaceService(
            properties,
            workspaceGuard,
            reconcileService,
            diskCleanupService,
            repository
        );

        service.deleteWorkspace(REPO_OWNER, "demo-repo");

        verify(diskCleanupService).deleteWorkspaceDirectoryAsync(savedPath, REPO_OWNER + "/demo-repo");
    }

    @Test
    void deleteWorkspaceFallsBackToResolvedPathWhenSavedPathIsOutsideWorkspaceRoot() throws Exception {
        Path workspaceRoot = tempDir.resolve("workspaces");
        Path resolvedPath = workspaceRoot.resolve(REPO_OWNER).resolve("demo-repo");
        Files.createDirectories(resolvedPath);
        Path outsideSavedPath = tempDir.resolve("legacy-root").resolve("demo-repo");

        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setRoot(workspaceRoot.toString());

        RepositoryWorkspaceRepository repository = Mockito.mock(RepositoryWorkspaceRepository.class);
        WorkspaceReconcileService reconcileService = Mockito.mock(WorkspaceReconcileService.class);
        WorkspaceDiskCleanupService diskCleanupService = Mockito.mock(WorkspaceDiskCleanupService.class);
        WorkspaceGuard workspaceGuard = new WorkspaceGuard();

        when(reconcileService.deleteWorkspaceRecord(REPO_OWNER, "demo-repo"))
            .thenReturn(new WorkspaceDeletionSnapshot(REPO_OWNER, "demo-repo", outsideSavedPath));

        WorkspaceService service = new WorkspaceService(
            properties,
            workspaceGuard,
            reconcileService,
            diskCleanupService,
            repository
        );

        service.deleteWorkspace(REPO_OWNER, "demo-repo");

        verify(diskCleanupService).deleteWorkspaceDirectoryAsync(resolvedPath, REPO_OWNER + "/demo-repo");
    }

    @Test
    void deleteWorkspaceSkipsCleanupWhenSavedPathIsOutsideRootAndResolvedPathIsAbsent() {
        Path workspaceRoot = tempDir.resolve("workspaces");
        Path outsideSavedPath = tempDir.resolve("legacy-root").resolve("demo-repo");

        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setRoot(workspaceRoot.toString());

        RepositoryWorkspaceRepository repository = Mockito.mock(RepositoryWorkspaceRepository.class);
        WorkspaceReconcileService reconcileService = Mockito.mock(WorkspaceReconcileService.class);
        WorkspaceDiskCleanupService diskCleanupService = Mockito.mock(WorkspaceDiskCleanupService.class);
        WorkspaceGuard workspaceGuard = new WorkspaceGuard();

        when(reconcileService.deleteWorkspaceRecord(REPO_OWNER, "demo-repo"))
            .thenReturn(new WorkspaceDeletionSnapshot(REPO_OWNER, "demo-repo", outsideSavedPath));

        WorkspaceService service = new WorkspaceService(
            properties,
            workspaceGuard,
            reconcileService,
            diskCleanupService,
            repository
        );

        service.deleteWorkspace(REPO_OWNER, "demo-repo");

        verifyNoInteractions(diskCleanupService);
    }

    @Test
    void buildGitAccessFailureReturnsInternalErrorWhenWorkspaceStillPresent() throws Exception {
        Path workspaceRoot = tempDir.resolve(REPO_OWNER).resolve("present-repo");
        Files.createDirectories(workspaceRoot);
        try (Git ignored = Git.init().setDirectory(workspaceRoot.toFile()).call()) {
            // initialized git workspace only
        }

        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setRoot(tempDir.toString());

        RepositoryWorkspaceRepository repository = Mockito.mock(RepositoryWorkspaceRepository.class);
        WorkspaceDiskCleanupService diskCleanupService = Mockito.mock(WorkspaceDiskCleanupService.class);
        WorkspaceGuard workspaceGuard = new WorkspaceGuard();

        WorkspaceService service = new WorkspaceService(
            properties,
            workspaceGuard,
            new WorkspaceReconcileService(workspaceGuard, repository),
            diskCleanupService,
            repository
        );

        IOException cause = new IOException("git read failed");
        AppException exception = service.buildGitAccessFailure(REPO_OWNER, "present-repo", workspaceRoot, cause);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(exception.getMessage()).isEqualTo(WorkspaceService.GIT_ACCESS_FAILED_MESSAGE);
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void buildGitAccessFailureReconcilesWhenWorkspaceIsAbsent() {
        Path missingRoot = tempDir.resolve(REPO_OWNER).resolve("missing-repo");

        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setRoot(tempDir.toString());

        RepositoryWorkspaceRepository repository = Mockito.mock(RepositoryWorkspaceRepository.class);
        WorkspaceReconcileService reconcileService = Mockito.mock(WorkspaceReconcileService.class);
        WorkspaceDiskCleanupService diskCleanupService = Mockito.mock(WorkspaceDiskCleanupService.class);
        WorkspaceGuard workspaceGuard = new WorkspaceGuard();

        WorkspaceService service = new WorkspaceService(
            properties,
            workspaceGuard,
            reconcileService,
            diskCleanupService,
            repository
        );

        IOException cause = new IOException("git read failed");
        AppException exception = service.buildGitAccessFailure(REPO_OWNER, "missing-repo", missingRoot, cause);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.WORKSPACE_NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo(WorkspaceService.RECONCILED_NOT_FOUND_MESSAGE);
        assertThat(exception.getCause()).isSameAs(cause);
        verify(reconcileService).reconcileMissingDisk(REPO_OWNER, "missing-repo");
    }
}
