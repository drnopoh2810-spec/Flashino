param(
    [switch]$RunGradleSmokeTest
)

$ErrorActionPreference = "Stop"

function Write-Ok($message) { Write-Host "OK   $message" -ForegroundColor Green }
function Write-Warn($message) { Write-Host "WARN $message" -ForegroundColor Yellow }
function Write-Fail($message) { Write-Host "FAIL $message" -ForegroundColor Red }

$failures = 0

Write-Host "EduSpecial environment check" -ForegroundColor Cyan
Write-Host "----------------------------"

if (-not $env:JAVA_HOME) {
    Write-Fail "JAVA_HOME is not set."
    $failures++
} else {
    Write-Ok "JAVA_HOME = $($env:JAVA_HOME)"
}

try {
    $javaVersion = & java -version 2>&1
    $javaText = ($javaVersion | Out-String)
    if ($javaText -match "version `"17") {
        Write-Ok "Java 17 detected."
    } else {
        Write-Warn "Java is installed but version is not clearly 17."
    }
} catch {
    Write-Fail "java command not found in PATH."
    $failures++
}

if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
    Write-Warn "ANDROID_HOME/ANDROID_SDK_ROOT is not set. Android builds may fail."
} else {
    $sdkPath = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
    Write-Ok "Android SDK path = $sdkPath"
}

if (Test-Path ".\gradlew.bat") {
    Write-Ok "gradlew.bat found."
} else {
    Write-Fail "gradlew.bat not found in repository root."
    $failures++
}

if (Test-Path ".\scripts\validate_config_security.py") {
    try {
        Write-Host ""
        Write-Host "Running runtime config security validation..." -ForegroundColor Cyan
        & python .\scripts\validate_config_security.py | Out-Host
        if ($LASTEXITCODE -eq 0) {
            Write-Ok "Runtime config security validation passed."
        } else {
            Write-Fail "Runtime config security validation failed."
            $failures++
        }
    } catch {
        Write-Warn "Python is unavailable, skipped runtime config security validation."
    }
} else {
    Write-Warn "scripts/validate_config_security.py not found."
}

if ($RunGradleSmokeTest) {
    Write-Host ""
    Write-Host "Running Gradle smoke test: .\gradlew.bat -version" -ForegroundColor Cyan
    try {
        & .\gradlew.bat -version | Out-Host
        if ($LASTEXITCODE -eq 0) {
            Write-Ok "Gradle smoke test passed."
        } else {
            Write-Fail "Gradle smoke test failed with exit code $LASTEXITCODE."
            $failures++
        }
    } catch {
        Write-Fail "Gradle smoke test failed: $($_.Exception.Message)"
        $failures++
    }
}

Write-Host ""
if ($failures -gt 0) {
    Write-Fail "Environment check failed with $failures blocking issue(s)."
    exit 1
}

Write-Ok "Environment check passed."
exit 0
