package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.dto.ChangedFileResponse;
import com.demo.githubcopilotwithcursor.dto.DiffResponse;
import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DiffViewModelBuilder {

    private static final int MAX_LINES_PER_FILE = 5_000;

    private final DiffRowGenerator generator = DiffRowGenerator.create()
        .showInlineDiffs(false)
        .ignoreWhiteSpaces(false)
        .build();

    public DiffViewModel build(DiffResponse response) {
        List<FileDiffView> files = response.changedFiles().stream()
            .map(this::buildFile)
            .toList();
        return new DiffViewModel(
            response.repoOwner(),
            response.repoName(),
            response.headCommitSha(),
            response.comparedAt(),
            response.totalChangedFiles(),
            files
        );
    }

    private FileDiffView buildFile(ChangedFileResponse file) {
        boolean renderable = !file.binary()
            && !file.truncated()
            && !file.metadataOnly()
            && (file.originalContent() != null || file.newContent() != null);

        List<DiffRowView> rows = renderable
            ? computeRows(nullToEmpty(file.originalContent()), nullToEmpty(file.newContent()))
            : Collections.emptyList();

        boolean tooLarge = rows.size() > MAX_LINES_PER_FILE;
        if (tooLarge) {
            rows = rows.subList(0, MAX_LINES_PER_FILE);
        }

        return new FileDiffView(
            file.path(),
            file.oldPath(),
            file.changeType(),
            file.binary(),
            file.metadataOnly(),
            file.truncated() || tooLarge,
            file.originalSize(),
            file.newSize(),
            rows
        );
    }

    private List<DiffRowView> computeRows(String oldText, String newText) {
        List<String> oldLines = splitLines(oldText);
        List<String> newLines = splitLines(newText);
        List<DiffRow> rawRows = generator.generateDiffRows(oldLines, newLines);

        List<DiffRowView> result = new ArrayList<>(rawRows.size());
        int oldLine = 1;
        int newLine = 1;
        for (DiffRow row : rawRows) {
            String oldNum = "";
            String newNum = "";
            switch (row.getTag()) {
                case EQUAL -> {
                    oldNum = String.valueOf(oldLine++);
                    newNum = String.valueOf(newLine++);
                }
                case DELETE -> oldNum = String.valueOf(oldLine++);
                case INSERT -> newNum = String.valueOf(newLine++);
                case CHANGE -> {
                    oldNum = String.valueOf(oldLine++);
                    newNum = String.valueOf(newLine++);
                }
            }
            result.add(new DiffRowView(
                row.getTag().name(),
                oldNum,
                row.getOldLine(),
                newNum,
                row.getNewLine()
            ));
        }
        List<DiffRowView> changedRows = result.stream()
            .filter(row -> !"EQUAL".equals(row.tag()))
            .toList();
        return changedRows.isEmpty() ? result : changedRows;
    }

    private List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(text.split("\\R", -1));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record DiffViewModel(
        String repoOwner,
        String repoName,
        String headCommitSha,
        java.time.OffsetDateTime comparedAt,
        int totalChangedFiles,
        List<FileDiffView> files
    ) {
    }

    public record FileDiffView(
        String path,
        String oldPath,
        String changeType,
        boolean binary,
        boolean metadataOnly,
        boolean truncated,
        long originalSize,
        long newSize,
        List<DiffRowView> rows
    ) {
    }

    public record DiffRowView(
        String tag,
        String oldLineNumber,
        String oldText,
        String newLineNumber,
        String newText
    ) {
    }
}
