# 5. 문제 해결 (v3)

> 로컬 실행·운영 중 자주 겪는 이슈입니다.

## 시작 버튼 disabled

`GITHUB_TOKEN`과 `CURSOR_API_KEY` 둘 다 필요. 설정 후 앱 재기동.

## Agent 400 / branch validation

Cursor Dashboard에서 GitHub repo 연결 확인.

## Composer fallback (`fallbackUsed=true`)

- timeout: `app.cursor.composer.timeout-ms` 상향 ([4. 실행 가이드](04-getting-started.md))
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
