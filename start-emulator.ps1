# QluCampus modification, 2026-09-21. Headless local Android test device; all task files on E:.
param([switch]$WaitForBoot, [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$AvdName = 'QluCampusTest35', [ValidateRange(5554,5682)][int]$Port = 5562)
$ErrorActionPreference = 'Stop'
$env:ANDROID_HOME = 'E:\Android\sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = 'E:\Android\cache\android'
$env:ANDROID_EMULATOR_HOME = $env:ANDROID_USER_HOME
$env:ANDROID_AVD_HOME = 'E:\Android\cache\avd'
$env:TEMP = 'E:\Android\cache\tmp'
$env:TMP = $env:TEMP
$service = Get-Service aehd -ErrorAction SilentlyContinue
if ($service -and $service.Status -ne 'Running') { Start-Service aehd }
$adb = 'E:\Android\sdk\platform-tools\adb.exe'
$serial = "emulator-$Port"
if (-not ((& $adb devices) -match "^$serial\s")) {
    Start-Process -FilePath 'E:\Android\sdk\emulator\emulator.exe' -WindowStyle Hidden -ArgumentList @(
        '-avd',$AvdName,'-no-window','-no-audio','-no-boot-anim','-no-snapshot',
        '-gpu','swiftshader','-feature','-Vulkan','-accel','on','-memory','3072','-cores','4',
        '-camera-back','none','-camera-front','none','-port',"$Port",'-no-metrics'
    ) -RedirectStandardOutput 'E:\Android\logs\emulator-accelerated.log' -RedirectStandardError 'E:\Android\logs\emulator-accelerated-error.log' | Out-Null
}
if ($WaitForBoot) {
    $deadline = (Get-Date).AddSeconds(60)
    do {
        $boot = & $adb -s $serial shell getprop sys.boot_completed 2>$null
        if ($boot -eq '1') { Write-Output "$serial boot complete (headless)"; exit 0 }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    Write-Output 'Emulator is still starting; see E:\Android\logs\emulator-accelerated*.log'
} else { Write-Output "Headless emulator requested: $serial ($AvdName)" }
