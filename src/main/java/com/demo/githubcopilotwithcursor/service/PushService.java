package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.config.GitHubProperties;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import java.nio.file.Path;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;

@Service
public class PushService {

    private final GitHubProperties gitHubProperties;
    private final WorkspaceService workspaceService;

    public PushService(GitHubProperties gitHubProperties, WorkspaceService workspaceService) {
        this.gitHubProperties = gitHubProperties;
        this.workspaceService = workspaceService;
    }

    public void push(String repoOwner, String repoName, Path workspacePath, String branchName) {
        try (Git git = Git.open(workspacePath.toFile())) {
            RefSpec refSpec = new RefSpec("refs/heads/" + branchName + ":refs/heads/" + branchName);
            Iterable<PushResult> results = git.push()
                .setRemote("origin")
                .setRefSpecs(refSpec)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider("x-access-token", gitHubProperties.getToken()))
                .call();
            validateResults(results);
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw workspaceService.buildGitAccessFailure(repoOwner, repoName, workspacePath, exception);
        }
    }

    private void validateResults(Iterable<PushResult> results) {
        boolean sawUpdate = false;
        for (PushResult result : results) {
            for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                sawUpdate = true;
                RemoteRefUpdate.Status status = update.getStatus();
                if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE) {
                    throw new AppException(
                        ErrorCode.PUSH_FAILED,
                        "upstream이 변경되어 push가 거부되었습니다. 워크스페이스를 삭제하고 다시 시작하세요.",
                        Map.of("refUpdateStatus", status.name())
                    );
                }
            }
        }
        if (!sawUpdate) {
            throw new AppException(ErrorCode.PUSH_FAILED, "push 결과를 확인할 수 없습니다.");
        }
    }
}
