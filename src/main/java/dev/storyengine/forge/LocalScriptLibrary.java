package dev.storyengine.forge;

import dev.storyengine.core.StoryDefinition;
import dev.storyengine.script.KotlinStoryCompiler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Loads a bounded set of trusted, local Kotlin scripts when the server starts. */
final class LocalScriptLibrary {
    record Result(Map<String, StoryDefinition> stories, List<String> errors) {
        Result { stories = Map.copyOf(stories); errors = List.copyOf(errors); }
    }
    private LocalScriptLibrary() {}

    static CompletableFuture<Result> reloadAsync(Path directory, Map<String, StoryDefinition> previous) {
        Map<String, StoryDefinition> snapshot = Map.copyOf(previous);
        return CompletableFuture.supplyAsync(() -> reload(directory, snapshot), task -> {
            Thread worker = new Thread(task, "storyengine-script-reload");
            worker.setDaemon(true);
            worker.setContextClassLoader(LocalScriptLibrary.class.getClassLoader());
            worker.start();
        });
    }

    /** A reload commits only a completely valid catalog. Active runtime instances keep their graphs. */
    static Result reload(Path directory, Map<String, StoryDefinition> previous) {
        Result candidate = load(directory);
        return candidate.errors().isEmpty() ? candidate : new Result(previous, candidate.errors());
    }

    static Result load(Path directory) {
        Map<String, StoryDefinition> stories = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(path -> path.getFileName().toString().endsWith(".se.kts"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).limit(33).toList();
        } catch (IOException ex) {
            return new Result(stories, List.of("Cannot list scripts: " + ex.getMessage()));
        }
        int considered = 0;
        for (Path file : files) {
            if (considered++ >= 32) { errors.add("Only the first 32 scripts are considered"); break; }
            String name = file.getFileName().toString();
            String base = name.substring(0, name.length() - ".se.kts".length());
            if (!base.matches("[a-z0-9_-]{1,64}") || Files.isSymbolicLink(file)) {
                errors.add("Invalid script filename or symlink: " + name); continue;
            }
            String id = "local:" + base;
            try {
                stories.put(id, new KotlinStoryCompiler().compile(file, id));
            } catch (Exception | LinkageError ex) {
                String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                errors.add(id + ": " + message.substring(0, Math.min(message.length(), 1024)));
            }
        }
        return new Result(stories, errors);
    }
}
