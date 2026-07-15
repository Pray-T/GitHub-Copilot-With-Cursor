# 3. 기술 결정 (v3)

> 포트폴리오 README [상세 문서](../../README.md#상세-문서)의 3번 보완 페이지입니다.

## Cursor Cloud Agent

- `CloudAgentClient.startAgent`: **`autoCreatePR=false` LOCKED**
- repo Agent 모델: **`composer-2.5` + `fast=true`(Composer 2.5 Fast) 코드 상수 고정**
- Contribute PR 메타: repo Agent(`cursor_agent_id`)에 **follow-up run** (`POST /v1/agents/{id}/runs`)
- no-repo `runComposer` 경로: **제거됨** (follow-up 전환)

## DB · Flyway

- V5: v3 Agent·LLM 컬럼 11종
- V6: `status` VARCHAR(32) (v3 enum 호환)
- `spring.jpa.hibernate.ddl-auto=validate`

## Diff

- 기준: clone 시점 `headCommitSha` vs working tree
- 크기 제한: `app.workspace.diff.max-file-bytes` (1MB), `max-total-bytes` (50MB)

## 워크스페이스 정리

- `WorkspaceDeleteCleanupScheduler`: 삭제 실패 시 백그라운드 디스크 cleanup 재시도

## 보안

- `GITHUB_TOKEN`, `CURSOR_API_KEY` — 환경변수만, DB·로그·UI·예외 메시지 미노출
- `RestClientConfig.AuthorizationMaskingInterceptor` — DEBUG 로그 Authorization 마스킹

## 테스트

- `AgentE2EFlowIntegrationTest` — Review/Contribute Agent polling E2E
- `ContributeWebFlowIntegrationTest` — Contribute web 풀사이클
- `CloudAgentClientTest` — `fast=true`, follow-up endpoint 회귀
