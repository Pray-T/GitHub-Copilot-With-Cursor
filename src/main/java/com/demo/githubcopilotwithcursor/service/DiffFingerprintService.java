package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.dto.ChangedFileResponse;
import com.demo.githubcopilotwithcursor.dto.DiffResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DiffFingerprintService {

    public String compute(DiffResponse diff) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            appendLine(digest, diff.headCommitSha());
            appendLine(digest, String.valueOf(diff.totalChangedFiles()));

            List<ChangedFileResponse> files = diff.changedFiles();
            if (files != null) {
                files.stream()
                    .sorted(Comparator.comparing(ChangedFileResponse::path, Comparator.nullsLast(String::compareTo)))
                    .forEach(file -> appendFile(digest, file));
            }

            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void appendFile(MessageDigest digest, ChangedFileResponse file) {
        appendToken(digest, file.path());
        appendToken(digest, file.changeType());
        appendToken(digest, String.valueOf(file.newSize()));
        appendToken(digest, file.binary() ? "binary" : "text");
        appendLine(digest, contentDigest(file));
    }

    private String contentDigest(ChangedFileResponse file) {
        if (file.binary() || file.newContent() == null) {
            return "";
        }
        try {
            MessageDigest contentHash = MessageDigest.getInstance("SHA-256");
            contentHash.update(file.newContent().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(contentHash.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void appendLine(MessageDigest digest, String value) {
        appendToken(digest, value);
        digest.update((byte) '\n');
    }

    private void appendToken(MessageDigest digest, String value) {
        if (value != null) {
            digest.update(value.getBytes(StandardCharsets.UTF_8));
        }
        digest.update((byte) '|');
    }
}
