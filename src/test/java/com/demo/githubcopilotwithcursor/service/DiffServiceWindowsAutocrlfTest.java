package com.demo.githubcopilotwithcursor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.dto.DiffResponse;
import com.demo.githubcopilotwithcursor.repository.RepositoryWorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class DiffServiceWindowsAutocrlfTest {

    private static final Path BANK_TRANSFER_WORKSPACE = Path.of(
        System.getenv().getOrDefault("LOCALAPPDATA", ""),
        "Temp",
        "refactor-workspace",
        "Pray-T",
        "BankTranferSys_Backend_Restful"
    );

    private static final String HEAD_COMMIT_SHA = "55b40cc7740ebfd473299eac6baaca69ec845f67";

    @Test
    @EnabledIf("bankTransferWorkspaceExists")
    void jgitTreeVsWorkingTreeOverReportsWhenAutocrlfEnabled() throws Exception {
        Path workspace = BANK_TRANSFER_WORKSPACE.toAbsolutePath().normalize();

        try (Repository repository = openRepository(workspace);
             RevWalk revWalk = new RevWalk(repository);
             ObjectReader reader = repository.newObjectReader();
             DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

            RevCommit baseCommit = revWalk.parseCommit(repository.resolve(HEAD_COMMIT_SHA));
            RevCommit headCommit = revWalk.parseCommit(repository.resolve("HEAD"));

            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            oldTree.reset(reader, baseCommit.getTree());
            CanonicalTreeParser headTree = new CanonicalTreeParser();
            headTree.reset(reader, headCommit.getTree());
            FileTreeIterator workingTree = new FileTreeIterator(repository);

            formatter.setRepository(repository);
            int treeVsHead = formatter.scan(oldTree, headTree).size();
            int treeVsWorkingTree = formatter.scan(oldTree, workingTree).size();

            assertThat(Git.wrap(repository).status().call().isClean()).isTrue();
            assertThat(treeVsHead).isEqualTo(1);
            assertThat(treeVsWorkingTree).isGreaterThan(1);
        }
    }

    @Test
    @EnabledIf("bankTransferWorkspaceExists")
    void diffServiceShouldMatchGitForCleanWorkingTree() throws Exception {
        Path workspace = BANK_TRANSFER_WORKSPACE.toAbsolutePath().normalize();
        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setRoot(workspace.getParent().getParent().toString());

        RepositoryWorkspace workspaceEntity = new RepositoryWorkspace(
            "Pray-T",
            "BankTranferSys_Backend_Restful",
            "https://github.com/Pray-T/BankTranferSys_Backend_Restful.git",
            workspace.toString(),
            HEAD_COMMIT_SHA
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

        DiffResponse response = service.diff("Pray-T", "BankTranferSys_Backend_Restful", false, 0);

        assertThat(response.totalChangedFiles()).isEqualTo(1);
        assertThat(response.changedFiles()).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("README.md");
            assertThat(file.metadataOnly()).isFalse();
        });
    }

    static boolean bankTransferWorkspaceExists() {
        return Files.isDirectory(BANK_TRANSFER_WORKSPACE.resolve(".git"));
    }

    private static Repository openRepository(Path workspace) throws Exception {
        return new FileRepositoryBuilder()
            .setGitDir(workspace.resolve(".git").toFile())
            .setWorkTree(workspace.toFile())
            .build();
    }
}
