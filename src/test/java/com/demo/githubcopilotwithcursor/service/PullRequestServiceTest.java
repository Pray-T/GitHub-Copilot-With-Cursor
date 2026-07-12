package com.demo.githubcopilotwithcursor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.dto.ChangedFileResponse;
import com.demo.githubcopilotwithcursor.dto.DiffResponse;
import com.demo.githubcopilotwithcursor.dto.PullRequestRequest;
import com.demo.githubcopilotwithcursor.dto.PullRequestResponse;
import com.demo.githubcopilotwithcursor.github.CreatePullRequestPayload;
import com.demo.githubcopilotwithcursor.github.GitHubApiClient;
import com.demo.githubcopilotwithcursor.github.GitHubAuth;
import com.demo.githubcopilotwithcursor.github.GitHubPullRequest;
import com.demo.githubcopilotwithcursor.github.GitHubRepository;
import com.demo.githubcopilotwithcursor.repository.RepositoryWorkspaceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PullRequestServiceTest {

    private static final String REPO_OWNER = "spring-projects";
    private static final String REPO_NAME = "demo-repo";

    @Test
    void createPullRequestUsesDefaultBaseHeadAndStoresPrUrl() {
        RepositoryWorkspace workspace = contributeWorkspace();
        RepositoryWorkspaceRepository repository = mock(RepositoryWorkspaceRepository.class);
        GitHubAuth gitHubAuth = mock(GitHubAuth.class);
        GitHubApiClient gitHubApiClient = mock(GitHubApiClient.class);
        DiffService diffService = mock(DiffService.class);

        WorkspaceService workspaceService = mock(WorkspaceService.class);
        when(workspaceService.requireContributeWorkspace(REPO_OWNER, REPO_NAME)).thenReturn(workspace);
        when(gitHubAuth.githubLogin()).thenReturn("octocat");
        when(gitHubApiClient.getRepository(REPO_OWNER, REPO_NAME))
            .thenReturn(new GitHubRepository(
                "spring-projects/demo-repo",
                "https://github.com/spring-projects/demo-repo",
                "https://github.com/spring-projects/demo-repo.git",
                "main"
            ));
        when(gitHubApiClient.createPullRequest(eq(REPO_OWNER), eq(REPO_NAME), any(CreatePullRequestPayload.class)))
            .thenReturn(new GitHubPullRequest("https://github.com/spring-projects/demo-repo/pull/7", 7, "open", true, false));

        PullRequestService service = new PullRequestService(
            new WorkspaceGuard(),
            workspaceService,
            repository,
            gitHubAuth,
            gitHubApiClient,
            diffService
        );

        PullRequestResponse response = service.createPullRequest(
            REPO_OWNER,
            REPO_NAME,
            new PullRequestRequest("Refactor demo", "Body", null, null)
        );

        ArgumentCaptor<CreatePullRequestPayload> payloadCaptor = ArgumentCaptor.forClass(CreatePullRequestPayload.class);
        verify(gitHubApiClient).createPullRequest(eq(REPO_OWNER), eq(REPO_NAME), payloadCaptor.capture());
        CreatePullRequestPayload payload = payloadCaptor.getValue();
        assertThat(payload.title()).isEqualTo("Refactor demo");
        assertThat(payload.body()).isEqualTo("Body");
        assertThat(payload.base()).isEqualTo("main");
        assertThat(payload.head()).isEqualTo("octocat:refactor/demo-repo-202605041800");
        assertThat(payload.draft()).isTrue();

        assertThat(response.prUrl()).isEqualTo("https://github.com/spring-projects/demo-repo/pull/7");
        assertThat(workspace.getPrUrl()).isEqualTo("https://github.com/spring-projects/demo-repo/pull/7");
        verify(repository).save(workspace);
    }

    @Test
    void draftBuildsBodyFromDiffChangedFilesWithoutLlm() {
        RepositoryWorkspace workspace = contributeWorkspace();
        RepositoryWorkspaceRepository repository = mock(RepositoryWorkspaceRepository.class);
        GitHubAuth gitHubAuth = mock(GitHubAuth.class);
        GitHubApiClient gitHubApiClient = mock(GitHubApiClient.class);
        DiffService diffService = mock(DiffService.class);

        WorkspaceService workspaceService = mock(WorkspaceService.class);
        when(workspaceService.requireContributeWorkspace(REPO_OWNER, REPO_NAME)).thenReturn(workspace);
        when(gitHubApiClient.getRepository(REPO_OWNER, REPO_NAME))
            .thenReturn(new GitHubRepository(
                "spring-projects/demo-repo",
                "https://github.com/spring-projects/demo-repo",
                "https://github.com/spring-projects/demo-repo.git",
                "main"
            ));
        when(diffService.diffWithoutPersist(REPO_OWNER, REPO_NAME, false, 1)).thenReturn(new DiffResponse(
            REPO_OWNER,
            REPO_NAME,
            "a".repeat(40),
            OffsetDateTime.now(),
            2,
            List.of(
                new ChangedFileResponse("src/Foo.java", null, "MODIFIED", false, false, 10, 20, null, null, false),
                new ChangedFileResponse("src/Bar.java", null, "ADDED", false, false, 0, 15, null, null, false)
            )
        ));

        PullRequestService service = new PullRequestService(
            new WorkspaceGuard(),
            workspaceService,
            repository,
            gitHubAuth,
            gitHubApiClient,
            diffService
        );

        PullRequestService.PullRequestDraft draft = service.draft(REPO_OWNER, REPO_NAME);

        assertThat(draft.title()).isEqualTo("Update demo-repo");
        assertThat(draft.base()).isEqualTo("main");
        assertThat(draft.draft()).isTrue();
        assertThat(draft.body()).contains("- M src/Foo.java", "- A src/Bar.java");
    }

    private RepositoryWorkspace contributeWorkspace() {
        return new RepositoryWorkspace(
            REPO_OWNER,
            REPO_NAME,
            "https://github.com/spring-projects/demo-repo",
            "C:/tmp/demo-repo",
            "a".repeat(40),
            "https://github.com/spring-projects/demo-repo",
            "https://github.com/octocat/demo-repo",
            true,
            "refactor/demo-repo-202605041800"
        );
    }
}
