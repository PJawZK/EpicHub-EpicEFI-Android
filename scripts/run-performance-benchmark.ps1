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

$adb = Require-Command "adb"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

# A CI benchmark kit places the qualified APKs beside this script. Auto-detect them so the
# physical run can use the exact CI artifacts without rebuilding anything locally.
if ([string]::IsNullOrWhiteSpace($AppApk)) {
    $candidate = Join-Path $scriptRoot "EpicHub-Performance.apk"
    if (Test-Path $candidate) {
        $AppApk = $candidate
    }
}
if ([string]::IsNullOrWhiteSpace($BenchmarkApk)) {
    $candidate = Join-Path $scriptRoot "EpicHub-Macrobenchmark.apk"
    if (Test-Path $candidate) {
        $BenchmarkApk = $candidate
    }
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
$model = (& $adb -s $serial shell getprop ro.product.model).Trim()
$apiLevel = (& $adb -s $serial shell getprop ro.build.version.sdk).Trim()
$fingerprint = (& $adb -s $serial shell getprop ro.build.fingerprint).Trim()
$qemu = (& $adb -s $serial shell getprop ro.boot.qemu).Trim()

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

    $repoRoot = Resolve-Path (Join-Path $scriptRoot "..")
    $outputRoot = Join-Path $repoRoot "benchmark\build\outputs"

    Write-Host ""
    Write-Host "Benchmark completed successfully."
    Write-Host "Searchable result artifacts:"
    if (Test-Path $outputRoot) {
        Get-ChildItem $outputRoot -Recurse -File |
            Where-Object { $_.Extension -in @(".json", ".trace", ".perfetto-trace") } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 30 FullName, Length, LastWriteTime |
            Format-Table -AutoSize
    } else {
        Write-Host "No benchmark/build/outputs directory was found. Check Gradle instrumentation output above."
    }
    exit 0
}

# Prebuilt mode: install exactly the APK pair emitted by CI, invoke instrumentation directly,
# and pull all benchmark JSON / Perfetto evidence into one uploadable ZIP.
$appHash = (Get-FileHash -Algorithm SHA256 $AppApk).Hash.ToLowerInvariant()
$benchmarkHash = (Get-FileHash -Algorithm SHA256 $BenchmarkApk).Hash.ToLowerInvariant()

Write-Host "App APK       : $AppApk"
Write-Host "App SHA-256   : $appHash"
Write-Host "Benchmark APK : $BenchmarkApk"
Write-Host "Bench SHA-256 : $benchmarkHash"
Write-Host ""

Invoke-Adb @("-s", $serial, "install", "-r", "-t", $AppApk) "Could not install EpicHub performance APK"
Invoke-Adb @("-s", $serial, "install", "-r", "-t", $BenchmarkApk) "Could not install EpicHub Macrobenchmark APK"

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $scriptRoot "benchmark-results\$timestamp"
}
$OutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$deviceOutput = "/sdcard/Android/media/com.epicefi.epichub.benchmark/epichub-benchmark"
# This is the benchmark package's app-specific media directory, so it remains writable without
# broad storage permissions on modern Android.
& $adb -s $serial shell rm -rf $deviceOutput | Out-Null
& $adb -s $serial shell mkdir -p $deviceOutput | Out-Null

$runManifest = @(
    "EpicHub physical Macrobenchmark",
    "timestamp=$timestamp",
    "deviceModel=$model",
    "apiLevel=$apiLevel",
    "buildFingerprint=$fingerprint",
    "selector=$selector",
    "appApkSha256=$appHash",
    "benchmarkApkSha256=$benchmarkHash"
)
$runManifest | Set-Content -Encoding utf8 (Join-Path $OutputDirectory "benchmark-run-manifest.txt")

$instrumentation = "com.epicefi.epichub.benchmark/androidx.test.runner.AndroidJUnitRunner"
$logPath = Join-Path $OutputDirectory "instrumentation-output.txt"
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
if ($instrumentExit -ne 0 -or ($instrumentOutput -match "INSTRUMENTATION_FAILED|FAILURES!!!|Process crashed")) {
    throw "Macrobenchmark instrumentation failed. See $logPath"
}

$pulledRoot = Join-Path $OutputDirectory "device-output"
New-Item -ItemType Directory -Force -Path $pulledRoot | Out-Null
Invoke-Adb @("-s", $serial, "pull", $deviceOutput, $pulledRoot) "Could not pull benchmark result files"

$files = @(Get-ChildItem $OutputDirectory -Recurse -File |
    Where-Object { $_.Name -like "*benchmarkData.json" -or $_.Extension -in @(".trace", ".perfetto-trace") })
if ($files.Count -eq 0) {
    throw "Benchmark finished but no JSON/Perfetto result files were pulled from $deviceOutput."
}

$zipPath = Join-Path $scriptRoot "EpicHub-Benchmark-$timestamp.zip"
if (Test-Path $zipPath) {
    Remove-Item -Force $zipPath
}
Compress-Archive -Path (Join-Path $OutputDirectory "*") -DestinationPath $zipPath -CompressionLevel Optimal

Write-Host ""
Write-Host "Benchmark completed successfully."
Write-Host "Result files:"
$files |
    Sort-Object LastWriteTime -Descending |
    Select-Object FullName, Length, LastWriteTime |
    Format-Table -AutoSize
Write-Host ""
Write-Host "Upload this ZIP for analysis:"
Write-Host $zipPath
