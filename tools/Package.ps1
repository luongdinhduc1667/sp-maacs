$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$outputPath = Join-Path $projectPath 'output/sp-maacs-jpbc-2.0.1.zip'
$files = @()
foreach ($directory in @('src','docs','tools','lib')) {
    $files += Get-ChildItem -LiteralPath (Join-Path $projectPath $directory) -Recurse -File
}
$explicitFiles = @(
    'README.md','pom.xml','build.ps1','run.bat','.gitignore','build/sp-maacs-simulator.jar',
    'output/paper-figure3-517.csv','output/paper-figure3-517.csv.raw.csv',
    'output/paper-figure3-517.csv.metadata.json','output/paper-figure3-517.csv.png',
    'output/maven-verification.log','output/ui-verification.log','output/paper-demo-run.txt',
    'output/ui-lock-fix-verification.log','output/visible-ui-click-verification.log',
    'output/screenshots/08-visible-startup.png','output/screenshots/09-visible-cancelled.png',
    'output/paper-demo/cloud-EHR-001.json'
)
foreach ($item in $explicitFiles) { $files += Get-Item -LiteralPath (Join-Path $projectPath $item) }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$stream = [IO.File]::Open($outputPath,[IO.FileMode]::Create)
try {
    $archive = [IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Create,$true)
    try {
        foreach ($file in $files) {
            $relative = [IO.Path]::GetRelativePath($projectPath,$file.FullName).Replace('\','/')
            if ($relative.StartsWith('../') -or $relative.StartsWith('build/private/')) { throw "Path outside package scope: $relative" }
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,$relative) | Out-Null
        }
    } finally { $archive.Dispose() }
} finally { $stream.Dispose() }
Write-Host "Package: $outputPath ($($files.Count) files; no private setup parameters)"
