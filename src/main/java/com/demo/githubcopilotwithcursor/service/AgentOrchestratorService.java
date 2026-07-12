package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.cursor.CloudAgentClient;
import com.demo.githubcopilotwithcursor.cursor.CursorApiException;
import com.demo.githubcopilotwithcursor.cursor.dto.AgentStatusResponse;
import com.demo.githubcopilotwithcursor.cursor.dto.StartAgentRequest;
import com.demo.githubcopilotwithcursor.cursor.dto.StartAgentResponse;
import com.demo.githubcopilotwithcursor.domain.AgentStatus;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.dto.AgentCancelResponse;
import com.demo.githubcopilotwithcursor.dto.AgentStartRequest;
import com.demo.githubcopilotwithcursor.dto.AgentStartResponse;
import com.demo.githubcopilotwithcursor.dto.AgentStatusJsonResponse;
import com.demo.githubcopilotwithcursor.dto.AgentSyncResponse;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AgentOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestratorService.class);

    private final WorkspaceService workspaceService;
    private final CloudAgentClient cloudAgentClient;
    private final AgentSyncService agentSyncService;

    public AgentOrchestratorService(
        WorkspaceService workspaceService,
        CloudAgentClient cloudAgentClient,
        AgentSyncService agentSyncService
    ) {
        this.workspaceService = workspaceService;
        this.cloudAgentClient = cloudAgentClient;
        this.agentSyncService = agentSyncService;
    }

    public void startAgent(RepositoryWorkspace workspace) {
        startAgent(workspace, workspace.getAgentPrompt());
    }

    public void startAgent(RepositoryWorkspace workspace, String agentPrompt) {
        if (workspace.isAgentRunning()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("currentAgentId", workspace.getCursorAgentId());
            throw new AppException(ErrorCode.AGENT_BUSY, "다른 Cursor Agent가 아직 실행 중입니다.", details);
        }
        String prompt = agentPrompt != null && !agentPrompt.isBlank() ? agentPrompt : workspace.getAgentPrompt();
        if (prompt == null || prompt.isBlank()) {
            throw new AppException(ErrorCode.INVALID_AGENT_PROMPT, "Agent 프롬프트는 비워둘 수 없습니다.");
        }
        workspace.setAgentPrompt(prompt);
        workspace.invalidateLlmCache();

        StartAgentResponse response = cloudAgentClient.startAgent(
            StartAgentRequest.of(prompt, workspace.getForkUrl(), workspace.getBranchName(), workspace.getHeadCommitSha())
        );
        OffsetDateTime startedAt = response.startedAt() != null ? response.startedAt() : OffsetDateTime.now();
        workspace.markAgentStarted(response.agentId(), response.runId(), startedAt);
        workspaceService.saveWorkspace(workspace);
        log.info("Started Cursor agent {} for {}/{}", response.agentId(), workspace.getRepoOwner(), workspace.getRepoName());
    }

    public AgentStartResponse restartAgent(AgentStartRequest request) {
        RepositoryWorkspace workspace = workspaceService.requirePresentWorkspace(request.repoOwner(), request.repoName());
        startAgent(workspace, request.agentPrompt());
        return new AgentStartResponse(
            workspace.getRepoOwner(),
            workspace.getRepoName(),
            workspace.getCursorAgentId(),
            workspace.getCursorRunId(),
            workspace.getAgentStatus() != null ? workspace.getAgentStatus().name() : AgentStatus.RUNNING.name(),
            workspace.getAgentStartedAt()
        );
    }

    public AgentStatusJsonResponse getStatus(String repoOwner, String repoName) {
        RepositoryWorkspace workspace = workspaceService.requirePresentWorkspace(repoOwner, repoName);
        if (workspace.getCursorAgentId() == null) {
            throw new AppException(ErrorCode.AGENT_NOT_FOUND, "Cursor Agent가 시작되지 않았습니다.");
        }

        Map<String, Object> details = null;
        String cursorStatus = null;
        String syncedHeadSha = null;

        try {
            AgentStatusResponse cursorResponse = cloudAgentClient.getAgentStatus(
                workspace.getCursorAgentId(),
                workspace.getCursorRunId()
            );
            cursorStatus = cursorResponse.status();

            if (cursorResponse.isSuccessful() && workspace.getAgentStatus() != AgentStatus.COMPLETED) {
                syncedHeadSha = agentSyncService.pullAgentBranch(workspace);
                workspace = workspaceService.requirePresentWorkspace(repoOwner, repoName);
            } else if (cursorResponse.isFailed()) {
                workspace.markAgentFailed(OffsetDateTime.now());
                workspaceService.saveWorkspace(workspace);
                throw agentFailedException(workspace, cursorResponse);
            } else if (cursorResponse.isCancelled()) {
                workspace.markAgentCancelled(OffsetDateTime.now());
                workspaceService.saveWorkspace(workspace);
            } else if (cursorResponse.reason() != null && !cursorResponse.reason().isBlank()) {
                details = new LinkedHashMap<>();
                details.put("cursorMessage", cursorResponse.reason());
            }
        } catch (CursorApiException exception) {
            log.warn("Cursor status poll failed for {}/{}: {}", repoOwner, repoName, exception.getMessage());
            details = exception.details() != null ? exception.details() : new LinkedHashMap<>();
            details.putIfAbsent("cursorError", exception.getMessage());
        } catch (AppException exception) {
            if (exception.getErrorCode() == ErrorCode.AGENT_SYNC_FAILED || exception.getErrorCode() == ErrorCode.AGENT_FAILED) {
                throw exception;
            }
            details = exception.getDetails();
        }

        AgentStatus agentStatus = workspace.getAgentStatus();
        return new AgentStatusJsonResponse(
            workspace.getRepoOwner(),
            workspace.getRepoName(),
            workspace.getCursorAgentId(),
            agentStatus != null ? agentStatus.name() : null,
            cursorStatus,
            workspace.getAgentStartedAt(),
            workspace.getAgentCompletedAt(),
            syncedHeadSha,
            details
        );
    }

    public AgentCancelResponse cancel(String repoOwner, String repoName) {
        RepositoryWorkspace workspace = workspaceService.requirePresentWorkspace(repoOwner, repoName);
        if (workspace.getCursorAgentId() == null) {
            throw new AppException(ErrorCode.AGENT_NOT_FOUND, "취소할 Cursor Agent가 없습니다.");
        }
        cloudAgentClient.cancelAgent(workspace.getCursorAgentId(), workspace.getCursorRunId());
        OffsetDateTime cancelledAt = OffsetDateTime.now();
        workspace.markAgentCancelled(cancelledAt);
        workspaceService.saveWorkspace(workspace);
        return new AgentCancelResponse(
            workspace.getRepoOwner(),
            workspace.getRepoName(),
            workspace.getCursorAgentId(),
            AgentStatus.CANCELLED.name(),
            cancelledAt
        );
    }

    public AgentSyncResponse sync(String repoOwner, String repoName) {
        RepositoryWorkspace workspace = workspaceService.requirePresentWorkspace(repoOwner, repoName);
        String syncedHeadSha = agentSyncService.pullAgentBranch(workspace);
        workspace = workspaceService.requirePresentWorkspace(repoOwner, repoName);
        return new AgentSyncResponse(
            workspace.getRepoOwner(),
            workspace.getRepoName(),
            syncedHeadSha,
            OffsetDateTime.now(),
            workspace.getAgentStatus() != null ? workspace.getAgentStatus().name() : AgentStatus.COMPLETED.name(),
            workspace.getStatus().name()
        );
    }

    private AppException agentFailedException(RepositoryWorkspace workspace, AgentStatusResponse cursorResponse) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("agentId", workspace.getCursorAgentId());
        details.put("cursorStatus", cursorResponse.status());
        if (cursorResponse.reason() != null) {
            details.put("cursorReason", cursorResponse.reason());
        }
        return new AppException(ErrorCode.AGENT_FAILED, "Cursor Cloud Agent가 실패했습니다.", details);
    }
}
