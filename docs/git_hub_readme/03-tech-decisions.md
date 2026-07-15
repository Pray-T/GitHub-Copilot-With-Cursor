# 3. 기술 결정 (v3)

## Cursor Cloud Agent

- `CloudAgentClient.startAgent`: **`autoCreatePR=false` LOCKED**
- repo Agent 모델: **`composer-2.5` + `fast=true`(Composer 2.5 Fast) 코드 상수 고정** — 외부 설정·Standard·다른 모델 금지
- Contribute PR 메타: `CloudAgentClient.requestPrMetadataFollowUp` — 코드 수정에 사용한 repo Agent(`cursor_agent_id`)에 **follow-up run**(`POST /v1/agents/{id}/runs`). `model`/`repos`/`autoCreatePR` 생략(Agent 생성 시점 Fast 모델 상속)
- no-repo `runComposer` 경로: **제거됨** (2026-06-26 follow-up 전환)

## DB · Flyway

- V5: v3 Agent·LLM 컬럼 11종
- V6: `status` VARCHAR(32) (v3 enum 호환)
- `spring.jpa.hibernate.ddl-auto=validate` — 스키마는 Flyway만 변경

## Diff

- 기준: clone 시점 `headCommitSha` vs working tree (v2 동일)
- 크기 제한 (`application.properties`):
  - `app.workspace.diff.max-file-bytes=1048576` (파일당 1MB)
  - `app.workspace.diff.max-total-bytes=52428800` (합계 50MB)

## 워크스페이스 정리

- `WorkspaceDeleteCleanupScheduler`: 삭제 실패 시 백그라운드 디스크 cleanup 재시도
- `app.workspace.delete.*`: sync/background retry·interval 설정

## 보안

- `GITHUB_TOKEN`, `CURSOR_API_KEY` — 환경변수만, DB·로그·UI·예외 메시지 미노출
- `RestClientConfig.AuthorizationMaskingInterceptor` — GitHub·Cursor RestClient 공통, DEBUG 로그에서 Authorization 헤더 마스킹 (private inner class)

## 테스트

- `AgentE2EFlowIntegrationTest` — Review/Contribute Agent status polling E2E
- `ContributeWebFlowIntegrationTest` — Contribute web 풀사이클 회귀
- `CloudAgentClientTest` — repo Agent payload `fast=true` + follow-up endpoint 회귀
- `CloudAgentClientComposerLiveTest` — live API 필요, `CURSOR_API_KEY` 없으면 skip
