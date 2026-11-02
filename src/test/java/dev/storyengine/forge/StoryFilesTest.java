package dev.storyengine.forge;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StoryFilesTest {
    @TempDir Path directory;
    @Test void createsUserFoldersAndSeedsExample() throws Exception {
        StoryFiles.initialize(directory);
        assertTrue(Files.isDirectory(directory.resolve("assets/heads")));
        assertTrue(Files.readString(directory.resolve("scripts/temple.json")).contains("demo:temple"));
        assertTrue(Files.readString(directory.resolve("scripts/chapter_01.se.kts")).contains("npc<Oren>()"));
    }
    @Test void preservesEditedScriptsAcrossLaunches() throws Exception {
        StoryFiles.initialize(directory);
        Files.writeString(directory.resolve("scripts/temple.json"), "user content");
        Files.writeString(directory.resolve("scripts/chapter_01.se.kts"), "user kotlin");
        StoryFiles.initialize(directory);
        assertEquals("user content", Files.readString(directory.resolve("scripts/temple.json")));
        assertEquals("user kotlin", Files.readString(directory.resolve("scripts/chapter_01.se.kts")));
    }
}
