package com.demo.githubcopilotwithcursor.cursor;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.demo.githubcopilotwithcursor.config.CursorProperties;
import com.demo.githubcopilotwithcursor.config.GitHubProperties;
import com.demo.githubcopilotwithcursor.cursor.dto.ComposerRequest;
import com.demo.githubcopilotwithcursor.cursor.dto.ComposerResponse;
import com.demo.githubcopilotwithcursor.cursor.dto.StartAgentRequest;
import com.demo.githubcopilotwithcursor.cursor.dto.StartAgentResponse;
import com.demo.githubcopilotwithcursor.github.GitHubApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CloudAgentClientTest {

    private CursorProperties properties;
    private CloudAgentClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        properties = new CursorProperties();
        properties.setApiBaseUrl("https://api.cursor.test");
        properties.setApiVersion("v1");
        properties.setApiKey("cursor-secret-key");
        GitHubApiClient gitHubApiClient = new GitHubApiClient(RestClient.builder(), new GitHubProperties());
        client = new CloudAgentClient(builder, properties, gitHubApiClient);
    }

    @Test
    void startAgentSendsBearerTokenAndForcesAutoCreatePrFalse() {
        server.expect(requestTo("https://api.cursor.test/v1/agents"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer cursor-secret-key"))
            .andExpect(content().string(containsString("\"model\":{\"id\":\"composer-2.5\",\"params\":[{\"id\":\"fast\",\"value\":\"true\"}]}")))
            .andExpect(content().string(containsString("\"autoCreatePR\":false")))
            .andRespond(withSuccess("""
                {
                  "agent": {
                    "id": "bc-test-001",
                    "latestRunId": "run-test-001",
                    "status": "ACTIVE"
                  },
                  "run": {
                    "id": "run-test-001",
                    "status": "CREATING",
                    "createdAt": "2026-05-28T10:00:00Z"
                  }
                }
                """, MediaType.APPLICATION_JSON));

        StartAgentResponse response = client.startAgent(
            StartAgentRequest.of(
                "Refactor Owner.java",
                "https://github.com/octocat/demo",
                "refactor/demo-1",
                "abc123"
            )
        );

        assertThat(response.agentId()).isEqualTo("bc-test-001");
        assertThat(response.runId()).isEqualTo("run-test-001");
        assertThat(CloudAgentClient.AUTO_CREATE_PR).isFalse();
        assertThat(CloudAgentClient.LOCKED_MODEL_ID).isEqualTo("composer-2.5");
        assertThat(CloudAgentClient.LOCKED_MODEL_FAST_VALUE).isEqualTo("true");
        server.verify();
    }

    @Test
    void verifyApiKeyCallsMeEndpoint() {
        server.expect(requestTo("https://api.cursor.test/v1/me"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer cursor-secret-key"))
            .andRespond(withSuccess("""
                {
                  "apiKeyName": "Test Key",
                  "userId": 42,
                  "userEmail": "dev@example.com"
                }
                """, MediaType.APPLICATION_JSON));

        assertThat(client.verifyApiKey().userId()).isEqualTo("42");
        server.verify();
    }

    @Test
    void startAgentRequiresApiKey() {
        properties.setApiKey("");
        assertThatThrownBy(() -> client.startAgent(
            StartAgentRequest.of("prompt", "https://github.com/a/b", "branch", "sha")
        )).hasMessageContaining("CURSOR_API_KEY");
    }

    @Test
    void requestPrMetadataFollowUpUsesRepoAgentFollowUpEndpoint() {
        server.expect(requestTo("https://api.cursor.test/v1/agents/bc-test-001/runs"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer cursor-secret-key"))
            .andExpect(content().string(containsString("Return JSON only.")))
            .andExpect(content().string(not(containsString("autoCreatePR"))))
            .andExpect(content().string(not(containsString("\"repos\""))))
            .andRespond(withSuccess("""
                {
                  "run": {
                    "id": "run-followup-001",
                    "agentId": "bc-test-001",
                    "status": "CREATING",
                    "createdAt": "2026-05-29T10:00:00Z"
                  }
                }
                """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.cursor.test/v1/agents/bc-test-001/runs/run-followup-001"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {
                  "id": "run-followup-001",
                  "status": "FINISHED",
                  "result": "{\\"commitMessage\\":\\"feat: improve readme\\",\\"prTitle\\":\\"Improve readme\\",\\"prBody\\":\\"## 변경 요약\\\\n- wording\\\\n\\\\n## 변경 파일\\\\n- README.md\\"}",
                  "createdAt": "2026-05-29T10:00:00Z",
                  "updatedAt": "2026-05-29T10:00:05Z"
                }
                """, MediaType.APPLICATION_JSON));

        ComposerResponse response = client.requestPrMetadataFollowUp("bc-test-001", new ComposerRequest(
            "Return JSON only.",
            null,
            "## Diff context\n- README.md",
            50,
            131072
        ));

        assertThat(response.commitMessage()).isEqualTo("feat: improve readme");
        assertThat(response.prTitle()).isEqualTo("Improve readme");
        assertThat(response.prBody()).contains("## 변경 요약");
        server.verify();
    }

    @Test
    void requestPrMetadataFollowUpRequiresAgentId() {
        assertThatThrownBy(() -> client.requestPrMetadataFollowUp("", new ComposerRequest(
            "system",
            null,
            "",
            50,
            131072
        ))).isInstanceOf(LlmMetadataException.class)
            .hasMessageContaining("agent id is required");
    }

    @Test
    void requestPrMetadataFollowUpSurfacesAgentBusyError() {
        server.expect(requestTo("https://api.cursor.test/v1/agents/bc-test-001/runs"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatusCode.valueOf(409))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {"error":"agent_busy","message":"Another run is active"}
                    """));

        assertThatThrownBy(() -> client.requestPrMetadataFollowUp("bc-test-001", new ComposerRequest(
            "system",
            null,
            "",
            50,
            131072
        ))).isInstanceOf(LlmMetadataException.class)
            .hasMessageContaining("agent is busy");
    }
}
