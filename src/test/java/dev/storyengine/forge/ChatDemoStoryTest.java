package dev.storyengine.forge;

import dev.storyengine.core.StoryInstanceId;
import dev.storyengine.core.StoryRuntime;
import dev.storyengine.core.FiberState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatDemoStoryTest {
    @Test void playsThreeLinesWithoutExternalFileOrSignal() {
        List<String> lines = new ArrayList<>();
        var runtime = new StoryRuntime((id, key, type, value) -> {
            assertEquals("engine:message", type);
            lines.add(value);
            return true;
        }, 16, 1);
        var id = new StoryInstanceId(UUID.randomUUID());
        runtime.start(id, ChatDemoStory.create());

        runtime.tick(0, 16, 100, 20);
        assertEquals(1, lines.size());
        runtime.tick(259, 16, 100, 20);
        assertEquals(1, lines.size());
        runtime.tick(260, 16, 100, 20);
        assertEquals(2, lines.size());
        runtime.tick(500, 16, 100, 20);
        assertEquals(3, lines.size());
        assertEquals(FiberState.COMPLETED, runtime.inspect(id).orElseThrow().state());
    }
}
