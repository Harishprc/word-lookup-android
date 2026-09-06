# Boots one of round 8's preview AVDs and waits for it to be ready.
# Usage: .\tools\emulator.ps1 [wl_phone|wl_small|wl_tall|wl_tablet]
# Defaults to wl_phone (the S24-ish baseline). One shared system image
# (system-images;android-35;google_apis;x86_64) backs all four - see
# CLAUDE.md's "Round 8" section for why these four and not more.
param(
    [string]$Avd = "wl_phone"
)
$ErrorActionPreference = "Stop"
Set-Location "$PSScriptRoot\.."
. .\tools\env.ps1

$known = & avdmanager.bat list avd 2>$null | Select-String "Name: $Avd$"
if (-not $known) {
    Write-Error "No AVD named '$Avd'. Known AVDs: wl_small wl_phone wl_tall wl_tablet"
    exit 1
}

$running = & adb devices | Select-String "emulator.*device$"
if (-not $running) {
    Write-Host "Booting $Avd..."
    Start-Process -FilePath "emulator.exe" -ArgumentList "-avd", $Avd, "-no-snapshot-load" -NoNewWindow
    & adb wait-for-device
    do {
        Start-Sleep -Seconds 2
        $booted = (& adb shell getprop sys.boot_completed 2>$null).Trim()
    } while ($booted -ne "1")
    Write-Host "$Avd is booted."
} else {
    Write-Host "An emulator is already running."
}

$apk = "app\build\outputs\apk\release\app-release.apk"
if (Test-Path $apk) {
    & adb install -r $apk
}
