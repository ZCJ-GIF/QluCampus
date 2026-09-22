# QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
param([string[]]$Tasks = @(':app:assembleDebug'), [switch]$Offline)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'E:\Android\tools\jdk-21'
$env:ANDROID_HOME = 'E:\Android\sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = 'E:\Android\cache\android'
$env:ANDROID_EMULATOR_HOME = $env:ANDROID_USER_HOME
$env:ANDROID_AVD_HOME = 'E:\Android\cache\avd'
$env:GRADLE_USER_HOME = 'E:\Android\cache\gradle'
$env:TEMP = 'E:\Android\cache\tmp'
$env:TMP = $env:TEMP
$env:GRADLE_OPTS = '-Duser.home=E:/Android/cache -Djava.io.tmpdir=E:/Android/cache/tmp'
$proxyUri = [System.Net.WebRequest]::DefaultWebProxy.GetProxy([uri]'https://services.gradle.org/')
if ($proxyUri.Host -ne 'services.gradle.org') {
    $env:GRADLE_OPTS += " -Dhttp.proxyHost=$($proxyUri.Host) -Dhttp.proxyPort=$($proxyUri.Port) -Dhttps.proxyHost=$($proxyUri.Host) -Dhttps.proxyPort=$($proxyUri.Port)"
}
$env:CI = 'false' # 使用基础项目既有镜像与官方仓库回退，适配本机网络。
Push-Location $PSScriptRoot
try {
    $flags = @('--console=plain', '--no-daemon')
    if ($Offline) { $flags += '--offline' }
    $localGradle = 'E:\Android\tools\gradle-9.5.0\bin\gradle.bat'
    if (Test-Path $localGradle) { & $localGradle @Tasks @flags }
    else { & .\gradlew.bat @Tasks @flags }
    exit $LASTEXITCODE
} finally { Pop-Location }
