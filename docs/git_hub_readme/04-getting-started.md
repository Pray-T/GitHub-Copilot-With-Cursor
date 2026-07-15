# 4. 실행 가이드 (v3)

> 포트폴리오 README [상세 문서](../../README.md#상세-문서)의 4번 보완 페이지입니다.

## 사전 요구

- Java 17+, MySQL 8.x, Git
- (선택) `cursor` CLI — Diff 후 「추가 수정」
- GitHub PAT: `repo` scope
- Cursor Cloud Agents API key

## MySQL

기본 DB: `GitHubCopilotWithCursor` (`createDatabaseIfNotExist=true`)  
Flyway V1~V6 자동 적용.

## 환경변수

```powershell
$env:GITHUB_TOKEN = "ghp_..."
$env:CURSOR_API_KEY = "key_..."
$env:DB_URL = "jdbc:mysql://localhost:3306/GitHubCopilotWithCursor?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8&createDatabaseIfNotExist=true"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "secret"
```

## 기동

```powershell
.\gradlew.bat bootRun    # Windows
./gradlew bootRun        # Linux / macOS
```

- UI: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html

## Cursor Dashboard

Cloud Agent 시작 전 GitHub repo 연결 필요.  
`GET https://api.cursor.com/v1/repositories`에 repo가 보여야 validation 통과.

## Composer timeout (대형 저장소)

```powershell
.\gradlew.bat bootRun --args="--app.cursor.composer.timeout-ms=180000 --app.cursor.composer.max-files=10"
```

## 테스트

```powershell
.\gradlew.bat test
```

live API 테스트는 `CURSOR_API_KEY` 없으면 skip — 정상.

오류: [5. 문제 해결](05-troubleshooting.md)
