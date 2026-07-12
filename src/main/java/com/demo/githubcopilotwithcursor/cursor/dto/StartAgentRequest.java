package com.demo.githubcopilotwithcursor.cursor.dto;

public record StartAgentRequest(
    String agentPrompt,
    String forkUrl,
    String branchName,
    String baseRef
) {
    public static StartAgentRequest of(String prompt, String forkUrl, String branch, String baseRef) {
        return new StartAgentRequest(prompt, forkUrl, branch, baseRef);
    }
}
