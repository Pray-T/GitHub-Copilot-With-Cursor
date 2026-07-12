package com.demo.githubcopilotwithcursor.github;

import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import java.util.Map;

public class GitHubApiException extends AppException {

    private final int githubStatus;
    private final String githubBody;

    public GitHubApiException(ErrorCode errorCode, String message, int githubStatus, String githubBody) {
        this(errorCode, message, githubStatus, githubBody, null);
    }

    public GitHubApiException(
        ErrorCode errorCode,
        String message,
        int githubStatus,
        String githubBody,
        Map<String, Object> details
    ) {
        super(errorCode, message, details);
        this.githubStatus = githubStatus;
        this.githubBody = githubBody;
    }

    public int getGithubStatus() {
        return githubStatus;
    }

    public String getGithubBody() {
        return githubBody;
    }
}
