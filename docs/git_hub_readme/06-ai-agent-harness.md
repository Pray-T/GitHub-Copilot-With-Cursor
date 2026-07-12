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

## 멀티 에이전트와 토큰·비용 절감

> 아래 수치는 **본 프로젝트(v3) 개발 과정을 가정한 설명용 추정**입니다.  
> Cursor의 모델별 단가·요청 단위(premium request 등)는 시점마다 달라질 수 있으며, **절대 금액보다 “역할 분리 → 고가 모델 노출 최소화” 구조**를 보여 주는 것이 목적입니다.

### 비교 시나리오

**가정:** 기획(PRD·API·DB) → 구현(Spring·Cursor API·Thymeleaf) → 통합 테스트·E2E·회귀 디버깅까지 **한 번의 v3 사이클**을 완료한다.

| 방식 | 담당 | 특징 |
|------|------|------|
| **A. 단일 에이전트** | Claude Opus 4.8 하나가 전 구간 담당 | 동일 스레드에 기획·코드·테스트·디버깅 맥락이 누적 |
| **B. 멀티 에이전트 (본 하네스)** | @AgentA(Opus) → @AgentC(Composer 2.5) → @AgentB(GPT-5.5) | 역할·산출물·권한 분리 + `STATUS.md` Append-only 인수인계 |

### 추정 토큰·상대 비용 (illustrative)

Opus를 **입력·출력 모두 고가 티어**로 두고, Composer·GPT-5.5는 **구현·테스트에 상대적으로 저렴한 티어**로 둔 **상대 비용 지수**입니다. (Opus 단일 = 100 기준)

| 단계 | A. Opus 단일 (추정) | B. 역할 분리 (추정) |
|------|---------------------|---------------------|
| 기획 | Opus — 입력 ~180K / 출력 ~90K | **@AgentA Opus** — 입력 ~120K / 출력 ~70K (`docs/`만, 코드 미접근) |
| 구현 | Opus — 입력 ~650K / 출력 ~280K (매 턴 전체 맥락 재주입) | **@AgentC Composer 2.5** — 입력 ~420K / 출력 ~190K |
| 통합·E2E·디버깅 | Opus — 입력 ~380K / 출력 ~140K (기획·구현 이력까지 함께 소비) | **@AgentB GPT-5.5** — 입력 ~220K / 출력 ~85K (`src/test/**`·실패 로그 중심) |
| **합계 (토큰)** | **입력 ~1.21M / 출력 ~510K** | **입력 ~760K / 출력 ~345K** |
| **상대 비용 지수** | **~100** | **~42~48** (모델 단가 차이 반영 시) |

**해석 (예시 문장):**  
만약 기획·구현·통합 테스트까지 **Opus 4.8 한 모델**이 담당했다면, 동일 v3 사이클 기준 **상대 비용 지수 약 100(추정)** 에 가까울 수 있습니다.  
반면 **역할별 에이전트 분리**(@AgentA / @AgentC / @AgentB)와 Handoff Protocol을 적용하면, 고가 모델(Opus)은 **기획 구간에만** 쓰고 구현·테스트는 **Composer 2.5·GPT-5.5**로 넘기므로 **상대 비용 지수 약 42~48(추정)** 수준까지 낮출 수 있었습니다.  
→ 같은 품질 목표를 두었을 때 **체감 절감 약 50~55%** (토큰량·모델 단가를 함께 고려한 illustrative 추정).

### 비용이 줄어드는 이유 (하네스 설계 포인트)

1. **모델 티어 매칭 (Model–Task Fit)**  
   - 고가 Opus는 **요구사항·API·DB·화면 흐름**처럼 모호함이 큰 기획에만 사용  
   - 대량 코드 생성·수정은 **Composer 2.5**, 테스트·터미널 디버깅은 **GPT-5.5** — 단가가 낮은 구간에 작업 배치

2. **컨텍스트 범위 제한 (Scoped Context)**  
   - @AgentA: `docs/` 작성 전용 → `src/**` 전체를 매번 읽지 않음  
   - @AgentC: `docs/` Read-Only → 기획 전체 재작성 루프 방지  
   - @AgentB: **신규 기능 구현 금지** → “테스트 실패 → 최소 수정”만 허용, 구현 전면 재작성 방지

3. **인수인계 문서화로 장문 대화 누적 방지**  
   - 단일 Opus 스레드는 기획 결정·시행착오·테스트 로그가 **한 컨텍스트에 계속 쌓임**  
   - `STATUS.md` Append-only는 **다음 에이전트가 필요한 요약만** 읽게 하여, 매 턴 “전체 역사” 재전송을 줄임

4. **역할 충돌·중복 작업 감소**  
   - “기획 에이전트가 코드를 고치거나 / 테스트 에이전트가 아키텍처를 다시 짜는” **경계 붕괴**를 `.cursorrules`로 차단  
   - 불필요한 고가 모델 호출(재기획·재구현 루프)을 구조적으로 억제

### 단일 Opus vs 멀티 에이전트 (요약)

| 항목 | Opus 단일 | 멀티 에이전트 하네스 |
|------|-----------|----------------------|
| 고가 모델 사용 구간 | 기획 + 구현 + 테스트 **전 구간** | 기획(**@AgentA**) 위주 |
| 컨텍스트 성장 | 한 스레드에 무한 누적 | 단계별 **분리·요약 인수인계** |
| 산출물 중복 | 기획서·코드·테스트가 한 에이전트에 혼재 | 페르소나별 **산출물 고정** |
| 디버깅 시 비용 | Opus로 Gradle·JGit·E2E 로그 분석 | **@AgentB**가 담당 (상대 저비용) |

### 포트폴리오에서 강조할 한 줄

> **“비싼 모델을 전 구간에 쓰지 않고, `.cursorrules`로 역할·권한·인수인계를 고정해 Opus는 기획에, Composer는 구현에, GPT는 검증에 배치함으로써 토큰·비용을 절감했다.”**

### 주의

* 위 표의 K·M 토큰·%는 **재현 가능한 청구서가 아닌**, 하네스 설계 의도를 설명하기 위한 **illustrative 추정**입니다.  
* 실제 절감률은 프롬프트 길이, 실패·재시도 횟수, Cursor 요금제에 따라 달라집니다.  
* 비용 절감과 함께 **품질·보안(LOCKED 정책)** 을 동시에 지키는 것이 본 하네스의 목표입니다.


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
