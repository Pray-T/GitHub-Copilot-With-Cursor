package com.demo.githubcopilotwithcursor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.demo.githubcopilotwithcursor.config.GitHubProperties;
import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import com.demo.githubcopilotwithcursor.repository.RepositoryWorkspaceRepository;

class PushServiceTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    @AfterEach
    void releaseTempDirectory() throws Exception {
        deleteRecursivelyWithRetry(tempDir, 8, 200L);
    }

    @Test
    void pushRejectedNonFastForwardIncludesRefUpdateStatusInDetails() throws Exception {
        Path remote = createBareRemoteWithInitialCommit(tempDir);
        Path workspace = tempDir.resolve("workspace");
        String branchName = "refactor/demo-nonff";

        try (Git git = Git.cloneRepository()
            .setURI(remote.toUri().toString())
            .setDirectory(workspace.toFile())
            .call()) {
            git.checkout().setCreateBranch(true).setName(branchName).call();
            Files.writeString(workspace.resolve("README.md"), "# Demo\n\nWorkspace change\n");
            git.add().addFilepattern("README.md").call();
            git.commit().setMessage("Workspace commit").setAuthor("Test", "test@example.com").call();
            git.push().setRemote("origin").add(branchName).call();
        }

        Path divergedSeed = tempDir.resolve("diverged-seed");
        Files.createDirectories(divergedSeed);
        try (Git divergedGit = Git.cloneRepository()
            .setURI(remote.toUri().toString())
            .setDirectory(divergedSeed.toFile())
            .call()) {
            divergedGit.fetch().setRemote("origin").call();
            divergedGit.checkout()
                .setCreateBranch(true)
                .setName(branchName)
                .setStartPoint("origin/" + branchName)
                .call();
            Files.writeString(divergedSeed.resolve("README.md"), "# Demo\n\nRemote-only change\n");
            divergedGit.add().addFilepattern("README.md").call();
            divergedGit.commit().setMessage("Remote diverged commit").setAuthor("Test", "test@example.com").call();
            divergedGit.push().setRemote("origin").add(branchName).call();
        }

        try (Git git = Git.open(workspace.toFile())) {
            Files.writeString(workspace.resolve("README.md"), "# Demo\n\nAnother workspace change\n");
            git.add().addFilepattern("README.md").call();
            git.commit().setMessage("Second workspace commit").setAuthor("Test", "test@example.com").call();
        }

        GitHubProperties gitHubProperties = new GitHubProperties();
        gitHubProperties.setToken("test-token");
        PushService pushService = new PushService(gitHubProperties, workspaceService());

        assertThatThrownBy(() -> pushService.push("spring-projects", "demo-repo", workspace, branchName))
            .isInstanceOfSatisfying(AppException.class, exception -> {
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PUSH_FAILED);
                assertThat(exception.getMessage())
                    .isEqualTo("upstream이 변경되어 push가 거부되었습니다. 워크스페이스를 삭제하고 다시 시작하세요.");
                assertThat(exception.getMessage()).doesNotContain("refUpdateStatus=");
                assertThat(exception.getDetails()).containsEntry("refUpdateStatus", "REJECTED_NONFASTFORWARD");
            });
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
                // Bare remote for push rejection scenario.
            }
            seedGit.remoteAdd().setName("origin").setUri(new URIish(remote.toUri().toString())).call();
            seedGit.push().setRemote("origin").add("master").call();
        }
        return remote;
    }

    private WorkspaceService workspaceService() {
        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setRoot(tempDir.toString());
        properties.setAllowedHosts(List.of("github.com"));
        WorkspaceGuard workspaceGuard = new WorkspaceGuard();
        RepositoryWorkspaceRepository repository = Mockito.mock(RepositoryWorkspaceRepository.class);
        return new WorkspaceService(
            properties,
            workspaceGuard,
            new WorkspaceReconcileService(workspaceGuard, repository),
            Mockito.mock(WorkspaceDiskCleanupService.class),
            repository
        );
    }

    private void deleteRecursivelyWithRetry(Path root, int attempts, long delayMs) throws Exception {
        if (root == null || !Files.exists(root)) {
            return;
        }
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                try (var paths = Files.walk(root)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                            // Windows may still hold JGit pack handles briefly.
                        }
                    });
                }
                if (!Files.exists(root)) {
                    return;
                }
            } catch (Exception ignored) {
                // Retry below.
            }
            Thread.sleep(delayMs);
        }
    }
}
