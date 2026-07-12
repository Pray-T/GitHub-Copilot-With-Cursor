package com.demo.githubcopilotwithcursor.github;

import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import java.net.URI;
import java.net.URISyntaxException;

public record GitHubPullRequestRef(String owner, String repo, int number) {

    public static GitHubPullRequestRef fromUrl(String prUrl) {
        try {
            URI uri = new URI(prUrl);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                throw new AppException(ErrorCode.PR_CREATE_FAILED, "PR URL 경로가 비어 있습니다.");
            }
            String[] parts = path.split("/");
            if (parts.length != 5 || !"pull".equals(parts[3])) {
                throw new AppException(
                    ErrorCode.PR_CREATE_FAILED,
                    "PR URL은 https://github.com/{owner}/{repo}/pull/{number} 형식이어야 합니다."
                );
            }
            return new GitHubPullRequestRef(parts[1], parts[2], Integer.parseInt(parts[4]));
        } catch (URISyntaxException | NumberFormatException exception) {
            throw new AppException(ErrorCode.PR_CREATE_FAILED, "PR URL 형식이 올바르지 않습니다.", exception);
        }
    }
}
