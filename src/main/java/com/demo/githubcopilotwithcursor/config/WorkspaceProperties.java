package com.demo.githubcopilotwithcursor.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.workspace")
public class WorkspaceProperties {

    @NotBlank
    private String root;

    @NotEmpty
    private List<String> allowedHosts = new ArrayList<>(List.of("github.com"));

    @NotBlank
    private String ideCommand = "cursor";

    @Valid
    private Diff diff = new Diff();

    @Valid
    private Delete delete = new Delete();

    public Path rootPath() {
        return Path.of(root).toAbsolutePath().normalize();
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public List<String> getAllowedHosts() {
        return allowedHosts;
    }

    public void setAllowedHosts(List<String> allowedHosts) {
        this.allowedHosts = allowedHosts;
    }

    public String getIdeCommand() {
        return ideCommand;
    }

    public void setIdeCommand(String ideCommand) {
        this.ideCommand = ideCommand;
    }

    public Diff getDiff() {
        return diff;
    }

    public void setDiff(Diff diff) {
        this.diff = diff;
    }

    public Delete getDelete() {
        return delete;
    }

    public void setDelete(Delete delete) {
        this.delete = delete;
    }

    public static class Delete {

        @Min(1)
        private int syncRetryCount = 12;

        @Min(0)
        private long syncInitialDelayMs = 500L;

        @Min(0)
        private long syncMaxDelayMs = 5_000L;

        @Min(1)
        private int backgroundRetryCount = 30;

        @Min(0)
        private long backgroundInitialDelayMs = 3_000L;

        @Min(0)
        private long backgroundMaxDelayMs = 30_000L;

        @Min(60_000)
        private long cleanupIntervalMs = 300_000L;

        public int getSyncRetryCount() {
            return syncRetryCount;
        }

        public void setSyncRetryCount(int syncRetryCount) {
            this.syncRetryCount = syncRetryCount;
        }

        public long getSyncInitialDelayMs() {
            return syncInitialDelayMs;
        }

        public void setSyncInitialDelayMs(long syncInitialDelayMs) {
            this.syncInitialDelayMs = syncInitialDelayMs;
        }

        public long getSyncMaxDelayMs() {
            return syncMaxDelayMs;
        }

        public void setSyncMaxDelayMs(long syncMaxDelayMs) {
            this.syncMaxDelayMs = syncMaxDelayMs;
        }

        public int getBackgroundRetryCount() {
            return backgroundRetryCount;
        }

        public void setBackgroundRetryCount(int backgroundRetryCount) {
            this.backgroundRetryCount = backgroundRetryCount;
        }

        public long getBackgroundInitialDelayMs() {
            return backgroundInitialDelayMs;
        }

        public void setBackgroundInitialDelayMs(long backgroundInitialDelayMs) {
            this.backgroundInitialDelayMs = backgroundInitialDelayMs;
        }

        public long getBackgroundMaxDelayMs() {
            return backgroundMaxDelayMs;
        }

        public void setBackgroundMaxDelayMs(long backgroundMaxDelayMs) {
            this.backgroundMaxDelayMs = backgroundMaxDelayMs;
        }

        public long getCleanupIntervalMs() {
            return cleanupIntervalMs;
        }

        public void setCleanupIntervalMs(long cleanupIntervalMs) {
            this.cleanupIntervalMs = cleanupIntervalMs;
        }
    }

    public static class Diff {

        @Min(1)
        private int maxFileBytes = 1_048_576;

        @Min(1)
        private long maxTotalBytes = 52_428_800L;

        public int getMaxFileBytes() {
            return maxFileBytes;
        }

        public void setMaxFileBytes(int maxFileBytes) {
            this.maxFileBytes = maxFileBytes;
        }

        public long getMaxTotalBytes() {
            return maxTotalBytes;
        }

        public void setMaxTotalBytes(long maxTotalBytes) {
            this.maxTotalBytes = maxTotalBytes;
        }
    }
}
