Add-Type -AssemblyName System.Drawing

$imgDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$demoOwner = "demo-owner"
$demoRepo = "sample-app"
$demoFull = "$demoOwner/$demoRepo"
$demoUrl = "https://github.com/$demoOwner/$demoRepo"
$demoBranch = "refactor/sample-app-demo"
$demoPrUrl = "https://github.com/$demoOwner/$demoRepo/pull/1"
$demoPath = "C:\demo\refactor-workspace\$demoOwner\$demoRepo"

function Pixelate-Region(
    [System.Drawing.Bitmap]$bmp,
    [int]$rx, [int]$ry, [int]$rw, [int]$rh,
    [int]$blockSize
) {
    $xEnd = [Math]::Min($rx + $rw, $bmp.Width)
    $yEnd = [Math]::Min($ry + $rh, $bmp.Height)
    for ($by = $ry; $by -lt $yEnd; $by += $blockSize) {
        $bh = [Math]::Min($blockSize, $yEnd - $by)
        for ($bx = $rx; $bx -lt $xEnd; $bx += $blockSize) {
            $bw = [Math]::Min($blockSize, $xEnd - $bx)
            $r = 0; $g = 0; $b = 0; $n = 0
            for ($y = $by; $y -lt $by + $bh; $y++) {
                for ($x = $bx; $x -lt $bx + $bw; $x++) {
                    $c = $bmp.GetPixel($x, $y)
                    $r += $c.R; $g += $c.G; $b += $c.B; $n++
                }
            }
            if ($n -eq 0) { continue }
            $avg = [System.Drawing.Color]::FromArgb($r / $n, $g / $n, $b / $n)
            for ($y = $by; $y -lt $by + $bh; $y++) {
                for ($x = $bx; $x -lt $bx + $bw; $x++) {
                    $bmp.SetPixel($x, $y, $avg)
                }
            }
        }
    }
}

function Blur-Region(
    [System.Drawing.Bitmap]$bmp,
    [int]$rx, [int]$ry, [int]$rw, [int]$rh
) {
    Pixelate-Region $bmp $rx $ry $rw $rh 20
    Pixelate-Region $bmp $rx $ry $rw $rh 12
    Pixelate-Region $bmp $rx $ry $rw $rh 8
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
    & $draw $bitmap
    $bitmap.Save($tempPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
    Move-Item -Path $tempPath -Destination $path -Force
}

Set-Location (Split-Path $imgDir -Parent | Split-Path -Parent)
git checkout 1d41dcd -- docs/images/index.png docs/images/wait.png docs/images/diff.png docs/images/pr.png | Out-Null

# index.png — repo column; PR badge ~y940-958 untouched
Process-Image "index.png" {
    param($bmp)
    Blur-Region $bmp 208 846 288 88
    Blur-Region $bmp 208 940 288 64
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    Draw-Text $g $demoFull 242 868 11.5 "#1f2937" $true
    Draw-Text $g "$demoUrl.git" 244 896 9 "#6b7280"
    Draw-Text $g "branch: $demoBranch" 244 917 9 "#6b7280"
    $g.Dispose()
}

Process-Image "diff.png" {
    param($bmp)
    Blur-Region $bmp 40 134 520 48
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    Draw-Text $g "$demoFull - 1 changed file" 52 148 15 "#1f2937" $true
    $g.Dispose()
}

# wait.png — value cells; labels (x<248) and PR_OPENED badge preserved
Process-Image "wait.png" {
    param($bmp)
    Blur-Region $bmp 346 618 325 36
    Blur-Region $bmp 338 670 485 44
    Blur-Region $bmp 338 700 475 42
    Blur-Region $bmp 338 748 485 46
    Blur-Region $bmp 338 774 635 36
    Blur-Region $bmp 248 802 710 44
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    Draw-Text $g $demoFull 362 634 10.5 "#1f2937"
    Draw-Text $g "$demoUrl.git" 352 688 10 "#1f2937"
    Draw-Text $g $demoUrl 352 718 10 "#2563eb"
    Draw-Text $g $demoBranch 392 766 10 "#1f2937"
    Draw-Text $g $demoPath 392 784 9.5 "#1f2937"
    $g.Dispose()
}

# pr.png — value cells; bottom buttons y>=360 preserved
Process-Image "pr.png" {
    param($bmp)
    Blur-Region $bmp 328 212 305 44
    Blur-Region $bmp 328 242 435 44
    Blur-Region $bmp 328 272 465 46
    Blur-Region $bmp 328 302 455 48
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    Draw-Text $g $demoFull 354 236 10.5 "#1f2937"
    Draw-Text $g $demoUrl 330 264 10 "#2563eb"
    Draw-Text $g $demoBranch 290 296 10 "#1f2937"
    Draw-Text $g $demoPrUrl 330 324 10 "#2563eb"
    $g.Dispose()
}

Write-Output "Masked portfolio screenshots (blur mode)."
