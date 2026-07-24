# 4. 문제 해결 (v3)

← [메인 README로 돌아가기](../../README.md) · [상세 문서 목록](../../README.md#상세-문서)

> 포트폴리오 README [상세 문서](../../README.md#상세-문서)의 4번 보완 페이지입니다.

## 시작 버튼 disabled

`GITHUB_TOKEN`과 `CURSOR_API_KEY` 둘 다 필요. 설정 후 앱 재기동.

## Agent 400 / branch validation

Cursor Dashboard에서 GitHub repo 연결 확인.

## PR 메타 stale 배너 (`diff.html`)

IDE 「추가 수정」 후 diff 내용이 바뀌었는데 이전에 「PR 진행」으로 생성한 PR 메타(`llm_*`)가 남아 있으면 diff 화면에 **경고 배너**가 표시됩니다.

- **원인:** PR 메타 캐시는 **시간이 아니라 diff fingerprint**로 유효성을 판정합니다. IDE uncommitted 변경으로 fingerprint가 달라지면 캐시가 stale입니다.
- **조치:** 「PR 진행」을 **다시 클릭**하면 follow-up이 재호출되어 메타가 갱신됩니다(웹 flash / REST `metadataRegeneratedDueToDiffChange=true`).
- **정상:** diff 변경 없이 「PR 진행」을 두 번 눌러도 follow-up은 **1회만** 호출됩니다(cache hit).

## Composer fallback (`fallbackUsed=true`)

- timeout: `app.cursor.composer.timeout-ms` 상향 (예: `bootRun --args="--app.cursor.composer.timeout-ms=180000 --app.cursor.composer.max-files=10"`)
- fallback 사용 시에도 PR 흐름은 정상 진행

## 재클론 / CLONE_FAILED

IDE가 `.git` pack 파일을 잠그면 폴더 삭제 실패. Cursor에서 워크스페이스 닫고 재시도.

## MySQL / Flyway

- **`Access denied`**: `DB_USERNAME`/`DB_PASSWORD` 확인
- **Migration 실패 이력**: `FlywayConfig`가 failed migration 감지 시 `repair()` 후 `migrate()` 자동 실행

## repoOwner / repoName 불일치

404 또는 「워크스페이스 없음」 → GitHub URL의 owner/name과 API path 일치 확인.

## GitHub side effect (워크스페이스 삭제)

로컬 DB·디스크만 정리. fork·branch·PR은 GitHub에 잔존 — 수동 정리.

## Windows JGit pack lock (테스트)

통합 테스트 cleanup WARN은 알려진 flake.
