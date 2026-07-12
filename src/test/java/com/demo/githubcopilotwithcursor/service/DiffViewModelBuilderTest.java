package com.demo.githubcopilotwithcursor.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.githubcopilotwithcursor.dto.ChangedFileResponse;
import com.demo.githubcopilotwithcursor.dto.DiffResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiffViewModelBuilderTest {

  private final DiffViewModelBuilder builder = new DiffViewModelBuilder();

  @Test
  void buildShowsOnlyChangedRows() {
    DiffResponse response = new DiffResponse(
        "owner",
        "repo",
        "abc123",
        OffsetDateTime.now(),
        1,
        List.of(new ChangedFileResponse(
            "README.md",
            null,
            "MODIFIED",
            false,
            false,
            10,
            12,
            "line1\nold line\nline3\n",
            "line1\nnew line\nline3\n",
            false
        ))
    );

    DiffViewModelBuilder.DiffViewModel viewModel = builder.build(response);

    assertThat(viewModel.files()).singleElement().satisfies(file -> {
      assertThat(file.rows()).hasSize(1);
      assertThat(file.rows().get(0).tag()).isEqualTo("CHANGE");
      assertThat(file.rows().get(0).oldText()).contains("old line");
      assertThat(file.rows().get(0).newText()).contains("new line");
    });
  }

  @Test
  void buildSkipsRowsForMetadataOnlyFiles() {
    DiffResponse response = new DiffResponse(
        "owner",
        "repo",
        "abc123",
        OffsetDateTime.now(),
        1,
        List.of(new ChangedFileResponse(
            "App.java",
            null,
            "MODIFIED",
            false,
            true,
            10,
            10,
            "same\n",
            "same\n",
            false
        ))
    );

    DiffViewModelBuilder.DiffViewModel viewModel = builder.build(response);

    assertThat(viewModel.files()).singleElement().satisfies(file -> {
      assertThat(file.metadataOnly()).isTrue();
      assertThat(file.rows()).isEmpty();
    });
  }
}
