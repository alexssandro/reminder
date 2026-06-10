# Build the debug APK, install it on a connected device, and cold-start the app with
# the fire_preview hook so the daily-preview notification fires immediately — a quick
# smoke check that notifications still work after each deploy.
#
# Usage: .\deploy.ps1 [-DeviceSerial <serial>]   (serial optional when one device is connected)
param([string]$DeviceSerial)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

# Resolve adb from local.properties' sdk.dir, falling back to the default SDK location.
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$localProps = Join-Path $root 'local.properties'
if (Test-Path $localProps) {
    $match = Select-String -Path $localProps -Pattern '^sdk\.dir=(.+)$' | Select-Object -First 1
    if ($match) {
        # local.properties escapes backslashes and colons (C\:\\Users\\...)
        $sdkDir = $match.Matches[0].Groups[1].Value -replace '\\\\', '\' -replace '\\:', ':'
        $candidate = Join-Path $sdkDir 'platform-tools\adb.exe'
        if (Test-Path $candidate) { $adb = $candidate }
    }
}
if (-not (Test-Path $adb)) { Write-Error "adb not found at $adb" }

& "$root\gradlew.bat" -p $root assembleDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (-not $DeviceSerial) {
    $devices = (& $adb devices) |
        Select-Object -Skip 1 |
        Where-Object { $_ -match "`tdevice$" } |
        ForEach-Object { ($_ -split "`t")[0] }
    if (-not $devices) { Write-Error 'No device connected — check `adb devices`.' }
    $DeviceSerial = @($devices)[0]
}

& $adb -s $DeviceSerial install -r "$root\app\build\outputs\apk\debug\app-debug.apk"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# fire_preview is only read in onCreate, so force a cold start.
& $adb -s $DeviceSerial shell am force-stop com.reminder
& $adb -s $DeviceSerial shell am start -n com.reminder/.MainActivity --ez fire_preview true

Write-Host ''
Write-Host "Deployed to $DeviceSerial and fired the daily-preview notification."
Write-Host '(If everything is already checked off today, the preview is skipped by design.)'
