param([switch]$Test, [switch]$RenderUi, [switch]$SkipPackage)
$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Can JDK 21+. Hay cai JDK va them thu muc bin vao PATH.' }
    # Read stderr directly: Windows PowerShell 5 otherwise treats java -version as a native error.
    $probe = New-Object System.Diagnostics.Process
    $probe.StartInfo.FileName = (Get-Command java).Source
    $probe.StartInfo.Arguments = '-XshowSettings:properties -version'
    $probe.StartInfo.UseShellExecute = $false
    $probe.StartInfo.CreateNoWindow = $true
    $probe.StartInfo.RedirectStandardError = $true
    try {
        [void]$probe.Start()
        $runtimeInfo = $probe.StandardError.ReadToEnd()
        $probe.WaitForExit()
        if ($probe.ExitCode -ne 0) { throw 'Khong doc duoc thong tin JDK.' }
    } finally { $probe.Dispose() }
    $jdkPath = [regex]::Match($runtimeInfo, '(?m)^\s*java\.home\s*=\s*(.+)$').Groups[1].Value.Trim()
    $executableSuffix = if ($env:OS -eq 'Windows_NT') { '.exe' } else { '' }
    $compilerPath = Join-Path $jdkPath ('bin/javac' + $executableSuffix)
    $jarPath = Join-Path $jdkPath ('bin/jar' + $executableSuffix)
    if (-not (Test-Path -LiteralPath $compilerPath)) { throw 'Can JDK 21+ day du (javac), khong chi JRE.' }
    $classesPath = Join-Path $PSScriptRoot 'build/classes'
    $testClassesPath = Join-Path $PSScriptRoot 'build/test-classes'
    $libraryPath = Join-Path $PSScriptRoot 'lib'
    if (-not (Test-Path -LiteralPath (Join-Path $libraryPath 'jpbc-plaf-2.0.0.jar'))) { throw 'Thieu JPBC. Chay pwsh -File tools/Fetch-Jpbc.ps1 truoc.' }
    New-Item -ItemType Directory -Path $classesPath -Force | Out-Null
    $sourceFiles = @(Get-ChildItem -LiteralPath 'src/main/java' -Recurse -File -Filter '*.java' | ForEach-Object FullName)
    & $compilerPath -encoding UTF-8 --release 21 -cp "$libraryPath/*" -d $classesPath @sourceFiles
    if ($LASTEXITCODE -ne 0) { throw 'Bien dich that bai.' }
    $manifestText = "Manifest-Version: 1.0`nMain-Class: vn.healthshare.Main`nClass-Path: ../lib/jpbc-api-2.0.0.jar ../lib/jpbc-plaf-2.0.0.jar`n  ../lib/jpbc-crypto-2.0.0.jar ../lib/bcprov-jdk16-1.46.jar`n`n"
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'build/MANIFEST.MF'),$manifestText,[Text.Encoding]::ASCII)
    if (-not $SkipPackage) {
        & $jarPath --create --file 'build/sp-maacs-simulator.jar' --manifest 'build/MANIFEST.MF' -C $classesPath .
        if ($LASTEXITCODE -ne 0) { throw 'Dong goi JAR that bai. Hay dong ung dung/benchmark dang su dung JAR.' }
    }
    if ($Test -or $RenderUi) {
        New-Item -ItemType Directory -Path $testClassesPath -Force | Out-Null
        $testFiles = @(Get-ChildItem -LiteralPath 'src/test/java' -Recurse -File -Filter '*.java' | ForEach-Object FullName)
        $mainClassPath = $classesPath + [IO.Path]::PathSeparator + "$libraryPath/*"
        & $compilerPath -encoding UTF-8 --release 21 -cp $mainClassPath -d $testClassesPath @testFiles
        if ($LASTEXITCODE -ne 0) { throw 'Bien dich kiem thu that bai.' }
        $testClassPath = $mainClassPath + [IO.Path]::PathSeparator + $testClassesPath
        if ($Test) {
            & java -ea '-Dfile.encoding=UTF-8' '-Djava.awt.headless=true' -cp $testClassPath org.junit.runner.JUnitCore vn.healthshare.CoreRegressionTest vn.healthshare.LsssReconstructionTest vn.healthshare.UiLifecycleTest
            if ($LASTEXITCODE -ne 0) { throw 'Kiem thu that bai.' }
        }
        if ($RenderUi) {
            & java -ea '-Dfile.encoding=UTF-8' '-Djava.awt.headless=true' -cp $testClassPath vn.healthshare.UiSmokeTest
            if ($LASTEXITCODE -ne 0) { throw 'Kiem tra giao dien that bai.' }
        }
    }
    Write-Host 'Build OK: build/sp-maacs-simulator.jar'
    Write-Host 'Chay: java -jar build/sp-maacs-simulator.jar'
} finally { Pop-Location }
