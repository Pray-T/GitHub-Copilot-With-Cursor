package com.demo.githubcopilotwithcursor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.dto.LaunchIdeResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LaunchIdeServiceTest {

    private WorkspaceProperties workspaceProperties;
    private WorkspaceGuard workspaceGuard;
    private WorkspaceService workspaceService;
    private IdeLauncher ideLauncher;
    private LaunchIdeService service;

    @BeforeEach
    void setUp() {
        workspaceProperties = new WorkspaceProperties();
        workspaceProperties.setIdeCommand("cursor");
        workspaceGuard = org.mockito.Mockito.mock(WorkspaceGuard.class);
        workspaceService = org.mockito.Mockito.mock(WorkspaceService.class);
        ideLauncher = org.mockito.Mockito.mock(IdeLauncher.class);
        service = new LaunchIdeService(workspaceProperties, workspaceGuard, workspaceService, ideLauncher);
    }

    @Test
    void launchIdeTriggersAsyncLauncher() {
        RepositoryWorkspace workspace = new RepositoryWorkspace(
            "octocat",
            "demo",
            "https://github.com/octocat/demo",
            "build/tmp/launch-ide/octocat/demo",
            "0123456789012345678901234567890123456789",
            null,
            null,
            false,
            null
        );
        Path path = Path.of(workspace.getWorkspacePath());
        when(workspaceService.requirePresentWorkspace("octocat", "demo")).thenReturn(workspace);
        when(workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath())).thenReturn(path);

        LaunchIdeResponse response = service.launchIde("octocat", "demo");

        assertThat(response.repoOwner()).isEqualTo("octocat");
        assertThat(response.ideLaunchPending()).isTrue();
        assertThat(response.ideCommand()).isEqualTo("cursor");
        verify(ideLauncher).launchAsync(any(Path.class), any());
    }

    @Test
    void launchIdeRecordsResultFromCallback() {
        RepositoryWorkspace workspace = new RepositoryWorkspace(
            "octocat",
            "demo",
            "https://github.com/octocat/demo",
            "build/tmp/launch-ide/octocat/demo",
            "0123456789012345678901234567890123456789",
            null,
            null,
            false,
            null
        );
        Path path = Path.of(workspace.getWorkspacePath());
        when(workspaceService.requirePresentWorkspace("octocat", "demo")).thenReturn(workspace);
        when(workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath())).thenReturn(path);

        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<Boolean> callback = invocation.getArgument(1);
            callback.accept(true);
            return null;
        }).when(ideLauncher).launchAsync(any(Path.class), any());

        service.launchIde("octocat", "demo");

        verify(workspaceService).recordIdeLaunchResult("octocat", "demo", true);
    }
}
