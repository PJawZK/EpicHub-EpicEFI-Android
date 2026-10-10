param(
    [string]$TestClass = "com.epicefi.epichub.benchmark.EpicHubNavigationBenchmark",
    [string]$Method = "",
    [string]$AppApk = "",
    [string]$BenchmarkApk = "",
    [string]$OutputDirectory = "",
    [switch]$AllowEmulator
)

$ErrorActionPreference = "Stop"

function Require-Command([string]$Name) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $command) {
        throw "Required command '$Name' was not found in PATH."
    }
    return $command.Source
}

function Invoke-Adb([string[]]$Arguments, [string]$FailureMessage) {
    & $script:adb @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FailureMessage (adb exit code $LASTEXITCODE)."
    }
}

function Resolve-OptionalPath([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return ""
    }
    return (Resolve-Path $Value).Path
}

function Get-AdbText([string[]]$Arguments) {
    return ((& $script:adb -s $script:serial @Arguments 2>$null) -join "`n").Trim()
}

$adb = Require-Command "adb"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

if ([string]::IsNullOrWhiteSpace($AppApk)) {
    $candidate = Join-Path $scriptRoot "EpicHub-Performance.apk"
    if (Test-Path $candidate) { $AppApk = $candidate }
}
if ([string]::IsNullOrWhiteSpace($BenchmarkApk)) {
    $candidate = Join-Path $scriptRoot "EpicHub-Macrobenchmark.apk"
    if (Test-Path $candidate) { $BenchmarkApk = $candidate }
}

$AppApk = Resolve-OptionalPath $AppApk
$BenchmarkApk = Resolve-OptionalPath $BenchmarkApk
$prebuiltMode = -not [string]::IsNullOrWhiteSpace($AppApk) -or -not [string]::IsNullOrWhiteSpace($BenchmarkApk)
if ($prebuiltMode -and ([string]::IsNullOrWhiteSpace($AppApk) -or [string]::IsNullOrWhiteSpace($BenchmarkApk))) {
    throw "Prebuilt mode requires both -AppApk and -BenchmarkApk (or both CI-kit APKs beside this script)."
}

$deviceLines = @(& $adb devices | Select-String "\tdevice$")
if ($deviceLines.Count -ne 1) {
    throw "Expected exactly one connected Android device in 'device' state; found $($deviceLines.Count)."
}

$serial = (($deviceLines[0].ToString() -split "\s+")[0]).Trim()
$script:serial = $serial
$model = (Get-AdbText @("shell", "getprop", "ro.product.model"))
$apiLevel = (Get-AdbText @("shell", "getprop", "ro.build.version.sdk"))
$fingerprint = (Get-AdbText @("shell", "getprop", "ro.build.fingerprint"))
$qemu = (Get-AdbText @("shell", "getprop", "ro.boot.qemu"))
$deviceAbis = (Get-AdbText @("shell", "getprop", "ro.product.cpu.abilist"))

if (($qemu -eq "1") -and (-not $AllowEmulator)) {
    throw "Refusing to run performance qualification on emulator '$model'. Use -AllowEmulator only for harness debugging, not performance acceptance."
}

$selector = $TestClass
if (-not [string]::IsNullOrWhiteSpace($Method)) {
    $selector = "$TestClass#$Method"
}

Write-Host "EpicHub Macrobenchmark"
Write-Host "Device : $model ($serial)"
Write-Host "API    : $apiLevel"
Write-Host "Target : $selector"
Write-Host "Mode   : $(if ($prebuiltMode) { 'qualified prebuilt APKs' } else { 'Gradle connected test' })"
Write-Host ""

if (-not $prebuiltMode) {
    $gradle = Require-Command "gradle"
    & $gradle ":benchmark:connectedPerformanceAndroidTest" "-Pandroid.testInstrumentationRunnerArguments.class=$selector" --stacktrace
    if ($LASTEXITCODE -ne 0) {
        throw "Macrobenchmark Gradle task failed with exit code $LASTEXITCODE."
    }
    exit 0
}

$appHash = (Get-FileHash -Algorithm SHA256 $AppApk).Hash.ToLowerInvariant()
$benchmarkHash = (Get-FileHash -Algorithm SHA256 $BenchmarkApk).Hash.ToLowerInvariant()

Write-Host "App APK       : $AppApk"
Write-Host "App SHA-256   : $appHash"
Write-Host "Benchmark APK : $BenchmarkApk"
Write-Host "Bench SHA-256 : $benchmarkHash"
Write-Host ""

Invoke-Adb @("-s", $serial, "install", "-r", "-t", $AppApk) "Could not install EpicHub performance APK"
Invoke-Adb @("-s", $serial, "install", "-r", "-t", $BenchmarkApk) "Could not install EpicHub Macrobenchmark APK"

$packageDump = Get-AdbText @("shell", "dumpsys", "package", "com.epicefi.epichub")
$appAbiMatch = [regex]::Match($packageDump, "(?m)^\s*primaryCpuAbi=(.+)$")
$appAbi = if ($appAbiMatch.Success) { $appAbiMatch.Groups[1].Value.Trim() } else { "unknown" }

$wmSize = Get-AdbText @("shell", "wm", "size")
$wmDensity = Get-AdbText @("shell", "wm", "density")
$physicalSize = [regex]::Match($wmSize, "Physical size:\s*(\d+x\d+)").Groups[1].Value
$overrideSize = [regex]::Match($wmSize, "Override size:\s*(\d+x\d+)").Groups[1].Value
$displaySize = if ($overrideSize) { $overrideSize } else { $physicalSize }
$physicalDensity = [regex]::Match($wmDensity, "Physical density:\s*(\d+)").Groups[1].Value
$overrideDensity = [regex]::Match($wmDensity, "Override density:\s*(\d+)").Groups[1].Value
$displayDensity = if ($overrideDensity) { $overrideDensity } else { $physicalDensity }

$displayWidthDp = "unknown"
$displayHeightDp = "unknown"
$orientation = "unknown"
$sizeMatch = [regex]::Match($displaySize, "^(\d+)x(\d+)$")
if ($sizeMatch.Success) {
    $widthPx = [int]$sizeMatch.Groups[1].Value
    $heightPx = [int]$sizeMatch.Groups[2].Value
    $orientation = if ($widthPx -gt $heightPx) { "landscape" } else { "portrait" }
    if ($displayDensity -match "^\d+$" -and [int]$displayDensity -gt 0) {
        $displayWidthDp = [math]::Floor($widthPx * 160 / [int]$displayDensity)
        $displayHeightDp = [math]::Floor($heightPx * 160 / [int]$displayDensity)
    }
}

$displayDump = Get-AdbText @("shell", "dumpsys", "display")
$refreshMatch = [regex]::Match($displayDump, "refreshRate=([0-9.]+)")
$refreshRate = if ($refreshMatch.Success) { $refreshMatch.Groups[1].Value } else { "unknown" }

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $scriptRoot "benchmark-results\$timestamp"
}
$OutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$deviceOutput = "/sdcard/Android/media/com.epicefi.epichub.benchmark/epichub-benchmark"
& $adb -s $serial shell rm -rf $deviceOutput | Out-Null
& $adb -s $serial shell mkdir -p $deviceOutput | Out-Null

$manifestPath = Join-Path $OutputDirectory "benchmark-run-manifest.txt"
$runManifest = @(
    "EpicHub physical Macrobenchmark",
    "timestamp=$timestamp",
    "deviceModel=$model",
    "apiLevel=$apiLevel",
    "buildFingerprint=$fingerprint",
    "deviceAbis=$deviceAbis",
    "selectedAppAbi=$appAbi",
    "displaySizePx=$displaySize",
    "displayDensityDpi=$displayDensity",
    "displayWidthDpApprox=$displayWidthDp",
    "displayHeightDpApprox=$displayHeightDp",
    "orientation=$orientation",
    "refreshRateHz=$refreshRate",
    "cpuLockRequestedByRunner=false",
    "sustainedPerformanceRequestedByRunner=false",
    "selector=$selector",
    "appApkSha256=$appHash",
    "benchmarkApkSha256=$benchmarkHash"
)
$runManifest | Set-Content -Encoding utf8 $manifestPath

$instrumentation = "com.epicefi.epichub.benchmark/androidx.test.runner.AndroidJUnitRunner"
$logPath = Join-Path $OutputDirectory "instrumentation-output.txt"

& $adb -s $serial logcat -c | Out-Null

$instrumentArgs = @(
    "-s", $serial,
    "shell", "am", "instrument", "-w", "-r",
    "-e", "androidx.benchmark.output.enable", "true",
    "-e", "additionalTestOutputDir", $deviceOutput,
    "-e", "class", $selector,
    $instrumentation
)

Write-Host "Running benchmark. Keep the device connected, awake and unlocked."
Write-Host ""
$instrumentOutput = & $adb @instrumentArgs 2>&1 | Tee-Object -FilePath $logPath
$instrumentExit = $LASTEXITCODE
$benchmarkFailed = $instrumentExit -ne 0 -or ($instrumentOutput -match "INSTRUMENTATION_FAILED|FAILURES!!!|Process crashed")

& $adb -s $serial shell dumpsys gfxinfo com.epicefi.epichub framestats *> (Join-Path $OutputDirectory "gfxinfo-framestats.txt")
& $adb -s $serial shell dumpsys activity activities *> (Join-Path $OutputDirectory "activity-state.txt")
& $adb -s $serial shell dumpsys window windows *> (Join-Path $OutputDirectory "window-state.txt")
& $adb -s $serial logcat -d -v threadtime *> (Join-Path $OutputDirectory "device-logcat.txt")

$uiDumpDevice = "/sdcard/epichub-benchmark-window.xml"
& $adb -s $serial shell uiautomator dump $uiDumpDevice *> $null
if ($LASTEXITCODE -eq 0) {
    & $adb -s $serial pull $uiDumpDevice (Join-Path $OutputDirectory "ui-hierarchy.xml") *> $null
    & $adb -s $serial shell rm -f $uiDumpDevice *> $null
}

$pulledRoot = Join-Path $OutputDirectory "device-output"
New-Item -ItemType Directory -Force -Path $pulledRoot | Out-Null
& $adb -s $serial pull $deviceOutput $pulledRoot *> $null

$files = @(Get-ChildItem $OutputDirectory -Recurse -File |
    Where-Object { $_.Name -like "*benchmarkData.json" -or $_.Extension -in @(".trace", ".perfetto-trace") })

$benchmarkJson = @(Get-ChildItem $OutputDirectory -Recurse -File | Where-Object { $_.Name -like "*benchmarkData.json" } | Select-Object -First 1)
$cpuLocked = "unknown"
$sustainedMode = "unknown"
if ($benchmarkJson.Count -gt 0) {
    $jsonText = Get-Content -Raw $benchmarkJson[0].FullName
    $cpuMatch = [regex]::Match($jsonText, '"cpuLocked"\s*:\s*(true|false)')
    $sustainedMatch = [regex]::Match($jsonText, '"sustainedPerformanceModeEnabled"\s*:\s*(true|false)')
    if ($cpuMatch.Success) { $cpuLocked = $cpuMatch.Groups[1].Value }
    if ($sustainedMatch.Success) { $sustainedMode = $sustainedMatch.Groups[1].Value }
}
Add-Content -Encoding utf8 $manifestPath "macrobenchmarkCpuLocked=$cpuLocked"
Add-Content -Encoding utf8 $manifestPath "macrobenchmarkSustainedPerformanceModeEnabled=$sustainedMode"

if ($files.Count -eq 0) {
    $benchmarkFailed = $true
}

$zipPath = Join-Path $scriptRoot "EpicHub-Benchmark-$timestamp.zip"
if (Test-Path $zipPath) { Remove-Item -Force $zipPath }
Compress-Archive -Path (Join-Path $OutputDirectory "*") -DestinationPath $zipPath -CompressionLevel Optimal

if ($benchmarkFailed) {
    Write-Error "Macrobenchmark failed, but failure evidence was preserved. Upload: $zipPath"
    exit 1
}

Write-Host ""
Write-Host "Benchmark completed successfully."
Write-Host "Result files:"
$files | Sort-Object LastWriteTime -Descending | Select-Object FullName, Length, LastWriteTime | Format-Table -AutoSize
Write-Host ""
Write-Host "Upload this ZIP for analysis:"
Write-Host $zipPath
