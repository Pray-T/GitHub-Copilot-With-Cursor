# docs/images

포트폴리오 README [Screenshots](../../README.md#screenshots)용 UI 캡처입니다.

| 파일 | 화면 |
|------|------|
| `index.png` | 메인 — URL·프롬프트 입력 + 워크스페이스 목록 |
| `wait.png` | Agent 대기 (완료 상태) |
| `diff.png` | Diff — 변경 파일 목록 |
| `pr.png` | PR 생성/확인 (Contribute) |

재촬영 (앱 기동 후, Windows Edge headless 예시):

```powershell
$edge = "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
$img = "docs/images"
& $edge --headless=new --disable-gpu --window-size=1280,1200 --screenshot="$img/index.png" http://localhost:8080/
```
