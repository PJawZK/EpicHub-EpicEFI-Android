$ErrorActionPreference = "Stop"

function Require-Command([string]$Name) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $command) {
        throw "Required command '$Name' was not found in PATH."
    }
    return $command.Source
}

$adb = Require-Command "adb"
$deviceLines = @(& $adb devices | Select-String "\tdevice$")
if ($deviceLines.Count -ne 1) {
    throw "Expected exactly one connected Android device in 'device' state; found $($deviceLines.Count)."
}

$serial = (($deviceLines[0].ToString() -split "\s+")[0]).Trim()
$model = (& $adb -s $serial shell getprop ro.product.model).Trim()

Write-Host "EpicHub benchmark uninstall"
Write-Host "Device : $model ($serial)"
Write-Host ""

foreach ($package in @("com.epicefi.epichub.benchmark", "com.epicefi.epichub")) {
    & $adb -s $serial shell pm path $package *> $null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Removing $package"
        & $adb -s $serial uninstall $package
        if ($LASTEXITCODE -ne 0) {
            throw "Could not uninstall $package (adb exit code $LASTEXITCODE)."
        }
    } else {
        Write-Host "$package is not installed"
    }
}

Write-Host ""
Write-Host "EpicHub benchmark packages removed."
