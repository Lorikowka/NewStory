package dev.storyengine.script

import dev.storyengine.core.FiberState
import dev.storyengine.core.StoryDefinition
import dev.storyengine.core.StoryInstanceId
import dev.storyengine.core.StoryRuntime
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class KotlinStoryCompilerTest {
    @TempDir lateinit var directory: Path

    @Test fun topLevelScriptBuildsRunnableGraph() {
        val file = directory.resolve("chapter_01.se.kts")
        Files.writeString(file, """
            import dev.storyengine.script.StoryNPC
            import dev.storyengine.core.StoryDefinition.PortraitMode
            class Oren : StoryNPC("story:oren", "Орен", 0x36612F, "oren", PortraitMode.NONE)
            val oren = npc<Oren>()
            oren.say("Первая реплика")
            wait(2.seconds)
            oren.say("Вторая реплика")
            oren.think("Надо осмотреться")
            oren.whisper("Тише")
        """.trimIndent())
        val graph = KotlinStoryCompiler().compile(file, "story:chapter_01")
        val first = graph.nodes()["step_0"] as StoryDefinition.Dialogue
        assertEquals("Орен", first.speaker().name())
        assertEquals(0x36612F, first.speaker().color())
        assertEquals(StoryDefinition.PortraitMode.NONE, first.speaker().portraitMode())
        val lines = mutableListOf<String>()
        val runtime = StoryRuntime({ _, _, type, value ->
            assertEquals("engine:message", type)
            lines.add(value)
            true
        }, 8, 1)
        val id = StoryInstanceId(UUID.randomUUID())
        runtime.start(id, graph)
        runtime.tick(0, 8, 20, 20)
        assertEquals(listOf("Первая реплика"), lines)
        runtime.tick(39, 8, 20, 20)
        assertEquals(1, lines.size)
        runtime.tick(40, 8, 20, 20)
        assertEquals(listOf("Первая реплика", "Вторая реплика", "Надо осмотреться", "Тише"), lines)
        assertEquals(StoryDefinition.DialogueMode.THOUGHT, (graph.nodes()["step_3"] as StoryDefinition.Dialogue).mode())
        assertEquals(StoryDefinition.DialogueMode.WHISPER, (graph.nodes()["step_4"] as StoryDefinition.Dialogue).mode())
        assertEquals(FiberState.COMPLETED, runtime.inspect(id).orElseThrow().state())
    }

    @Test fun rejectsWrongExtensionBeforeEvaluation() {
        val file = directory.resolve("chapter.txt")
        Files.writeString(file, "error(\"should not run\")")
        val error = assertThrows(IllegalArgumentException::class.java) {
            KotlinStoryCompiler().compile(file, "story:chapter_01")
        }
        assertTrue(error.message!!.contains(".se.kts"))
    }

    @Test fun topLevelEventWaitCompilesIntoAwaitNode() {
        val file = directory.resolve("interaction.se.kts")
        Files.writeString(file, "awaitEvent(\"engine:block_interact\")")
        val graph = KotlinStoryCompiler().compile(file, "story:interaction")
        assertEquals("engine:block_interact", (graph.nodes()["step_0"] as StoryDefinition.Await).event())
    }
}
