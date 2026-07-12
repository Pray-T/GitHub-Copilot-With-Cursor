package com.demo.githubcopilotwithcursor.github;

import com.demo.githubcopilotwithcursor.config.GitHubProperties;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitHubAuth {

    private static final Logger log = LoggerFactory.getLogger(GitHubAuth.class);

    private final GitHubProperties properties;
    private final GitHubApiClient gitHubApiClient;
    private volatile GitHubAuthenticatedUser authenticatedUser;
    private volatile boolean contributeEnabled;

    public GitHubAuth(GitHubProperties properties, GitHubApiClient gitHubApiClient) {
        this.properties = properties;
        this.gitHubApiClient = gitHubApiClient;
    }

    @PostConstruct
    void initialize() {
        if (!properties.hasToken()) {
            contributeEnabled = false;
            log.info("GITHUB_TOKEN is not configured. Contribute mode is disabled.");
            return;
        }

        try {
            authenticatedUser = gitHubApiClient.getAuthenticatedUser();
            contributeEnabled = true;
            log.info("GitHub token validated for login {}. Contribute mode is enabled.", authenticatedUser.login());
        } catch (RuntimeException exception) {
            contributeEnabled = false;
            log.warn("GitHub token validation failed. Contribute mode is disabled: {}", gitHubApiClient.maskToken(exception.getMessage()));
        }
    }

    public boolean isContributeEnabled() {
        return contributeEnabled;
    }

    public Optional<GitHubAuthenticatedUser> authenticatedUser() {
        return Optional.ofNullable(authenticatedUser);
    }

    public GitHubAuthenticatedUser requireAuthenticatedUser() {
        if (!contributeEnabled || authenticatedUser == null) {
            throw new AppException(ErrorCode.GITHUB_AUTH_REQUIRED, "환경변수 GITHUB_TOKEN을 설정한 뒤 앱을 재시작하세요.");
        }
        return authenticatedUser;
    }

    public String githubLogin() {
        return requireAuthenticatedUser().login();
    }

    public String defaultAuthorName() {
        GitHubAuthenticatedUser user = requireAuthenticatedUser();
        return user.name() == null || user.name().isBlank() ? user.login() : user.name();
    }

    public String defaultAuthorEmail() {
        GitHubAuthenticatedUser user = requireAuthenticatedUser();
        return user.email() == null || user.email().isBlank()
            ? user.login() + "@users.noreply.github.com"
            : user.email();
    }

    public String maskToken(String value) {
        return gitHubApiClient.maskToken(value);
    }
}
