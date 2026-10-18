$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
foreach ($artifact in @('jpbc-api','jpbc-plaf','jpbc-crypto','jpbc-pbc','bcprov-jdk16','jna')) {
    $group = if ($artifact -like 'jpbc-*') { 'it.unisa.dia.gas' } elseif ($artifact -eq 'jna') { 'net.java.dev.jna' } else { 'org.bouncycastle' }
    $version = if ($artifact -like 'jpbc-*') { '2.0.0' } elseif ($artifact -eq 'jna') { '3.2.5' } else { '1.46' }
    $directory = Join-Path "$projectPath/lib/maven" ($group.Replace('.','/') + "/$artifact/$version")
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    Copy-Item -LiteralPath "$projectPath/lib/$artifact-$version.jar" -Destination $directory -Force
    $pom = "<project xmlns=`"http://maven.apache.org/POM/4.0.0`"><modelVersion>4.0.0</modelVersion><groupId>$group</groupId><artifactId>$artifact</artifactId><version>$version</version><description>Unmodified vendored research dependency; see lib/provenance.json and docs/THIRD_PARTY.md</description></project>"
    [IO.File]::WriteAllText((Join-Path $directory "$artifact-$version.pom"),$pom,[Text.Encoding]::UTF8)
    foreach ($file in Get-ChildItem -LiteralPath $directory -File | Where-Object { $_.Extension -in '.jar','.pom' }) {
        $digest = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA1).Hash.ToLowerInvariant()
        [IO.File]::WriteAllText(($file.FullName + '.sha1'),$digest,[Text.Encoding]::ASCII)
    }
}
