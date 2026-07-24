package com.demo.githubcopilotwithcursor.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.githubcopilotwithcursor.dto.ChangedFileResponse;
import com.demo.githubcopilotwithcursor.dto.DiffResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiffFingerprintServiceTest {

    private DiffFingerprintService service;

    @BeforeEach
    void setUp() {
        service = new DiffFingerprintService();
    }

    @Test
    void producesSameHashForIdenticalDiff() {
        DiffResponse diff = sampleDiff("README.md", "MODIFIED", "hello");

        String first = service.compute(diff);
        String second = service.compute(diff);

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
    }

    @Test
    void changesHashWhenFilePathOrChangeTypeChanges() {
        DiffResponse base = sampleDiff("README.md", "MODIFIED", "hello");
        DiffResponse renamed = sampleDiff("docs/README.md", "MODIFIED", "hello");
        DiffResponse added = sampleDiff("README.md", "ADDED", "hello");

        assertThat(service.compute(base)).isNotEqualTo(service.compute(renamed));
        assertThat(service.compute(base)).isNotEqualTo(service.compute(added));
    }

    @Test
    void changesHashWhenFileContentChanges() {
        DiffResponse before = sampleDiff("README.md", "MODIFIED", "hello");
        DiffResponse after = sampleDiff("README.md", "MODIFIED", "hello world");

        assertThat(service.compute(before)).isNotEqualTo(service.compute(after));
    }

    private DiffResponse sampleDiff(String path, String changeType, String content) {
        ChangedFileResponse file = new ChangedFileResponse(
            path,
            null,
            changeType,
            false,
            false,
            content.length(),
            content.length(),
            null,
            content,
            false
        );
        return new DiffResponse(
            "octocat",
            "demo",
            "0123456789012345678901234567890123456789",
            OffsetDateTime.now(),
            1,
            List.of(file)
        );
    }
}
