package com.demo.githubcopilotwithcursor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.dto.ContributeStatusResponse;
import com.demo.githubcopilotwithcursor.github.GitHubApiClient;
import com.demo.githubcopilotwithcursor.github.GitHubPullRequest;
import com.demo.githubcopilotwithcursor.repository.RepositoryWorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContributeStatusServiceTest {

    private static final String REPO_OWNER = "spring-projects";
    private static final String REPO_NAME = "demo-repo";

    @TempDir
    Path tempDir;

    @Test
    void statusReturnsForkBranchLastCommitAndPrState() throws Exception {
        Path workspacePath = createWorkspaceWithCommit();
        RepositoryWorkspace workspace = new RepositoryWorkspace(
            REPO_OWNER,
            REPO_NAME,
            "https://github.com/spring-projects/demo-repo",
            workspacePath.toString(),
            "a".repeat(40),
            "https://github.com/spring-projects/demo-repo",
            "https://github.com/octocat/demo-repo",
            true,
            "refactor/demo-repo-202605041900"
        );
        workspace.attachPullRequest("https://github.com/spring-projects/demo-repo/pull/9");

        RepositoryWorkspaceRepository repository = mock(RepositoryWorkspaceRepository.class);
        when(repository.findByRepoOwnerAndRepoName(REPO_OWNER, REPO_NAME)).thenReturn(Optional.of(workspace));
        GitHubApiClient gitHubApiClient = mock(GitHubApiClient.class);
        when(gitHubApiClient.getPullRequest(REPO_OWNER, REPO_NAME, 9))
            .thenReturn(new GitHubPullRequest("https://github.com/spring-projects/demo-repo/pull/9", 9, "closed", false, true));

        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        workspaceProperties.setRoot(tempDir.toString());
        WorkspaceGuard workspaceGuard = new WorkspaceGuard();
        WorkspaceService workspaceService = new WorkspaceService(
            workspaceProperties,
            workspaceGuard,
            new WorkspaceReconcileService(workspaceGuard, repository),
            new WorkspaceDiskCleanupService(workspaceProperties),
            repository
        );
        ContributeStatusService service = new ContributeStatusService(workspaceGuard, workspaceService, gitHubApiClient);

        ContributeStatusResponse response = service.status(REPO_OWNER, REPO_NAME);

        assertThat(response.repoOwner()).isEqualTo(REPO_OWNER);
        assertThat(response.repoName()).isEqualTo(REPO_NAME);
        assertThat(response.mode()).isEqualTo("CONTRIBUTE");
        assertThat(response.fork().url()).isEqualTo("https://github.com/octocat/demo-repo");
        assertThat(response.fork().reused()).isTrue();
        assertThat(response.branch()).isEqualTo("refactor/demo-repo-202605041900");
        assertThat(response.agent()).isNotNull();
        assertThat(response.lastCommit()).isNotNull();
        assertThat(response.lastCommit().message()).isEqualTo("Initial commit");
        assertThat(response.prUrl()).isEqualTo("https://github.com/spring-projects/demo-repo/pull/9");
        assertThat(response.prState()).isEqualTo("merged");
        assertThat(response.llmCache()).isNotNull();
        assertThat(response.llmCache().hasCommitMessage()).isFalse();
    }

    private Path createWorkspaceWithCommit() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace);
        try (Git git = Git.init().setDirectory(workspace.toFile()).call()) {
            Files.writeString(workspace.resolve("README.md"), "# Demo\n");
            git.add().addFilepattern("README.md").call();
            git.commit()
                .setMessage("Initial commit")
                .setAuthor("Test", "test@example.com")
                .call();
        }
        return workspace;
    }
}
