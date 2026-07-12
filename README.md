<center>

# GitHub URL + Agent 프롬프트 → Cursor Cloud Agent → Diff → Review 또는 Contribute PR

안녕하세요. **GitHub Copilot With Cursor** 저장소에 방문해 주셔서 감사합니다.

본 프로젝트는 GitHub Copilot Coding Agent와 유사한 **리포지토리 에이전트 워크플로**를, **Cursor Cloud Agent**와 Spring Boot로 구현한 Cloud-first Workbench입니다.  
GitHub URL과 자연어 프롬프트만 입력하면 Cloud Agent가 fork branch에 1차 수정을 push하고, Spring이 pull한 뒤 JGit Diff로 변경을 검토한 다음 **Review(보관)** 또는 **Contribute(upstream PR)** 로 마무리할 수 있습니다.

</center>

<br>

## Profile

* **프로젝트명** : GitHub Copilot With Cursor (Cloud-first Workbench v3.0.2)
* **한 줄 요약** : Browser → Spring Boot(:8080) → Cursor Cloud Agents API + GitHub API + JGit + MySQL
* **기술 스택** : `Java 17`, `Spring Boot 4`, `Thymeleaf`, `JPA`, `Flyway`, `MySQL`, `JGit`, `Cursor Cloud Agents API`, `GitHub REST API`
* **운영 모드** : **Review** (fork·Agent·Diff·보관, PR 없음) · **Contribute** (Review + Composer follow-up → commit/PR)
* **필수 환경변수** : `GITHUB_TOKEN`, `CURSOR_API_KEY` (토큰은 환경변수 ref만, 코드·로그·UI 미노출)
* **로컬 실행** : `.\gradlew.bat bootRun` → [http://localhost:8080](http://localhost:8080) · [Swagger UI](http://localhost:8080/swagger-ui.html)

<br>

---

<br>

## 상세 문서

_아래 링크를 클릭하시면 해당 상세 페이지로 이동합니다._

## [1. 웹앱 흐름 및 아키텍처 개요](docs/git_hub_readme/01-architecture.md)

Browser → Spring Boot → Cursor Cloud Agent · GitHub · JGit · MySQL로 이어지는 전체 아키텍처와 v3 화면 흐름(`index` → `wait` → `diff` → `commit` → `pr`)을 설명합니다.

## [2. 주요 기능](docs/git_hub_readme/02-features.md)

Review(R-B)와 Contribute 모드, Agent wait 폴링, Diff 확인, M1 로컬 IDE 추가 수정, `pr/prepare` Composer follow-up 등 프로젝트의 핵심 기능을 소개합니다.

## [3. 기술적 고민 및 아키텍처 결정](docs/git_hub_readme/03-tech-decisions.md)

* Composer 2.5 Fast(`fast=true`) 모델 고정 및 `autoCreatePR=false` 정책
* Agent push 후 Spring `fetch`/`pull --ff-only` 기반 Diff(A1) 유지
* PR 메타 생성을 repo Agent **follow-up run**으로 처리하는 방식
* Flyway V5~V6, 토큰 마스킹(`AuthorizationMaskingInterceptor`) 등 기술 결정

## [4. 실행 가이드](docs/git_hub_readme/04-getting-started.md)

Java·MySQL·Git 사전 요구, `GITHUB_TOKEN`/`CURSOR_API_KEY` 설정, `bootRun`, Cursor Dashboard repo 연결, Composer timeout(기본 120000ms) 조정 방법을 안내합니다.

## [5. 문제 해결](docs/git_hub_readme/05-troubleshooting.md)

시작 버튼 disabled, Agent 400 validation, Composer fallback, 재클론 실패, MySQL/Flyway 오류 등 운영 중 자주 겪는 이슈와 해결 방법을 정리했습니다.

## [6. AI 에이전트 역할 분리 & 인수인계](docs/git_hub_readme/06-agent-roles-handoff.md)

@AgentA/@AgentC/@AgentB 역할·모델 분리, Handoff Protocol, LOCKED 정책, 토큰·비용 절감 설계 및 [`.cursorrules`](.cursorrules) 협업 규칙을 설명합니다.

<br>

_이상입니다. 저장소 방문에 감사드립니다._
