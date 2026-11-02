param(
    [string]$Script = 'samples/kotlin/chapter_01.se.kts',
    [string]$StoryId = 'smoke:chapter_01'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$archive = Join-Path $projectRoot 'build/libs/storyengine-0.1.0-SNAPSHOT.jar'
$destination = Join-Path $projectRoot ('build/packaged-smoke/' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $destination -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($archive)
try {
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName.StartsWith('META-INF/jarjar/') -and $entry.FullName.EndsWith('.jar')) {
            $target = Join-Path $destination ([System.IO.Path]::GetFileName($entry.FullName))
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target)
        }
    }
} finally { $zip.Dispose() }
$java = Join-Path $env:JAVA_HOME 'bin/java.exe'
& $java -cp "$archive;$destination/*" dev.storyengine.script.StoryScriptCliKt (Join-Path $projectRoot $Script) $StoryId
if ($LASTEXITCODE -ne 0) { throw "Packaged Kotlin smoke failed: $LASTEXITCODE" }
