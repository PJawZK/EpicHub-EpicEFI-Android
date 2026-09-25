param(
    [string]$TestClass = "com.epicefi.epichub.benchmark.EpicHubNavigationBenchmark",
    [string]$Method = "",
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

$adb = Require-Command "adb"
$gradle = Require-Command "gradle"

$deviceLines = @(& $adb devices | Select-String "\tdevice$")
if ($deviceLines.Count -ne 1) {
    throw "Expected exactly one connected Android device in 'device' state; found $($deviceLines.Count)."
}

$serial = (($deviceLines[0].ToString() -split "\s+")[0]).Trim()
$model = (& $adb -s $serial shell getprop ro.product.model).Trim()
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
Write-Host "Target : $selector"
Write-Host ""

& $gradle ":benchmark:connectedPerformanceAndroidTest" "-Pandroid.testInstrumentationRunnerArguments.class=$selector" --stacktrace
if ($LASTEXITCODE -ne 0) {
    throw "Macrobenchmark Gradle task failed with exit code $LASTEXITCODE."
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
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
