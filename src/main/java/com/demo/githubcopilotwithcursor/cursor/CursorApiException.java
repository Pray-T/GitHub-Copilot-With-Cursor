package com.demo.githubcopilotwithcursor.cursor;

import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import java.util.Map;

public class CursorApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final int cursorStatus;
    private final String cursorMessage;
    private final Map<String, Object> details;

    public CursorApiException(
        ErrorCode errorCode,
        String message,
        int cursorStatus,
        String cursorMessage,
        Map<String, Object> details
    ) {
        super(message);
        this.errorCode = errorCode;
        this.cursorStatus = cursorStatus;
        this.cursorMessage = cursorMessage;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getCursorStatus() {
        return cursorStatus;
    }

    public String getCursorMessage() {
        return cursorMessage;
    }

    public Map<String, Object> details() {
        return details;
    }
}
