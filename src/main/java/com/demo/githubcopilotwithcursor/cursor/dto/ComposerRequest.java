package com.demo.githubcopilotwithcursor.cursor.dto;

public record ComposerRequest(
    String systemPrompt,
    String userPrompt,
    String diffPatch,
    int maxFiles,
    int maxPatchBytes
) {
}
