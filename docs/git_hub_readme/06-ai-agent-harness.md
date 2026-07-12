# 6. AI Agent Harness (v3)

## 개요

본 저장소는 애플리케이션 코드뿐 아니라, **Cursor AI 에이전트가 역할·권한·인수인계 규칙에 따라 협업하도록 설계한 하네스**([`.cursorrules`](../../.cursorrules))를 함께 공개합니다.

하네스의 목표는 다음과 같습니다.

* 기획·구현·테스트를 **한 에이전트에 섞지 않기**
* 산출물·권한·금지 사항을 **명시적으로 분리**
* 작업 완료 시 **Append-only 인수인계**로 다음 에이전트가 맥락을 잃지 않게 하기

## 멀티 에이전트 역할 분리

| 에이전트 | 전용 모델(설계) | 역할 | 권한 |
|--------|----------------|------|------|
| **@AgentA** | Claude Opus 4.8 | PRD·API·DB·화면 흐름 기획 | `docs/` 기획 문서 작성 전용 |
| **@AgentC** | Composer 2.5 | v3 기능 구현 (Spring + Cursor API) | 코드·`TECH_SPEC` 작성, `docs/` Read-Only |
| **@AgentB** | GPT-5.5 | 통합 테스트·E2E·회귀 디버깅 | `src/test/**` 중심, 신규 기능 구현은 @AgentC 영역 |

`.cursorrules` 수정 권한은 **사용자만** 가지며, 에이전트는 규칙을 따르는 쪽으로 설계했습니다.

## Handoff Protocol

1. **동기화:** 작업 시작 시 `STATUS.md`(로컬 인수인계 로그) 최우선 정독
2. **Append-only:** `STATUS.md` 덮어쓰기·삭제 금지
3. **기본 체인:** `@AgentA`(기획) → `@AgentC`(구현) → `@AgentB`(통합 테스트·디버깅)
4. 기획과 구현이 어긋나면 코드를 먼저 바꾸지 않고, 역할에 맞는 문서에 기록 후 다음 에이전트로 넘김

인수인계 포맷 예시:

```markdown
### [날짜 및 시간] @작업한에이전트이름
- **수행한 작업:**
- **변경 사항 (기획 수정 시):**
- **다음 에이전트를 위한 인수인계:**
```

> `STATUS.md`·`TECH_SPEC.md`·내부 기획 문서는 공개 GitHub에는 올리지 않고 로컬에서만 유지합니다 (`.gitignore`).

## LOCKED 정책 (에이전트 공통 금지)

| # | 정책 |
|---|------|
| 1 | JGit `setForce(true)` 등 **force push** 금지 |
| 2 | `GITHUB_TOKEN`, `CURSOR_API_KEY`를 DB·로그·응답·UI·예외 메시지에 **노출 금지** (마스킹 필수) |
| 3 | GitLab/Bitbucket 등 GitHub 외 호스팅 분기 추가 금지 |
| 4 | `application.properties`에 토큰 **실값** 기재 금지 (환경변수 ref만) |
| 5 | Cursor **`autoCreatePR=true`** 로 upstream PR 생성 금지 — Spring `PullRequestService`만 |
| 6 | Node/Python SDK **브릿지**를 v3 필수 경로로 도입 금지 |

추가 LOCKED (구현):

* Cloud Agent 모델 **`composer-2.5` Fast (`fast=true`)** 코드 상수 고정
* repo Agent `autoCreatePR=false` 고정

## @AgentC 구현 범위 (요약)

* **Review (R-B):** fork·branch·Agent push·pull·Diff·보관 (PR 없음)
* **Contribute:** Review + 조건부 commit·upstream PR
* **A1:** Agent push → Spring `fetch/pull` → `headCommitSha` vs working tree Diff
* **M1:** Diff 후 「추가 수정」 시에만 `IdeLauncher`
* **Contribute LLM:** 「PR 진행」 시 Composer follow-up 1회 → commit/PR 메타

패키지 구조 (평탄):

`com.demo.githubcopilotwithcursor.{config|controller|cursor|domain|dto|exception|github|repository|service}`

## 더 보기

전체 규칙·프로젝트 트리·에이전트별 필수 행동은 [`.cursorrules`](../../.cursorrules) 원문을 참고하세요.
