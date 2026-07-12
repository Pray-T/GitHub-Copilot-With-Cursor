package com.demo.githubcopilotwithcursor.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record ErrorResponse(
    OffsetDateTime timestamp,
    int status,
    String error,
    String code,
    String message,
    String path,
    Map<String, Object> details
) {
}
