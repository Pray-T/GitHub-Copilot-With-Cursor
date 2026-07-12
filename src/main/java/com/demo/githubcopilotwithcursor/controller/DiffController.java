package com.demo.githubcopilotwithcursor.controller;

import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import com.demo.githubcopilotwithcursor.dto.DiffResponse;
import com.demo.githubcopilotwithcursor.service.DiffService;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
public class DiffController {

    private final DiffService diffService;
    private final WorkspaceProperties workspaceProperties;

    public DiffController(DiffService diffService, WorkspaceProperties workspaceProperties) {
        this.diffService = diffService;
        this.workspaceProperties = workspaceProperties;
    }

    @GetMapping("/diff/{repoOwner}/{repoName}")
    public ResponseEntity<DiffResponse> diff(
        @PathVariable("repoOwner") @Pattern(regexp = "^[A-Za-z0-9._-]+$") String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = "^[A-Za-z0-9._-]+$") String repoName,
        @RequestParam(name = "includeContent", defaultValue = "true") boolean includeContent,
        @RequestParam(name = "maxFileBytes", defaultValue = "1048576") int maxFileBytes
    ) {
        int cappedMaxFileBytes = capMaxFileBytes(maxFileBytes);
        return ResponseEntity.ok(diffService.diff(repoOwner, repoName, includeContent, cappedMaxFileBytes));
    }

    private int capMaxFileBytes(int maxFileBytes) {
        int configuredMax = workspaceProperties.getDiff().getMaxFileBytes();
        if (maxFileBytes <= 0) {
            return configuredMax;
        }
        return Math.min(maxFileBytes, configuredMax);
    }
}
