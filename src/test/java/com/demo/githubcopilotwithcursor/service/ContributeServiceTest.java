package com.demo.githubcopilotwithcursor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.githubcopilotwithcursor.config.ContributeProperties;
import com.demo.githubcopilotwithcursor.config.GitHubProperties;
import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.domain.WorkspaceMode;
import com.demo.githubcopilotwithcursor.domain.WorkspaceStatus;
import com.demo.githubcopilotwithcursor.github.GitHubApiClient;
import com.demo.githubcopilotwithcursor.github.GitHubAuthenticatedUser;
import com.demo.githubcopilotwithcursor.github.GitHubRepository;
import com.demo.githubcopilotwithcursor.repository.RepositoryWorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class ContributeServiceTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    @Test
    void bootstrapReusesExistingForkAndCreatesFeatureBranchWorkspace() throws Exception {
        Path sourceRepo = createSourceRepository(tempDir);
        Path workspaceRoot = tempDir.resolve("workspaces");

        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        workspaceProperties.setRoot(workspaceRoot.toString());
        workspaceProperties.setAllowedHosts(List.of("github.com"));

        ContributeProperties contributeProperties = new ContributeProperties();
        GitHubProperties gitHubProperties = new GitHubProperties();
        gitHubProperties.setToken("test-token");

        WorkspaceGuard workspaceGuard = new WorkspaceGuard();
        GitHubApiClient gitHubApiClient = mock(GitHubApiClient.class);
        RepositoryWorkspaceRepository repository = mock(RepositoryWorkspaceRepository.class);

        when(gitHubApiClient.findRepository("octocat", "demo-repo"))
            .thenReturn(Optional.of(new GitHubRepository(
                "octocat/demo-repo",
                "https://github.com/octocat/demo-repo",
                sourceRepo.toUri().toString(),
                "master"
            )));
        when(repository.save(any(RepositoryWorkspace.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkspaceService workspaceService = new WorkspaceService(
            workspaceProperties,
            workspaceGuard,
            new WorkspaceReconcileService(workspaceGuard, repository),
            mock(WorkspaceDiskCleanupService.class),
            repository
        );
        when(repository.existsByRepoOwnerAndRepoName("spring-projects", "demo-repo")).thenReturn(false);

        WorkspaceBootstrapService service = new WorkspaceBootstrapService(
            workspaceProperties,
            contributeProperties,
            gitHubProperties,
            workspaceGuard,
            workspaceService,
            gitHubApiClient
        );

        RepositoryWorkspace workspace = service.bootstrap(
            "https://github.com/spring-projects/demo-repo",
            new com.demo.githubcopilotwithcursor.github.GitHubRepoRef("spring-projects", "demo-repo"),
            new GitHubAuthenticatedUser("octocat", "The Octocat", "octocat@example.com"),
            WorkspaceMode.CONTRIBUTE,
            "Refactor demo repository"
        );

        assertThat(workspace.getRepoOwner()).isEqualTo("spring-projects");
        assertThat(workspace.getRepoName()).isEqualTo("demo-repo");
        assertThat(workspace.getUpstreamUrl()).isEqualTo("https://github.com/spring-projects/demo-repo");
        assertThat(workspace.getForkUrl()).isEqualTo("https://github.com/octocat/demo-repo");
        assertThat(workspace.isForkReused()).isTrue();
        assertThat(workspace.getBranchName()).startsWith("refactor/demo-repo-");
        assertThat(workspace.getStatus()).isEqualTo(WorkspaceStatus.CREATED);
        assertThat(workspace.getMode()).isEqualTo(WorkspaceMode.CONTRIBUTE);
        assertThat(Files.exists(workspaceRoot.resolve("spring-projects").resolve("demo-repo").resolve("README.md"))).isTrue();

        try (Repository clonedRepository = new FileRepositoryBuilder()
            .setGitDir(workspaceRoot.resolve("spring-projects").resolve("demo-repo").resolve(".git").toFile())
            .build()) {
            assertThat(clonedRepository.getConfig().getString("remote", "upstream", "url"))
                .isEqualTo("https://github.com/spring-projects/demo-repo");
            assertThat(clonedRepository.getBranch()).isEqualTo(workspace.getBranchName());
        }

        ArgumentCaptor<RepositoryWorkspace> workspaceCaptor = ArgumentCaptor.forClass(RepositoryWorkspace.class);
        verify(repository, times(1)).save(workspaceCaptor.capture());
        RepositoryWorkspace savedWorkspace = workspaceCaptor.getValue();
        assertThat(savedWorkspace.getUpstreamUrl()).isEqualTo("https://github.com/spring-projects/demo-repo");
        assertThat(savedWorkspace.getForkUrl()).isEqualTo("https://github.com/octocat/demo-repo");
        assertThat(savedWorkspace.isForkReused()).isTrue();
        assertThat(savedWorkspace.getBranchName()).isEqualTo(workspace.getBranchName());
    }

    @AfterEach
    void releaseWorkspaceDirectory() throws Exception {
        Path workspaceRoot = tempDir.resolve("workspaces");
        deleteRecursivelyWithRetry(workspaceRoot, 12, 250L);
        System.gc();
        Thread.sleep(250L);
        deleteRecursivelyWithRetry(workspaceRoot, 4, 250L);
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
                        }
                    });
                }
                if (!Files.exists(root)) {
                    return;
                }
            } catch (Exception ignored) {
            }
            Thread.sleep(delayMs);
        }
    }

    private Path createSourceRepository(Path testRoot) throws Exception {
        Path sourceRepo = testRoot.resolve("source-repo");
        Files.createDirectories(sourceRepo);
        try (Git git = Git.init().setDirectory(sourceRepo.toFile()).call()) {
            Files.writeString(sourceRepo.resolve("README.md"), "# Demo\n");
            git.add().addFilepattern("README.md").call();
            git.commit()
                .setMessage("Initial commit")
                .setAuthor("Test", "test@example.com")
                .call();
        }
        return sourceRepo;
    }
}
