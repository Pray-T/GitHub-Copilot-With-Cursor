package com.demo.githubcopilotwithcursor.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REPO_URL(HttpStatus.BAD_REQUEST),
    INVALID_REPO_NAME(HttpStatus.BAD_REQUEST),
    NOT_CONTRIBUTE_WORKSPACE(HttpStatus.BAD_REQUEST),
    WORKSPACE_NOT_FOUND(HttpStatus.NOT_FOUND),
    WORKSPACE_ALREADY_EXISTS(HttpStatus.CONFLICT),
    NO_CHANGES_TO_COMMIT(HttpStatus.CONFLICT),
    CLONE_FAILED(HttpStatus.BAD_GATEWAY),
    IDE_LAUNCH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
    GITHUB_AUTH_REQUIRED(HttpStatus.UNAUTHORIZED),
    GITHUB_API_ERROR(HttpStatus.BAD_GATEWAY),
    FORK_FAILED(HttpStatus.BAD_GATEWAY),
    PUSH_FAILED(HttpStatus.BAD_GATEWAY),
    PR_CREATE_FAILED(HttpStatus.BAD_GATEWAY),
    CURSOR_AUTH_REQUIRED(HttpStatus.UNAUTHORIZED),
    INVALID_AGENT_PROMPT(HttpStatus.BAD_REQUEST),
    AGENT_START_FAILED(HttpStatus.BAD_GATEWAY),
    AGENT_BUSY(HttpStatus.CONFLICT),
    AGENT_FAILED(HttpStatus.BAD_GATEWAY),
    AGENT_SYNC_FAILED(HttpStatus.BAD_GATEWAY),
    AGENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    M1_IDE_LAUNCH_FAILED(HttpStatus.BAD_GATEWAY),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
