# 3. 기술 결정

 [메인 README로 돌아가기](../../README.md)

## Cursor Cloud Agent

- `CloudAgentClient.startAgent`: **`autoCreatePR=false` LOCKED**
- repo Agent 모델: **`composer-2.5` + `fast=true`(Composer 2.5 Fast) 코드 상수 고정**
- Contribute PR 메타: repo Agent(`cursor_agent_id`)에 **follow-up run** (`POST /v1/agents/{id}/runs`)
- no-repo `runComposer` 경로: **제거됨** (follow-up 전환)

## PR 메타 캐시 (content-aware)

- `DiffFingerprintService`: `DiffResponse` → SHA-256 hex (`headCommitSha`, 파일 수, path 정렬별 path/changeType/size/content hash)
- `LlmMetadataService.isCacheValid`: **`llm_diff_fingerprint == 현재 diff fingerprint`** + `llm_*` 완비 시 cache hit (시간 baseline 폐기)
- IDE 추가 수정으로 diff가 바뀌면 fingerprint miss → follow-up 재호출 → `PrPrepareResponse.metadataRegeneratedDueToDiffChange`
- `GET .../status`의 `llmCache.hasFingerprint`로 fingerprint 저장 여부 확인

## DB · Flyway

- V5: v3 Agent·LLM 컬럼 11종
- V6: `status` VARCHAR(32) (v3 enum 호환)
- V7: `llm_diff_fingerprint VARCHAR(64)` (`V7__add_llm_diff_fingerprint.sql`, information_schema 가드)
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
- `PrMetadataFingerprintIntegrationTest` — fingerprint cache hit/miss, IDE 수정 stale 배너, `metadataRegeneratedDueToDiffChange`
- `CloudAgentClientTest` — `fast=true`, follow-up endpoint 회귀
