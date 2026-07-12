package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.domain.WorkspaceMode;
import com.demo.githubcopilotwithcursor.dto.CloneRequest;
import com.demo.githubcopilotwithcursor.dto.CloneResponse;
import com.demo.githubcopilotwithcursor.dto.ContributeStartRequest;
import com.demo.githubcopilotwithcursor.dto.ContributeStartResponse;
import org.springframework.stereotype.Service;

@Service
public class ContributeService {

    private final CloneService cloneService;

    public ContributeService(CloneService cloneService) {
        this.cloneService = cloneService;
    }

    public ContributeStartResponse start(ContributeStartRequest request) {
        CloneResponse response = cloneService.cloneRepository(
            new CloneRequest(request.repoUrl(), defaultPrompt(request), WorkspaceMode.CONTRIBUTE)
        );
        return toContributeResponse(response);
    }

    private String defaultPrompt(ContributeStartRequest request) {
        return "Refactor the repository according to best practices while keeping public APIs stable.";
    }

    private ContributeStartResponse toContributeResponse(CloneResponse response) {
        return new ContributeStartResponse(
            response.repoOwner(),
            response.repoName(),
            response.upstreamUrl(),
            response.forkUrl(),
            response.forkReused(),
            response.branchName(),
            response.workspacePath(),
            response.headCommitSha(),
            response.status(),
            response.clonedAt(),
            response.agentId(),
            response.runId(),
            response.agentStatus(),
            response.agentStartedAt()
        );
    }
}
