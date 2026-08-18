Add-Type -AssemblyName System.Drawing

$srcPath = "C:\Users\vasav\.gemini\antigravity\brain\16293235-9339-4a0d-ad1e-36a4c6d5a65b\.user_uploaded\media_1787073428437.png"
$srcImg = [System.Drawing.Image]::FromFile($srcPath)

$resDir = "C:\Users\vasav\.gemini\antigravity\scratch\WalkieYappie\app\src\main\res"

$sizes = @(
    @{ folder = "mipmap-mdpi"; full = 48; fore = 108 },
    @{ folder = "mipmap-hdpi"; full = 72; fore = 162 },
    @{ folder = "mipmap-xhdpi"; full = 96; fore = 216 },
    @{ folder = "mipmap-xxhdpi"; full = 144; fore = 324 },
    @{ folder = "mipmap-xxxhdpi"; full = 192; fore = 432 }
)

# Helper function to draw centered, padded artwork inside a safe-zone canvas
function CreatePaddedIcon($img, $canvasSize, $scaleRatio = 0.68) {
    $bmp = New-Object System.Drawing.Bitmap($canvasSize, $canvasSize)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.Clear([System.Drawing.Color]::Black)

    $targetW = [int]($canvasSize * $scaleRatio)
    $targetH = [int]($canvasSize * $scaleRatio)

    # Maintain aspect ratio
    if ($img.Width -gt $img.Height) {
        $targetH = [int]($targetW * ($img.Height / $img.Width))
    } else {
        $targetW = [int]($targetH * ($img.Width / $img.Height))
    }

    $posX = [int](($canvasSize - $targetW) / 2)
    $posY = [int](($canvasSize - $targetH) / 2)

    $g.DrawImage($img, $posX, $posY, $targetW, $targetH)
    $g.Dispose()
    return $bmp
}

foreach ($s in $sizes) {
    $targetFolder = Join-Path $resDir $s.folder
    if (-not (Test-Path $targetFolder)) {
        New-Item -ItemType Directory -Path $targetFolder | Out-Null
    }

    # 1. Full launcher icon (48px, 72px, etc. padded to prevent circle mask crop)
    $bmpFull = CreatePaddedIcon $srcImg $s.full 0.78
    $bmpFull.Save((Join-Path $targetFolder "ic_launcher.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmpFull.Save((Join-Path $targetFolder "ic_launcher_round.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmpFull.Dispose()

    # 2. Adaptive Foreground icon (108px, 162px, etc. padded to fit 66% safe zone)
    $bmpFore = CreatePaddedIcon $srcImg $s.fore 0.65
    $bmpFore.Save((Join-Path $targetFolder "ic_launcher_foreground.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmpFore.Dispose()
}

# 3. Also update drawable/ic_launcher.png with safe-zone padding
$drawableFolder = Join-Path $resDir "drawable"
$bmpDrawable = CreatePaddedIcon $srcImg 512 0.75
$bmpDrawable.Save((Join-Path $drawableFolder "ic_launcher.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$bmpDrawable.Dispose()

$srcImg.Dispose()
Write-Host "All padded safe-zone Android launcher icons generated successfully!"
