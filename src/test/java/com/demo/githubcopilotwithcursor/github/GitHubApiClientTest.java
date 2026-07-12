package com.demo.githubcopilotwithcursor.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.demo.githubcopilotwithcursor.config.GitHubProperties;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubApiClientTest {

    private GitHubProperties properties;
    private GitHubApiClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        properties = new GitHubProperties();
        properties.setApiBaseUrl("https://api.github.test");
        properties.setApiVersion("2022-11-28");
        properties.setToken("secret-token");
        client = new GitHubApiClient(builder, properties);
    }

    @Test
    void getAuthenticatedUserSendsBearerTokenAndMapsResponse() {
        server.expect(requestTo("https://api.github.test/user"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret-token"))
            .andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
            .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
            .andRespond(withSuccess("""
                {
                  "login": "octocat",
                  "name": "The Octocat",
                  "email": "octocat@example.com"
                }
                """, MediaType.APPLICATION_JSON));

        GitHubAuthenticatedUser user = client.getAuthenticatedUser();

        assertThat(user.login()).isEqualTo("octocat");
        assertThat(user.name()).isEqualTo("The Octocat");
        assertThat(user.email()).isEqualTo("octocat@example.com");
        server.verify();
    }

    @Test
    void createPullRequestPostsPayloadAndMapsResponse() {
        server.expect(requestTo("https://api.github.test/repos/spring-projects/spring-petclinic/pulls"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret-token"))
            .andRespond(withSuccess("""
                {
                  "html_url": "https://github.com/spring-projects/spring-petclinic/pull/123",
                  "number": 123,
                  "state": "open",
                  "draft": true
                }
                """, MediaType.APPLICATION_JSON));

        GitHubPullRequest pullRequest = client.createPullRequest(
            "spring-projects",
            "spring-petclinic",
            new CreatePullRequestPayload("Title", "Body", "main", "octocat:refactor/demo", true)
        );

        assertThat(pullRequest.htmlUrl()).isEqualTo("https://github.com/spring-projects/spring-petclinic/pull/123");
        assertThat(pullRequest.number()).isEqualTo(123);
        assertThat(pullRequest.state()).isEqualTo("open");
        assertThat(pullRequest.draft()).isTrue();
        server.verify();
    }

    @Test
    void getPullRequestMapsMergedState() {
        server.expect(requestTo("https://api.github.test/repos/spring-projects/spring-petclinic/pulls/123"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret-token"))
            .andRespond(withSuccess("""
                {
                  "html_url": "https://github.com/spring-projects/spring-petclinic/pull/123",
                  "number": 123,
                  "state": "closed",
                  "draft": false,
                  "merged": true
                }
                """, MediaType.APPLICATION_JSON));

        GitHubPullRequest pullRequest = client.getPullRequest("spring-projects", "spring-petclinic", 123);

        assertThat(pullRequest.htmlUrl()).isEqualTo("https://github.com/spring-projects/spring-petclinic/pull/123");
        assertThat(pullRequest.state()).isEqualTo("closed");
        assertThat(pullRequest.merged()).isTrue();
        server.verify();
    }

    @Test
    void missingTokenThrowsAuthRequiredBeforeRequest() {
        properties.setToken("");

        assertThatThrownBy(() -> client.getAuthenticatedUser())
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.GITHUB_AUTH_REQUIRED);
    }

    @Test
    void githubErrorIsMappedAndTokenIsMasked() {
        server.expect(requestTo("https://api.github.test/user"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"bad secret-token\"}"));

        assertThatThrownBy(() -> client.getAuthenticatedUser())
            .isInstanceOfSatisfying(GitHubApiException.class, exception -> {
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GITHUB_API_ERROR);
                assertThat(exception.getGithubStatus()).isEqualTo(403);
                assertThat(exception.getGithubBody()).doesNotContain("secret-token");
                assertThat(exception.getGithubBody()).contains("***");
            });
        server.verify();
    }

    @Test
    void forkRepositoryMapsUpstreamDetailsOnFailure() {
        server.expect(requestTo("https://api.github.test/repos/spring-projects/spring-petclinic/forks"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"Resource not accessible\"}"));

        assertThatThrownBy(() -> client.forkRepository("spring-projects", "spring-petclinic"))
            .isInstanceOfSatisfying(GitHubApiException.class, exception -> {
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORK_FAILED);
                assertThat(exception.getMessage())
                    .isEqualTo("Fine-grained PAT에 Contents: Write, Pull requests: Write 스코프가 필요합니다.");
                assertThat(exception.getGithubStatus()).isEqualTo(403);
                assertThat(exception.getDetails())
                    .containsEntry("upstreamOwner", "spring-projects")
                    .containsEntry("upstreamRepo", "spring-petclinic")
                    .containsEntry("githubStatus", 403);
            });

        server.verify();
    }

    @Test
    void createPullRequestExtractsExistingPrUrlFrom422Body() {
        server.expect(requestTo("https://api.github.test/repos/spring-projects/spring-petclinic/pulls"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatusCode.valueOf(422))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {
                      "message": "Validation Failed",
                      "errors": [
                        {
                          "message": "A pull request already exists for octocat:refactor/demo. See pull request #321"
                        }
                      ]
                    }
                    """));

        assertThatThrownBy(() -> client.createPullRequest(
            "spring-projects",
            "spring-petclinic",
            new CreatePullRequestPayload("Title", "Body", "main", "octocat:refactor/demo", true)
        )).isInstanceOfSatisfying(GitHubApiException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PR_CREATE_FAILED);
            assertThat(exception.getGithubStatus()).isEqualTo(422);
            assertThat(exception.getDetails())
                .containsEntry("existingPrUrl", "https://github.com/spring-projects/spring-petclinic/pull/321");
        });

        server.verify();
    }

    @Test
    void createPullRequestFallsBackToOpenPullLookupWhen422BodyHasNoNumber() {
        server.expect(requestTo("https://api.github.test/repos/spring-projects/spring-petclinic/pulls"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatusCode.valueOf(422))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {
                      "message": "A pull request already exists for octocat:refactor/demo."
                    }
                    """));
        server.expect(requestTo("https://api.github.test/repos/spring-projects/spring-petclinic/pulls?head=octocat:refactor/demo&state=open"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                [
                  {
                    "html_url": "https://github.com/spring-projects/spring-petclinic/pull/654",
                    "number": 654,
                    "state": "open",
                    "draft": true
                  }
                ]
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.createPullRequest(
            "spring-projects",
            "spring-petclinic",
            new CreatePullRequestPayload("Title", "Body", "main", "octocat:refactor/demo", true)
        )).isInstanceOfSatisfying(GitHubApiException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PR_CREATE_FAILED);
            assertThat(exception.getDetails())
                .containsEntry("existingPrUrl", "https://github.com/spring-projects/spring-petclinic/pull/654");
        });

        server.verify();
    }
}
