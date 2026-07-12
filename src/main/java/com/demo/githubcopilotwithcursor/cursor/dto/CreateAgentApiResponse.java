package com.demo.githubcopilotwithcursor.cursor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateAgentApiResponse(
    AgentRef agent,
    RunRef run
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AgentRef(String id, String latestRunId, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RunRef(String id, String status, String createdAt) {
    }
}
