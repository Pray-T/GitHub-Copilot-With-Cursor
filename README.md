<center>

# GitHub URL + Agent 프롬프트 → Cursor Cloud Agent → Diff → Review 또는 Contribute PR

안녕하세요. **GitHub Copilot With Cursor** 저장소에 방문해 주셔서 감사합니다.

본 프로젝트는 GitHub Copilot Coding Agent와 유사한 **리포지토리 에이전트 워크플로**를, **Cursor Cloud Agent**와 Spring Boot로 구현한 Cloud-first Workbench입니다.  
GitHub URL과 자연어 프롬프트만 입력하면 Cloud Agent가 fork branch에 1차 수정을 push하고, Spring이 pull한 뒤 JGit Diff로 변경을 검토한 다음 **Review(보관)** 또는 **Contribute(upstream PR)** 로 마무리할 수 있습니다.

</center>

<br>

## Quick Start

1. **MySQL 8.x** 기동 (기본 DB명 `GitHubCopilotWithCursor`, Flyway V1~V6 자동 적용)
2. 환경변수 설정: `GITHUB_TOKEN`, `CURSOR_API_KEY`
3. `.\gradlew.bat bootRun` (Linux/macOS: `./gradlew bootRun`)
4. [http://localhost:8080](http://localhost:8080) → GitHub URL + Agent 프롬프트 → **Review** 또는 **Contribute**

상세 설정은 [4. 실행 가이드](docs/git_hub_readme/04-getting-started.md)를 참고하세요.

<br>

## Profile

* **프로젝트명** : GitHub Copilot With Cursor (Cloud-first Workbench v3.0.2)
* **한 줄 요약** : Browser → Spring Boot(:8080) → Cursor Cloud Agents API + GitHub API + JGit + MySQL
* **기술 스택** : `Java 17`, `Spring Boot 4`, `Thymeleaf`, `JPA`, `Flyway`, `MySQL`, `JGit`, `Cursor Cloud Agents API`, `GitHub REST API`
* **운영 모드** : **Review** (fork·Agent·Diff·로컬 보관, PR 없음) · **Contribute** (Review + commit/PR)
* **필수 환경변수** : `GITHUB_TOKEN`, `CURSOR_API_KEY` (토큰은 환경변수 ref만, 코드·로그·UI 미노출)
* **로컬 실행** : `.\gradlew.bat bootRun` → [http://localhost:8080](http://localhost:8080) · [Swagger UI](http://localhost:8080/swagger-ui.html) (API 상세는 Swagger 참고)

<br>

## 용어 (Glossary)

| 용어 | 의미 |
|------|------|
| **Review** | fork·Agent push·Diff·로컬 보관. upstream PR 없음 |
| **Contribute** | Review + Diff 후 commit·push·upstream PR |
| **A1** | Agent push → Spring `fetch`/`pull` → `headCommitSha` vs working tree Diff |
| **M1** | Diff 확인 후 「추가 수정」 시에만 로컬 `cursor` CLI IDE 실행 |

<br>

## 저장소 구조

```
.
├── README.md
├── docs/git_hub_readme/          # 공개 상세 문서 01~05
├── src/main/java/com/demo/githubcopilotwithcursor/
│   ├── config/                   # 설정·Flyway·RestClient
│   ├── controller/               # REST + Thymeleaf Web
│   ├── cursor/                   # Cursor Cloud Agents API 클라이언트
│   ├── domain/                   # JPA 엔티티·enum
│   ├── dto/                      # 요청/응답 DTO
│   ├── exception/                # 공통 예외 처리
│   ├── github/                   # GitHub REST API 클라이언트
│   ├── repository/               # Spring Data JPA
│   └── service/                  # 비즈니스 로직
├── src/main/resources/
│   ├── templates/                # index, wait, diff, commit, pr
│   └── db/migration/             # Flyway V1~V6
└── .cursorrules                  # (참고) Cursor로 이 저장소를 개발할 때의 AI 협업 규칙 — 앱 실행과 무관
```

<br>

---

<br>

## 상세 문서

_아래 링크를 클릭하시면 해당 상세 페이지로 이동합니다._

## [1. 웹앱 흐름 및 아키텍처 개요](docs/git_hub_readme/01-architecture.md)

Browser → Spring Boot → Cursor Cloud Agent · GitHub · JGit · MySQL 흐름, `repoOwner`/`repoName` 경로 규칙, 화면 흐름(`index` → `wait` → `diff` → `commit` → `pr`).

## [2. 주요 기능](docs/git_hub_readme/02-features.md)

Review·Contribute 모드, Agent wait 폴링, Diff 확인, M1 IDE 추가 수정, REST·Web API 요약표.

## [3. 기술적 고민 및 아키텍처 결정](docs/git_hub_readme/03-tech-decisions.md)

Composer 2.5 Fast(`fast=true`) 모델 고정, `autoCreatePR=false`, PR follow-up run, Flyway V5~V6, 토큰 마스킹 등.

## [4. 실행 가이드](docs/git_hub_readme/04-getting-started.md)

Java·MySQL·Git 사전 요구, 환경변수·DB 설정, `bootRun`, Cursor Dashboard repo 연결, Composer timeout 조정, 테스트.

## [5. 문제 해결](docs/git_hub_readme/05-troubleshooting.md)

시작 버튼 disabled, Agent 400 validation, Composer fallback, 재클론 실패, MySQL/Flyway 오류, GitHub 잔존 리소스.

<br>

_이상입니다. 저장소 방문에 감사드립니다._
