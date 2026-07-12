package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import com.demo.githubcopilotwithcursor.github.GitHubRepoRef;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceGuard {

    private static final Pattern REPO_SEGMENT_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");

    public void validateRepoOwner(String repoOwner) {
        validateRepoSegment(repoOwner, "repoOwner");
    }

    public void validateRepoName(String repoName) {
        validateRepoSegment(repoName, "repoName");
    }

    public URI parseRepoUrl(String repoUrl, Iterable<String> allowedHosts) {
        try {
            URI uri = new URI(repoUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(scheme) || host == null) {
                throw invalidUrl("HTTPS GitHub URL만 허용됩니다.");
            }

            String normalizedHost = host.toLowerCase(Locale.ROOT);
            for (String allowedHost : allowedHosts) {
                if (normalizedHost.equals(allowedHost.toLowerCase(Locale.ROOT))) {
                    return uri;
                }
            }
            throw invalidUrl("지원하지 않는 호스트입니다. github.com URL만 허용됩니다.");
        } catch (URISyntaxException exception) {
            throw invalidUrl("저장소 URL 형식이 올바르지 않습니다.");
        }
    }

    public GitHubRepoRef extractRepoRef(URI uri) {
        GitHubRepoRef ref = GitHubRepoRef.from(uri);
        validateRepoOwner(ref.owner());
        validateRepoName(ref.repo());
        return ref;
    }

    public String extractRepoName(URI uri) {
        return extractRepoRef(uri).repo();
    }

    public Path normalizePath(Path path) {
        if (path == null) {
            return null;
        }
        return path.toAbsolutePath().normalize();
    }

    public Path normalizeStoredPath(String workspacePath) {
        if (workspacePath == null || workspacePath.isBlank()) {
            return null;
        }
        return normalizePath(Path.of(workspacePath));
    }

    public boolean isUnderRoot(Path root, Path path) {
        Path normalizedRoot = normalizePath(root);
        Path normalizedPath = normalizePath(path);
        return normalizedRoot != null && normalizedPath != null && normalizedPath.startsWith(normalizedRoot);
    }

    public Path resolveWorkspace(Path root, String repoOwner, String repoName) {
        validateRepoOwner(repoOwner);
        validateRepoName(repoName);
        Path normalizedRoot = normalizePath(root);
        Path target = normalizedRoot.resolve(repoOwner).resolve(repoName).normalize();
        if (!isUnderRoot(normalizedRoot, target)) {
            throw new AppException(ErrorCode.INVALID_REPO_NAME, "워크스페이스 경로가 허용된 루트를 벗어났습니다.");
        }
        return target;
    }

    private void validateRepoSegment(String value, String fieldName) {
        if (value == null || !REPO_SEGMENT_PATTERN.matcher(value).matches()) {
            throw new AppException(
                ErrorCode.INVALID_REPO_NAME,
                fieldName + "은 영문, 숫자, '.', '_', '-'만 사용할 수 있습니다."
            );
        }
    }

    private AppException invalidUrl(String message) {
        return new AppException(ErrorCode.INVALID_REPO_URL, message);
    }
}
