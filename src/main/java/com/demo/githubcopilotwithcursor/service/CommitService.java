package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.dto.CommitPushRequest;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CommitService {

    private static final Logger log = LoggerFactory.getLogger(CommitService.class);

    private static final Set<String> SENSITIVE_FILE_NAMES = Set.of(
        ".env",
        ".env.local",
        ".env.production",
        ".env.development",
        "credentials.json",
        "secrets.json",
        "id_rsa",
        "id_dsa",
        "id_ecdsa",
        "id_ed25519"
    );

    private static final Pattern SENSITIVE_SUFFIX = Pattern.compile(".*\\.(pem|key|p12|pfx)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENSITIVE_ENV_SUFFIX = Pattern.compile("\\.env\\..+", Pattern.CASE_INSENSITIVE);

    private final WorkspaceService workspaceService;

    public CommitService(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    public String commit(String repoOwner, String repoName, Path workspacePath, CommitPushRequest request) {
        try (Git git = Git.open(workspacePath.toFile())) {
            Status status = git.status().call();
            if (status.isClean()) {
                throw new AppException(ErrorCode.NO_CHANGES_TO_COMMIT, "커밋할 변경 사항이 없습니다.");
            }

            stageChanges(git, status);

            if (git.diff().setCached(true).call().isEmpty()) {
                throw new AppException(
                    ErrorCode.NO_CHANGES_TO_COMMIT,
                    "커밋할 변경 사항이 없습니다. 민감 파일은 자동으로 제외됩니다."
                );
            }

            ObjectId commitId = git.commit()
                .setMessage(request.message())
                .setAuthor(request.authorName(), request.authorEmail())
                .call()
                .getId();
            return commitId.name();
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw workspaceService.buildGitAccessFailure(repoOwner, repoName, workspacePath, exception);
        }
    }

    private void stageChanges(Git git, Status status) throws GitAPIException {
        if (hasTrackedChanges(status)) {
            git.add().setUpdate(true).addFilepattern(".").call();
        }

        for (String path : status.getUntracked()) {
            if (isSensitiveUntracked(path)) {
                log.warn("Skipping sensitive untracked file from commit staging: {}", path);
                continue;
            }
            git.add().addFilepattern(path).call();
        }
    }

    private boolean hasTrackedChanges(Status status) {
        return !status.getModified().isEmpty()
            || !status.getChanged().isEmpty()
            || !status.getRemoved().isEmpty()
            || !status.getMissing().isEmpty();
    }

    private boolean isSensitiveUntracked(String path) {
        String normalized = path.replace('\\', '/');
        String fileName = normalized.contains("/")
            ? normalized.substring(normalized.lastIndexOf('/') + 1)
            : normalized;
        String lowerFileName = fileName.toLowerCase(Locale.ROOT);

        if (SENSITIVE_FILE_NAMES.contains(lowerFileName)) {
            return true;
        }
        if (SENSITIVE_ENV_SUFFIX.matcher(fileName).matches()) {
            return true;
        }
        return SENSITIVE_SUFFIX.matcher(normalized).matches();
    }
}
