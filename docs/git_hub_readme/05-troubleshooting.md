# 5. 문제 해결 (v3)

## 시작 버튼 disabled

`GITHUB_TOKEN`과 `CURSOR_API_KEY` 둘 다 필요. 설정 후 앱 재기동.

## Agent 400 / branch validation

Cursor Dashboard에서 GitHub repo 연결 확인. API key만 유효하고 repo 미연결 시 Agent 시작 실패.

## Composer fallback (`fallbackUsed=true`)

- no-repo payload에 `autoCreatePR` 포함 시 400 — 코드에서 제외됨
- timeout: `app.cursor.composer.timeout-ms` 상향 ([4. 실행 가이드](04-getting-started.md) Composer 절)
- fallback 사용 시에도 PR 흐름은 정상 진행

## 재클론 / CLONE_FAILED

IDE가 `.git` pack 파일을 잠그면 폴더 삭제 실패. Cursor에서 해당 워크스pace 닫고 「새로 시작」 후 재시도.

## MySQL / Flyway

- **`Access denied`**: `DB_USERNAME`/`DB_PASSWORD` 확인
- **Migration 실패 이력**: 앱 기동 시 `FlywayConfig`가 failed migration을 감지하면 `repair()` 후 `migrate()`를 자동 실행 (`FlywayConfig.java`)

## repoOwner / repoName 불일치

404 또는 「워크스페이스 없음」:

- GitHub URL의 owner/name과 API·Web path의 `{repoOwner}/{repoName}`이 일치하는지 확인
- 예: `https://github.com/myorg/my-repo` → `/api/agents/myorg/my-repo/status`

## Windows JGit pack lock (테스트)

통합 테스트 cleanup WARN은 알려진 flake. `build/tmp` 격리 root 사용.

## GitHub side effect (워크스페이스 삭제)

`DELETE /api/workspaces/{repoOwner}/{repoName}` 또는 Web delete는 **로컬 DB·디스크만** 정리합니다.

GitHub에 남는 것 (수동 정리 필요):

- fork repository
- feature branch
- draft/open pull request
