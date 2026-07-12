package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IdeLauncher {

    private static final Logger log = LoggerFactory.getLogger(IdeLauncher.class);
    private static final long LAUNCH_VERIFY_TIMEOUT_SECONDS = 5L;

    private final WorkspaceProperties properties;
    private final WorkspaceGuard workspaceGuard;
    private final ExecutorService executor;

    public IdeLauncher(WorkspaceProperties properties, WorkspaceGuard workspaceGuard) {
        this.properties = properties;
        this.workspaceGuard = workspaceGuard;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ide-launcher");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void launchAsync(Path workspacePath, Consumer<Boolean> onComplete) {
        executor.submit(() -> {
            boolean launched = launch(workspacePath);
            onComplete.accept(launched);
        });
    }

    public boolean launch(Path workspacePath) {
        String absolutePath = workspaceGuard.normalizePath(workspacePath).toString();
        String ideCommand = properties.getIdeCommand();
        try {
            Process process;
            if (isWindows() && "cursor".equalsIgnoreCase(ideCommand)) {
                process = Runtime.getRuntime().exec(new String[] {"cmd", "/c", "cursor", absolutePath});
            } else {
                process = new ProcessBuilder(ideCommand, absolutePath).start();
            }
            boolean launched = verifyLaunch(process);
            if (launched) {
                log.info("Cursor IDE launch requested for {} with pid {}", absolutePath, process.pid());
            } else {
                log.warn("Cursor IDE launch failed for {}", absolutePath);
            }
            return launched;
        } catch (IOException exception) {
            log.warn("Cursor IDE launch failed for {}", absolutePath, exception);
            return false;
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private boolean verifyLaunch(Process process) {
        try {
            boolean finished = process.waitFor(LAUNCH_VERIFY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                return true;
            }
            return process.exitValue() == 0;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
