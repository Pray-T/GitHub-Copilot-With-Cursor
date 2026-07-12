package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.config.ContributeProperties;
import com.demo.githubcopilotwithcursor.config.GitHubProperties;
import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.domain.WorkspaceMode;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import com.demo.githubcopilotwithcursor.github.GitHubApiClient;
import com.demo.githubcopilotwithcursor.github.GitHubAuthenticatedUser;
import com.demo.githubcopilotwithcursor.github.GitHubRepoRef;
import com.demo.githubcopilotwithcursor.github.GitHubRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceBootstrapService.class);

    private final WorkspaceProperties workspaceProperties;
    private final ContributeProperties contributeProperties;
    private final GitHubProperties gitHubProperties;
    private final WorkspaceGuard workspaceGuard;
    private final WorkspaceService workspaceService;
    private final GitHubApiClient gitHubApiClient;

    public WorkspaceBootstrapService(
        WorkspaceProperties workspaceProperties,
        ContributeProperties contributeProperties,
        GitHubProperties gitHubProperties,
        WorkspaceGuard workspaceGuard,
        WorkspaceService workspaceService,
        GitHubApiClient gitHubApiClient
    ) {
        this.workspaceProperties = workspaceProperties;
        this.contributeProperties = contributeProperties;
        this.gitHubProperties = gitHubProperties;
        this.workspaceGuard = workspaceGuard;
        this.workspaceService = workspaceService;
        this.gitHubApiClient = gitHubApiClient;
    }

    public RepositoryWorkspace bootstrap(
        String upstreamUrl,
        GitHubRepoRef upstream,
        GitHubAuthenticatedUser user,
        WorkspaceMode mode,
        String agentPrompt
    ) {
        Path root = workspaceProperties.rootPath();
        Path target = workspaceGuard.resolveWorkspace(root, upstream.owner(), upstream.repo());
        if (workspaceService.isRegisteredAndPresent(upstream.owner(), upstream.repo(), target)) {
            throw new AppException(
                ErrorCode.WORKSPACE_ALREADY_EXISTS,
                "워크스페이스 '" + upstream.owner() + "/" + upstream.repo() + "'가 이미 존재합니다. 먼저 삭제 후 재시도하세요."
            );
        }
        workspaceService.assertCloneTargetClear(target);
        createWorkspaceRoot(root);

        ForkResolution fork = resolveFork(user.login(), upstream);
        String cloneUrl = fork.repository().cloneUrl() != null ? fork.repository().cloneUrl() : fork.repository().htmlUrl();
        String forkUrl = fork.repository().htmlUrl() != null ? fork.repository().htmlUrl() : cloneUrl;

        log.info("Bootstrapping v3 workspace {}/{} mode={} forkReused={}", upstream.owner(), upstream.repo(), mode, fork.reused());
        try (Git git = Git.cloneRepository()
            .setURI(cloneUrl)
            .setDirectory(target.toFile())
            .setCredentialsProvider(credentialsProvider())
            .call()) {

            addUpstreamRemote(git, upstreamUrl);
            String branchName = createFeatureBranch(git, upstream.repo());
            pushFeatureBranch(git, branchName);
            Repository gitRepository = git.getRepository();
            ObjectId head = gitRepository.resolve("HEAD");
            if (head == null) {
                throw new AppException(ErrorCode.CLONE_FAILED, "클론된 저장소의 HEAD를 찾을 수 없습니다.");
            }

            return workspaceService.persistNewWorkspace(
                new RepositoryWorkspace(
                    upstream.owner(),
                    upstream.repo(),
                    upstreamUrl,
                    target.toString(),
                    head.name(),
                    mode,
                    upstreamUrl,
                    forkUrl,
                    fork.reused(),
                    branchName,
                    agentPrompt
                )
            );
        } catch (GitAPIException exception) {
            if (Files.exists(target)) {
                workspaceService.cleanupOrphanDiskIfUnregistered(upstream.owner(), upstream.repo(), target);
            }
            if (Files.exists(target)) {
                throw new AppException(
                    ErrorCode.CLONE_FAILED,
                    WorkspaceService.CLONE_TARGET_LOCKED_MESSAGE,
                    exception
                );
            }
            throw new AppException(
                ErrorCode.CLONE_FAILED,
                "git clone 실행에 실패했습니다: " + sanitizeGitMessage(exception.getMessage()),
                exception
            );
        } catch (Exception exception) {
            if (exception instanceof AppException appException) {
                throw appException;
            }
            throw new AppException(ErrorCode.CLONE_FAILED, "워크스페이스를 준비하지 못했습니다.", exception);
        }
    }

    private ForkResolution resolveFork(String login, GitHubRepoRef upstream) {
        Optional<GitHubRepository> existingFork = gitHubApiClient.findRepository(login, upstream.repo());
        if (existingFork.isPresent()) {
            return new ForkResolution(existingFork.get(), true);
        }

        GitHubRepository fork = gitHubApiClient.forkRepository(upstream.owner(), upstream.repo());
        GitHubRepository readyFork = waitForFork(login, upstream.repo()).orElse(fork);
        return new ForkResolution(readyFork, false);
    }

    private Optional<GitHubRepository> waitForFork(String login, String repo) {
        int maxAttempts = contributeProperties.getForkReadyMaxAttempts();
        long delayMs = contributeProperties.getForkReadyDelayMs();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            Optional<GitHubRepository> repository = gitHubApiClient.findRepository(login, repo);
            if (repository.isPresent()) {
                return repository;
            }
            if (attempt < maxAttempts - 1) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            }
        }
        log.warn("Fork for {}/{} was not ready after {} attempts ({} ms apart)", login, repo, maxAttempts, delayMs);
        return Optional.empty();
    }

    private void createWorkspaceRoot(Path root) {
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "워크스페이스 루트 디렉터리를 생성하지 못했습니다.", exception);
        }
    }

    private void addUpstreamRemote(Git git, String upstreamUrl) throws Exception {
        git.remoteAdd()
            .setName("upstream")
            .setUri(new URIish(upstreamUrl))
            .call();
    }

    private String createFeatureBranch(Git git, String repoName) throws GitAPIException {
        Collection<Ref> remoteHeads = git.lsRemote()
            .setRemote("origin")
            .setHeads(true)
            .setCredentialsProvider(credentialsProvider())
            .call();
        String baseName = contributeProperties.getBranchPrefix()
            + "/"
            + repoName
            + "-"
            + LocalDateTime.now().format(contributeProperties.branchTimestampFormatter());
        for (int suffix = 0; suffix < 100; suffix++) {
            String branchName = suffix == 0 ? baseName : baseName + "-" + suffix;
            if (!remoteBranchExists(remoteHeads, branchName)) {
                git.checkout()
                    .setCreateBranch(true)
                    .setName(branchName)
                    .call();
                return branchName;
            }
        }
        throw new AppException(ErrorCode.CLONE_FAILED, "사용 가능한 feature branch 이름을 만들지 못했습니다.");
    }

    private void pushFeatureBranch(Git git, String branchName) throws GitAPIException {
        git.push()
            .setRemote("origin")
            .setCredentialsProvider(credentialsProvider())
            .add("refs/heads/" + branchName)
            .call();
    }

    private boolean remoteBranchExists(Collection<Ref> refs, String branchName) {
        String refName = "refs/heads/" + branchName;
        return refs.stream().anyMatch(ref -> refName.equals(ref.getName()));
    }

    private CredentialsProvider credentialsProvider() {
        return new UsernamePasswordCredentialsProvider("x-access-token", gitHubProperties.getToken());
    }

    private String sanitizeGitMessage(String message) {
        return gitHubApiClient.maskToken(message);
    }

    private record ForkResolution(GitHubRepository repository, boolean reused) {
    }
}
