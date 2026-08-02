# GitHub Copilot With Cursor
**긍정적으로 일하고 세상에 도움이 되는 사람이 되자.**
<br>
<br>
안녕하세요 먼저 귀한 시간을 내어 저의 깃허브에 방문해 주신 것에 감사드립니다.<br/>
저는 팀의 일원으로서 팀원들과 함께 좋은 에너지를 만들며 일하고 싶고, 동시에 혼자 고민하는 시간을 통해 전문가로서의 역량을 기르고자 하고 적은 비용으로 어떻게 하면 많은 효과를 누릴 수 있을까? 하고 생각하는 개발자입니다.
<br>
<br>
이 프로젝트는 "GitHub Copilot Coding Agent"와 유사한 **리포지토리 에이전트 워크플로**를, **Cursor Cloud Agents API**와 **Spring Boot**로 직접 구현한 풀스택 포트폴리오 프로젝트입니다. <br><br>
**한줄 소개:** **GitHub URL + 자연어 프롬프트** → Cursor Cloud Agent가 fork branch에 코드를 수정 → Spring Boot가 pull·Diff·(선택) PR까지 처리하는 **Cloud-first 리포지토리 워크벤치**입니다.

### 성과 · 규모 하이라이트

* **운영 모드 2종** — Review(Diff·로컬 보관) · Contribute(commit + upstream PR)
* **단위·통합 테스트 약 90개** — 테스트 클래스 30여 개 (`MockMvc` E2E·서비스·GitHub/Cursor 클라이언트)
* **Flyway 마이그레이션 7단계** — V1 초기 스키마 ~ V7 `llm_diff_fingerprint` 캐시
* **외부 API 직접 연동** — Cursor Cloud Agents · GitHub REST를 Spring `RestClient`로 호출 (SDK 브릿지 없음)

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 17 |
| Build | Gradle |
| Framework | Spring Boot 4 (WebMVC) |
| View | Thymeleaf + static CSS |
| ORM | Spring Data JPA (Hibernate) |
| Database | MySQL 8 |
| Migration | Flyway |
| Git | JGit |
| Diff | java-diff-utils |
| External API | Cursor Cloud Agents API · GitHub REST API (`RestClient`) |
| API Docs | springdoc-openapi (Swagger UI) |
| Monitoring | Spring Boot Actuator |

## 왜 만들었나

GitHub Copilot Coding Agent처럼 *"프롬프트만 주면 에이전트가 알아서 브랜치를 만들고 코드를 고쳐 PR을 올리는"* 워크플로를, Node/Python SDK 브릿지 없이 **Cursor Cloud Agents API를 Spring Boot에서 직접 호출**해 재현해보고 싶었습니다.

이 프로젝트를 통해 실서비스에서 마주치는 관심사를 한 번에 다뤄보는 것이 목표였습니다.

* **외부 에이전트 API 연동** — 에이전트 실행·상태 폴링·완료 후 동기화
* **Git 자동화** — JGit 기반 fork·branch·pull·Diff·commit·upstream PR
* **운영 관심사** — 토큰 보안(마스킹), DB 마이그레이션, 워크스페이스 정리 스케줄러

## 빠른 시작

### 사전 요구

Java 17+, MySQL 8.x, Git · GitHub PAT(`repo`) · Cursor Cloud Agents API key

### 환경 변수

```powershell
$env:GITHUB_TOKEN = "ghp_..."
$env:CURSOR_API_KEY = "key_..."
```

### 기동

```powershell
.\gradlew.bat bootRun    # Windows
./gradlew bootRun        # Linux / macOS
```

→ http://localhost:8080 · [Swagger UI](http://localhost:8080/swagger-ui.html)

<br>
## 상세 문서

*아래 링크를 클릭하시면 해당 상세 페이지로 이동합니다.*

## [1.웹앱 흐름 및 아키텍처 개요](./docs/git_hub_readme/01-architecture.md)

Browser → Spring Boot → Cursor Cloud Agent · GitHub · JGit · MySQL 흐름, `repoOwner`/`repoName` 경로 규칙, 시퀀스 다이어그램, 화면 흐름(`index` → `wait` → `diff` → `commit` → `pr`).

## [2.주요 기능](./docs/git_hub_readme/02-features.md)

Review·Contribute 모드, Agent wait 폴링, Diff 확인, 로컬 IDE 추가 수정, REST·Web API 요약표.

## [3.기술적 고민 및 아키텍처 결정](./docs/git_hub_readme/03-tech-decisions.md)

* Composer 2.5 Fast(`fast=true`) 모델 고정 및 `autoCreatePR=false` 정책
* Agent push 후 Spring `fetch`/`pull --ff-only` 기반 Diff
* PR 메타 생성을 repo Agent **follow-up run**으로 처리하고, **diff fingerprint**로 캐시 hit/miss 판정(IDE 추가 수정 시 자동 재생성)
* `WorkspaceGitStateService`로 uncommitted/unpushed JGit 상태 판정 · PR 생성 직전 **no-force push** (`ensureBranchPushed`, FR-8.6)
* Flyway V7 `llm_diff_fingerprint`, 토큰 마스킹(`AuthorizationMaskingInterceptor`) 등

## [4.문제 해결](./docs/git_hub_readme/04-troubleshooting.md)

시작 버튼 disabled, Agent 400 validation, Composer fallback, 재클론 실패, MySQL/Flyway 오류, GitHub 잔존 리소스.

<br>



<br>

## 다른 포트폴리오

- [은행 이체 REST API를 통해 MySQL 동시성 정합성과 Redis 멱등성/스로틀을 중심으로 구현, 대량 데이터 인덱스 조회 실험 검증](https://github.com/Pray-T/BankTransferSys_Backend_Restful) 
- [AWS에 배포·운영한 JWT+Redis 이중 토큰 인증과 실시간 채팅 웹 앱](https://github.com/Pray-T/ReadyPlz-Production_main)

**이상입니다, 저의 깃허브 방문을 감사드립니다.**
