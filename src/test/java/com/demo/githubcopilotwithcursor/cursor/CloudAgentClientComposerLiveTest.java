package com.demo.githubcopilotwithcursor.cursor;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.githubcopilotwithcursor.config.CursorProperties;
import com.demo.githubcopilotwithcursor.config.GitHubProperties;
import com.demo.githubcopilotwithcursor.cursor.dto.ComposerRequest;
import com.demo.githubcopilotwithcursor.cursor.dto.ComposerResponse;
import com.demo.githubcopilotwithcursor.github.GitHubApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/**
 * Optional live verification against Cursor Cloud API when {@code CURSOR_API_KEY} and
 * {@code CURSOR_AGENT_ID} (a finished repo agent from a prior code-change run) are set.
 */
@EnabledIfEnvironmentVariable(named = "CURSOR_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "CURSOR_AGENT_ID", matches = ".+")
class CloudAgentClientComposerLiveTest {

    private CloudAgentClient client;
    private String agentId;

    @BeforeEach
    void setUp() {
        CursorProperties properties = new CursorProperties();
        properties.setApiKey(System.getenv("CURSOR_API_KEY"));
        properties.getComposer().setTimeoutMs(180000);
        GitHubApiClient gitHubApiClient = new GitHubApiClient(RestClient.builder(), new GitHubProperties());
        client = new CloudAgentClient(RestClient.builder(), properties, gitHubApiClient);
        agentId = System.getenv("CURSOR_AGENT_ID");
    }

    @Test
    void requestPrMetadataFollowUpReturnsStructuredMetadataFromLiveApi() {
        ComposerResponse response = client.requestPrMetadataFollowUp(agentId, new ComposerRequest(
            """
                You are a technical writer for GitHub pull requests.
                You already completed the code changes in this conversation.
                Respond with ONLY valid JSON (no markdown prose outside the JSON) using exactly these keys:
                commitMessage (string), prTitle (string), prBody (string, markdown).
                prBody must include sections "## 변경 요약" and "## 변경 파일".
                Do not modify any repository files.
                """,
            null,
            "",
            50,
            131072
        ));

        assertThat(response.commitMessage()).isNotBlank();
        assertThat(response.prTitle()).isNotBlank();
        assertThat(response.prBody()).contains("## 변경 요약");
        assertThat(response.prBody()).contains("## 변경 파일");
    }
}
