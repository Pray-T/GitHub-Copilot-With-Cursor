Add-Type -AssemblyName System.Drawing

$imgDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$demoOwner = "demo-owner"
$demoRepo = "sample-app"
$demoFull = "$demoOwner/$demoRepo"
$demoUrl = "https://github.com/$demoOwner/$demoRepo"
$demoBranch = "refactor/sample-app-demo"
$demoPrUrl = "https://github.com/$demoOwner/$demoRepo/pull/1"
$demoPath = "C:\demo\refactor-workspace\$demoOwner\$demoRepo"

function Mask-Rect($graphics, [int]$x, [int]$y, [int]$w, [int]$h, [string]$color = "#ffffff") {
    $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml($color))
    $graphics.FillRectangle($brush, $x, $y, $w, $h)
    $brush.Dispose()
}

function Draw-Text($graphics, [string]$text, [int]$x, [int]$y, [single]$size, [string]$color, [bool]$bold = $false) {
    $style = if ($bold) { [System.Drawing.FontStyle]::Bold } else { [System.Drawing.FontStyle]::Regular }
    $font = New-Object System.Drawing.Font("Malgun Gothic", $size, $style)
    $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml($color))
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $graphics.DrawString($text, $font, $brush, [single]$x, [single]$y)
    $font.Dispose(); $brush.Dispose()
}

function Process-Image([string]$fileName, [scriptblock]$draw) {
    $path = Join-Path $imgDir $fileName
    $tempPath = Join-Path $imgDir (".tmp-" + $fileName)
    $source = [System.Drawing.Image]::FromFile($path)
    $bitmap = New-Object System.Drawing.Bitmap $source
    $source.Dispose()
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    & $draw $graphics
    $graphics.Dispose()
    $bitmap.Save($tempPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
    Move-Item -Path $tempPath -Destination $path -Force
}

Set-Location (Split-Path $imgDir -Parent | Split-Path -Parent)
git checkout 1d41dcd -- docs/images/index.png docs/images/wait.png docs/images/diff.png docs/images/pr.png | Out-Null

Process-Image "index.png" {
    param($g)
    $bg = "#f5f7fa"
    Mask-Rect $g 230 860 252 78 $bg
    Mask-Rect $g 230 954 252 44 $bg
    Draw-Text $g $demoFull 242 868 11.5 "#1f2937" $true
    Draw-Text $g "$demoUrl.git" 244 896 9 "#6b7280"
    Draw-Text $g "branch: $demoBranch" 244 917 9 "#6b7280"
}

Process-Image "diff.png" {
    param($g)
    Mask-Rect $g 48 143 420 30 "#ffffff"
    Draw-Text $g "$demoFull - 1 changed file" 52 148 15 "#1f2937" $true
}

Process-Image "wait.png" {
    param($g)
    $bg = "#ffffff"
    $pill = "#eef2ff"
    Mask-Rect $g 358 628 288 20 $bg
    Mask-Rect $g 348 686 455 26 $bg
    Mask-Rect $g 348 716 435 24 $bg
    Mask-Rect $g 385 768 410 26 $pill
    Mask-Rect $g 385 784 410 22 $pill
    Mask-Rect $g 385 812 575 30 $pill
    Draw-Text $g $demoFull 362 634 10.5 "#1f2937"
    Draw-Text $g "$demoUrl.git" 352 692 10 "#1f2937"
    Draw-Text $g $demoUrl 352 722 10 "#2563eb"
    Draw-Text $g $demoBranch 392 772 10 "#1f2937"
    Draw-Text $g $demoPath 392 788 9.5 "#1f2937"
}

Process-Image "pr.png" {
    param($g)
    $bg = "#ffffff"
    $pill = "#eef2ff"
    Mask-Rect $g 332 224 288 28 $bg
    Mask-Rect $g 325 256 420 28 $bg
    Mask-Rect $g 278 286 478 28 $pill
    Mask-Rect $g 325 316 468 28 $bg
    Draw-Text $g $demoFull 354 236 10.5 "#1f2937"
    Draw-Text $g $demoUrl 330 266 10 "#2563eb"
    Draw-Text $g $demoBranch 290 296 10 "#1f2937"
    Draw-Text $g $demoPrUrl 330 326 10 "#2563eb"
}

Write-Output "Masked portfolio screenshots (precision mode v6)."
