param(
    [string]$Script = 'samples/kotlin/chapter_01.se.kts',
    [string]$StoryId = 'smoke:chapter_01'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$archive = Join-Path $projectRoot 'build/libs/storyengine-0.1.0-SNAPSHOT.jar'
$java = Join-Path $env:JAVA_HOME 'bin/java.exe'
# Deliberately no external Kotlin classpath: the production loader must find its own nested libraries.
& $java -cp $archive dev.storyengine.forge.IsolatedScriptCompiler (Join-Path $projectRoot $Script) $StoryId
if ($LASTEXITCODE -ne 0) { throw "Packaged Kotlin smoke failed: $LASTEXITCODE" }
