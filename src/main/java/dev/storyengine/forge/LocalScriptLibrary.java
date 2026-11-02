package dev.storyengine.forge;

import dev.storyengine.core.StoryDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Loads a bounded set of trusted, local Kotlin scripts when the server starts. */
final class LocalScriptLibrary {
    record Result(Map<String, StoryDefinition> stories, List<String> errors) {
        Result { stories = Map.copyOf(stories); errors = List.copyOf(errors); }
    }
    private LocalScriptLibrary() {}

    static CompletableFuture<Result> reloadAsync(Path directory, Map<String, StoryDefinition> previous) {
        Map<String, StoryDefinition> snapshot = Map.copyOf(previous);
        return async(() -> reload(directory, snapshot));
    }

    static CompletableFuture<Result> validateAsync(Path directory, String id) {
        return async(() -> validate(directory, id));
    }

    private static CompletableFuture<Result> async(Supplier<Result> operation) {
        return CompletableFuture.supplyAsync(operation, task -> {
            Thread worker = new Thread(task, "storyengine-script-reload");
            worker.setDaemon(true);
            worker.setContextClassLoader(LocalScriptLibrary.class.getClassLoader());
            worker.start();
        });
    }

    /** Checks the current file without changing a loaded catalog or executing gameplay effects. */
    static Result validate(Path directory, String id) {
        if (id == null || !id.matches("local:[a-z0-9_-]{1,64}")) {
            return new Result(Map.of(), List.of("Invalid local story ID"));
        }
        Path file = directory.resolve(id.substring("local:".length()) + ".se.kts");
        if (Files.isSymbolicLink(file)) {
            return new Result(Map.of(), List.of(id + ": Symlink scripts are not allowed"));
        }
        try {
            return new Result(Map.of(id, IsolatedScriptCompiler.compile(file, id)), List.of());
        } catch (Exception | LinkageError ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return new Result(Map.of(), List.of(id + ": " + message.substring(0, Math.min(message.length(), 1024))));
        }
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
            Result checked = validate(directory, id);
            stories.putAll(checked.stories());
            errors.addAll(checked.errors());
        }
        return new Result(stories, errors);
    }
}
