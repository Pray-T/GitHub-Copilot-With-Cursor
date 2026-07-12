package com.demo.githubcopilotwithcursor.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceDiskCleanupServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void deleteWorkspaceDirectoryRemovesExistingTree() throws Exception {
        Path workspaceRoot = tempDir.resolve("demo-repo");
        Path nested = workspaceRoot.resolve("src").resolve("Main.java");
        Files.createDirectories(nested.getParent());
        Files.writeString(nested, "class Main {}");

        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setRoot(tempDir.toString());
        WorkspaceDiskCleanupService service = new WorkspaceDiskCleanupService(properties);

        service.deleteWorkspaceDirectory(workspaceRoot, "demo-repo");

        assertThat(workspaceRoot).doesNotExist();
        assertThat(tempDir.resolve(WorkspaceDiskCleanupService.QUARANTINE_DIR)).doesNotExist();
    }

    @Test
    void sweepQuarantineDirectoryRemovesLeftoverEntries() throws Exception {
        Path quarantineRoot = tempDir.resolve(WorkspaceDiskCleanupService.QUARANTINE_DIR);
        Path leftover = quarantineRoot.resolve("demo-repo-20260522");
        Files.createDirectories(leftover.resolve("nested"));
        Files.writeString(leftover.resolve("nested").resolve("file.txt"), "leftover");

        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setRoot(tempDir.toString());
        WorkspaceDiskCleanupService service = new WorkspaceDiskCleanupService(properties);

        int removed = service.sweepQuarantineDirectory();

        assertThat(removed).isEqualTo(1);
        assertThat(leftover).doesNotExist();
    }
}
