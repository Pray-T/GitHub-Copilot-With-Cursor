# 5. 문제 해결 (v3)

## 시작 버튼 disabled

`GITHUB_TOKEN`과 `CURSOR_API_KEY` 둘 다 필요. 설정 후 앱 재기동.

## Agent 400 / branch validation

Cursor Dashboard에서 GitHub repo 연결 확인. API key만 유효하고 repo 미연결 시 Agent 시작 실패.

## Composer fallback (`fallbackUsed=true`)

- no-repo payload에 `autoCreatePR` 포함 시 400 — 코드에서 제외됨
- timeout: `app.cursor.composer.timeout-ms` 상향
- fallback 사용 시에도 PR 흐름은 정상 진행 (FR-7.3)

## 재클론 / CLONE_FAILED

IDE가 `.git` pack 파일을 잠그면 폴더 삭제 실패. Cursor에서 해당 워크스pace 닫고 「새로 시작」 후 재시도.

## MySQL / Flyway

- `Access denied`: `DB_USERNAME`/`DB_PASSWORD`
- 실패 이력: 앱 기동 시 Flyway `repair()` → `migrate()` 자동복구 (TECH_SPEC)

## Windows JGit pack lock (테스트)

통합 테스트 cleanup WARN은 알려진 flake. `build/tmp` 격리 root 사용.

## GitHub side effect

workspace delete는 로컬 DB·디스크만 정리. fork branch·PR은 GitHub에 남음 — 수동 정리.
