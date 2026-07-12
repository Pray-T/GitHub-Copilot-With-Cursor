package com.demo.githubcopilotwithcursor.config;

import com.demo.githubcopilotwithcursor.service.WorkspaceDiskCleanupService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceDeleteCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceDeleteCleanupScheduler.class);

    private final WorkspaceDiskCleanupService workspaceDiskCleanupService;

    public WorkspaceDeleteCleanupScheduler(WorkspaceDiskCleanupService workspaceDiskCleanupService) {
        this.workspaceDiskCleanupService = workspaceDiskCleanupService;
    }

    @PostConstruct
    public void cleanupOnStartup() {
        int removed = workspaceDiskCleanupService.sweepQuarantineDirectory();
        if (removed > 0) {
            log.info("Startup quarantine sweep removed {} leftover workspace director(ies)", removed);
        }
    }

    @Scheduled(fixedDelayString = "${app.workspace.delete.cleanup-interval-ms:300000}")
    public void cleanupQuarantineDirectory() {
        int removed = workspaceDiskCleanupService.sweepQuarantineDirectory();
        if (removed > 0) {
            log.info("Scheduled quarantine sweep removed {} leftover workspace director(ies)", removed);
        }
    }
}
