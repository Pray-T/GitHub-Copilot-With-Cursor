# GitHub Copilot With Cursor

**GitHub URL + 자연어 프롬프트** → Cursor Cloud Agent가 fork branch에 코드를 수정 → Spring Boot가 pull·Diff·(선택) PR까지 처리하는 **Cloud-first 리포지토리 워크벤치**입니다.

GitHub Copilot Coding Agent와 유사한 **리포지토리 에이전트 워크플로**를, **Cursor Cloud Agents API**와 **Spring Boot**로 직접 구현한 풀스택 포트폴리오 프로젝트입니다.

<br>

## 왜 만들었나

GitHub Copilot Coding Agent처럼 *"프롬프트만 주면 에이전트가 알아서 브랜치를 만들고 코드를 고쳐 PR을 올리는"* 워크플로를, Node/Python SDK 브릿지 없이 **Cursor Cloud Agents API를 Spring Boot에서 직접 호출**해 재현해보고 싶었습니다.

이 프로젝트를 통해 실서비스에서 마주치는 관심사를 한 번에 다뤄보는 것이 목표였습니다.

* **외부 에이전트 API 연동** — 에이전트 실행·상태 폴링·완료 후 동기화
* **Git 자동화** — JGit 기반 fork·branch·pull·Diff·commit·upstream PR
* **운영 관심사** — 토큰 보안(마스킹), DB 마이그레이션, 워크스페이스 정리 스케줄러

## 내가 한 일

* **1인 풀스택으로 설계·구현** — 기획, 백엔드, 화면(Thymeleaf), 테스트 전 과정을 단독 진행했습니다.
* Cursor Cloud Agents API 연동(`CloudAgentClient`), 에이전트 상태 폴링·동기화, JGit 기반 Git 자동화(fork·branch·pull·Diff·commit·PR), 토큰 마스킹 등 **핵심 연동 로직을 직접 작성**했습니다.
* 이 애플리케이션은 *"에이전트로 코드를 고치는 도구"*이지만, **애플리케이션 자체의 코드와 아키텍처는 제가 직접 설계·구현**한 것입니다.
* **테스트 29개**(단위 + `AgentE2EFlowIntegrationTest`·`ContributeWebFlowIntegrationTest` 등 E2E·통합 포함)로 Review/Contribute 주요 흐름을 검증합니다.

<br>

## Profile

* **프로젝트명** : GitHub Copilot With Cursor (Cloud-first Workbench)
* **한 줄 요약** : Browser → Spring Boot(:8080) → Cursor Cloud Agents API + GitHub API + JGit + MySQL
* **기술 스택** : `Java 17`, `Spring Boot 4`, `Thymeleaf`, `JPA`, `Flyway`, `MySQL`, `JGit`, `Cursor Cloud Agents API`, `GitHub REST API`
* **운영 모드** : **Review** (Diff·로컬 보관, PR 없음) · **Contribute** (commit + upstream PR)
* **필수 환경변수** : `GITHUB_TOKEN`, `CURSOR_API_KEY`
* **로컬 실행** : `.\gradlew.bat bootRun` → http://localhost:8080 · [Swagger UI](http://localhost:8080/swagger-ui.html)

<br>

---

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
* PR 메타 생성을 repo Agent **follow-up run**으로 처리하는 방식
* Flyway 기반 스키마 마이그레이션, 토큰 마스킹(`AuthorizationMaskingInterceptor`) 등

## [4.실행 가이드](./docs/git_hub_readme/04-getting-started.md)

Java·MySQL·Git 사전 요구, `GITHUB_TOKEN`/`CURSOR_API_KEY` 설정, `bootRun`, Cursor Dashboard repo 연결, Composer timeout 조정, 테스트.

## [5.문제 해결](./docs/git_hub_readme/05-troubleshooting.md)

시작 버튼 disabled, Agent 400 validation, Composer fallback, 재클론 실패, MySQL/Flyway 오류, GitHub 잔존 리소스.

<br>



<br>

*이상입니다. 저장소 방문에 감사드립니다.*
