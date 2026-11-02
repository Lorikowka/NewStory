package dev.storyengine.forge;

import dev.storyengine.core.StoryDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.ArrayList;
import java.util.UUID;
import dev.storyengine.core.StoryRuntime;
import dev.storyengine.core.StoryInstanceId;
import dev.storyengine.core.StoryId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalScriptLibraryTest {
    @TempDir Path directory;

    @Test void failedReloadPreservesPreviousCatalog() throws Exception {
        var graph = ChatDemoStory.create();
        var previous = Map.of("local:working", graph);
        Files.writeString(directory.resolve("Bad Name.se.kts"), "error(\"must not execute\")");
        var result = LocalScriptLibrary.reload(directory, previous);
        assertEquals(previous, result.stories());
        assertSame(graph, result.stories().get("local:working"));
        assertFalse(result.errors().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.stories().clear());
    }

    @Test void successfulReloadRemovesDeletedScriptsFromCatalog() {
        var previous = Map.of("local:deleted", ChatDemoStory.create());
        var result = LocalScriptLibrary.reload(directory, previous);
        assertTrue(result.errors().isEmpty());
        assertTrue(result.stories().isEmpty());
        assertEquals(1, previous.size());
    }

    @Test void compilerFailureKeepsWorkingGraphAndReportsSource() throws Exception {
        var previous = Map.of("local:chapter_01", ChatDemoStory.create());
        Files.writeString(directory.resolve("chapter_01.se.kts"), "this is not valid Kotlin !!!");
        var result = LocalScriptLibrary.reload(directory, previous);
        assertEquals(previous, result.stories());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("chapter_01.se.kts"));
    }

    @Test void successfulReloadAffectsNewInstancesOnly() throws Exception {
        var old = new StoryDefinition(new StoryId("local:chapter_01"), "old", "wait", Map.of(
                "wait", new StoryDefinition.Delay(1, "line"),
                "line", new StoryDefinition.Action("engine:message", "old line", "end"),
                "end", new StoryDefinition.Finish()));
        var effects = new ArrayList<String>();
        var runtime = new StoryRuntime((id, key, type, text) -> { effects.add(text); return true; }, 8, 8);
        var first = new StoryInstanceId(UUID.randomUUID());
        runtime.start(first, old);
        runtime.tick(0, 8, 8, 8);
        Files.writeString(directory.resolve("chapter_01.se.kts"), """
                import dev.storyengine.script.StoryNPC
                class Oren : StoryNPC("story:oren", "Oren", 0x36612F, "oren")
                npc<Oren>().say("new line")
                """);
        var result = LocalScriptLibrary.reloadAsync(directory, Map.of("local:chapter_01", old))
                .get(90, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        runtime.start(new StoryInstanceId(UUID.randomUUID()), result.stories().get("local:chapter_01"));
        runtime.tick(1, 8, 16, 8);
        assertTrue(effects.contains("old line"));
        assertTrue(effects.contains("new line"));
        assertEquals("old", runtime.inspect(first).orElseThrow().hash());
    }

    @Test void missingDirectoryProducesDiagnostic() {
        var result = LocalScriptLibrary.load(directory.resolve("missing"));
        assertTrue(result.stories().isEmpty());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).startsWith("Cannot list scripts:"));
    }

    @Test void limitsNumberOfConsideredScripts() throws Exception {
        for (int i = 0; i < 40; i++) {
            Files.writeString(directory.resolve("Bad_" + i + ".se.kts"), "error(\"must not execute\")");
        }
        var result = LocalScriptLibrary.load(directory);
        assertTrue(result.stories().isEmpty());
        assertEquals(33, result.errors().size());
        assertEquals("Only the first 32 scripts are considered", result.errors().get(32));
    }

    @Test void loadsValidScriptAndReportsBadName() throws Exception {
        Files.writeString(directory.resolve("chapter_01.se.kts"), """
                import dev.storyengine.script.StoryNPC
                class Oren : StoryNPC("story:oren", "Орен", 0x36612F, "oren")
                npc<Oren>().say("Привет")
                """);
        Files.writeString(directory.resolve("Bad Name.se.kts"), "error(\"should not run\")");
        var result = LocalScriptLibrary.load(directory);
        assertEquals(1, result.stories().size());
        assertEquals(1, result.errors().size());
        var graph = result.stories().get("local:chapter_01");
        assertNotNull(graph);
        var line = (StoryDefinition.Dialogue) graph.nodes().get("step_0");
        assertEquals("Орен", line.speaker().name());
    }
}
