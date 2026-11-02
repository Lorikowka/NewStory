package dev.storyengine.script

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.time.Duration

/** Top-level DSL for trusted local story files; calls only record graph steps. */
@KotlinScript(fileExtension = "se.kts", compilationConfiguration = StoryScriptCompilationConfiguration::class)
abstract class StoryScriptTemplate(private val builder: StoryScriptBuilder) {
    fun awaitEvent(event: String) = builder.awaitEvent(event)

    fun <T : StoryNPC> npc(type: Class<T>): StoryScriptBuilder.Speaker =
        builder.npc(type.getDeclaredConstructor().newInstance())

    inline fun <reified T : StoryNPC> npc(): StoryScriptBuilder.Speaker = npc(T::class.java)

    fun wait(duration: Duration) {
        val millis = duration.inWholeMilliseconds
        require(millis in 1..50_000_000) { "wait duration must be 1..50000000 ms" }
        builder.waitTicks((millis + 49) / 50)
    }
}

object StoryScriptCompilationConfiguration : ScriptCompilationConfiguration({
    defaultImports("kotlin.time.Duration.Companion.seconds")
    compilerOptions("-jvm-target", "17")
    jvm { dependenciesFromCurrentContext(wholeClasspath = true) }
})
