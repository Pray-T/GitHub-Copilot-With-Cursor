package com.demo.githubcopilotwithcursor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.dto.DiffResponse;
import com.demo.githubcopilotwithcursor.repository.RepositoryWorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class DiffServiceTest {

    private static final String REPO_OWNER = "spring-projects";
    private static final String REPO_NAME = "demo-repo";

    private static final Path BANK_TRANSFER_WORKSPACE = Path.of(
        System.getenv().getOrDefault("LOCALAPPDATA", ""),
        "Temp",
        "refactor-workspace",
        "Pray-T",
        "BankTranferSys_Backend_Restful"
    );

    @TempDir
    Path tempDir;

    @Test
    void diffShowsChangesAgainstCloneHeadEvenAfterLocalCommit() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace);

        String cloneHeadSha;
        try (Git git = Git.init().setDirectory(workspace.toFile()).call()) {
            Files.writeString(workspace.resolve("README.md"), "# Demo\n");
            git.add().addFilepattern("README.md").call();
            RevCommit initialCommit = git.commit()
                .setMessage("Initial commit")
                .setAuthor("Test", "test@example.com")
                .call();
            cloneHeadSha = initialCommit.getId().name();

            Files.writeString(workspace.resolve("README.md"), "# Demo\n\nUpdated after clone\n");
            git.add().addFilepattern("README.md").call();
            git.commit()
                .setMessage("Update README")
                .setAuthor("Test", "test@example.com")
                .call();
        }

        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setRoot(tempDir.toString());

        RepositoryWorkspace workspaceEntity = new RepositoryWorkspace(
            REPO_OWNER,
            REPO_NAME,
            "https://github.com/spring-projects/demo-repo",
            workspace.toString(),
            cloneHeadSha
        );

        RepositoryWorkspaceRepository repository = mock(RepositoryWorkspaceRepository.class);
        when(repository.findByRepoOwnerAndRepoName(REPO_OWNER, REPO_NAME)).thenReturn(Optional.of(workspaceEntity));

        WorkspaceGuard workspaceGuard = new WorkspaceGuard();
        WorkspaceService workspaceService = new WorkspaceService(
            properties,
            workspaceGuard,
            new WorkspaceReconcileService(workspaceGuard, repository),
            new WorkspaceDiskCleanupService(properties),
            repository
        );
        DiffService service = new DiffService(properties, new WorkspaceGuard(), workspaceService, repository);

        DiffResponse response = service.diff(REPO_OWNER, REPO_NAME, true, 0);

        assertThat(response.repoOwner()).isEqualTo(REPO_OWNER);
        assertThat(response.repoName()).isEqualTo(REPO_NAME);
        assertThat(response.totalChangedFiles()).isEqualTo(1);
        assertThat(response.changedFiles()).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("README.md");
            assertThat(file.changeType()).isEqualTo("MODIFIED");
            assertThat(file.metadataOnly()).isFalse();
            assertThat(file.newContent()).contains("Updated after clone");
        });
        verify(repository).save(workspaceEntity);
    }

    @Test
    void diffSkipsDirectoryPathsAndStillReportsFileChanges() throws Exception {
        Path workspace = tempDir.resolve("nested-workspace");
        Files.createDirectories(workspace);

        String cloneHeadSha;
        try (Git git = Git.init().setDirectory(workspace.toFile()).call()) {
            Path moduleDir = workspace.resolve("module");
            Files.createDirectories(moduleDir);
            Files.writeString(moduleDir.resolve("App.java"), "class App {}\n");
            Files.writeString(workspace.resolve("README.md"), "v1\n");
            git.add().addFilepattern(".").call();
            RevCommit initialCommit = git.commit()
                .setMessage("Initial commit")
                .setAuthor("Test", "test@example.com")
                .call();
            cloneHeadSha = initialCommit.getId().name();

            Files.writeString(workspace.resolve("README.md"), "v2\n");
        }

        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setRoot(tempDir.toString());

        RepositoryWorkspace workspaceEntity = new RepositoryWorkspace(
            REPO_OWNER,
            REPO_NAME,
            "https://github.com/spring-projects/demo-repo",
            workspace.toString(),
            cloneHeadSha
        );

        RepositoryWorkspaceRepository repository = mock(RepositoryWorkspaceRepository.class);
        when(repository.findByRepoOwnerAndRepoName(REPO_OWNER, REPO_NAME)).thenReturn(Optional.of(workspaceEntity));

        WorkspaceService workspaceService = new WorkspaceService(
            properties,
            new WorkspaceGuard(),
            new WorkspaceReconcileService(new WorkspaceGuard(), repository),
            new WorkspaceDiskCleanupService(properties),
            repository
        );
        DiffService service = new DiffService(properties, new WorkspaceGuard(), workspaceService, repository);

        DiffResponse response = service.diff(REPO_OWNER, REPO_NAME, true, 0);

        assertThat(response.totalChangedFiles()).isEqualTo(1);
        assertThat(response.changedFiles()).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("README.md");
            assertThat(file.newContent()).contains("v2");
        });
    }

    @Test
    @EnabledIf("bankTransferWorkspaceExists")
    void diffCompletesForBankTransferWorkspaceOnDisk() throws Exception {
        Path workspace = BANK_TRANSFER_WORKSPACE.toAbsolutePath().normalize();
        String cloneHeadSha = "55b40cc7740ebfd473299eac6baaca69ec845f67";

        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setRoot(workspace.getParent().getParent().toString());

        RepositoryWorkspace workspaceEntity = new RepositoryWorkspace(
            "Pray-T",
            "BankTranferSys_Backend_Restful",
            "https://github.com/Pray-T/BankTranferSys_Backend_Restful.git",
            workspace.toString(),
            cloneHeadSha
        );

        RepositoryWorkspaceRepository repository = mock(RepositoryWorkspaceRepository.class);
        when(repository.findByRepoOwnerAndRepoName("Pray-T", "BankTranferSys_Backend_Restful"))
            .thenReturn(Optional.of(workspaceEntity));

        WorkspaceService workspaceService = new WorkspaceService(
            properties,
            new WorkspaceGuard(),
            new WorkspaceReconcileService(new WorkspaceGuard(), repository),
            new WorkspaceDiskCleanupService(properties),
            repository
        );
        DiffService service = new DiffService(properties, new WorkspaceGuard(), workspaceService, repository);

        DiffResponse response = service.diff("Pray-T", "BankTranferSys_Backend_Restful", true, 0);

        assertThat(response.totalChangedFiles()).isGreaterThanOrEqualTo(1);
        assertThat(response.changedFiles()).anyMatch(file -> "README.md".equals(file.path()));
    }

    static boolean bankTransferWorkspaceExists() {
        return Files.isDirectory(BANK_TRANSFER_WORKSPACE.resolve(".git"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void diffMarksMetadataOnlyWhenBlobIdsMatch() throws Exception {
        Path workspace = tempDir.resolve("metadata-workspace");
        Files.createDirectories(workspace);

        String cloneHeadSha;
        try (Git git = Git.init().setDirectory(workspace.toFile()).call()) {
            Path file = workspace.resolve("App.java");
            Files.writeString(file, "public class App {}\n");
            git.add().addFilepattern("App.java").call();
            RevCommit initialCommit = git.commit()
                .setMessage("Initial commit")
                .setAuthor("Test", "test@example.com")
                .call();
            cloneHeadSha = initialCommit.getId().name();

            Files.setPosixFilePermissions(file, java.util.Set.of(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
            ));
        }

        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setRoot(tempDir.toString());

        RepositoryWorkspace workspaceEntity = new RepositoryWorkspace(
            REPO_OWNER,
            REPO_NAME,
            "https://github.com/spring-projects/demo-repo",
            workspace.toString(),
            cloneHeadSha
        );

        RepositoryWorkspaceRepository repository = mock(RepositoryWorkspaceRepository.class);
        when(repository.findByRepoOwnerAndRepoName(REPO_OWNER, REPO_NAME)).thenReturn(Optional.of(workspaceEntity));

        WorkspaceService workspaceService = new WorkspaceService(
            properties,
            new WorkspaceGuard(),
            new WorkspaceReconcileService(new WorkspaceGuard(), repository),
            new WorkspaceDiskCleanupService(properties),
            repository
        );
        DiffService service = new DiffService(properties, new WorkspaceGuard(), workspaceService, repository);

        DiffResponse response = service.diff(REPO_OWNER, REPO_NAME, true, 0);

        assertThat(response.totalChangedFiles()).isEqualTo(1);
        assertThat(response.changedFiles()).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("App.java");
            assertThat(file.metadataOnly()).isTrue();
            assertThat(file.originalContent()).isEqualTo(file.newContent());
        });
    }
}
