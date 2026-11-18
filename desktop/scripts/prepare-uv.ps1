# Package uv with the Windows installer so Studysh can download a private Python
# 3.12 runtime without relying on any Python installed on the user's computer.
$ErrorActionPreference = "Stop"
$desktop = Split-Path -Parent $PSScriptRoot
$resourceDir = Join-Path $desktop "src-tauri\resources"
$target = Join-Path $resourceDir "uv.exe"
New-Item -ItemType Directory -Path $resourceDir -Force | Out-Null

if ((Test-Path $target) -and ((Get-Item $target).Length -gt 1000000)) {
    Write-Host "Using cached uv.exe for Studysh."
    exit 0
}

# Pinned, official Astral GitHub release and verified digest.
$url = "https://github.com/astral-sh/uv/releases/download/0.13.7/uv-x86_64-pc-windows-msvc.zip"
$sha256 = "0cdc5b38fe5a8168ed6a25814f5ba43c21d313dd3187314fccb37b7cec46e021"
$tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("studysh-uv-" + [guid]::NewGuid().ToString("N"))
$tempZip = Join-Path $tempDir "uv.zip"
$unpacked = Join-Path $tempDir "contents"
try {
    New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
    Write-Host "Downloading uv 0.13.7 for the Windows installer..."
    Invoke-WebRequest -Uri $url -OutFile $tempZip -UseBasicParsing
    $hashAlgorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        $actual = [System.BitConverter]::ToString(
            $hashAlgorithm.ComputeHash([System.IO.File]::ReadAllBytes($tempZip))
        ).Replace("-", "").ToLowerInvariant()
    } finally {
        $hashAlgorithm.Dispose()
    }
    if ($actual -ne $sha256) {
        throw "uv download failed integrity check (SHA256 mismatch)."
    }
    Expand-Archive -Path $tempZip -DestinationPath $unpacked -Force
    $binary = Get-ChildItem -Path $unpacked -Filter "uv.exe" -File -Recurse | Select-Object -First 1
    if (-not $binary) { throw "uv.exe was not found in the verified release archive." }
    Copy-Item $binary.FullName $target -Force
    Write-Host "Studysh bundled uv successfully."
} finally {
    Remove-Item $tempDir -Recurse -Force -ErrorAction SilentlyContinue
}
