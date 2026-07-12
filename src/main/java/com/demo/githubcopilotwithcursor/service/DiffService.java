package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.dto.ChangedFileResponse;
import com.demo.githubcopilotwithcursor.dto.DiffResponse;
import com.demo.githubcopilotwithcursor.repository.RepositoryWorkspaceRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiffService {

    private static final Logger log = LoggerFactory.getLogger(DiffService.class);

    private final WorkspaceProperties properties;
    private final WorkspaceGuard workspaceGuard;
    private final WorkspaceService workspaceService;
    private final RepositoryWorkspaceRepository repository;

    public DiffService(
        WorkspaceProperties properties,
        WorkspaceGuard workspaceGuard,
        WorkspaceService workspaceService,
        RepositoryWorkspaceRepository repository
    ) {
        this.properties = properties;
        this.workspaceGuard = workspaceGuard;
        this.workspaceService = workspaceService;
        this.repository = repository;
    }

    @Transactional
    public DiffResponse diff(String repoOwner, String repoName, boolean includeContent, int maxFileBytes) {
        return computeDiff(repoOwner, repoName, includeContent, maxFileBytes, true);
    }

    @Transactional(readOnly = true)
    public DiffResponse diffWithoutPersist(String repoOwner, String repoName, boolean includeContent, int maxFileBytes) {
        return computeDiff(repoOwner, repoName, includeContent, maxFileBytes, false);
    }

    private DiffResponse computeDiff(String repoOwner, String repoName, boolean includeContent, int maxFileBytes, boolean persistMetadata) {
        RepositoryWorkspace workspace = workspaceService.requirePresentWorkspace(repoOwner, repoName);

        int effectiveMaxFileBytes = resolveMaxFileBytes(maxFileBytes);
        Path workspacePath = workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath());
        OffsetDateTime comparedAt = OffsetDateTime.now();

        try (Repository gitRepository = new FileRepositoryBuilder()
            .setGitDir(workspacePath.resolve(".git").toFile())
            .setWorkTree(workspacePath.toFile())
            .build()) {
            List<ChangedFileResponse> changedFiles = scanChanges(
                gitRepository,
                workspace.getHeadCommitSha(),
                includeContent,
                effectiveMaxFileBytes
            );
            if (persistMetadata) {
                workspace.setLastDiffAt(comparedAt);
                repository.save(workspace);
            }
            log.info("Diff completed for {}/{} with {} changed files", repoOwner, repoName, changedFiles.size());
            return new DiffResponse(repoOwner, repoName, workspace.getHeadCommitSha(), comparedAt, changedFiles.size(), changedFiles);
        } catch (IOException exception) {
            throw workspaceService.buildGitAccessFailure(repoOwner, repoName, workspacePath, exception);
        }
    }

    private int resolveMaxFileBytes(int maxFileBytes) {
        int configuredMax = properties.getDiff().getMaxFileBytes();
        if (maxFileBytes <= 0) {
            return configuredMax;
        }
        return Math.min(maxFileBytes, configuredMax);
    }

    private List<ChangedFileResponse> scanChanges(
        Repository repository,
        String headCommitSha,
        boolean includeContent,
        int maxFileBytes
    ) throws IOException {
        try (ObjectReader reader = repository.newObjectReader();
             RevWalk revWalk = new RevWalk(repository);
             DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

            ObjectId baseId = repository.resolve(headCommitSha);
            if (baseId == null) {
                throw new IOException("Cannot resolve headCommitSha: " + headCommitSha);
            }
            RevCommit baseCommit = revWalk.parseCommit(baseId);
            CanonicalTreeParser baseTree = new CanonicalTreeParser();
            baseTree.reset(reader, baseCommit.getTree().getId());

            ObjectId currentHeadId = repository.resolve(Constants.HEAD);
            if (currentHeadId == null) {
                throw new IOException("Cannot resolve HEAD");
            }
            RevCommit currentHead = revWalk.parseCommit(currentHeadId);
            CanonicalTreeParser headTree = new CanonicalTreeParser();
            headTree.reset(reader, currentHead.getTree().getId());

            formatter.setRepository(repository);
            formatter.setDetectRenames(true);
            // Windows(core.autocrlf=true)에서 commit tree vs FileTreeIterator는 git CLI와 달리 전체 파일을 오검출한다.
            // 커밋분은 tree vs tree, 미커밋(IDE)분만 status 기준으로 base vs working tree를 합친다.
            List<DiffEntry> diffs = collectDiffEntries(repository, formatter, baseTree, headTree);
            diffs.sort(Comparator.comparing(this::diffPath));

            List<ChangedFileResponse> results = new ArrayList<>();
            long accumulatedContentBytes = 0;
            for (DiffEntry entry : diffs) {
                if (shouldSkipDiffEntry(repository, entry)) {
                    continue;
                }
                try {
                    ChangedFileDraft draft = buildChangedFile(repository, entry, includeContent, maxFileBytes);
                    if (draft.contentBytes() > 0 && accumulatedContentBytes + draft.contentBytes() > properties.getDiff().getMaxTotalBytes()) {
                        draft = draft.withoutContent(true);
                    } else {
                        accumulatedContentBytes += draft.contentBytes();
                    }
                    results.add(draft.toResponse());
                } catch (IOException exception) {
                    String path = entry.getNewPath().equals(DiffEntry.DEV_NULL) ? entry.getOldPath() : entry.getNewPath();
                    log.warn("Skipping diff entry for {}: {}", path, exception.getMessage());
                }
            }
            return results;
        }
    }

    private List<DiffEntry> collectDiffEntries(
        Repository repository,
        DiffFormatter formatter,
        CanonicalTreeParser baseTree,
        CanonicalTreeParser headTree
    ) throws IOException {
        List<DiffEntry> diffs = new ArrayList<>(formatter.scan(baseTree, headTree));

        Status status;
        try {
            status = Git.wrap(repository).status().call();
        } catch (GitAPIException exception) {
            throw new IOException("Failed to inspect working tree status", exception);
        }
        if (status.isClean()) {
            return diffs;
        }

        Set<String> uncommittedPaths = new LinkedHashSet<>(status.getUncommittedChanges());
        if (uncommittedPaths.isEmpty()) {
            return diffs;
        }

        FileTreeIterator workingTree = new FileTreeIterator(repository);
        Map<String, DiffEntry> byPath = new LinkedHashMap<>();
        for (DiffEntry entry : diffs) {
            byPath.put(diffPath(entry), entry);
        }
        for (DiffEntry entry : formatter.scan(baseTree, workingTree)) {
            if (uncommittedPaths.contains(diffPath(entry))) {
                byPath.put(diffPath(entry), entry);
            }
        }
        return new ArrayList<>(byPath.values());
    }

    private String diffPath(DiffEntry entry) {
        return entry.getNewPath().equals(DiffEntry.DEV_NULL) ? entry.getOldPath() : entry.getNewPath();
    }

    private ChangedFileDraft buildChangedFile(
        Repository repository,
        DiffEntry entry,
        boolean includeContent,
        int maxFileBytes
    ) throws IOException {
        String oldPath = entry.getOldPath().equals(DiffEntry.DEV_NULL) ? null : entry.getOldPath();
        String newPath = entry.getNewPath().equals(DiffEntry.DEV_NULL) ? null : entry.getNewPath();
        String responsePath = newPath != null ? newPath : oldPath;
        String changeType = mapChangeType(entry.getChangeType());

        ContentSnapshot original = switch (entry.getChangeType()) {
            case ADD, COPY -> ContentSnapshot.empty(includeContent);
            default -> readBlobContent(repository, entry.getOldId().toObjectId(), includeContent, maxFileBytes);
        };
        ContentSnapshot current = switch (entry.getChangeType()) {
            case DELETE -> ContentSnapshot.empty(includeContent);
            default -> readWorkingTreeContent(repository, newPath, includeContent, maxFileBytes);
        };

        boolean metadataOnly = entry.getChangeType() == DiffEntry.ChangeType.MODIFY
            && entry.getOldId().equals(entry.getNewId());
        boolean binary = original.binary() || current.binary();
        boolean truncated = original.truncated() || current.truncated();
        String originalContent = binary ? null : original.content();
        String newContent = binary ? null : current.content();
        long contentBytes = binary ? 0 : original.contentBytes() + current.contentBytes();

        return new ChangedFileDraft(
            responsePath,
            entry.getChangeType() == DiffEntry.ChangeType.RENAME ? oldPath : null,
            changeType,
            binary,
            metadataOnly,
            original.size(),
            current.size(),
            originalContent,
            newContent,
            truncated,
            contentBytes
        );
    }

    private ContentSnapshot readBlobContent(
        Repository repository,
        ObjectId objectId,
        boolean includeContent,
        int maxFileBytes
    ) throws IOException {
        if (objectId == null || ObjectId.zeroId().equals(objectId)) {
            return ContentSnapshot.empty(includeContent);
        }
        ObjectLoader loader = repository.open(objectId);
        if (loader.getType() != Constants.OBJ_BLOB) {
            return ContentSnapshot.empty(includeContent);
        }
        try (InputStream inputStream = loader.openStream()) {
            return readContent(inputStream, loader.getSize(), includeContent, maxFileBytes);
        }
    }

    private ContentSnapshot readWorkingTreeContent(
        Repository repository,
        String relativePath,
        boolean includeContent,
        int maxFileBytes
    ) throws IOException {
        if (relativePath == null || !isRegularFileInWorkTree(repository, relativePath)) {
            return ContentSnapshot.empty(includeContent);
        }
        Path filePath = repository.getWorkTree().toPath().resolve(relativePath);
        long size = Files.size(filePath);
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return readContent(inputStream, size, includeContent, maxFileBytes);
        }
    }

    private ContentSnapshot readContent(InputStream inputStream, long size, boolean includeContent, int maxFileBytes) throws IOException {
        byte[] bytes = readLimited(inputStream, maxFileBytes + 1L);
        boolean truncated = bytes.length > maxFileBytes || size > maxFileBytes;
        byte[] visibleBytes = truncated ? java.util.Arrays.copyOf(bytes, maxFileBytes) : bytes;
        boolean binary = isBinary(visibleBytes);
        if (binary) {
            return new ContentSnapshot(size, null, true, false, 0);
        }
        if (!includeContent) {
            return new ContentSnapshot(size, null, false, false, 0);
        }
        String content = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(visibleBytes)).toString();
        return new ContentSnapshot(size, content, false, truncated, visibleBytes.length);
    }

    private byte[] readLimited(InputStream inputStream, long limit) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long remaining = limit;
        while (remaining > 0) {
            int read = inputStream.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read == -1) {
                break;
            }
            outputStream.write(buffer, 0, read);
            remaining -= read;
        }
        return outputStream.toByteArray();
    }

    private boolean isBinary(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes));
            return false;
        } catch (CharacterCodingException exception) {
            return true;
        }
    }

    private boolean shouldSkipDiffEntry(Repository repository, DiffEntry entry) {
        if (entry.getChangeType() == DiffEntry.ChangeType.DELETE) {
            return false;
        }
        String newPath = entry.getNewPath().equals(DiffEntry.DEV_NULL) ? null : entry.getNewPath();
        return newPath == null || !isRegularFileInWorkTree(repository, newPath);
    }

    private boolean isRegularFileInWorkTree(Repository repository, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        Path path = repository.getWorkTree().toPath().resolve(relativePath);
        return Files.isRegularFile(path);
    }

    private String mapChangeType(DiffEntry.ChangeType changeType) {
        return switch (changeType) {
            case ADD, COPY -> "ADDED";
            case MODIFY -> "MODIFIED";
            case DELETE -> "DELETED";
            case RENAME -> "RENAMED";
        };
    }

    private record ContentSnapshot(long size, String content, boolean binary, boolean truncated, long contentBytes) {

        static ContentSnapshot empty(boolean includeContent) {
            return new ContentSnapshot(0, includeContent ? "" : null, false, false, 0);
        }
    }

    private record ChangedFileDraft(
        String path,
        String oldPath,
        String changeType,
        boolean binary,
        boolean metadataOnly,
        long originalSize,
        long newSize,
        String originalContent,
        String newContent,
        boolean truncated,
        long contentBytes
    ) {

        ChangedFileDraft withoutContent(boolean truncated) {
            return new ChangedFileDraft(
                path,
                oldPath,
                changeType,
                binary,
                metadataOnly,
                originalSize,
                newSize,
                null,
                null,
                truncated,
                0
            );
        }

        ChangedFileResponse toResponse() {
            return new ChangedFileResponse(
                path,
                oldPath,
                changeType,
                binary,
                metadataOnly,
                originalSize,
                newSize,
                originalContent,
                newContent,
                truncated
            );
        }
    }
}
