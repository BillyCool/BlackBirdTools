# Builds patches/build/libs/patches-*.mpp
# Needs JDK 17+ (Android Studio's bundled JBR works) and the GitHub CLI logged in,
# because Morphe's Gradle plugin is downloaded from GitHub Packages.
$ErrorActionPreference = 'Stop'

if (-not $env:JAVA_HOME) {
    $jbr = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path $jbr) { $env:JAVA_HOME = $jbr }
}
if (-not $env:GITHUB_TOKEN) {
    $env:GITHUB_ACTOR = (gh api user --jq .login)
    $env:GITHUB_TOKEN = (gh auth token)
}

Push-Location $PSScriptRoot
try {
    .\gradlew.bat buildAndroid
    Get-ChildItem patches\build\libs\*.mpp
} finally {
    Pop-Location
}
