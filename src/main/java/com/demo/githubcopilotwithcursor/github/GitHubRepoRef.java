package com.demo.githubcopilotwithcursor.github;

import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import java.net.URI;
import java.net.URISyntaxException;

public record GitHubRepoRef(String owner, String repo) {

    public static GitHubRepoRef from(URI uri) {
        return fromPath(uri.getPath());
    }

    public static GitHubRepoRef fromUrl(String url) {
        try {
            return from(new URI(url));
        } catch (URISyntaxException exception) {
            throw new AppException(ErrorCode.INVALID_REPO_URL, "저장소 URL 형식이 올바르지 않습니다.", exception);
        }
    }

    private static GitHubRepoRef fromPath(String path) {
        if (path == null || path.isBlank()) {
            throw new AppException(ErrorCode.INVALID_REPO_URL, "저장소 경로가 비어 있습니다.");
        }
        String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        String[] parts = trimmed.split("/");
        if (parts.length != 3 || parts[1].isBlank() || parts[2].isBlank()) {
            throw new AppException(
                ErrorCode.INVALID_REPO_URL,
                "GitHub 저장소 URL은 https://github.com/{owner}/{repo} 형식이어야 합니다."
            );
        }
        String repoName = parts[2].endsWith(".git") ? parts[2].substring(0, parts[2].length() - 4) : parts[2];
        return new GitHubRepoRef(parts[1], repoName);
    }
}
