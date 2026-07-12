package com.demo.githubcopilotwithcursor.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceGuardTest {

    private final WorkspaceGuard workspaceGuard = new WorkspaceGuard();

    @TempDir
    Path tempDir;

    @Test
    void normalizePathReturnsNullForNullInput() {
        assertThat(workspaceGuard.normalizePath(null)).isNull();
    }

    @Test
    void normalizeStoredPathReturnsNullForBlankInput() {
        assertThat(workspaceGuard.normalizeStoredPath(null)).isNull();
        assertThat(workspaceGuard.normalizeStoredPath("  ")).isNull();
    }

    @Test
    void isUnderRootAcceptsPathInsideRoot() {
        Path root = tempDir.resolve("workspaces");
        Path inside = root.resolve("demo-repo");

        assertThat(workspaceGuard.isUnderRoot(root, inside)).isTrue();
        assertThat(workspaceGuard.isUnderRoot(root, root)).isTrue();
    }

    @Test
    void isUnderRootRejectsPathOutsideRoot() {
        Path root = tempDir.resolve("workspaces");
        Path outside = tempDir.resolve("legacy-root").resolve("demo-repo");

        assertThat(workspaceGuard.isUnderRoot(root, outside)).isFalse();
    }

    @Test
    void normalizeStoredPathMatchesNormalizePathOfStringPath() {
        Path expected = workspaceGuard.normalizePath(tempDir.resolve("demo-repo"));

        assertThat(workspaceGuard.normalizeStoredPath(tempDir.resolve("demo-repo").toString()))
            .isEqualTo(expected);
    }
}
