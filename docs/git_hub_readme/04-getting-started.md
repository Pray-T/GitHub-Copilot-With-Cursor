# 4. 실행 가이드 (v3)

## 사전 요구

- Java 17+, MySQL 8.x, Git, (M1) `cursor` CLI
- GitHub PAT: `repo` / Contents + Pull requests
- Cursor Cloud Agents API key

## 환경변수

```powershell
$env:GITHUB_TOKEN = "ghp_..."
$env:CURSOR_API_KEY = "key_..."
# 선택
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "secret"
```

## 기동

```powershell
.\gradlew.bat bootRun
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
