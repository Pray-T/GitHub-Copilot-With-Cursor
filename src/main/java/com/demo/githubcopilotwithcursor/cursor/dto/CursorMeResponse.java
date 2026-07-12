package com.demo.githubcopilotwithcursor.cursor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CursorMeResponse(
    String apiKeyName,
    Long userId,
    String userEmail,
    String userFirstName,
    String userLastName
) {
}
