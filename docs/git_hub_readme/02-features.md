# 2. 주요 기능 (v3)

> 포트폴리오 README [상세 문서](../../README.md#상세-문서)의 2번 보완 페이지입니다.

## Review

1. GitHub URL + Agent 프롬프트 입력 (`POST /web/clone`)
2. fork·feature branch·remote push 준비
3. Cursor Cloud Agent 실행 (`composer-2.5` Fast, `autoCreatePR=false`)
4. wait 화면 5초 폴링 → 완료 시 sync → Diff
5. 「Review 종료(보관)」 또는 「추가 수정」(로컬 `cursor` CLI IDE)

## Contribute

Review와 동일 + Diff에서 「PR 진행」:

- `POST /api/contribute/{repoOwner}/{repoName}/pr/prepare` — repo Agent **follow-up run** 1회 (실패 시 fallback)
- uncommitted 있으면 commit 폼 → push → PR 폼
- upstream draft PR 생성 (`PullRequestService` 단일 출구)

## REST API 요약

상세 요청/응답 스키마는 [Swagger UI](http://localhost:8080/swagger-ui.html) 참고.

| Method | Path | 용도 |
|--------|------|------|
| POST | `/api/contribute/start` | Contribute 워크스페이스 시작 (v3 주 경로) |
| POST | `/api/agents/start` | 기존 workspace Agent 재시작 |
| GET | `/api/agents/{repoOwner}/{repoName}/status` | Agent 상태 폴링 |
| POST | `/api/agents/{repoOwner}/{repoName}/cancel` | Agent 취소 |
| POST | `/api/agents/{repoOwner}/{repoName}/sync` | Agent 완료 후 fetch/pull·Diff 준비 |
| GET | `/api/diff/{repoOwner}/{repoName}` | Diff JSON |
| GET | `/api/contribute/{repoOwner}/{repoName}/status` | mode, agent, llmCache 메타 |
| POST | `/api/contribute/{repoOwner}/{repoName}/pr/prepare` | PR 메타 Composer follow-up |
| POST | `/api/contribute/{repoOwner}/{repoName}/commit-push` | commit + push |
| POST | `/api/contribute/{repoOwner}/{repoName}/pull-request` | upstream PR 생성 |
| GET | `/api/workspaces` | 워크스페이스 목록 |
| POST | `/api/workspaces/{repoOwner}/{repoName}/launch-ide` | IDE 실행 (REST) |
| DELETE | `/api/workspaces/{repoOwner}/{repoName}` | 로컬 DB·디스크 삭제 |
| POST | `/api/clone` | Legacy v1 클론 API |

## Web UI 경로

| Method | Path | 용도 |
|--------|------|------|
| GET | `/`, `/web` | index (워크스페이스 목록) |
| POST | `/web/clone` | Review/Contribute 시작 |
| GET | `/web/workspaces/{repoOwner}/{repoName}/wait` | Agent 대기 |
| GET | `/web/workspaces/{repoOwner}/{repoName}/diff` | Diff 화면 |
| POST | `/web/workspaces/{repoOwner}/{repoName}/launch-ide` | IDE 실행 |
| POST | `/web/workspaces/{repoOwner}/{repoName}/pr/prepare` | PR 메타 준비 |
| GET | `/web/workspaces/{repoOwner}/{repoName}/commit` | commit 폼 |
| POST | `/web/workspaces/{repoOwner}/{repoName}/commit-push` | commit + push |
| GET | `/web/workspaces/{repoOwner}/{repoName}/pr` | PR 폼 |
| POST | `/web/workspaces/{repoOwner}/{repoName}/create-pr` | PR 생성 |
| POST | `/web/workspaces/{repoOwner}/{repoName}/delete` | 워크스페이스 삭제 |

## 운영 주의

- **워크스페이스 삭제**: 로컬 DB·디스크만 정리. GitHub fork branch·PR은 수동 정리.
