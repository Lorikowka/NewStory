package dev.storyengine.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeTest {
    private static StoryInstanceId id(long value) { return new StoryInstanceId(new UUID(0, value)); }
    private static StoryDefinition story(Map<String, StoryDefinition.Node> nodes) {
        return new StoryDefinition(new StoryId("test:story"), "hash", "start", nodes);
    }
    @Test void waitResumeDuplicateAndFinish() {
        List<String> effects = new ArrayList<>();
        var runtime = new StoryRuntime((id, key, type, value) -> { effects.add(key); return true; }, 8, 8);
        runtime.start(id(1), story(Map.of("start", new StoryDefinition.Await("go", "act"),
                "act", new StoryDefinition.Action("message", "hello", "end"), "end", new StoryDefinition.Finish())));
        runtime.tick(0, 8, 8, 8);
        assertEquals(FiberState.WAITING, runtime.inspect(id(1)).orElseThrow().state());
        runtime.publish(new StoryRuntime.Event(id(2), "go"));
        runtime.publish(new StoryRuntime.Event(id(1), "wrong"));
        runtime.tick(1, 8, 8, 8);
        assertTrue(effects.isEmpty());
        runtime.publish(new StoryRuntime.Event(id(1), "go"));
        runtime.publish(new StoryRuntime.Event(id(1), "go"));
        runtime.tick(2, 8, 8, 8);
        assertEquals(1, effects.size());
        assertEquals(FiberState.COMPLETED, runtime.inspect(id(1)).orElseThrow().state());
        assertFalse(runtime.cancel(id(1)));
    }
    @Test void budgetFairnessAndFailureIsolation() {
        var runtime = new StoryRuntime((id, key, type, value) -> { throw new IllegalStateException(); }, 8, 8);
        runtime.start(id(1), story(Map.of("start", new StoryDefinition.Jump("start"))));
        runtime.start(id(2), story(Map.of("start", new StoryDefinition.Finish())));
        runtime.start(id(3), story(Map.of("start", new StoryDefinition.Action("bad", "x", "end"), "end", new StoryDefinition.Finish())));
        assertEquals(5, runtime.tick(0, 8, 20, 3).steps());
        assertEquals(FiberState.READY, runtime.inspect(id(1)).orElseThrow().state());
        assertEquals(FiberState.COMPLETED, runtime.inspect(id(2)).orElseThrow().state());
        assertEquals(FiberState.FAILED, runtime.inspect(id(3)).orElseThrow().state());
        assertTrue(runtime.cancel(id(1)));
    }
    @Test void timersCancellationAndBackpressure() {
        var runtime = new StoryRuntime((id, key, type, value) -> true, 1, 2);
        var story = story(Map.of("start", new StoryDefinition.Delay(3, "end"), "end", new StoryDefinition.Finish()));
        runtime.start(id(1), story); runtime.start(id(2), story);
        runtime.tick(0, 8, 8, 8); runtime.cancel(id(2));
        assertTrue(runtime.publish(new StoryRuntime.Event(id(1), "irrelevant")));
        assertFalse(runtime.publish(new StoryRuntime.Event(id(1), "overflow")));
        runtime.tick(2, 8, 8, 8);
        assertEquals(FiberState.WAITING, runtime.inspect(id(1)).orElseThrow().state());
        runtime.tick(3, 8, 8, 8);
        assertEquals(FiberState.COMPLETED, runtime.inspect(id(1)).orElseThrow().state());
        assertEquals(FiberState.CANCELLED, runtime.inspect(id(2)).orElseThrow().state());
        assertThrows(IllegalArgumentException.class, () -> runtime.tick(3, 8, 8, 8));
        assertTrue(runtime.forget(id(2)));
    }
    @Test void thousandWaitersDoNoExecutionWorkUntilAddressed() {
        var runtime = new StoryRuntime((id, key, type, value) -> true, 100, 1000);
        var story = story(Map.of("start", new StoryDefinition.Await("go", "end"), "end", new StoryDefinition.Finish()));
        for (int i = 0; i < 1000; i++) runtime.start(id(i), story);
        assertEquals(1000, runtime.tick(0, 100, 2000, 10).steps());
        assertEquals(0, runtime.tick(1, 100, 2000, 10).steps());
        for (int i = 0; i < 100; i++) assertTrue(runtime.publish(new StoryRuntime.Event(id(i), "go")));
        assertEquals(100, runtime.tick(2, 100, 2000, 10).steps());
    }
}
