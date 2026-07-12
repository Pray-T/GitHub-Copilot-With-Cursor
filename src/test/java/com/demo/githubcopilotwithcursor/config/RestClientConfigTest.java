package com.demo.githubcopilotwithcursor.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class RestClientConfigTest {

    @Test
    void masksCursorAuthorizationHeaderInDebugLogs(CapturedOutput output) {
        Logger logger = (Logger) LoggerFactory.getLogger(RestClientConfig.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try {
            GitHubProperties gitHubProperties = new GitHubProperties();
            gitHubProperties.setToken("github-secret-token");
            CursorProperties cursorProperties = new CursorProperties();
            cursorProperties.setApiKey("cursor-secret-key");

            RestClient.Builder builder = new RestClientConfig()
                .restClientBuilder(gitHubProperties, cursorProperties)
                .baseUrl("https://api.cursor.test");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            RestClient client = builder.build();

            server.expect(requestTo("https://api.cursor.test/v1/me"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            client.get()
                .uri("/v1/me")
                .headers(headers -> headers.setBearerAuth("cursor-secret-key"))
                .retrieve()
                .toBodilessEntity();

            assertThat(output).contains("Cursor API request");
            assertThat(output).contains("Bearer ***");
            assertThat(output).doesNotContain("cursor-secret-key");
            server.verify();
        } finally {
            logger.setLevel(previousLevel);
        }
    }
}
