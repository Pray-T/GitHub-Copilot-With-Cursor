package com.demo.githubcopilotwithcursor.config;

import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    private static final Logger log = LoggerFactory.getLogger(RestClientConfig.class);

    @Bean
    public RestClient.Builder restClientBuilder(GitHubProperties gitHubProperties, CursorProperties cursorProperties) {
        return RestClient.builder()
            .requestInterceptor(new AuthorizationMaskingInterceptor(gitHubProperties, cursorProperties));
    }

    private static final class AuthorizationMaskingInterceptor implements ClientHttpRequestInterceptor {

        private final GitHubProperties gitHubProperties;
        private final CursorProperties cursorProperties;

        private AuthorizationMaskingInterceptor(GitHubProperties gitHubProperties, CursorProperties cursorProperties) {
            this.gitHubProperties = gitHubProperties;
            this.cursorProperties = cursorProperties;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            String apiLabel = resolveApiLabel(request);
            HttpHeaders maskedRequestHeaders = maskedHeaders(request.getHeaders());
            log.debug("{} request: {} {} headers={}", apiLabel, request.getMethod(), request.getURI(), maskedRequestHeaders);
            ClientHttpResponse response = execution.execute(request, body);
            log.debug(
                "{} response: {} {} status={} headers={}",
                apiLabel,
                request.getMethod(),
                request.getURI(),
                response.getStatusCode(),
                maskedHeaders(response.getHeaders())
            );
            return response;
        }

        private String resolveApiLabel(HttpRequest request) {
            String host = request.getURI().getHost();
            if (host != null && host.contains("cursor")) {
                return "Cursor API";
            }
            return "GitHub API";
        }

        private HttpHeaders maskedHeaders(HttpHeaders headers) {
            HttpHeaders copy = new HttpHeaders();
            copy.putAll(headers);
            List<String> authorization = copy.get(HttpHeaders.AUTHORIZATION);
            if (authorization != null && !authorization.isEmpty()) {
                copy.set(HttpHeaders.AUTHORIZATION, maskAuthorization(authorization.get(0)));
            }
            return copy;
        }

        private String maskAuthorization(String value) {
            if (value == null || value.isBlank()) {
                return value;
            }
            if (gitHubProperties.hasToken() && value.contains(gitHubProperties.getToken())) {
                return value.replace(gitHubProperties.getToken(), "***");
            }
            if (cursorProperties.hasApiKey() && value.contains(cursorProperties.getApiKey())) {
                return value.replace(cursorProperties.getApiKey(), "***");
            }
            if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return "Bearer ***";
            }
            return "***";
        }
    }
}
