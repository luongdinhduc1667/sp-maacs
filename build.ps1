param([switch]$Test, [switch]$RenderUi)
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
    New-Item -ItemType Directory -Path $classesPath -Force | Out-Null
    $sourceFiles = @(Get-ChildItem -LiteralPath 'src/main/java' -Recurse -File -Filter '*.java' | ForEach-Object FullName)
    & $compilerPath -encoding UTF-8 --release 21 -d $classesPath @sourceFiles
    if ($LASTEXITCODE -ne 0) { throw 'Bien dich that bai.' }
    & $jarPath --create --file 'build/sp-maacs-simulator.jar' --main-class vn.healthshare.Main -C $classesPath .
    if ($LASTEXITCODE -ne 0) { throw 'Dong goi JAR that bai.' }
    if ($Test -or $RenderUi) {
        New-Item -ItemType Directory -Path $testClassesPath -Force | Out-Null
        $testFiles = @(Get-ChildItem -LiteralPath 'src/test/java' -Recurse -File -Filter '*.java' | ForEach-Object FullName)
        & $compilerPath -encoding UTF-8 --release 21 -cp $classesPath -d $testClassesPath @testFiles
        if ($LASTEXITCODE -ne 0) { throw 'Bien dich kiem thu that bai.' }
        $testClassPath = $classesPath + [IO.Path]::PathSeparator + $testClassesPath
        if ($Test) {
            & java -ea '-Dfile.encoding=UTF-8' '-Djava.awt.headless=true' -cp $testClassPath vn.healthshare.SelfTest
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
