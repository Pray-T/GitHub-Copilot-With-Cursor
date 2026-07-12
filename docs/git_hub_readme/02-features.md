# 2. 주요 기능 (v3)

## Review (R-B)

1. GitHub URL + Agent 프롬프트 입력
2. fork·feature branch·remote push 준비
3. Cursor Cloud Agent 실행 (`composer-2.5`, `autoCreatePR=false`)
4. wait 화면 5초 폴링 → 완료 시 sync → Diff
5. 「Review 종료(보관)」 또는 「추가 수정」(M1 IDE)

## Contribute

Review와 동일 + Diff에서 「PR 진행」:

- `POST .../pr/prepare` — Composer 1회 (실패 시 fallback)
- uncommitted 있으면 commit 폼 → push → PR 폼
- upstream draft PR 생성 (`PullRequestService` 단일 출구)

## 워크스페이스 목록 (index)

- mode / status / agentStatus 배지
- PR URL 링크 배지 (Contribute PR 생성 후)

## REST

- `/api/agents/*` — Agent lifecycle
- `/api/contribute/.../status` — mode, agent, llmCache 메타 포함
