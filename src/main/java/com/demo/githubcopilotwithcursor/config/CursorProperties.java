package com.demo.githubcopilotwithcursor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cursor")
public class CursorProperties {

    private String apiBaseUrl = "https://api.cursor.com";

    private String apiVersion = "v1";

    private String apiKey = "";

    private String clientName = "github-copilot-with-cursor/v3.0.2";

    private final Agent agent = new Agent();

    private final Composer composer = new Composer();

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public Agent getAgent() {
        return agent;
    }

    public Composer getComposer() {
        return composer;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public static class Agent {

        private long pollIntervalMs = 5000;

        private long pollBackoffOnBusyMs = 10000;

        private long pollBackoffMaxMs = 30000;

        private long timeoutMs = 600000;

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public long getPollBackoffOnBusyMs() {
            return pollBackoffOnBusyMs;
        }

        public void setPollBackoffOnBusyMs(long pollBackoffOnBusyMs) {
            this.pollBackoffOnBusyMs = pollBackoffOnBusyMs;
        }

        public long getPollBackoffMaxMs() {
            return pollBackoffMaxMs;
        }

        public void setPollBackoffMaxMs(long pollBackoffMaxMs) {
            this.pollBackoffMaxMs = pollBackoffMaxMs;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class Composer {

        private boolean enabled = true;

        private int maxFiles = 50;

        private int maxPatchBytes = 131072;

        private long timeoutMs = 120000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxFiles() {
            return maxFiles;
        }

        public void setMaxFiles(int maxFiles) {
            this.maxFiles = maxFiles;
        }

        public int getMaxPatchBytes() {
            return maxPatchBytes;
        }

        public void setMaxPatchBytes(int maxPatchBytes) {
            this.maxPatchBytes = maxPatchBytes;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }
}
