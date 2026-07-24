package com.demo.githubcopilotwithcursor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "repository_workspace",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_repository_workspace_owner_name",
        columnNames = {"repo_owner", "repo_name"}
    )
)
public class RepositoryWorkspace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repo_owner", nullable = false, length = 255)
    private String repoOwner;

    @Column(name = "repo_name", nullable = false, length = 255)
    private String repoName;

    @Column(name = "repo_url", nullable = false, length = 2048)
    private String repoUrl;

    @Column(name = "workspace_path", nullable = false, length = 1024)
    private String workspacePath;

    @Column(name = "head_commit_sha", nullable = false, length = 40, columnDefinition = "CHAR(40)")
    private String headCommitSha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkspaceStatus status;

    @Column(name = "ide_launched", nullable = false)
    private boolean ideLaunched;

    @Column(name = "cloned_at", nullable = false)
    private OffsetDateTime clonedAt;

    @Column(name = "last_diff_at")
    private OffsetDateTime lastDiffAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "upstream_url", length = 2048)
    private String upstreamUrl;

    @Column(name = "fork_url", length = 2048)
    private String forkUrl;

    @Column(name = "fork_reused", nullable = false)
    private boolean forkReused;

    @Column(name = "branch_name", length = 255)
    private String branchName;

    @Column(name = "pr_url", length = 512)
    private String prUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkspaceMode mode = WorkspaceMode.REVIEW;

    @Column(name = "cursor_agent_id", length = 128)
    private String cursorAgentId;

    @Column(name = "cursor_run_id", length = 128)
    private String cursorRunId;

    @Lob
    @Column(name = "agent_prompt", columnDefinition = "TEXT")
    private String agentPrompt;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_status", length = 32)
    private AgentStatus agentStatus;

    @Column(name = "agent_started_at")
    private OffsetDateTime agentStartedAt;

    @Column(name = "agent_completed_at")
    private OffsetDateTime agentCompletedAt;

    @Column(name = "llm_commit_message", length = 4096)
    private String llmCommitMessage;

    @Column(name = "llm_pr_title", length = 256)
    private String llmPrTitle;

    @Lob
    @Column(name = "llm_pr_body", columnDefinition = "TEXT")
    private String llmPrBody;

    @Column(name = "llm_cached_at")
    private OffsetDateTime llmCachedAt;

    @Column(name = "llm_diff_fingerprint", length = 64)
    private String llmDiffFingerprint;

    protected RepositoryWorkspace() {
    }

    public RepositoryWorkspace(String repoOwner, String repoName, String repoUrl, String workspacePath, String headCommitSha) {
        OffsetDateTime now = OffsetDateTime.now();
        this.repoOwner = repoOwner;
        this.repoName = repoName;
        this.repoUrl = repoUrl;
        this.workspacePath = workspacePath;
        this.headCommitSha = headCommitSha;
        this.status = WorkspaceStatus.CLONED;
        this.mode = WorkspaceMode.REVIEW;
        this.ideLaunched = false;
        this.clonedAt = now;
        this.updatedAt = now;
    }

    public RepositoryWorkspace(
        String repoOwner,
        String repoName,
        String repoUrl,
        String workspacePath,
        String headCommitSha,
        WorkspaceMode mode,
        String upstreamUrl,
        String forkUrl,
        boolean forkReused,
        String branchName,
        String agentPrompt
    ) {
        this(repoOwner, repoName, repoUrl, workspacePath, headCommitSha);
        this.mode = mode == null ? WorkspaceMode.REVIEW : mode;
        this.upstreamUrl = upstreamUrl;
        this.forkUrl = forkUrl;
        this.forkReused = forkReused;
        this.branchName = branchName;
        this.agentPrompt = agentPrompt;
        this.status = WorkspaceStatus.CREATED;
    }

    /** v2 Contribute 테스트·픽스처 호환용. */
    public RepositoryWorkspace(
        String repoOwner,
        String repoName,
        String repoUrl,
        String workspacePath,
        String headCommitSha,
        String upstreamUrl,
        String forkUrl,
        boolean forkReused,
        String branchName
    ) {
        this(
            repoOwner,
            repoName,
            repoUrl,
            workspacePath,
            headCommitSha,
            WorkspaceMode.CONTRIBUTE,
            upstreamUrl,
            forkUrl,
            forkReused,
            branchName,
            null
        );
    }

    @PreUpdate
    void refreshUpdatedAt() {
        updatedAt = OffsetDateTime.now();
    }

    public void markAgentStarted(String agentId, String runId, OffsetDateTime startedAt) {
        this.cursorAgentId = agentId;
        this.cursorRunId = runId;
        this.agentStartedAt = startedAt;
        this.agentStatus = AgentStatus.RUNNING;
        this.status = WorkspaceStatus.AGENT_RUNNING;
    }

    public void markAgentSyncing() {
        this.agentStatus = AgentStatus.SYNCING;
        this.status = WorkspaceStatus.AGENT_SYNCING;
    }

    public void markAgentCompleted(OffsetDateTime completedAt) {
        this.agentCompletedAt = completedAt;
        this.agentStatus = AgentStatus.COMPLETED;
        this.status = WorkspaceStatus.READY_FOR_REVIEW;
    }

    public void markAgentFailed(OffsetDateTime completedAt) {
        this.agentCompletedAt = completedAt;
        this.agentStatus = AgentStatus.FAILED;
        this.status = WorkspaceStatus.AGENT_FAILED;
    }

    public void markAgentCancelled(OffsetDateTime completedAt) {
        this.agentCompletedAt = completedAt;
        this.agentStatus = AgentStatus.CANCELLED;
        this.status = WorkspaceStatus.AGENT_FAILED;
    }

    public void cacheLlmMetadata(
        String commitMessage,
        String prTitle,
        String prBody,
        OffsetDateTime cachedAt,
        String diffFingerprint
    ) {
        this.llmCommitMessage = commitMessage;
        this.llmPrTitle = prTitle;
        this.llmPrBody = prBody;
        this.llmCachedAt = cachedAt;
        this.llmDiffFingerprint = diffFingerprint;
    }

    public void invalidateLlmCache() {
        this.llmCommitMessage = null;
        this.llmPrTitle = null;
        this.llmPrBody = null;
        this.llmCachedAt = null;
        this.llmDiffFingerprint = null;
    }

    public void attachPullRequest(String prUrl) {
        this.prUrl = prUrl;
        this.status = WorkspaceStatus.PR_OPENED;
    }

    public Long getId() {
        return id;
    }

    public String getRepoOwner() {
        return repoOwner;
    }

    public String getRepoName() {
        return repoName;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public String getWorkspacePath() {
        return workspacePath;
    }

    public String getHeadCommitSha() {
        return headCommitSha;
    }

    public WorkspaceStatus getStatus() {
        return status;
    }

    public void setStatus(WorkspaceStatus status) {
        this.status = status;
    }

    public boolean isIdeLaunched() {
        return ideLaunched;
    }

    public void setIdeLaunched(boolean ideLaunched) {
        this.ideLaunched = ideLaunched;
    }

    public OffsetDateTime getClonedAt() {
        return clonedAt;
    }

    public OffsetDateTime getLastDiffAt() {
        return lastDiffAt;
    }

    public void setLastDiffAt(OffsetDateTime lastDiffAt) {
        this.lastDiffAt = lastDiffAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getUpstreamUrl() {
        return upstreamUrl;
    }

    public String getForkUrl() {
        return forkUrl;
    }

    public boolean isForkReused() {
        return forkReused;
    }

    public String getBranchName() {
        return branchName;
    }

    public String getPrUrl() {
        return prUrl;
    }

    public WorkspaceMode getMode() {
        return mode;
    }

    public String getCursorAgentId() {
        return cursorAgentId;
    }

    public String getCursorRunId() {
        return cursorRunId;
    }

    public String getAgentPrompt() {
        return agentPrompt;
    }

    public void setAgentPrompt(String agentPrompt) {
        this.agentPrompt = agentPrompt;
    }

    public AgentStatus getAgentStatus() {
        return agentStatus;
    }

    public OffsetDateTime getAgentStartedAt() {
        return agentStartedAt;
    }

    public OffsetDateTime getAgentCompletedAt() {
        return agentCompletedAt;
    }

    public String getLlmCommitMessage() {
        return llmCommitMessage;
    }

    public String getLlmPrTitle() {
        return llmPrTitle;
    }

    public String getLlmPrBody() {
        return llmPrBody;
    }

    public OffsetDateTime getLlmCachedAt() {
        return llmCachedAt;
    }

    public String getLlmDiffFingerprint() {
        return llmDiffFingerprint;
    }

    public boolean isContributeMode() {
        return mode == WorkspaceMode.CONTRIBUTE;
    }

    public boolean isAgentRunning() {
        return agentStatus == AgentStatus.RUNNING || agentStatus == AgentStatus.SYNCING || agentStatus == AgentStatus.PENDING;
    }
}
