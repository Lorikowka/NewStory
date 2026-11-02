package dev.storyengine.forge;

import dev.storyengine.core.StoryDefinition;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Keeps the Kotlin compiler outside Forge's module layer, sharing only the Java graph and DSL API. */
final class IsolatedScriptCompiler {
    private static final String LIBRARIES = "/META-INF/storyengine-libraries/";
    private static URLClassLoader compilerLoader;

    private IsolatedScriptCompiler() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: <script.se.kts> <namespace:story_id>");
        StoryDefinition graph = compile(Path.of(args[0]), args[1]);
        System.out.println("VALID " + graph.id().value() + " " + graph.contentHash() + " nodes=" + graph.nodes().size());
    }

    static StoryDefinition compile(Path file, String id) throws Exception {
        ClassLoader loader = loader();
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            Class<?> compiler = loader.loadClass("dev.storyengine.script.KotlinStoryCompiler");
            Object instance = compiler.getConstructor().newInstance();
            return (StoryDefinition) compiler.getMethod("compile", Path.class, String.class).invoke(instance, file, id);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException(cause);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static synchronized URLClassLoader loader() throws IOException {
        if (compilerLoader != null) return compilerLoader;
        Path directory = Files.createTempDirectory("storyengine-kotlin-");
        directory.toFile().deleteOnExit();
        var urls = new ArrayList<URL>();
        try (var input = IsolatedScriptCompiler.class.getResourceAsStream(LIBRARIES + "index.txt")) {
            if (input == null) throw new IOException("Isolated Kotlin library index is missing");
            String index = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            for (String name : index.lines().toList()) {
                if (!name.matches("[a-zA-Z0-9_.-]+\\.jar")) throw new IOException("Invalid library entry: " + name);
                Path target = directory.resolve(name);
                try (var library = IsolatedScriptCompiler.class.getResourceAsStream(LIBRARIES + name)) {
                    if (library == null) throw new IOException("Missing isolated library: " + name);
                    Files.copy(library, target);
                }
                target.toFile().deleteOnExit();
                urls.add(target.toUri().toURL());
            }
        }
        compilerLoader = new URLClassLoader(urls.toArray(URL[]::new), IsolatedScriptCompiler.class.getClassLoader()) {
            @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                boolean host = name.startsWith("kotlin.") || name.startsWith("org.jetbrains.kotlin.")
                        || name.startsWith("dev.storyengine.script.KotlinStoryCompiler")
                        || name.startsWith("dev.storyengine.script.StoryScriptTemplate")
                        || name.startsWith("dev.storyengine.script.StoryScriptCompilationConfiguration");
                if (!host) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) type = findClass(name);
                if (resolve) resolveClass(type);
                return type;
            }
        };
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { compilerLoader.close(); } catch (IOException ignored) { }
        }, "storyengine-script-close"));
        return compilerLoader;
    }
}
