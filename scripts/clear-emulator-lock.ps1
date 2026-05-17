# Удаляет зависшие lock-файлы эмулятора Pixel 6 API 34
$avdDir = "$env:USERPROFILE\.android\avd\Pixel_6_API_34.avd"

if (-not (Test-Path $avdDir)) {
    Write-Error "AVD folder not found: $avdDir"
    exit 1
}

# Завершить зависший эмулятор (если есть)
Get-Process -Name "qemu-system-x86_64","emulator" -ErrorAction SilentlyContinue | Stop-Process -Force

# Удалить lock-файлы
Get-ChildItem $avdDir -Recurse -Filter "*.lock" -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force
Remove-Item "$avdDir\hardware-qemu.ini.lock" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item "$avdDir\multiinstance.lock" -Force -ErrorAction SilentlyContinue

Write-Host "Lock files cleared. Start emulator from Android Studio (Device Manager)."
