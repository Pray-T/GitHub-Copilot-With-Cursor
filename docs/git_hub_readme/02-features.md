# 2. 주요 기능 

 [메인 README로 돌아가기](../../README.md)

## Review 모드

1. GitHub URL + Agent 프롬프트 입력 (`POST /web/clone`)
2. fork·feature branch·remote push 준비
3. Cursor Cloud Agent 실행 (`composer-2.5` Fast, `autoCreatePR=false`)
4. wait 화면 5초 폴링 → 완료 시 sync → Diff
5. 「Review 종료(보관)」 또는 「추가 수정」(로컬 `cursor` CLI IDE)

## Contribute 모드

Review와 동일 + Diff에서 「PR 진행」:

- `POST /api/contribute/{repoOwner}/{repoName}/pr/prepare` — **diff fingerprint** 일치 시 DB `llm_*` 캐시 재사용; 불일치(IDE 추가 수정 등) 시 repo Agent **follow-up run** 재호출 (실패 시 fallback)
- 동일 diff에서 「PR 진행」 재클릭 → follow-up **1회만** (cache hit). diff 변경 후 재클릭 → follow-up 재호출 + `metadataRegeneratedDueToDiffChange=true` (REST) / flash 안내 (웹)
- `diff.html` stale 배너: 캐시 fingerprint ≠ 현재 diff 시 PR 메타가 옛 변경을 반영할 수 있음을 경고
- uncommitted 있으면 commit 폼 → push → PR 폼
- upstream draft PR 생성 (`PullRequestService` 단일 출구)

## REST API 요약

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

- **워크스페이스 삭제**: 로컬 DB·디스크만 정리. GitHub fork branch·PR은 수동으로 정리해줘야 합니다.
