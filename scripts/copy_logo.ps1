$src = "$env:USERPROFILE\Downloads\FixerMediaStore.png"
$root = "C:\Users\markv\AndroidStudioProjects\FixerMediaStore"

$destinations = @(
    "$root\app\src\main\res\drawable\app_logo.png",
    "$root\app\src\main\res\drawable\ic_launcher_foreground.png",
    "$root\app\src\main\res\mipmap-xxxhdpi\ic_launcher.png",
    "$root\app\src\main\res\mipmap-xxxhdpi\ic_launcher_round.png"
)

if (-not (Test-Path -LiteralPath $src)) {
    "FAIL: source not found" | Set-Content "$root\logo_copy_status.txt"
    exit 1
}

foreach ($dest in $destinations) {
    $dir = Split-Path -LiteralPath $dest -Parent
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Copy-Item -LiteralPath $src -Destination $dest -Force
}

"OK" | Set-Content "$root\logo_copy_status.txt"
