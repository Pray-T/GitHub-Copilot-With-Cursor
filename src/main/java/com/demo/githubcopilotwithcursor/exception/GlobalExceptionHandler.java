package com.demo.githubcopilotwithcursor.exception;

import com.demo.githubcopilotwithcursor.cursor.CursorApiException;
import com.demo.githubcopilotwithcursor.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CursorApiException.class)
    public ResponseEntity<ErrorResponse> handleCursorApiException(CursorApiException exception, HttpServletRequest request) {
        ErrorCode code = exception.getErrorCode();
        if (code.status().is5xxServerError()) {
            log.error("Cursor API error: {}", exception.getMessage());
        } else {
            log.warn("Cursor API warning: {}", exception.getMessage());
        }
        return build(code.status(), code.name(), exception.getMessage(), request.getRequestURI(), exception.details());
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException exception, HttpServletRequest request) {
        ErrorCode code = exception.getErrorCode();
        if (code.status().is5xxServerError()) {
            log.error("Application error: {}", exception.getMessage(), exception);
        } else {
            log.warn("Application warning: {}", exception.getMessage());
        }
        return build(code.status(), code.name(), exception.getMessage(), request.getRequestURI(), exception.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .orElse("요청 값이 올바르지 않습니다.");
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REPO_URL.name(), message, request.getRequestURI(), null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REPO_NAME.name(), exception.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected server error", exception);
        return build(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorCode.INTERNAL_ERROR.name(),
            "서버 내부 오류가 발생했습니다.",
            request.getRequestURI(),
            null
        );
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message, String path, Map<String, Object> details) {
        ErrorResponse body = new ErrorResponse(
            OffsetDateTime.now(),
            status.value(),
            status.getReasonPhrase(),
            code,
            message,
            path,
            details
        );
        return ResponseEntity.status(status).body(body);
    }
}
