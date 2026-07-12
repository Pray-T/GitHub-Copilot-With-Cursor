package com.demo.githubcopilotwithcursor.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.contribute")
public class ContributeProperties {

    @NotBlank
    private String branchPrefix = "refactor";

    @NotBlank
    private String branchTimestampPattern = "yyyyMMddHHmm";

    @Min(1)
    private int forkReadyMaxAttempts = 15;

    @Min(0)
    private long forkReadyDelayMs = 2_000L;

    public String getBranchPrefix() {
        return branchPrefix;
    }

    public void setBranchPrefix(String branchPrefix) {
        this.branchPrefix = branchPrefix;
    }

    public String getBranchTimestampPattern() {
        return branchTimestampPattern;
    }

    public void setBranchTimestampPattern(String branchTimestampPattern) {
        this.branchTimestampPattern = branchTimestampPattern;
    }

    public int getForkReadyMaxAttempts() {
        return forkReadyMaxAttempts;
    }

    public void setForkReadyMaxAttempts(int forkReadyMaxAttempts) {
        this.forkReadyMaxAttempts = forkReadyMaxAttempts;
    }

    public long getForkReadyDelayMs() {
        return forkReadyDelayMs;
    }

    public void setForkReadyDelayMs(long forkReadyDelayMs) {
        this.forkReadyDelayMs = forkReadyDelayMs;
    }

    public DateTimeFormatter branchTimestampFormatter() {
        return DateTimeFormatter.ofPattern(branchTimestampPattern);
    }
}
