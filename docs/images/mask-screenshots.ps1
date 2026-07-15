Add-Type -AssemblyName System.Drawing

$imgDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$demoOwner = "demo-owner"
$demoRepo = "sample-app"
$demoFull = "$demoOwner/$demoRepo"
$demoUrl = "https://github.com/$demoOwner/$demoRepo"
$demoBranch = "refactor/sample-app-demo"
$demoPrUrl = "https://github.com/$demoOwner/$demoRepo/pull/1"
$demoPath = "C:\demo\refactor-workspace\$demoOwner\$demoRepo"

function Mask-Rect($graphics, [int]$x, [int]$y, [int]$w, [int]$h) {
    $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
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

# Restore originals before masking
Set-Location (Split-Path $imgDir -Parent | Split-Path -Parent)
git checkout 1d41dcd -- docs/images/index.png docs/images/wait.png docs/images/diff.png docs/images/pr.png | Out-Null

Process-Image "index.png" {
    param($g)
    Mask-Rect $g 78 878 760 88
    Draw-Text $g $demoFull 82 884 12 "#1f2937" $true
    Draw-Text $g "branch: $demoBranch" 82 918 9.5 "#6b7280"
}

Process-Image "diff.png" {
    param($g)
    Mask-Rect $g 48 86 1220 78
    Draw-Text $g "$demoFull - 1 changed file" 52 98 15 "#1f2937" $true
}

Process-Image "wait.png" {
    param($g)
    Mask-Rect $g 168 618 1080 220
    Draw-Text $g $demoFull 172 624 10.5 "#1f2937"
    Draw-Text $g "$demoUrl.git" 172 664 10 "#1f2937"
    Draw-Text $g $demoUrl 172 704 10 "#2563eb"
    Draw-Text $g $demoBranch 172 744 10 "#1f2937"
    Draw-Text $g $demoPath 172 784 9.5 "#1f2937"
}

Process-Image "pr.png" {
    param($g)
    Mask-Rect $g 168 182 1080 260
    Draw-Text $g $demoFull 172 190 10.5 "#1f2937"
    Draw-Text $g $demoUrl 172 240 10 "#2563eb"
    Draw-Text $g $demoBranch 172 310 10 "#1f2937"
    Draw-Text $g $demoPrUrl 172 380 10 "#2563eb"
}

Write-Output "Masked portfolio screenshots."
