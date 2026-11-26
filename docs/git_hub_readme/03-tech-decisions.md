# 3. 기술 결정

 [메인 README로 돌아가기](../../README.md)

## Cursor Cloud Agent

- Agent로 인한 PR 자동생성 금지: **`autoCreatePR=false`**
- repo 수정 Agent 모델 고정: **`composer-2.5` + `fast모드`**
- PR 메타 내용 작성은 수정을 담당한 Agent가 진행: repo Agent가 **follow-up** (`POST /v1/agents/{id}/runs`).  <br>예전에는 코드 수정 Agent와 별개의 Agent가 PR 제목·본문을 써서 환각증세가 발생할 수가 있었다.


## JGit 워크스페이스 상태 (`WorkspaceGitStateService`)

- force push 금지 유지:  PR API 호출 직전 unpushed면 `PushService.push()` **no-force** 선행 

## PR 메타 캐시

- `SHA-256 hex`: headCommitSha, 파일 수, path 정렬별 path/changeType/size/content hash
- IDE 추가 수정으로 diff가 바뀌면 fingerprint miss → follow-up 재호출
- `GET .../status`의 `llmCache.hasFingerprint`로 fingerprint 저장 여부 확인

## DB · Flyway

- V5: v3 Agent·LLM 컬럼 11종
- V6: `status` VARCHAR(32) (v3 enum 호환)
- V7: `llm_diff_fingerprint VARCHAR(64)` (`V7__add_llm_diff_fingerprint.sql`, information_schema 가드)
- `spring.jpa.hibernate.ddl-auto=validate`

## Diff

- 기준: clone 시점 `headCommitSha`(기준 커밋)과  working tree를 비교
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
