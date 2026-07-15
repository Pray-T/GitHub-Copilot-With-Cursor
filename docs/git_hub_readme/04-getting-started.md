# 4. 실행 가이드 (v3)

## 사전 요구

- Java 17+, MySQL 8.x, Git
- (M1) `cursor` CLI — Diff 후 「추가 수정」 시
- GitHub PAT: `repo` scope (Contents + Pull requests)
- Cursor Cloud Agents API key

## MySQL

기본 연결 URL (`application.properties`):

```
jdbc:mysql://localhost:3306/GitHubCopilotWithCursor?createDatabaseIfNotExist=true
```

DB가 없으면 `createDatabaseIfNotExist=true`로 자동 생성됩니다. Flyway V1~V6이 기동 시 적용됩니다.

## 환경변수

```powershell
$env:GITHUB_TOKEN = "ghp_..."
$env:CURSOR_API_KEY = "key_..."

# 선택 — MySQL (기본값: root / 빈 비밀번호)
$env:DB_URL = "jdbc:mysql://localhost:3306/GitHubCopilotWithCursor?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8&createDatabaseIfNotExist=true"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "secret"
```

Linux/macOS (bash):

```bash
export GITHUB_TOKEN="ghp_..."
export CURSOR_API_KEY="key_..."
export DB_USERNAME="root"
export DB_PASSWORD="secret"
```

## 기동

```powershell
# Windows
.\gradlew.bat bootRun
```

```bash
# Linux / macOS
./gradlew bootRun
```

- UI: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

## Cursor Dashboard

Cloud Agent 시작 전 GitHub 계정·대상 repository 연결 필요.  
`GET https://api.cursor.com/v1/repositories`에 repo가 보여야 Agent validation 통과.

## Composer (대형 저장소)

기본 `app.cursor.composer.timeout-ms=120000`(2분). 대형 diff·파일 수가 많으면 여전히 부족할 수 있음:

```powershell
.\gradlew.bat bootRun --args="--app.cursor.composer.timeout-ms=180000 --app.cursor.composer.max-files=10"
```

## 테스트

```powershell
.\gradlew.bat test
```

- 대부분 mock/integration 테스트
- `CloudAgentClientComposerLiveTest` 등 live API 테스트는 `CURSOR_API_KEY` 없으면 **skip** (정상)
- 일부 Windows JGit cleanup WARN은 알려진 flake — [5. 문제 해결](05-troubleshooting.md) 참고

기동·DB 오류는 [5. 문제 해결](05-troubleshooting.md)을 참고하세요.
