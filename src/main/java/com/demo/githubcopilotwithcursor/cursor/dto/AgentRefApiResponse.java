package com.demo.githubcopilotwithcursor.cursor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentRefApiResponse(String id, String latestRunId, String status) {
}
