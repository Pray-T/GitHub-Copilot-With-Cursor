# 1. 아키텍처 (v3)

## 한 줄 요약

Browser → Spring Boot(:8080) → Cursor Cloud Agents API + GitHub API + JGit + MySQL

## v3 변경 (v2 대비)

- clone 직후 IDE 자동 실행 **폐기** → M1 `launch-ide`에서만
- Read-Only → **Review (R-B)**: fork·branch·Agent push·Diff·보관 (PR 없음)
- **A1**: Agent push → Spring `fetch` + `pull --ff-only` → Diff(`headCommitSha` vs working tree)
- Contribute: Diff 후 **Composer 1회** (`pr/prepare`) → commit/PR pre-fill

## 주요 컴포넌트

| 계층 | 클래스 |
|------|--------|
| Cursor API | `CloudAgentClient`, `CursorAuth` |
| Agent | `AgentOrchestratorService`, `AgentSyncService` |
| LLM | `LlmMetadataService` |
| Git | `WorkspaceBootstrapService`, `DiffService`, `CommitPushService`, `PullRequestService` |

## 화면

`index` → `wait`(Agent 폴링) → `diff` → (M1) `wait` / (Contribute) `commit` → `pr`
