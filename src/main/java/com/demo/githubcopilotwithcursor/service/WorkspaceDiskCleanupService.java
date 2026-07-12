package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.time.OffsetDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceDiskCleanupService {

    static final String QUARANTINE_DIR = ".deleting";

    private static final Logger log = LoggerFactory.getLogger(WorkspaceDiskCleanupService.class);
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    private final WorkspaceProperties properties;
    private final ScheduledExecutorService backgroundExecutor;

    public WorkspaceDiskCleanupService(WorkspaceProperties properties) {
        this.properties = properties;
        this.backgroundExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "workspace-disk-cleanup");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void deleteWorkspaceDirectoryAsync(Path root, String repoName) {
        if (root == null) {
            return;
        }
        backgroundExecutor.execute(() -> deleteWorkspaceDirectory(root, repoName));
    }

    public void deleteWorkspaceDirectory(Path root, String repoName) {
        if (root == null || !Files.exists(root)) {
            return;
        }

        IOException syncFailure = deleteDirectoryWithRetry(root, syncRetryPolicy());
        if (syncFailure == null) {
            log.info("Workspace directory deleted: {}", root);
            return;
        }

        Path quarantine = quarantinePath(repoName);
        Path target = root;
        try {
            Files.createDirectories(quarantine.getParent());
            if (Files.exists(root)) {
                Files.move(root, quarantine);
                target = quarantine;
                log.warn(
                    "Workspace {} could not be deleted immediately. Moved to quarantine path {}",
                    repoName,
                    quarantine
                );
            }
        } catch (IOException moveException) {
            log.warn(
                "Workspace {} could not be moved to quarantine. Will retry in place and in background: {}",
                repoName,
                moveException.getMessage()
            );
        }

        IOException quarantineFailure = deleteDirectoryWithRetry(target, syncRetryPolicy());
        if (quarantineFailure == null) {
            log.info("Workspace directory deleted after quarantine move: {}", target);
            return;
        }

        log.warn(
            "Workspace {} still has locked files after synchronous cleanup: {}",
            repoName,
            quarantineFailure.getMessage()
        );
        scheduleBackgroundDelete(target, repoName);
    }

    public int sweepQuarantineDirectory() {
        Path quarantineRoot = properties.rootPath().resolve(QUARANTINE_DIR);
        if (!Files.isDirectory(quarantineRoot)) {
            return 0;
        }

        int removed = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(quarantineRoot)) {
            for (Path entry : entries) {
                if (!Files.exists(entry)) {
                    continue;
                }
                IOException failure = deleteDirectoryWithRetry(entry, syncRetryPolicy());
                if (failure == null) {
                    removed++;
                    log.info("Quarantine sweep removed {}", entry);
                    continue;
                }
                scheduleBackgroundDelete(entry, entry.getFileName().toString());
            }
        } catch (IOException exception) {
            log.warn("Quarantine sweep failed for {}: {}", quarantineRoot, exception.getMessage());
        }
        return removed;
    }

    private void scheduleBackgroundDelete(Path root, String label) {
        WorkspaceProperties.Delete deleteProperties = properties.getDelete();
        AtomicInteger attempt = new AtomicInteger(1);
        backgroundExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                if (!Files.exists(root)) {
                    log.info("Background cleanup completed for {} (path already absent)", label);
                    return;
                }

                int currentAttempt = attempt.getAndIncrement();
                IOException failure = deleteDirectoryWithRetry(root, backgroundRetryPolicy());
                if (failure == null) {
                    log.info("Background cleanup succeeded for {} on attempt {}", label, currentAttempt);
                    return;
                }

                if (currentAttempt >= deleteProperties.getBackgroundRetryCount()) {
                    log.error(
                        "Background cleanup exhausted for {} after {} attempts: {}",
                        label,
                        currentAttempt,
                        failure.getMessage()
                    );
                    return;
                }

                long delayMs = retryDelayMs(currentAttempt, backgroundRetryPolicy());
                log.warn(
                    "Background cleanup retry scheduled for {} in {} ms (attempt {}/{}): {}",
                    label,
                    delayMs,
                    currentAttempt,
                    deleteProperties.getBackgroundRetryCount(),
                    failure.getMessage()
                );
                backgroundExecutor.schedule(this, delayMs, TimeUnit.MILLISECONDS);
            }
        }, deleteProperties.getBackgroundInitialDelayMs(), TimeUnit.MILLISECONDS);
    }

    private IOException deleteDirectoryWithRetry(Path root, RetryPolicy retryPolicy) {
        if (!Files.exists(root)) {
            return null;
        }

        IOException lastException = null;
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            try {
                clearReadOnlyAttributes(root);
                deleteDirectoryOnce(root);
                return null;
            } catch (IOException exception) {
                lastException = exception;
                if (!isRetryableDeleteFailure(exception) || attempt == retryPolicy.maxAttempts()) {
                    break;
                }
                long delayMs = retryDelayMs(attempt, retryPolicy);
                log.warn(
                    "Workspace delete retry {}/{} for {} failed: {}",
                    attempt,
                    retryPolicy.maxAttempts(),
                    root,
                    exception.getMessage()
                );
                sleep(delayMs);
            }
        }
        return lastException;
    }

    private void deleteDirectoryOnce(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(dir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private void clearReadOnlyAttributes(Path root) throws IOException {
        if (!WINDOWS || !Files.exists(root)) {
            return;
        }

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                clearReadOnly(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                clearReadOnly(dir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private void clearReadOnly(Path path) throws IOException {
        DosFileAttributeView attributes = Files.getFileAttributeView(path, DosFileAttributeView.class);
        if (attributes == null) {
            return;
        }
        DosFileAttributes dosAttributes = attributes.readAttributes();
        if (dosAttributes.isReadOnly()) {
            attributes.setReadOnly(false);
        }
    }

    private boolean isRetryableDeleteFailure(IOException exception) {
        return exception instanceof AccessDeniedException
            || exception instanceof DirectoryNotEmptyException
            || exception instanceof FileSystemException;
    }

    private RetryPolicy syncRetryPolicy() {
        WorkspaceProperties.Delete delete = properties.getDelete();
        return new RetryPolicy(
            delete.getSyncRetryCount(),
            delete.getSyncInitialDelayMs(),
            delete.getSyncMaxDelayMs()
        );
    }

    private RetryPolicy backgroundRetryPolicy() {
        WorkspaceProperties.Delete delete = properties.getDelete();
        return new RetryPolicy(
            delete.getBackgroundRetryCount(),
            delete.getBackgroundInitialDelayMs(),
            delete.getBackgroundMaxDelayMs()
        );
    }

    private long retryDelayMs(int attempt, RetryPolicy retryPolicy) {
        int exponent = Math.max(0, attempt - 1);
        long delay = retryPolicy.initialDelayMs() * (1L << Math.min(exponent, 6));
        return Math.min(delay, retryPolicy.maxDelayMs());
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private Path quarantinePath(String repoName) {
        String suffix = OffsetDateTime.now().toString()
            .replace(":", "")
            .replace("+", "_");
        return properties.rootPath()
            .resolve(QUARANTINE_DIR)
            .resolve(repoName + "-" + suffix);
    }

    @PreDestroy
    void shutdownBackgroundExecutor() {
        backgroundExecutor.shutdownNow();
    }

    private record RetryPolicy(int maxAttempts, long initialDelayMs, long maxDelayMs) {
    }
}
