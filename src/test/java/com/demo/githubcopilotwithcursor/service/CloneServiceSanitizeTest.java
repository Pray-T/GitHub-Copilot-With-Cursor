package com.demo.githubcopilotwithcursor.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.githubcopilotwithcursor.config.GitHubProperties;
import com.demo.githubcopilotwithcursor.github.GitHubApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class CloneServiceSanitizeTest {

    @Test
    void sanitizeGitMessageMasksConfiguredToken() {
        GitHubProperties properties = new GitHubProperties();
        properties.setToken("ghp_secret_token_value");
        GitHubApiClient gitHubApiClient = new GitHubApiClient(RestClient.builder(), properties);

        String sanitized = gitHubApiClient.maskToken("Auth failed for ghp_secret_token_value in URL");

        assertThat(sanitized).isEqualTo("Auth failed for *** in URL");
        assertThat(sanitized).doesNotContain("ghp_secret_token_value");
    }
}
