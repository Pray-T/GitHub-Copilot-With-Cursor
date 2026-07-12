package com.demo.githubcopilotwithcursor.dto;

public record ChangedFileResponse(
    String path,
    String oldPath,
    String changeType,
    boolean binary,
    boolean metadataOnly,
    long originalSize,
    long newSize,
    String originalContent,
    String newContent,
    boolean truncated
) {
}
