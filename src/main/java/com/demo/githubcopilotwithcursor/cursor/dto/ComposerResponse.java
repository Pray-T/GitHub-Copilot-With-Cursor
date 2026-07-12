package com.demo.githubcopilotwithcursor.cursor.dto;

public record ComposerResponse(
    String commitMessage,
    String prTitle,
    String prBody
) {
    public static ComposerResponse fallback(String fallbackMessage, String fallbackBody) {
        return new ComposerResponse(fallbackMessage, fallbackMessage, fallbackBody);
    }
}
