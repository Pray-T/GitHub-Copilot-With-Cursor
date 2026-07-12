package com.demo.githubcopilotwithcursor.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.demo.githubcopilotwithcursor.config.GitHubProperties;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubAuthTest {

    @Test
    void disablesContributeModeWhenTokenIsMissing() {
        GitHubProperties properties = new GitHubProperties();
        RestClient.Builder builder = RestClient.builder();
        GitHubApiClient client = new GitHubApiClient(builder, properties);
        GitHubAuth auth = new GitHubAuth(properties, client);

        auth.initialize();

        assertThat(auth.isContributeEnabled()).isFalse();
        assertThat(auth.authenticatedUser()).isEmpty();
        assertThatThrownBy(auth::requireAuthenticatedUser)
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.GITHUB_AUTH_REQUIRED);
    }

    @Test
    void validatesTokenAndCachesAuthenticatedUser() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubProperties properties = new GitHubProperties();
        properties.setApiBaseUrl("https://api.github.test");
        properties.setToken("secret-token");
        GitHubApiClient client = new GitHubApiClient(builder, properties);
        GitHubAuth auth = new GitHubAuth(properties, client);

        server.expect(requestTo("https://api.github.test/user"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {
                  "login": "octocat",
                  "name": "The Octocat",
                  "email": null
                }
                """, MediaType.APPLICATION_JSON));

        auth.initialize();

        assertThat(auth.isContributeEnabled()).isTrue();
        assertThat(auth.githubLogin()).isEqualTo("octocat");
        assertThat(auth.defaultAuthorName()).isEqualTo("The Octocat");
        assertThat(auth.defaultAuthorEmail()).isEqualTo("octocat@users.noreply.github.com");
        server.verify();
    }
}
