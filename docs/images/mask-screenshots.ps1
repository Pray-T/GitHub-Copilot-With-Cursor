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

# index.png — workspace table: repo name, .git URL, branch, PR badge
Process-Image "index.png" {
    param($g)
    Mask-Rect $g 62 842 500 185
    Draw-Text $g $demoFull 68 868 12 "#1f2937" $true
    Draw-Text $g "$demoUrl.git" 68 896 9 "#6b7280"
    Draw-Text $g $demoBranch 68 917 9 "#6b7280"
    # PR badge placeholder (non-sensitive)
    $badgeBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml("#dbeafe"))
    $g.FillRectangle($badgeBrush, 68, 948, 36, 22)
    $badgeBrush.Dispose()
    Draw-Text $g "PR" 76 950 9 "#1d4ed8" $true
}

# diff.png — page title with owner/repo
Process-Image "diff.png" {
    param($g)
    Mask-Rect $g 44 142 700 32
    Draw-Text $g "$demoFull - 1 changed file" 48 148 15 "#1f2937" $true
}

# wait.png — repo, upstream .git URL, fork URL, branch, local path
Process-Image "wait.png" {
    param($g)
    Mask-Rect $g 198 626 1060 215
    Draw-Text $g $demoFull 202 634 10.5 "#1f2937"
    Draw-Text $g "$demoUrl.git" 202 664 10 "#1f2937"
    Draw-Text $g $demoUrl 202 696 10 "#2563eb"
    Draw-Text $g $demoBranch 202 728 10 "#1f2937"
    Draw-Text $g $demoPath 202 788 9.5 "#1f2937"
}

# pr.png — repo, fork URL, branch, PR URL
Process-Image "pr.png" {
    param($g)
    Mask-Rect $g 198 228 1060 170
    Draw-Text $g $demoFull 202 236 10.5 "#1f2937"
    Draw-Text $g $demoUrl 202 268 10 "#2563eb"
    Draw-Text $g $demoBranch 202 298 10 "#1f2937"
    Draw-Text $g $demoPrUrl 202 380 10 "#2563eb"
}

Write-Output "Masked portfolio screenshots."
