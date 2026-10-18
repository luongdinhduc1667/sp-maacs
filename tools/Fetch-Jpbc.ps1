param([switch]$Force)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$libraryPath = Join-Path $projectPath 'lib'
$sourcePath = Join-Path $projectPath 'tmp/dependencies/jpbc-source'
New-Item -ItemType Directory -Path $libraryPath,$sourcePath -Force | Out-Null
$headers = @{ 'User-Agent' = 'SP-MAACS-Research' }
$commit = (Invoke-RestMethod -Uri 'https://api.github.com/repos/emilianobonassi/jpbc/commits/master' -Headers $headers).sha
$base = 'https://raw.githubusercontent.com/emilianobonassi/jpbc/' + $commit
$jars = @('jpbc-api-2.0.0.jar','jpbc-plaf-2.0.0.jar','jpbc-crypto-2.0.0.jar','jpbc-pbc-2.0.0.jar','bcprov-jdk16-1.46.jar','jna-3.2.5.jar')
foreach ($name in $jars) {
    $targetPath = Join-Path $libraryPath $name
    if ($Force -or -not (Test-Path -LiteralPath $targetPath)) {
        Invoke-WebRequest -Uri "$base/jars/$name" -OutFile $targetPath -TimeoutSec 90
    }
}
Invoke-WebRequest -Uri "$base/license/lgpl-3.0.txt" -OutFile (Join-Path $libraryPath 'JPBC-LGPL-3.0.txt') -TimeoutSec 30
$sources = @(
    'jpbc-plaf/src/main/java/it/unisa/dia/gas/plaf/jpbc/pairing/a1/TypeA1CurveGenerator.java',
    'jpbc-plaf/src/main/java/it/unisa/dia/gas/plaf/jpbc/util/ElementUtils.java',
    'jpbc-crypto/src/main/java/it/unisa/dia/gas/crypto/jpbc/signature/bls01/engines/BLS01Signer.java',
    'jpbc-crypto/src/test/java/it/unisa/dia/gas/crypto/jpbc/signature/bls01/BLS01SignerTest.java',
    'jpbc-crypto/src/main/java/it/unisa/dia/gas/crypto/jpbc/signature/bls01/params/BLS01Parameters.java',
    'jpbc-crypto/src/main/java/it/unisa/dia/gas/crypto/jpbc/signature/bls01/generators/BLS01ParametersGenerator.java'
)
foreach ($path in $sources) {
    Invoke-WebRequest -Uri "$base/$path" -OutFile (Join-Path $sourcePath (Split-Path -Leaf $path)) -TimeoutSec 30
}
$checksums = foreach ($name in $jars) {
    $digest = (Get-FileHash -LiteralPath (Join-Path $libraryPath $name) -Algorithm SHA256).Hash.ToLowerInvariant()
    "$digest  $name"
}
$checksums | Set-Content -LiteralPath (Join-Path $libraryPath 'SHA256SUMS') -Encoding ascii
@{ repository='https://github.com/emilianobonassi/jpbc'; commit=$commit; files=$jars; downloadedUtc=[DateTime]::UtcNow.ToString('o') } |
    ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $libraryPath 'provenance.json') -Encoding utf8
Write-Host "JPBC 2.0.0 downloaded; pinned source commit: $commit"
& (Join-Path $PSScriptRoot 'Register-VendoredRepository.ps1')
