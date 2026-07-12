package com.demo.githubcopilotwithcursor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.githubcopilotwithcursor.cursor.CloudAgentClient;
import com.demo.githubcopilotwithcursor.cursor.dto.AgentStatusResponse;
import com.demo.githubcopilotwithcursor.cursor.dto.CancelAgentResponse;
import com.demo.githubcopilotwithcursor.domain.AgentStatus;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.domain.WorkspaceStatus;
import com.demo.githubcopilotwithcursor.dto.AgentCancelResponse;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentOrchestratorServiceTest {

    private WorkspaceService workspaceService;
    private CloudAgentClient cloudAgentClient;
    private AgentSyncService agentSyncService;
    private AgentOrchestratorService service;

    @BeforeEach
    void setUp() {
        workspaceService = mock(WorkspaceService.class);
        cloudAgentClient = mock(CloudAgentClient.class);
        agentSyncService = mock(AgentSyncService.class);
        service = new AgentOrchestratorService(workspaceService, cloudAgentClient, agentSyncService);
    }

    @Test
    void cancelMarksWorkspaceCancelledAndPersistsState() {
        RepositoryWorkspace workspace = workspaceWithAgent();
        when(workspaceService.requirePresentWorkspace("octocat", "demo")).thenReturn(workspace);
        when(cloudAgentClient.cancelAgent("agent-1", "run-1")).thenReturn(new CancelAgentResponse("run-1"));

        AgentCancelResponse response = service.cancel("octocat", "demo");

        assertThat(response.agentId()).isEqualTo("agent-1");
        assertThat(response.agentStatus()).isEqualTo(AgentStatus.CANCELLED.name());
        assertThat(workspace.getAgentStatus()).isEqualTo(AgentStatus.CANCELLED);
        assertThat(workspace.getStatus()).isEqualTo(WorkspaceStatus.AGENT_FAILED);
        verify(cloudAgentClient).cancelAgent("agent-1", "run-1");
        verify(workspaceService).saveWorkspace(workspace);
    }

    @Test
    void finishedCursorStatusPropagatesAgentSyncFailed() {
        RepositoryWorkspace workspace = workspaceWithAgent();
        when(workspaceService.requirePresentWorkspace("octocat", "demo")).thenReturn(workspace);
        when(cloudAgentClient.getAgentStatus("agent-1", "run-1"))
            .thenReturn(new AgentStatusResponse("agent-1", "FINISHED", null, "abc123", OffsetDateTime.now(), OffsetDateTime.now()));
        when(agentSyncService.pullAgentBranch(workspace))
            .thenThrow(new AppException(ErrorCode.AGENT_SYNC_FAILED, "Agent 브랜치 pull에 실패했습니다."));

        assertThatThrownBy(() -> service.getStatus("octocat", "demo"))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.AGENT_SYNC_FAILED);

        verify(agentSyncService).pullAgentBranch(workspace);
    }

    private RepositoryWorkspace workspaceWithAgent() {
        RepositoryWorkspace workspace = new RepositoryWorkspace(
            "octocat",
            "demo",
            "https://github.com/octocat/demo",
            "build/tmp/agent-orchestrator-test/octocat/demo",
            "0123456789012345678901234567890123456789",
            "https://github.com/octocat/demo",
            "https://github.com/octocat/demo",
            false,
            "refactor/demo-202605281900"
        );
        workspace.setAgentPrompt("Refactor demo");
        workspace.markAgentStarted("agent-1", "run-1", OffsetDateTime.now());
        return workspace;
    }
}
