package dev.storyengine.forge;

import java.io.*;
import java.nio.file.*;
import net.minecraftforge.fml.loading.FMLPaths;

/** User-owned content lives in the launcher-selected game directory, never inside the mod JAR. */
public final class StoryFiles {
    public static Path root() { return FMLPaths.GAMEDIR.get().resolve("storyengine"); }
    public static void initialize() { initialize(root()); }
    static void initialize(Path directory) {
        try {
            Files.createDirectories(directory.resolve("assets/heads"));
            Files.createDirectories(directory.resolve("scripts"));
            copyExample(directory, "temple.json");
            copyExample(directory, "chapter_01.se.kts");
        } catch (IOException ex) { throw new IllegalStateException("Cannot initialize " + directory, ex); }
    }
    private static void copyExample(Path directory, String name) throws IOException {
        Path example = directory.resolve("scripts").resolve(name);
        if (Files.exists(example)) return;
        try (InputStream in = StoryFiles.class.getResourceAsStream("/stories/" + name)) {
            if (in == null) throw new IOException("Bundled example missing: " + name);
            Files.copy(in, example);
        }
    }
}
