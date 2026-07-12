package com.demo.githubcopilotwithcursor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.githubcopilotwithcursor.config.CursorProperties;
import com.demo.githubcopilotwithcursor.cursor.CloudAgentClient;
import com.demo.githubcopilotwithcursor.cursor.LlmMetadataException;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.dto.DiffResponse;
import com.demo.githubcopilotwithcursor.dto.PrPrepareResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmMetadataServiceTest {

    private CursorProperties cursorProperties;
    private CloudAgentClient cloudAgentClient;
    private WorkspaceService workspaceService;
    private WorkspaceGuard workspaceGuard;
    private DiffService diffService;
    private PullRequestService pullRequestService;
    private LlmMetadataService service;

    @BeforeEach
    void setUp() {
        cursorProperties = new CursorProperties();
        cloudAgentClient = org.mockito.Mockito.mock(CloudAgentClient.class);
        workspaceService = org.mockito.Mockito.mock(WorkspaceService.class);
        workspaceGuard = org.mockito.Mockito.mock(WorkspaceGuard.class);
        diffService = org.mockito.Mockito.mock(DiffService.class);
        pullRequestService = org.mockito.Mockito.mock(PullRequestService.class);
        service = new LlmMetadataService(
            cursorProperties,
            cloudAgentClient,
            workspaceService,
            workspaceGuard,
            diffService,
            pullRequestService
        );
    }

    @Test
    void usesCacheWhenStillValid() {
        RepositoryWorkspace workspace = contributeWorkspace();
        workspace.cacheLlmMetadata("cached commit", "cached title", "cached body", OffsetDateTime.now());
        workspace.setLastDiffAt(OffsetDateTime.now().minusMinutes(5));
        when(workspaceService.requireContributeWorkspace("octocat", "demo")).thenReturn(workspace);
        when(workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath()))
            .thenReturn(java.nio.file.Path.of(workspace.getWorkspacePath()));

        PrPrepareResponse response = service.prepareForPr("octocat", "demo");

        assertThat(response.commitMessage()).isEqualTo("cached commit");
        assertThat(response.fallbackUsed()).isFalse();
        verify(cloudAgentClient, never()).requestPrMetadataFollowUp(any(), any());
    }

    @Test
    void fallsBackWhenFollowUpFails() {
        RepositoryWorkspace workspace = contributeWorkspace();
        when(workspaceService.requireContributeWorkspace("octocat", "demo")).thenReturn(workspace);
        when(workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath()))
            .thenReturn(java.nio.file.Path.of(workspace.getWorkspacePath()));
        when(diffService.diffWithoutPersist(eq("octocat"), eq("demo"), eq(true), eq(0)))
            .thenReturn(new DiffResponse("octocat", "demo", "sha", OffsetDateTime.now(), 0, List.of()));
        when(cloudAgentClient.requestPrMetadataFollowUp(eq("bc-test-001"), any()))
            .thenThrow(new LlmMetadataException("follow-up down"));
        when(pullRequestService.buildDefaultBody("octocat", "demo")).thenReturn("## 변경 파일\n\n- M README.md\n");

        PrPrepareResponse response = service.prepareForPr("octocat", "demo");

        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.commitMessage()).isEqualTo("Refactor by Cursor Cloud Agent");
        assertThat(response.prBody()).contains("README.md");
        verify(workspaceService).saveWorkspace(workspace);
    }

    private RepositoryWorkspace contributeWorkspace() {
        RepositoryWorkspace workspace = new RepositoryWorkspace(
            "octocat",
            "demo",
            "https://github.com/octocat/demo",
            "build/tmp/llm-meta/octocat/demo",
            "0123456789012345678901234567890123456789",
            "https://github.com/octocat/demo",
            "https://github.com/octocat/demo-fork",
            false,
            "refactor/demo-202605281900"
        );
        workspace.setAgentPrompt("Refactor Owner to record");
        workspace.markAgentStarted("bc-test-001", "run-test-001", OffsetDateTime.now().minusHours(2));
        workspace.markAgentCompleted(OffsetDateTime.now().minusHours(1));
        return workspace;
    }
}
