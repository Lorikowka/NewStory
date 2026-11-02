package dev.storyengine.script

import dev.storyengine.core.StoryDefinition
import java.nio.file.Files
import java.nio.file.Path
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

/** Evaluates trusted local Kotlin files to build a graph; never run untrusted packs here. */
class KotlinStoryCompiler {
    fun compile(file: Path, storyId: String): StoryDefinition {
        require(file.fileName.toString().endsWith(".se.kts")) { "Expected .se.kts file: $file" }
        require(Files.isRegularFile(file) && Files.size(file) <= 262_144) { "Script missing or exceeds 256 KiB: $file" }
        val builder = StoryScriptBuilder(storyId)
        val result = BasicJvmScriptingHost().eval(
            file.toFile().toScriptSource(),
            createJvmCompilationConfigurationFromTemplate<StoryScriptTemplate>(),
            ScriptEvaluationConfiguration { constructorArgs(builder) }
        )
        val diagnostics = result.reports.filter { it.severity >= ScriptDiagnostic.Severity.WARNING }.joinToString("\n") { report ->
            val line = report.location?.start?.line?.let { ":$it" } ?: ""
            "${file.fileName}$line: ${report.severity}: ${report.message}"
        }
        if (result is ResultWithDiagnostics.Failure) throw IllegalArgumentException(diagnostics)
        val evaluation = (result as? ResultWithDiagnostics.Success)?.value
        val error = (evaluation?.returnValue as? ResultValue.Error)?.error
        if (error != null) throw IllegalArgumentException(diagnostics.ifBlank { "Script failed: $file" }, error)
        return builder.build()
    }
}
