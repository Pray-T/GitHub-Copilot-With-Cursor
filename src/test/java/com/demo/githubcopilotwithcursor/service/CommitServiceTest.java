package com.demo.githubcopilotwithcursor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import com.demo.githubcopilotwithcursor.dto.CommitPushRequest;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import com.demo.githubcopilotwithcursor.repository.RepositoryWorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommitServiceTest {

    private static final String REPO_OWNER = "spring-projects";
    private static final String REPO_NAME = "demo-repo";

    @TempDir
    Path tempDir;

    @Test
    void commitStagesTrackedChangesAndSafeUntrackedFiles() throws Exception {
        Path workspace = initRepository();
        Files.writeString(workspace.resolve("README.md"), "# Demo\n\nChanged\n");
        Files.writeString(workspace.resolve("notes.txt"), "new file\n");
        Files.writeString(workspace.resolve(".env"), "SECRET=token\n");

        CommitService service = new CommitService(workspaceService());
        String commitSha = service.commit(
            REPO_OWNER,
            REPO_NAME,
            workspace,
            new CommitPushRequest("Update docs", "Tester", "tester@example.com")
        );

        assertThat(commitSha).hasSize(40);
        try (Git git = Git.open(workspace.toFile())) {
            Status status = git.status().call();
            assertThat(status.getUntracked()).containsExactly(".env");
            assertThat(status.getModified()).isEmpty();
            assertThat(status.getAdded()).isEmpty();
        }
    }

    @Test
    void commitRejectsWhenOnlySensitiveUntrackedFilesChanged() throws Exception {
        Path workspace = initRepository();
        Files.writeString(workspace.resolve(".env"), "SECRET=token\n");

        CommitService service = new CommitService(workspaceService());

        assertThatThrownBy(() -> service.commit(
            REPO_OWNER,
            REPO_NAME,
            workspace,
            new CommitPushRequest("Add secrets", "Tester", "tester@example.com")
        ))
            .isInstanceOfSatisfying(AppException.class, exception -> {
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NO_CHANGES_TO_COMMIT);
                assertThat(exception.getMessage()).contains("민감 파일");
            });
    }

    private Path initRepository() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace);
        try (Git git = Git.init().setDirectory(workspace.toFile()).call()) {
            Files.writeString(workspace.resolve("README.md"), "# Demo\n");
            git.add().addFilepattern("README.md").call();
            git.commit()
                .setMessage("Initial commit")
                .setAuthor("Test", "test@example.com")
                .call();
        }
        return workspace;
    }

    private WorkspaceService workspaceService() {
        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setRoot(tempDir.toString());
        RepositoryWorkspaceRepository repository = mock(RepositoryWorkspaceRepository.class);
        WorkspaceGuard workspaceGuard = new WorkspaceGuard();
        return new WorkspaceService(
            properties,
            workspaceGuard,
            mock(WorkspaceReconcileService.class),
            mock(WorkspaceDiskCleanupService.class),
            repository
        );
    }
}
