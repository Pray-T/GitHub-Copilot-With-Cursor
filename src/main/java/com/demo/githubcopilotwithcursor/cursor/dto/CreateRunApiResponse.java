package com.demo.githubcopilotwithcursor.cursor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateRunApiResponse(
    RunRef run
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RunRef(String id, String agentId, String status, String createdAt) {
    }
}
