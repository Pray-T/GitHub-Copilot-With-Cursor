package com.demo.githubcopilotwithcursor.cursor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentRunApiResponse(
    String id,
    String status,
    String result,
    String createdAt,
    String updatedAt,
    GitInfo git
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitInfo(List<BranchRef> branches) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BranchRef(String branch) {
    }
}
