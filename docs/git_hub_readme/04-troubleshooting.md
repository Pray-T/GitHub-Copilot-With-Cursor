# 4. 문제 해결

[메인 README로 돌아가기](../../README.md)

## PR 메타 캐시관련

IDE를 통한 추가 수정 후 diff 내용이 바뀌었는데 이전에 「PR 진행」으로 생성한 PR 메타(`llm_*`)가 남아 있으면 diff 화면에 **경고 배너**가 표시됩니다.

- **원인:** PR 메타 캐시는 **시간이 아니라 diff fingerprint**로 유효성을 판정합니다. IDE uncommitted 변경으로 fingerprint가 달라지면 캐시가 유효하지 않습니다.
- **조치:** 「PR 진행」을 **다시 클릭**하면 follow-up이 재호출되어 메타가 갱신됩니다.
- **정상:** diff 변경 없이 「PR 진행」을 두 번 눌러도 follow-up은 **1회만** 호출됩니다(cache hit를 통해 1회만 호출).

## Flyway

- **Migration 실패 이력**: `FlywayConfig`가 failed migration 감지 시 `repair()` 후 `migrate()` 자동 실행


## 「PR 만들기」 직행 시 diff로 돌아감

Contribute에서 IDE 「추가 수정」 버튼 클릭 후 **「PR 진행」·「커밋+Push」 없이** PR 페이지(`/pr`) 또는 「PR 만들기」만 누르면 **Diff 화면으로 redirect**되며 flash 오류가 표시됩니다.

- **원인 1 (미커밋):** IDE에서 저장한 변경이 아직 커밋되지 않았습니다. flash: `PR_FLOW_UNCOMMITTED_CHANGES`.
- **원인 2 (stale 메타):** IDE 수정 후 「PR 진행」을 다시 누르지 않아 PR 메타(`llm_*`) fingerprint가 현재 diff와 불일치합니다. flash: `PR_FLOW_STALE_METADATA`. diff 화면 **stale 경고 배너**도 동일 정책(v3.0.4 fingerprint, v3.0.5 `WorkspaceGitStateService` 가드).

**올바른 순서:**

```
Diff → [추가 수정] → IDE 저장 → [변경 확인] → [PR 진행] → [커밋+Push] → [PR 만들기]
```

- IDE 복귀 직후 「변경 확인」만으로 PR 메타는 갱신되지 않습니다. **「PR 진행」을 다시 클릭**해야 Composer follow-up이 최신 diff를 반영합니다.
- commit-push 후 fingerprint가 post-commit 상태로 동기화되므로, 위 순서를 따르면 PR 페이지 직행이 허용됩니다.