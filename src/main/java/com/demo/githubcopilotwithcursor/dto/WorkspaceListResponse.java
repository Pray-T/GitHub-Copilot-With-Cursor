package com.demo.githubcopilotwithcursor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "활성 워크스페이스 목록")
public record WorkspaceListResponse(
    @Schema(description = "워크스페이스 항목")
    List<WorkspaceListItem> items
) {
}
