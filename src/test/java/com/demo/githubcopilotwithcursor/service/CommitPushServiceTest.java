package com.demo.githubcopilotwithcursor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.githubcopilotwithcursor.config.GitHubProperties;
import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.dto.ChangedFileResponse;
import com.demo.githubcopilotwithcursor.dto.CommitPushRequest;
import com.demo.githubcopilotwithcursor.dto.CommitPushResponse;
import com.demo.githubcopilotwithcursor.dto.DiffResponse;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import com.demo.githubcopilotwithcursor.github.GitHubAuth;
import com.demo.githubcopilotwithcursor.github.GitHubAuthenticatedUser;
import com.demo.githubcopilotwithcursor.repository.RepositoryWorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

class CommitPushServiceTest {

    private static final String REPO_OWNER = "spring-projects";
    private static final String REPO_NAME = "demo-repo";

    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    @AfterEach
    void cleanupTempDir() {
        if (tempDir == null || !Files.exists(tempDir)) {
            return;
        }
        try {
            FileUtils.delete(tempDir.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
        } catch (Exception ignored) {
            // Windows JGit pack lock — best-effort cleanup
        }
    }

    @Test
    void commitAndPushCreatesCommitAndPushesFeatureBranchWithoutForce() throws Exception {
        Path remote = createBareRemoteWithInitialCommit(tempDir);
        Path workspace = tempDir.resolve("workspace");
        String branchName = "refactor/demo-repo-202605011830";

        try (Git git = Git.cloneRepository()
            .setURI(remote.toUri().toString())
            .setDirectory(workspace.toFile())
            .call()) {
            git.checkout().setCreateBranch(true).setName(branchName).call();
        }
        Files.writeString(workspace.resolve("README.md"), "# Demo\n\nChanged\n");

        RepositoryWorkspace workspaceEntity = new RepositoryWorkspace(
            REPO_OWNER,
            REPO_NAME,
            "https://github.com/spring-projects/demo-repo",
            workspace.toString(),
            headOf(workspace),
            "https://github.com/spring-projects/demo-repo",
            remote.toUri().toString(),
            true,
            branchName
        );

        RepositoryWorkspaceRepository repository = mock(RepositoryWorkspaceRepository.class);
        when(repository.findByRepoOwnerAndRepoName(REPO_OWNER, REPO_NAME)).thenReturn(Optional.of(workspaceEntity));

        GitHubAuth gitHubAuth = mock(GitHubAuth.class);
        when(gitHubAuth.requireAuthenticatedUser())
            .thenReturn(new GitHubAuthenticatedUser("octocat", "The Octocat", "octocat@example.com"));

        GitHubProperties gitHubProperties = new GitHubProperties();
        gitHubProperties.setToken("test-token");

        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        workspaceProperties.setRoot(tempDir.toString());
        WorkspaceGuard workspaceGuard = new WorkspaceGuard();
        WorkspaceDiskCleanupService diskCleanupService = mock(WorkspaceDiskCleanupService.class);
        WorkspaceService workspaceService = new WorkspaceService(
            workspaceProperties,
            workspaceGuard,
            new WorkspaceReconcileService(workspaceGuard, repository),
            diskCleanupService,
            repository
        );

        CommitPushService service = new CommitPushService(
            new WorkspaceGuard(),
            workspaceService,
            gitHubAuth,
            new CommitService(workspaceService),
            new PushService(gitHubProperties, workspaceService),
            mock(DiffService.class),
            new DiffFingerprintService()
        );

        CommitPushResponse response = service.commitAndPush(
            REPO_OWNER,
            REPO_NAME,
            new CommitPushRequest("Update README", "The Octocat", "octocat@example.com")
        );

        assertThat(response.repoOwner()).isEqualTo(REPO_OWNER);
        assertThat(response.repoName()).isEqualTo(REPO_NAME);
        assertThat(response.branchName()).isEqualTo(branchName);
        assertThat(response.commitSha()).hasSize(40);
        assertThat(response.pushedTo()).isEqualTo(remote.toUri().toString());

        try (Repository remoteRepository = new FileRepositoryBuilder()
            .setGitDir(remote.toFile())
            .setBare()
            .build()) {
            ObjectId pushedHead = remoteRepository.resolve("refs/heads/" + branchName);
            assertThat(pushedHead).isNotNull();
            assertThat(pushedHead.name()).isEqualTo(response.commitSha());
        }
    }

    @Test
    void commitAndPushReconcilesWhenLocalWorkspaceWasRemoved() {
        RepositoryWorkspace workspaceEntity = new RepositoryWorkspace(
            REPO_OWNER,
            REPO_NAME,
            "https://github.com/spring-projects/demo-repo",
            tempDir.resolve("missing-workspace").toString(),
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "https://github.com/spring-projects/demo-repo",
            "https://github.com/octocat/demo-repo",
            true,
            "refactor/demo-repo-202605011830"
        );

        RepositoryWorkspaceRepository repository = mock(RepositoryWorkspaceRepository.class);
        when(repository.findByRepoOwnerAndRepoName(REPO_OWNER, REPO_NAME)).thenReturn(Optional.of(workspaceEntity));

        GitHubAuth gitHubAuth = mock(GitHubAuth.class);
        when(gitHubAuth.requireAuthenticatedUser())
            .thenReturn(new GitHubAuthenticatedUser("octocat", "The Octocat", "octocat@example.com"));

        GitHubProperties gitHubProperties = new GitHubProperties();
        gitHubProperties.setToken("test-token");

        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        workspaceProperties.setRoot(tempDir.toString());
        WorkspaceGuard workspaceGuard = new WorkspaceGuard();
        WorkspaceReconcileService reconcileService = mock(WorkspaceReconcileService.class);
        WorkspaceService workspaceService = new WorkspaceService(
            workspaceProperties,
            workspaceGuard,
            reconcileService,
            mock(WorkspaceDiskCleanupService.class),
            repository
        );

        CommitPushService service = new CommitPushService(
            workspaceGuard,
            workspaceService,
            gitHubAuth,
            new CommitService(workspaceService),
            new PushService(gitHubProperties, workspaceService),
            mock(DiffService.class),
            new DiffFingerprintService()
        );

        assertThatThrownBy(() -> service.commitAndPush(
            REPO_OWNER,
            REPO_NAME,
            new CommitPushRequest("Update README", "The Octocat", "octocat@example.com")
        ))
            .isInstanceOfSatisfying(AppException.class, exception -> {
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.WORKSPACE_NOT_FOUND);
                assertThat(exception.getMessage()).isEqualTo(WorkspaceService.RECONCILED_NOT_FOUND_MESSAGE);
            });

        verify(reconcileService, atLeastOnce()).reconcileMissingDisk(REPO_OWNER, REPO_NAME);
    }

    @Test
    void commitAndPushSyncsLlmDiffFingerprintAfterSuccessfulPush() {
        Path workspacePath = tempDir.resolve("workspace-with-llm-cache");
        RepositoryWorkspace workspaceEntity = new RepositoryWorkspace(
            REPO_OWNER,
            REPO_NAME,
            "https://github.com/spring-projects/demo-repo",
            workspacePath.toString(),
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "https://github.com/spring-projects/demo-repo",
            "https://github.com/octocat/demo-repo",
            true,
            "refactor/demo-repo-202605011830"
        );
        workspaceEntity.cacheLlmMetadata(
            "feat: cached metadata",
            "Cached PR title",
            "Cached PR body",
            OffsetDateTime.now(),
            "old-fingerprint"
        );

        GitHubAuth gitHubAuth = mock(GitHubAuth.class);
        when(gitHubAuth.requireAuthenticatedUser())
            .thenReturn(new GitHubAuthenticatedUser("octocat", "The Octocat", "octocat@example.com"));
        WorkspaceService workspaceService = mock(WorkspaceService.class);
        when(workspaceService.requireContributeWorkspace(REPO_OWNER, REPO_NAME)).thenReturn(workspaceEntity);
        CommitService commitService = mock(CommitService.class);
        when(commitService.commit(eq(REPO_OWNER), eq(REPO_NAME), eq(workspacePath), org.mockito.ArgumentMatchers.any()))
            .thenReturn("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        PushService pushService = mock(PushService.class);
        DiffService diffService = mock(DiffService.class);
        DiffResponse postCommitDiff = sampleDiff("README.md", "MODIFIED", "post commit content");
        when(diffService.diffWithoutPersist(REPO_OWNER, REPO_NAME, true, 0)).thenReturn(postCommitDiff);
        DiffFingerprintService diffFingerprintService = new DiffFingerprintService();
        String expectedFingerprint = diffFingerprintService.compute(postCommitDiff);

        CommitPushService service = new CommitPushService(
            new WorkspaceGuard(),
            workspaceService,
            gitHubAuth,
            commitService,
            pushService,
            diffService,
            diffFingerprintService
        );

        CommitPushResponse response = service.commitAndPush(
            REPO_OWNER,
            REPO_NAME,
            new CommitPushRequest("Update README", "The Octocat", "octocat@example.com")
        );

        assertThat(response.commitSha()).isEqualTo("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        assertThat(workspaceEntity.getLlmDiffFingerprint()).isEqualTo(expectedFingerprint);
        assertThat(workspaceEntity.getLlmCommitMessage()).isEqualTo("feat: cached metadata");
        assertThat(workspaceEntity.getLlmPrTitle()).isEqualTo("Cached PR title");
        assertThat(workspaceEntity.getLlmPrBody()).isEqualTo("Cached PR body");
        verify(pushService).push(REPO_OWNER, REPO_NAME, workspacePath, "refactor/demo-repo-202605011830");
        verify(diffService).diffWithoutPersist(REPO_OWNER, REPO_NAME, true, 0);
        verify(workspaceService).saveWorkspace(workspaceEntity);
    }

    @Test
    void commitBuildsGitAccessFailureWhenWorkspaceDirectoryIsMissing() {
        Path missingWorkspace = tempDir.resolve("missing-commit-target");
        CommitService commitService = new CommitService(workspaceServiceForMissingPath(missingWorkspace));

        assertThatThrownBy(() -> commitService.commit(
            REPO_OWNER,
            REPO_NAME,
            missingWorkspace,
            new CommitPushRequest("Update README", "The Octocat", "octocat@example.com")
        ))
            .isInstanceOfSatisfying(AppException.class, exception -> {
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.WORKSPACE_NOT_FOUND);
                assertThat(exception.getMessage()).isEqualTo(WorkspaceService.RECONCILED_NOT_FOUND_MESSAGE);
            });
    }

    private WorkspaceService workspaceServiceForMissingPath(Path missingWorkspace) {
        RepositoryWorkspace workspaceEntity = new RepositoryWorkspace(
            REPO_OWNER,
            REPO_NAME,
            "https://github.com/spring-projects/demo-repo",
            missingWorkspace.toString(),
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "https://github.com/spring-projects/demo-repo",
            "https://github.com/octocat/demo-repo",
            true,
            "refactor/demo-repo-202605011830"
        );

        RepositoryWorkspaceRepository repository = mock(RepositoryWorkspaceRepository.class);
        when(repository.findByRepoOwnerAndRepoName(REPO_OWNER, REPO_NAME)).thenReturn(Optional.of(workspaceEntity));

        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        workspaceProperties.setRoot(tempDir.toString());
        WorkspaceGuard workspaceGuard = new WorkspaceGuard();
        WorkspaceReconcileService reconcileService = mock(WorkspaceReconcileService.class);
        return new WorkspaceService(
            workspaceProperties,
            workspaceGuard,
            reconcileService,
            mock(WorkspaceDiskCleanupService.class),
            repository
        );
    }

    private Path createBareRemoteWithInitialCommit(Path testRoot) throws Exception {
        Path seed = testRoot.resolve("seed");
        Path remote = testRoot.resolve("remote.git");
        Files.createDirectories(seed);
        try (Git seedGit = Git.init().setDirectory(seed.toFile()).call()) {
            Files.writeString(seed.resolve("README.md"), "# Demo\n");
            seedGit.add().addFilepattern("README.md").call();
            seedGit.commit()
                .setMessage("Initial commit")
                .setAuthor("Test", "test@example.com")
                .call();

            try (Git ignored = Git.init().setBare(true).setDirectory(remote.toFile()).call()) {
                // Bare remote is used as the fork origin in this local integration test.
            }
            seedGit.remoteAdd().setName("origin").setUri(new URIish(remote.toUri().toString())).call();
            seedGit.push().setRemote("origin").add("master").call();
        }
        return remote;
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
        return new DiffResponse(
            REPO_OWNER,
            REPO_NAME,
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            OffsetDateTime.now(),
            1,
            List.of(file)
        );
    }

    private String headOf(Path workspace) throws Exception {
        try (Git git = Git.open(workspace.toFile())) {
            return git.getRepository().resolve("HEAD").name();
        }
    }
}
