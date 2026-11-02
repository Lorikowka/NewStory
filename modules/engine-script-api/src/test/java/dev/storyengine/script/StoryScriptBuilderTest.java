package dev.storyengine.script;

import dev.storyengine.core.FiberState;
import dev.storyengine.core.StoryDefinition;
import dev.storyengine.core.StoryInstanceId;
import dev.storyengine.core.StoryPlatform;
import dev.storyengine.core.StoryRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoryScriptBuilderTest {
    private static final class Oren extends StoryNPC {
        Oren() { super("story:oren", "Орен", 0x36612F, "oren"); }
    }

    @Test void eventWaitResumesOnlyForMatchingInstanceAndEvent() {
        var script = new StoryScriptBuilder("story:interaction");
        script.awaitEvent("engine:block_interact");
        script.npc(new Oren()).say("Продолжаем");
        var lines = new ArrayList<String>();
        var runtime = new StoryRuntime((id, key, type, value) -> { lines.add(value); return true; }, 8, 2);
        var first = new StoryInstanceId(UUID.randomUUID());
        var second = new StoryInstanceId(UUID.randomUUID());
        runtime.start(first, script.build());
        runtime.start(second, script.build());
        runtime.tick(0, 8, 20, 20);
        assertEquals(FiberState.WAITING, runtime.inspect(first).orElseThrow().state());
        runtime.publish(new StoryRuntime.Event(first, "engine:wrong"));
        runtime.tick(1, 8, 20, 20);
        assertTrue(lines.isEmpty());
        runtime.publish(new StoryRuntime.Event(first, "engine:block_interact"));
        runtime.tick(2, 8, 20, 20);
        assertEquals(List.of("Продолжаем"), lines);
        assertEquals(FiberState.COMPLETED, runtime.inspect(first).orElseThrow().state());
        assertEquals(FiberState.WAITING, runtime.inspect(second).orElseThrow().state());
        var different = new StoryScriptBuilder("story:interaction");
        different.awaitEvent("engine:other");
        assertNotEquals(script.build().contentHash(), different.build().contentHash());
    }

    @Test void rejectsInvalidEventIds() {
        var script = new StoryScriptBuilder("story:test");
        assertThrows(NullPointerException.class, () -> script.awaitEvent(null));
        assertThrows(IllegalArgumentException.class, () -> script.awaitEvent("missing_namespace"));
        assertThrows(IllegalArgumentException.class, () -> script.awaitEvent("engine:" + "a".repeat(256)));
    }

    @Test void dialogueModesReachPlatformInOrderAndAffectHash() {
        var script = new StoryScriptBuilder("story:modes");
        var oren = script.npc(new Oren());
        oren.say("Одинаковый текст");
        oren.think("Одинаковый текст");
        oren.whisper("Одинаковый текст");
        var received = new ArrayList<StoryDefinition.DialogueMode>();
        var runtime = new StoryRuntime(new StoryPlatform() {
            @Override public boolean apply(StoryInstanceId id, String key, String type, String text) {
                fail("Expected typed dialogue"); return false;
            }
            @Override public boolean applyDialogue(StoryInstanceId id, String key, StoryDefinition.Dialogue dialogue) {
                received.add(dialogue.mode()); return true;
            }
        }, 8, 1);
        runtime.start(new StoryInstanceId(UUID.randomUUID()), script.build());
        runtime.tick(0, 8, 20, 20);
        assertEquals(List.of(StoryDefinition.DialogueMode.SAY, StoryDefinition.DialogueMode.THOUGHT,
                StoryDefinition.DialogueMode.WHISPER), received);
        var sayOnly = new StoryScriptBuilder("story:modes");
        sayOnly.npc(new Oren()).say("Одинаковый текст");
        var thoughtOnly = new StoryScriptBuilder("story:modes");
        thoughtOnly.npc(new Oren()).think("Одинаковый текст");
        assertNotEquals(sayOnly.build().contentHash(), thoughtOnly.build().contentHash());
        assertThrows(IllegalArgumentException.class, () -> oren.think(" "));
        assertThrows(IllegalArgumentException.class, () -> oren.whisper("a".repeat(4097)));
    }

    @Test void sayWaitSayBuildsRunnableGraphWithoutStoryWrapper() {
        var script = new StoryScriptBuilder("story:chapter_01");
        var oren = script.npc(new Oren());
        oren.say("Что это за место?");
        script.waitTicks(40);
        oren.say("Тише...");
        var definition = script.build();
        assertEquals(definition.contentHash(), script.build().contentHash());
        assertEquals("story:oren", oren.character().id().value());
        var first = (StoryDefinition.Dialogue) definition.nodes().get("step_0");
        assertEquals("Орен", first.speaker().name());
        assertEquals(0x36612F, first.speaker().color());
        assertEquals("oren", first.speaker().portraitId());

        List<String> lines = new ArrayList<>();
        var runtime = new StoryRuntime((id, key, type, value) -> {
            assertEquals("engine:message", type);
            lines.add(value);
            return true;
        }, 8, 1);
        var instance = new StoryInstanceId(UUID.randomUUID());
        runtime.start(instance, definition);
        runtime.tick(0, 8, 20, 20);
        assertEquals(List.of("Что это за место?"), lines);
        runtime.tick(39, 8, 20, 20);
        assertEquals(1, lines.size());
        runtime.tick(40, 8, 20, 20);
        assertEquals(List.of("Что это за место?", "Тише..."), lines);
        assertEquals(FiberState.COMPLETED, runtime.inspect(instance).orElseThrow().state());
    }

    @Test void rejectsInvalidPortraitAndWait() {
        assertThrows(IllegalArgumentException.class, () -> new StoryNPC("story:bad", "Bad", 0xFFFFFF, "../escape") {});
        var script = new StoryScriptBuilder("story:test");
        assertThrows(IllegalArgumentException.class, () -> script.waitTicks(0));
        assertThrows(IllegalArgumentException.class, () -> script.npc(new Oren()).say(" "));
    }

    @Test void typedDialogueReachesPlatformWithSpeakerMetadata() {
        var script = new StoryScriptBuilder("story:chapter_01");
        script.npc(new Oren()).say("Тише...");
        List<StoryDefinition.Dialogue> received = new ArrayList<>();
        var platform = new StoryPlatform() {
            @Override public boolean apply(StoryInstanceId id, String key, String type, String value) {
                fail("Typed dialogue should use applyDialogue"); return false;
            }
            @Override public boolean applyDialogue(StoryInstanceId id, String key, StoryDefinition.Dialogue dialogue) {
                received.add(dialogue); return true;
            }
        };
        var runtime = new StoryRuntime(platform, 8, 1);
        runtime.start(new StoryInstanceId(UUID.randomUUID()), script.build());
        runtime.tick(0, 8, 20, 20);
        assertEquals(1, received.size());
        assertEquals("story:oren", received.get(0).speaker().id().value());
        assertEquals("Тише...", received.get(0).text());
    }
}
