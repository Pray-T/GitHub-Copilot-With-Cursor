package com.demo.githubcopilotwithcursor.cursor;

import com.demo.githubcopilotwithcursor.config.CursorProperties;
import com.demo.githubcopilotwithcursor.cursor.dto.AgentRefApiResponse;
import com.demo.githubcopilotwithcursor.cursor.dto.AgentRunApiResponse;
import com.demo.githubcopilotwithcursor.cursor.dto.AgentStatusResponse;
import com.demo.githubcopilotwithcursor.cursor.dto.CancelAgentResponse;
import com.demo.githubcopilotwithcursor.cursor.dto.ComposerRequest;
import com.demo.githubcopilotwithcursor.cursor.dto.ComposerResponse;
import com.demo.githubcopilotwithcursor.cursor.dto.CreateAgentApiResponse;
import com.demo.githubcopilotwithcursor.cursor.dto.CreateRunApiResponse;
import com.demo.githubcopilotwithcursor.cursor.dto.CursorIdentity;
import com.demo.githubcopilotwithcursor.cursor.dto.CursorMeResponse;
import com.demo.githubcopilotwithcursor.cursor.dto.StartAgentRequest;
import com.demo.githubcopilotwithcursor.cursor.dto.StartAgentResponse;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import com.demo.githubcopilotwithcursor.github.GitHubApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CloudAgentClient {

    /** PR 생성 단일 출구는 PullRequestService. 외부에서 변경 불가. */
    static final boolean AUTO_CREATE_PR = false;
    static final String LOCKED_MODEL_ID = "composer-2.5";
    static final String LOCKED_MODEL_FAST_VALUE = "true";

    private static final Logger log = LoggerFactory.getLogger(CloudAgentClient.class);
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final CursorProperties properties;
    private final GitHubApiClient gitHubApiClient;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CloudAgentClient(RestClient.Builder restClientBuilder, CursorProperties properties, GitHubApiClient gitHubApiClient) {
        this.properties = properties;
        this.gitHubApiClient = gitHubApiClient;
        this.restClient = restClientBuilder
            .baseUrl(properties.getApiBaseUrl())
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("X-Client", properties.getClientName())
            .build();
    }

    public CursorIdentity verifyApiKey() {
        requireApiKey();
        CursorMeResponse body = exchange(() -> restClient.get()
            .uri("/" + properties.getApiVersion() + "/me")
            .headers(this::addAuthorization)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> {
                throw toException(ErrorCode.CURSOR_AUTH_REQUIRED, response.getStatusCode(), readBody(response));
            })
            .body(CursorMeResponse.class));
        String userId = body.userId() != null ? String.valueOf(body.userId()) : body.apiKeyName();
        String displayName = body.userEmail() != null && !body.userEmail().isBlank()
            ? body.userEmail()
            : (body.apiKeyName() != null ? body.apiKeyName() : "cursor-user");
        return new CursorIdentity(userId, displayName);
    }

    public StartAgentResponse startAgent(StartAgentRequest request) {
        requireApiKey();
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode prompt = payload.putObject("prompt");
        prompt.put("text", request.agentPrompt());
        putLockedComposerStandardModel(payload);
        ArrayNode repos = payload.putArray("repos");
        ObjectNode repo = repos.addObject();
        repo.put("url", request.forkUrl());
        repo.put("startingRef", request.branchName());
        payload.put("workOnCurrentBranch", true);
        payload.put("autoCreatePR", AUTO_CREATE_PR);

        CreateAgentApiResponse body = exchange(() -> restClient.post()
            .uri("/" + properties.getApiVersion() + "/agents")
            .headers(this::addAuthorization)
            .body(payload.toString())
            .retrieve()
            .onStatus(status -> status.value() == 409, (httpRequest, response) -> {
                throw toBusyException(readBody(response));
            })
            .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                throw toException(ErrorCode.AGENT_START_FAILED, response.getStatusCode(), readBody(response));
            })
            .body(CreateAgentApiResponse.class));

        CreateAgentApiResponse.AgentRef agent = body.agent();
        CreateAgentApiResponse.RunRef run = body.run();
        String agentId = agent != null ? agent.id() : null;
        String runId = run != null ? run.id() : (agent != null ? agent.latestRunId() : null);
        String status = run != null && run.status() != null ? run.status() : (agent != null ? agent.status() : "RUNNING");
        OffsetDateTime startedAt = run != null ? parseOffsetDateTime(run.createdAt()) : null;
        if (agentId == null || agentId.isBlank()) {
            throw new AppException(ErrorCode.AGENT_START_FAILED, "Cursor Agent ID를 받지 못했습니다.");
        }
        return new StartAgentResponse(agentId, runId, status, startedAt);
    }

    public AgentStatusResponse getAgentStatus(String agentId, String runId) {
        requireApiKey();
        String resolvedRunId = runId;
        if (resolvedRunId == null || resolvedRunId.isBlank()) {
            AgentRefApiResponse agent = exchange(() -> restClient.get()
                .uri("/" + properties.getApiVersion() + "/agents/{id}", agentId)
                .headers(this::addAuthorization)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw toException(ErrorCode.AGENT_FAILED, response.getStatusCode(), readBody(response));
                })
                .body(AgentRefApiResponse.class));
            resolvedRunId = agent.latestRunId();
            if (resolvedRunId == null || resolvedRunId.isBlank()) {
                return new AgentStatusResponse(agentId, "CREATING", null, null, null, null);
            }
        }

        final String runIdForRequest = resolvedRunId;
        AgentRunApiResponse run = exchange(() -> restClient.get()
            .uri("/" + properties.getApiVersion() + "/agents/{id}/runs/{runId}", agentId, runIdForRequest)
            .headers(this::addAuthorization)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> {
                throw toException(ErrorCode.AGENT_FAILED, response.getStatusCode(), readBody(response));
            })
            .body(AgentRunApiResponse.class));

        String status = run.status() != null ? run.status() : "RUNNING";
        String reason = run.result();
        String latestCommitSha = extractLatestCommitSha(run);
        OffsetDateTime startedAt = parseOffsetDateTime(run.createdAt());
        OffsetDateTime completedAt = parseOffsetDateTime(run.updatedAt());
        AgentStatusResponse response = new AgentStatusResponse(agentId, status, maskSecrets(reason), latestCommitSha, startedAt, completedAt);
        if (response.isTerminal() && completedAt == null) {
            completedAt = OffsetDateTime.now();
            response = new AgentStatusResponse(agentId, status, maskSecrets(reason), latestCommitSha, startedAt, completedAt);
        }
        return response;
    }

    /**
     * Contribute pr/prepare 전용 — 코드 수정에 사용한 repo Agent에 follow-up run을 보내 PR 메타데이터 JSON을 생성한다.
     */
    public ComposerResponse requestPrMetadataFollowUp(String agentId, ComposerRequest request) {
        requireApiKey();
        if (!properties.getComposer().isEnabled()) {
            throw new LlmMetadataException("Composer is disabled in configuration");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new LlmMetadataException("Cursor agent id is required for PR metadata follow-up");
        }

        String promptText = buildComposerUserText(request);
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode prompt = payload.putObject("prompt");
        prompt.put("text", promptText);

        CreateRunApiResponse body = exchange(() -> restClient.post()
            .uri("/" + properties.getApiVersion() + "/agents/{id}/runs", agentId)
            .headers(this::addAuthorization)
            .body(payload.toString())
            .retrieve()
            .onStatus(status -> status.value() == 409, (httpRequest, response) -> {
                throw new LlmMetadataException(
                    "Cursor agent is busy; cannot start PR metadata follow-up"
                        + describeCursorErrorBody(safeReadBody(response))
                );
            })
            .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                throw new LlmMetadataException(
                    "Cursor PR metadata follow-up failed with status "
                        + response.getStatusCode().value()
                        + describeCursorErrorBody(safeReadBody(response))
                );
            })
            .body(CreateRunApiResponse.class));

        String runId = body.run() != null ? body.run().id() : null;
        if (runId == null || runId.isBlank()) {
            throw new LlmMetadataException("Cursor follow-up run did not return run id");
        }

        AgentRunApiResponse completedRun = waitForComposerRun(agentId, runId);
        if (!"FINISHED".equalsIgnoreCase(completedRun.status())) {
            throw new LlmMetadataException(
                "Cursor PR metadata follow-up finished with status " + completedRun.status()
            );
        }
        return parseComposerResult(completedRun.result());
    }

    private void putLockedComposerStandardModel(ObjectNode payload) {
        ObjectNode model = payload.putObject("model");
        model.put("id", LOCKED_MODEL_ID);
        ArrayNode params = model.putArray("params");
        ObjectNode fast = params.addObject();
        fast.put("id", "fast"); 
        fast.put("value", LOCKED_MODEL_FAST_VALUE);
    }

    public CancelAgentResponse cancelAgent(String agentId, String runId) {
        requireApiKey();
        if (runId == null || runId.isBlank()) {
            throw new AppException(ErrorCode.AGENT_NOT_FOUND, "취소할 Agent run ID가 없습니다.");
        }
        AgentRunApiResponse body = exchange(() -> restClient.post()
            .uri("/" + properties.getApiVersion() + "/agents/{id}/runs/{runId}/cancel", agentId, runId)
            .headers(this::addAuthorization)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> {
                throw toException(ErrorCode.AGENT_FAILED, response.getStatusCode(), readBody(response));
            })
            .body(AgentRunApiResponse.class));
        return new CancelAgentResponse(body.id() != null ? body.id() : runId);
    }

    public String maskSecrets(String value) {
        String masked = gitHubApiClient.maskToken(value);
        if (!properties.hasApiKey() || masked == null) {
            return masked;
        }
        return masked.replace(properties.getApiKey(), "***");
    }

    private String buildComposerUserText(ComposerRequest request) {
        StringBuilder builder = new StringBuilder();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            builder.append(request.systemPrompt()).append("\n\n");
        }
        if (request.userPrompt() != null) {
            builder.append(request.userPrompt());
        }
        if (request.diffPatch() != null && !request.diffPatch().isBlank()) {
            builder.append("\n\n").append(request.diffPatch());
        }
        return builder.toString();
    }

    private AgentRunApiResponse waitForComposerRun(String agentId, String runId) {
        long deadline = System.nanoTime() + Duration.ofMillis(properties.getComposer().getTimeoutMs()).toNanos();
        AgentRunApiResponse last = null;
        while (System.nanoTime() < deadline) {
            last = exchange(() -> restClient.get()
                .uri("/" + properties.getApiVersion() + "/agents/{id}/runs/{runId}", agentId, runId)
                .headers(this::addAuthorization)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new LlmMetadataException(
                        "Cursor Composer run poll failed with status " + response.getStatusCode().value()
                    );
                })
                .body(AgentRunApiResponse.class));
            if (last.status() != null && isComposerTerminal(last.status())) {
                return last;
            }
            sleepQuietly(1500);
        }
        throw new LlmMetadataException("Cursor Composer run timed out after " + properties.getComposer().getTimeoutMs() + "ms");
    }

    private boolean isComposerTerminal(String status) {
        return switch (status.toUpperCase()) {
            case "FINISHED", "ERROR", "CANCELLED", "EXPIRED", "FAILED" -> true;
            default -> false;
        };
    }

    private ComposerResponse parseComposerResult(String resultText) {
        if (resultText == null || resultText.isBlank()) {
            throw new LlmMetadataException("Cursor Composer returned an empty result");
        }
        String sanitized = maskSecrets(resultText);
        JsonNode root = parseComposerJson(sanitized);
        String commitMessage = textOrNull(root.path("commitMessage"));
        String prTitle = textOrNull(root.path("prTitle"));
        String prBody = textOrNull(root.path("prBody"));
        if (commitMessage == null || prTitle == null || prBody == null) {
            throw new LlmMetadataException("Cursor Composer JSON is missing required fields");
        }
        return new ComposerResponse(
            truncate(commitMessage, 4096),
            truncate(prTitle, 256),
            truncate(prBody, 65535)
        );
    }

    private JsonNode parseComposerJson(String sanitized) {
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(sanitized);
        if (matcher.find()) {
            sanitized = matcher.group(1).trim();
        }
        try {
            return objectMapper.readTree(sanitized);
        } catch (IOException exception) {
            throw new LlmMetadataException("Cursor Composer result is not valid JSON", exception);
        }
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText(null);
        return value != null && !value.isBlank() ? value : null;
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmMetadataException("Interrupted while waiting for Composer run", exception);
        }
    }

    private void requireApiKey() {
        if (!properties.hasApiKey()) {
            throw new AppException(ErrorCode.CURSOR_AUTH_REQUIRED, "환경변수 CURSOR_API_KEY를 설정한 뒤 앱을 재시작하세요.");
        }
    }

    private void addAuthorization(HttpHeaders headers) {
        headers.setBearerAuth(properties.getApiKey());
    }

    private String extractLatestCommitSha(AgentRunApiResponse run) {
        if (run.git() == null || run.git().branches() == null || run.git().branches().isEmpty()) {
            return null;
        }
        return run.git().branches().get(0).branch();
    }

    private OffsetDateTime parseOffsetDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private CursorApiException toBusyException(String responseBody) {
        Map<String, Object> details = new LinkedHashMap<>();
        JsonNode root = safeParseJson(responseBody);
        if (root != null) {
            String agentId = root.path("agentId").asText(root.path("agent").path("id").asText(null));
            if (agentId != null) {
                details.put("currentAgentId", agentId);
            }
        }
        return new CursorApiException(
            ErrorCode.AGENT_BUSY,
            "다른 Cursor Agent가 아직 실행 중입니다. 잠시 후 다시 시도하세요.",
            409,
            maskSecrets(responseBody),
            details.isEmpty() ? null : details
        );
    }

    private CursorApiException toException(ErrorCode code, HttpStatusCode statusCode, String responseBody) {
        int status = statusCode.value();
        if (status == 401 || status == 403) {
            code = ErrorCode.CURSOR_AUTH_REQUIRED;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("cursorStatus", status);
        String sanitized = maskSecrets(responseBody);
        if (sanitized != null && !sanitized.isBlank()) {
            details.put("cursorMessage", sanitized.length() > 300 ? sanitized.substring(0, 300) : sanitized);
        }
        String message = switch (code) {
            case CURSOR_AUTH_REQUIRED -> "환경변수 CURSOR_API_KEY를 설정한 뒤 앱을 재시작하세요.";
            case AGENT_START_FAILED -> "Cursor Cloud Agent를 시작하지 못했습니다.";
            default -> "Cursor Cloud Agent 요청에 실패했습니다.";
        };
        return new CursorApiException(code, message, status, sanitized, details);
    }

    private JsonNode safeParseJson(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(responseBody);
        } catch (IOException ignored) {
            return null;
        }
    }

    private String describeCursorErrorBody(String responseBody) {
        String sanitized = maskSecrets(responseBody);
        if (sanitized == null || sanitized.isBlank()) {
            return "";
        }
        JsonNode root = safeParseJson(sanitized);
        if (root != null) {
            String error = textOrNull(root.path("error"));
            String message = textOrNull(root.path("message"));
            if (error != null || message != null) {
                return ": " + (error != null ? error : "") + (message != null ? " " + message : "");
            }
        }
        return sanitized.length() > 200 ? ": " + sanitized.substring(0, 200) : ": " + sanitized;
    }

    private String readBody(org.springframework.http.client.ClientHttpResponse response) throws IOException {
        return StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
    }

    private String safeReadBody(org.springframework.http.client.ClientHttpResponse response) {
        try {
            return readBody(response);
        } catch (IOException exception) {
            log.warn("Could not read Cursor API error body: {}", exception.getMessage());
            return "";
        }
    }

    private <T> T exchange(CursorExchange<T> exchange) {
        try {
            T response = exchange.execute();
            if (response == null) {
                throw new AppException(ErrorCode.AGENT_START_FAILED, "Cursor API 응답 본문이 비어 있습니다.");
            }
            return response;
        } catch (CursorApiException exception) {
            throw exception;
        } catch (AppException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("Cursor API request failed: {}", maskSecrets(exception.getMessage()));
            throw new AppException(ErrorCode.AGENT_START_FAILED, "Cursor API 호출에 실패했습니다.", exception);
        }
    }

    @FunctionalInterface
    private interface CursorExchange<T> {
        T execute();
    }
}
