package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.domain.WorkspaceMode;
import com.demo.githubcopilotwithcursor.domain.WorkspaceStatus;
import com.demo.githubcopilotwithcursor.dto.WorkspaceListItem;
import com.demo.githubcopilotwithcursor.dto.WorkspaceListResponse;
import com.demo.githubcopilotwithcursor.dto.WorkspaceResponse;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import com.demo.githubcopilotwithcursor.repository.RepositoryWorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {

    public static final String RECONCILED_NOT_FOUND_MESSAGE =
        "로컬 워크스페이스가 없어 메타데이터를 정리했습니다. 다시 클론해 주세요.";

    public static final String CLONE_TARGET_LOCKED_MESSAGE =
        "이전 워크스페이스 폴더를 정리하지 못했습니다. Cursor 등 IDE에서 해당 저장소를 닫은 뒤 다시 클론해 주세요.";

    public static final String DELETE_SUCCESS_WITH_IDE_HINT =
        "워크스페이스를 삭제했습니다. 같은 저장소를 바로 다시 클론하려면 Cursor 등 IDE에서 이전 폴더를 닫아 주세요.";

    public static final String GIT_ACCESS_FAILED_MESSAGE =
        "로컬 Git 저장소에 접근하지 못했습니다.";

    public static final String NOT_CONTRIBUTE_WORKSPACE_MESSAGE =
        "Contribute 모드 워크스페이스가 아닙니다.";

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

    private final WorkspaceProperties properties;
    private final WorkspaceGuard workspaceGuard;
    private final WorkspaceReconcileService workspaceReconcileService;
    private final WorkspaceDiskCleanupService workspaceDiskCleanupService;
    private final RepositoryWorkspaceRepository repository;

    public WorkspaceService(
        WorkspaceProperties properties,
        WorkspaceGuard workspaceGuard,
        WorkspaceReconcileService workspaceReconcileService,
        WorkspaceDiskCleanupService workspaceDiskCleanupService,
        RepositoryWorkspaceRepository repository
    ) {
        this.properties = properties;
        this.workspaceGuard = workspaceGuard;
        this.workspaceReconcileService = workspaceReconcileService;
        this.workspaceDiskCleanupService = workspaceDiskCleanupService;
        this.repository = repository;
    }

    public boolean isWorkspacePresent(Path path) {
        return workspaceReconcileService.isWorkspacePresent(path);
    }

    public void reconcileMissingDisk(String repoOwner, String repoName) {
        workspaceReconcileService.reconcileMissingDisk(repoOwner, repoName);
    }

    public AppException buildGitAccessFailure(String repoOwner, String repoName, Path workspacePath, Exception cause) {
        if (isWorkspacePresent(workspacePath)) {
            return new AppException(ErrorCode.INTERNAL_ERROR, GIT_ACCESS_FAILED_MESSAGE, cause);
        }
        reconcileMissingDisk(repoOwner, repoName);
        return new AppException(ErrorCode.WORKSPACE_NOT_FOUND, RECONCILED_NOT_FOUND_MESSAGE, cause);
    }

    @Transactional
    public RepositoryWorkspace requirePresentWorkspace(String repoOwner, String repoName) {
        workspaceGuard.validateRepoOwner(repoOwner);
        workspaceGuard.validateRepoName(repoName);
        reconcileMissingDisk(repoOwner, repoName);
        return repository.findByRepoOwnerAndRepoName(repoOwner, repoName)
            .orElseThrow(() -> new AppException(ErrorCode.WORKSPACE_NOT_FOUND, RECONCILED_NOT_FOUND_MESSAGE));
    }

    public boolean isRegisteredAndPresent(String repoOwner, String repoName, Path resolvedPath) {
        workspaceGuard.validateRepoOwner(repoOwner);
        workspaceGuard.validateRepoName(repoName);
        if (isRegisteredInDatabase(repoOwner, repoName)) {
            if (isWorkspacePresent(resolvedPath)) {
                return true;
            }
            reconcileMissingDisk(repoOwner, repoName);
            return false;
        }
        reconcileOrphanDiskIfPresent(resolvedPath, repoOwner, repoName);
        return false;
    }

    @Transactional(readOnly = true)
    boolean isRegisteredInDatabase(String repoOwner, String repoName) {
        return repository.existsByRepoOwnerAndRepoName(repoOwner, repoName);
    }

    @Transactional
    public RepositoryWorkspace requireContributeWorkspace(String repoOwner, String repoName) {
        RepositoryWorkspace workspace = requirePresentWorkspace(repoOwner, repoName);
        assertContributeWorkspace(workspace);
        return workspace;
    }

    public void cleanupOrphanDiskIfUnregistered(String repoOwner, String repoName, Path resolvedPath) {
        workspaceGuard.validateRepoOwner(repoOwner);
        workspaceGuard.validateRepoName(repoName);
        if (!isRegisteredInDatabase(repoOwner, repoName)) {
            reconcileOrphanDiskIfPresent(resolvedPath, repoOwner, repoName);
        }
    }

    @Transactional
    public RepositoryWorkspace persistNewWorkspace(RepositoryWorkspace workspace) {
        workspaceGuard.validateRepoOwner(workspace.getRepoOwner());
        workspaceGuard.validateRepoName(workspace.getRepoName());
        return repository.save(workspace);
    }

    @Transactional
    public RepositoryWorkspace saveWorkspace(RepositoryWorkspace workspace) {
        workspaceGuard.validateRepoOwner(workspace.getRepoOwner());
        workspaceGuard.validateRepoName(workspace.getRepoName());
        return repository.save(workspace);
    }

    public void assertCloneTargetClear(Path target) {
        if (target == null) {
            return;
        }
        Path normalized = workspaceGuard.normalizePath(target);
        if (!workspaceGuard.isUnderRoot(properties.rootPath(), normalized)) {
            return;
        }
        if (Files.exists(normalized)) {
            throw new AppException(ErrorCode.CLONE_FAILED, CLONE_TARGET_LOCKED_MESSAGE);
        }
    }

    @Transactional
    public WorkspaceListResponse listWorkspaces() {
        List<WorkspaceListItem> items = repository.findAllByOrderByClonedAtDesc().stream()
            .filter(workspace -> {
                Path path = workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath());
                if (isWorkspacePresent(path)) {
                    return true;
                }
                workspaceReconcileService.reconcileMissingDisk(workspace.getRepoOwner(), workspace.getRepoName());
                return false;
            })
            .map(this::toListItem)
            .toList();
        return new WorkspaceListResponse(items);
    }

    @Transactional
    public WorkspaceResponse findWorkspace(String repoOwner, String repoName) {
        return toResponse(requirePresentWorkspace(repoOwner, repoName));
    }

    @Transactional
    public void recordIdeLaunchResult(String repoOwner, String repoName, boolean launched) {
        workspaceGuard.validateRepoOwner(repoOwner);
        workspaceGuard.validateRepoName(repoName);
        repository.findByRepoOwnerAndRepoName(repoOwner, repoName).ifPresent(workspace -> {
            workspace.setIdeLaunched(launched);
            workspace.setStatus(launched ? WorkspaceStatus.IDE_LAUNCHED : WorkspaceStatus.IDE_LAUNCH_FAILED);
            repository.save(workspace);
            log.info("IDE launch result for '{}/{}': launched={}", repoOwner, repoName, launched);
        });
    }

    public void deleteWorkspace(String repoOwner, String repoName) {
        WorkspaceDeletionSnapshot snapshot = workspaceReconcileService.deleteWorkspaceRecord(repoOwner, repoName);
        scheduleDiskCleanup(snapshot);
    }

    private void scheduleDiskCleanup(WorkspaceDeletionSnapshot snapshot) {
        String repoOwner = snapshot.repoOwner();
        String repoName = snapshot.repoName();
        Path resolvedPath = workspaceGuard.resolveWorkspace(properties.rootPath(), repoOwner, repoName);
        Path savedPath = workspaceGuard.normalizePath(snapshot.savedPath());
        if (workspaceGuard.isUnderRoot(properties.rootPath(), savedPath)) {
            scheduleDiskCleanupAsync(savedPath, snapshot.label());
            return;
        }
        if (savedPath != null) {
            warnPathOutsideWorkspaceRoot("Saved workspace path", snapshot.label(), savedPath);
        }
        if (Files.exists(resolvedPath)) {
            scheduleDiskCleanupAsync(resolvedPath, snapshot.label());
        }
    }

    private void reconcileOrphanDiskIfPresent(Path path, String repoOwner, String repoName) {
        if (path == null || !isWorkspacePresent(path)) {
            return;
        }
        Path normalized = workspaceGuard.normalizePath(path);
        if (!workspaceGuard.isUnderRoot(properties.rootPath(), normalized)) {
            warnPathOutsideWorkspaceRoot("Orphan disk workspace", repoOwner + "/" + repoName, normalized);
            return;
        }
        log.info("Reconciling orphan disk workspace for '{}/{}' at {}", repoOwner, repoName, normalized);
        workspaceDiskCleanupService.deleteWorkspaceDirectory(normalized, repoOwner + "/" + repoName);
    }

    private void warnPathOutsideWorkspaceRoot(String description, String label, Path path) {
        log.warn(
            "{} for '{}' at {} is outside workspace root — skipping cleanup",
            description,
            label,
            workspaceGuard.normalizePath(path)
        );
    }

    private void scheduleDiskCleanupAsync(Path path, String label) {
        workspaceDiskCleanupService.deleteWorkspaceDirectoryAsync(path, label);
    }

    private void assertContributeWorkspace(RepositoryWorkspace workspace) {
        if (workspace.getMode() != WorkspaceMode.CONTRIBUTE) {
            throw new AppException(ErrorCode.NOT_CONTRIBUTE_WORKSPACE, NOT_CONTRIBUTE_WORKSPACE_MESSAGE);
        }
        if (workspace.getBranchName() == null || workspace.getBranchName().isBlank()) {
            throw new AppException(ErrorCode.NOT_CONTRIBUTE_WORKSPACE, NOT_CONTRIBUTE_WORKSPACE_MESSAGE);
        }
        if (workspace.getUpstreamUrl() == null || workspace.getUpstreamUrl().isBlank()) {
            throw new AppException(ErrorCode.INVALID_REPO_URL, "upstream URL이 없는 워크스페이스입니다.");
        }
    }

    private WorkspaceListItem toListItem(RepositoryWorkspace workspace) {
        return new WorkspaceListItem(
            workspace.getRepoOwner(),
            workspace.getRepoName(),
            workspace.getRepoUrl(),
            workspace.getWorkspacePath(),
            workspace.getStatus().name(),
            workspace.getMode() != null ? workspace.getMode().name() : WorkspaceMode.REVIEW.name(),
            workspace.getClonedAt(),
            workspace.getLastDiffAt(),
            workspace.getUpstreamUrl(),
            workspace.getForkUrl(),
            workspace.isForkReused(),
            workspace.getBranchName(),
            workspace.getPrUrl(),
            workspace.getAgentStatus() != null ? workspace.getAgentStatus().name() : null,
            workspace.getAgentStartedAt(),
            workspace.getAgentCompletedAt(),
            workspace.isIdeLaunched()
        );
    }

    private WorkspaceResponse toResponse(RepositoryWorkspace workspace) {
        return new WorkspaceResponse(
            workspace.getRepoOwner(),
            workspace.getRepoName(),
            workspace.getRepoUrl(),
            workspace.getWorkspacePath(),
            workspace.getStatus().name(),
            workspace.getMode() != null ? workspace.getMode().name() : WorkspaceMode.REVIEW.name(),
            workspace.getClonedAt(),
            workspace.getLastDiffAt(),
            workspace.getUpstreamUrl(),
            workspace.getForkUrl(),
            workspace.isForkReused(),
            workspace.getBranchName(),
            workspace.getPrUrl(),
            workspace.getAgentStatus() != null ? workspace.getAgentStatus().name() : null,
            workspace.getAgentStartedAt(),
            workspace.getAgentCompletedAt(),
            workspace.getAgentPrompt(),
            workspace.isIdeLaunched(),
            workspace.getLlmCommitMessage(),
            workspace.getLlmPrTitle(),
            workspace.getLlmPrBody(),
            workspace.getLlmCachedAt()
        );
    }
}
