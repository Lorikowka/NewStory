package dev.storyengine.cli;

import dev.storyengine.compiler.*;
import dev.storyengine.core.*;
import java.nio.file.*;
import java.util.*;

/** Headless validator and deterministic sample runner. Exit: 0 success, 1 invalid/failed, 2 usage. */
public final class Main {
    public static void main(String[] args) { System.exit(run(args)); }
    public static int run(String[] args) {
        if (args.length < 2 || !Set.of("validate", "run").contains(args[0])) {
            System.err.println("Usage: validate <story.json> | run <story.json> [event-type ...]"); return 2;
        }
        try {
            var definition = new StoryCompiler().compile(Path.of(args[1]), Set.of("engine:message", "engine:title"));
            System.out.println("VALID " + definition.id().value() + " " + definition.contentHash());
            if (args[0].equals("validate")) return 0;
            StoryRuntime runtime = new StoryRuntime((id, key, type, value) -> {
                System.out.println(type + ": " + value); return true;
            }, 256, 100);
            var id = new StoryInstanceId(new UUID(0, 1)); runtime.start(id, definition);
            int eventIndex = 2;
            for (long tick = 0; tick < 100_000; tick++) {
                runtime.tick(tick, 100, 100, 20);
                var state = runtime.inspect(id).orElseThrow();
                if (state.state().isTerminal()) {
                    System.out.println(state.state()); return state.state() == FiberState.COMPLETED ? 0 : 1;
                }
                if (state.waitingEvent() != null) {
                    if (eventIndex == args.length) {
                        System.err.println("WAITING: supply event " + state.waitingEvent()); return 1;
                    }
                    runtime.publish(new StoryRuntime.Event(id, args[eventIndex++]));
                }
            }
            System.err.println("RUNTIME_DEMO_BUDGET: exceeded 100000 simulated ticks"); return 1;
        } catch (Exception ex) { System.err.println(ex.getMessage()); return 1; }
    }
}
