package com.demo.githubcopilotwithcursor.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IdeLauncherTest {

    @TempDir
    Path tempDir;

    @Test
    void launchReturnsFalseWhenIdeCommandDoesNotExist() throws Exception {
        Path workspacePath = tempDir.resolve("demo-repo");
        Files.createDirectories(workspacePath);

        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setIdeCommand("definitely-not-a-real-ide-command-12345");

        IdeLauncher launcher = new IdeLauncher(properties, new WorkspaceGuard());

        assertThat(launcher.launch(workspacePath)).isFalse();
    }
}
