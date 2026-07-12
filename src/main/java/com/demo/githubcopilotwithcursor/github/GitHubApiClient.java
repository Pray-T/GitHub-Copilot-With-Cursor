package com.demo.githubcopilotwithcursor.github;

import com.demo.githubcopilotwithcursor.config.GitHubProperties;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GitHubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);
    private static final String ACCEPT_HEADER = "application/vnd.github+json";
    private static final Pattern PULL_URL_PATTERN = Pattern.compile("https://github\\.com/[^\\s\"']+/[^\\s\"']+/pull/(\\d+)");
    private static final Pattern PR_NUMBER_PATTERN = Pattern.compile("(?i)(?:pull request|pull|pr)\\s*#?(\\d+)");

    private final GitHubProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GitHubApiClient(RestClient.Builder restClientBuilder, GitHubProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
            .baseUrl(properties.getApiBaseUrl())
            .defaultHeader(HttpHeaders.ACCEPT, ACCEPT_HEADER)
            .defaultHeader("X-GitHub-Api-Version", properties.getApiVersion())
            .build();
    }

    public GitHubAuthenticatedUser getAuthenticatedUser() {
        requireToken();
        return exchange(() -> restClient.get()
            .uri("/user")
            .headers(this::addAuthorization)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> {
                throw toException(ErrorCode.GITHUB_API_ERROR, response.getStatusCode(), response.getStatusText(), readBody(response));
            })
            .body(GitHubAuthenticatedUser.class));
    }

    public GitHubRepository getRepository(String owner, String repo) {
        requireToken();
        return exchange(() -> restClient.get()
            .uri("/repos/{owner}/{repo}", owner, repo)
            .headers(this::addAuthorization)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> {
                throw toException(ErrorCode.GITHUB_API_ERROR, response.getStatusCode(), response.getStatusText(), readBody(response));
            })
            .body(GitHubRepository.class));
    }

    public Optional<GitHubRepository> findRepository(String owner, String repo) {
        try {
            return Optional.of(getRepository(owner, repo));
        } catch (GitHubApiException exception) {
            if (exception.getGithubStatus() == 404) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    public GitHubRepository forkRepository(String owner, String repo) {
        requireToken();
        return exchange(() -> restClient.post()
            .uri("/repos/{owner}/{repo}/forks", owner, repo)
            .headers(this::addAuthorization)
            .body(new ForkRequest())
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> {
                throw toForkException(owner, repo, response.getStatusCode(), response.getStatusText(), readBody(response));
            })
            .body(GitHubRepository.class));
    }

    public GitHubPullRequest createPullRequest(String owner, String repo, CreatePullRequestPayload payload) {
        requireToken();
        return exchange(() -> restClient.post()
            .uri("/repos/{owner}/{repo}/pulls", owner, repo)
            .headers(this::addAuthorization)
            .body(payload)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> {
                throw toPullRequestException(owner, repo, payload.head(), response.getStatusCode(), response.getStatusText(), readBody(response));
            })
            .body(GitHubPullRequest.class));
    }

    public GitHubPullRequest getPullRequest(String owner, String repo, int pullNumber) {
        requireToken();
        return exchange(() -> restClient.get()
            .uri("/repos/{owner}/{repo}/pulls/{pullNumber}", owner, repo, pullNumber)
            .headers(this::addAuthorization)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> {
                throw toException(ErrorCode.GITHUB_API_ERROR, response.getStatusCode(), response.getStatusText(), readBody(response));
            })
            .body(GitHubPullRequest.class));
    }

    public List<GitHubPullRequest> listOpenPullRequestsByHead(String owner, String repo, String head) {
        requireToken();
        return exchange(() -> {
            GitHubPullRequest[] response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/repos/{owner}/{repo}/pulls")
                    .queryParam("head", head)
                    .queryParam("state", "open")
                    .build(owner, repo))
                .headers(this::addAuthorization)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, responseSpec) -> {
                    throw toException(ErrorCode.GITHUB_API_ERROR, responseSpec.getStatusCode(), responseSpec.getStatusText(), readBody(responseSpec));
                })
                .body(GitHubPullRequest[].class);
            if (response == null) {
                return List.of();
            }
            return Arrays.asList(response);
        });
    }

    public String maskToken(String value) {
        if (!properties.hasToken() || value == null) {
            return value;
        }
        return value.replace(properties.getToken(), "***");
    }

    private void requireToken() {
        if (!properties.hasToken()) {
            throw new AppException(ErrorCode.GITHUB_AUTH_REQUIRED, "환경변수 GITHUB_TOKEN을 설정한 뒤 앱을 재시작하세요.");
        }
    }

    private void addAuthorization(HttpHeaders headers) {
        headers.setBearerAuth(properties.getToken());
    }

    private <T> T exchange(GitHubExchange<T> exchange) {
        try {
            T response = exchange.execute();
            if (response == null) {
                throw new AppException(ErrorCode.GITHUB_API_ERROR, "GitHub API 응답 본문이 비어 있습니다.");
            }
            return response;
        } catch (AppException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("GitHub API request failed: {}", maskToken(exception.getMessage()));
            throw new AppException(ErrorCode.GITHUB_API_ERROR, "GitHub API 호출에 실패했습니다.", exception);
        }
    }

    private GitHubApiException toException(ErrorCode code, HttpStatusCode statusCode, String statusText, String responseBody) {
        return toException(code, statusCode, statusText, responseBody, null);
    }

    private GitHubApiException toException(
        ErrorCode code,
        HttpStatusCode statusCode,
        String statusText,
        String responseBody,
        Map<String, Object> details
    ) {
        int status = statusCode.value();
        String sanitizedBody = maskToken(responseBody);
        String message = githubMessage(code, status, statusText, sanitizedBody);
        return new GitHubApiException(code, message, status, sanitizedBody, details);
    }

    private GitHubApiException toForkException(
        String upstreamOwner,
        String upstreamRepo,
        HttpStatusCode statusCode,
        String statusText,
        String responseBody
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("upstreamOwner", upstreamOwner);
        details.put("upstreamRepo", upstreamRepo);
        details.put("githubStatus", statusCode.value());
        return toException(ErrorCode.FORK_FAILED, statusCode, statusText, responseBody, details);
    }

    private GitHubApiException toPullRequestException(
        String owner,
        String repo,
        String head,
        HttpStatusCode statusCode,
        String statusText,
        String responseBody
    ) {
        String existingPrUrl = null;
        int status = statusCode.value();
        if (status == 422) {
            String sanitizedBody = maskToken(responseBody);
            existingPrUrl = extractExistingPrUrl(owner, repo, sanitizedBody)
                .or(() -> findExistingPrUrl(owner, repo, head))
                .orElse(null);
        }

        Map<String, Object> details = null;
        if (existingPrUrl != null) {
            details = new LinkedHashMap<>();
            details.put("existingPrUrl", existingPrUrl);
        }
        return toException(ErrorCode.PR_CREATE_FAILED, statusCode, statusText, responseBody, details);
    }

    private String githubMessage(ErrorCode code, int status, String statusText, String responseBody) {
        if (status == 401 || status == 403) {
            return "Fine-grained PAT에 Contents: Write, Pull requests: Write 스코프가 필요합니다.";
        }
        if (code == ErrorCode.FORK_FAILED) {
            return "GitHub fork 생성에 실패했습니다. GitHub 응답: " + safeSummary(status, statusText, responseBody);
        }
        if (code == ErrorCode.PR_CREATE_FAILED) {
            return "GitHub PR 생성에 실패했습니다. GitHub 응답: " + safeSummary(status, statusText, responseBody);
        }
        return "GitHub API 호출에 실패했습니다. GitHub 응답: " + safeSummary(status, statusText, responseBody);
    }

    private String safeSummary(int status, String statusText, String responseBody) {
        String text = responseBody == null || responseBody.isBlank() ? statusText : responseBody;
        if (text == null || text.isBlank()) {
            return String.valueOf(status);
        }
        String singleLine = text.replaceAll("\\s+", " ").trim();
        return status + " " + singleLine.substring(0, Math.min(singleLine.length(), 300));
    }

    private String readBody(org.springframework.http.client.ClientHttpResponse response) throws IOException {
        return StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
    }

    private Optional<String> extractExistingPrUrl(String owner, String repo, String responseBody) {
        return extractExistingPrNumber(responseBody)
            .map(number -> "https://github.com/" + owner + "/" + repo + "/pull/" + number);
    }

    private Optional<String> extractExistingPrNumber(String responseBody) {
        JsonNode root = safeParseJson(responseBody);
        List<String> candidates = new ArrayList<>();
        appendMessageNodes(root, candidates);
        candidates.add(responseBody);

        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            Matcher urlMatcher = PULL_URL_PATTERN.matcher(candidate);
            if (urlMatcher.find()) {
                return Optional.of(urlMatcher.group(1));
            }
            Matcher numberMatcher = PR_NUMBER_PATTERN.matcher(candidate);
            if (numberMatcher.find()) {
                return Optional.of(numberMatcher.group(1));
            }
        }
        return Optional.empty();
    }

    private void appendMessageNodes(JsonNode root, List<String> candidates) {
        if (root == null || root.isMissingNode()) {
            return;
        }
        JsonNode message = root.get("message");
        if (message != null && message.isTextual()) {
            candidates.add(message.asText());
        }
        JsonNode errors = root.get("errors");
        if (errors != null && errors.isArray()) {
            for (JsonNode error : errors) {
                JsonNode errorMessage = error.get("message");
                if (errorMessage != null && errorMessage.isTextual()) {
                    candidates.add(errorMessage.asText());
                }
            }
        }
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

    private Optional<String> findExistingPrUrl(String owner, String repo, String head) {
        try {
            return listOpenPullRequestsByHead(owner, repo, head).stream()
                .map(GitHubPullRequest::htmlUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst();
        } catch (AppException exception) {
            log.warn("Failed to resolve existing PR URL for {}/{} head={}: {}", owner, repo, head, exception.getMessage());
            return Optional.empty();
        }
    }

    @FunctionalInterface
    private interface GitHubExchange<T> {
        T execute();
    }

    private record ForkRequest() {
    }
}
