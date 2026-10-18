param([ValidateSet('package','test','verify')][string]$Goal='verify',[switch]$PaperTests)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$mavenCommand = Get-Command mvn -ErrorAction SilentlyContinue
$mavenPath = if ($mavenCommand) { $mavenCommand.Source } else { $null }
if (-not $mavenPath) {
    $searchPath = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
    foreach ($entry in $searchPath.Split(';')) {
        if (-not $entry) { continue }
        $candidate = Join-Path ([Environment]::ExpandEnvironmentVariables($entry)) 'mvn.cmd'
        if (Test-Path -LiteralPath $candidate) { $mavenPath = $candidate; break }
    }
}
if (-not $mavenPath) { throw 'Khong tim thay Maven. Hay them Maven/bin vao PATH.' }
Push-Location -LiteralPath $projectPath
try {
    & (Join-Path $PSScriptRoot 'Register-VendoredRepository.ps1')
    $mavenArgs = @('--batch-mode', "-Dmaven.repo.local=$projectPath/build/maven-repository", $Goal)
    if ($PaperTests) { $mavenArgs += '-Ppaper-tests' }
    & $mavenPath @mavenArgs
    if ($LASTEXITCODE -ne 0) { throw 'Maven build/test failed.' }
    # Also support the direct JDK build script and its JUnit runners offline.
    foreach ($item in @('junit/junit/4.13.2/junit-4.13.2.jar','org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar')) {
        $source = Join-Path "$projectPath/build/maven-repository" $item
        if (Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination "$projectPath/lib/" -Force }
    }
} finally { Pop-Location }
