package dev.storyengine.script;

import dev.storyengine.core.StoryDefinition;
import dev.storyengine.core.StoryId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Sequential DSL target for Kotlin scripts; building a graph never runs gameplay actions. */
public final class StoryScriptBuilder {
    private sealed interface Step permits Line, Wait, Await {}
    private record Line(StoryNPC speaker, StoryDefinition.DialogueMode mode, String text) implements Step {}
    private record Wait(long ticks) implements Step {}
    private record Await(String event) implements Step {}

    public final class Speaker {
        private final StoryNPC npc;
        private Speaker(StoryNPC npc) { this.npc = npc; }
        public StoryNPC character() { return npc; }
        public void say(String text) {
            line(StoryDefinition.DialogueMode.SAY, text);
        }
        public void think(String text) {
            line(StoryDefinition.DialogueMode.THOUGHT, text);
        }
        public void whisper(String text) {
            line(StoryDefinition.DialogueMode.WHISPER, text);
        }
        private void line(StoryDefinition.DialogueMode mode, String text) {
            Objects.requireNonNull(text, "text");
            if (text.isBlank() || text.length() > 4096) throw new IllegalArgumentException("say text must have 1..4096 characters");
            steps.add(new Line(npc, mode, text));
        }
    }

    private final StoryId id;
    private final List<Step> steps = new ArrayList<>();

    public StoryScriptBuilder(String id) { this.id = new StoryId(id); }
    public Speaker npc(StoryNPC npc) { return new Speaker(Objects.requireNonNull(npc, "npc")); }
    public void waitTicks(long ticks) {
        if (ticks < 1 || ticks > 1_000_000) throw new IllegalArgumentException("waitTicks must be 1..1000000");
        steps.add(new Wait(ticks));
    }

    /** Waits for an addressed event delivered after this node becomes active. */
    public void awaitEvent(String event) {
        Objects.requireNonNull(event, "event");
        if (event.length() > 256) throw new IllegalArgumentException("Event ID exceeds 256 characters");
        steps.add(new Await(new StoryId(event).value()));
    }

    /** Node IDs are stable for the same ordered script; edits change the content hash. */
    public StoryDefinition build() {
        Map<String, StoryDefinition.Node> nodes = new LinkedHashMap<>();
        StringBuilder fingerprint = new StringBuilder(id.value());
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            String next = i + 1 == steps.size() ? "end" : "step_" + (i + 1);
            if (step instanceof Line line) {
                var npc = line.speaker();
                var speaker = new StoryDefinition.Speaker(npc.id(), npc.name(), npc.nameColor(), npc.portraitId());
                nodes.put("step_" + i, new StoryDefinition.Dialogue(speaker, line.mode(), line.text(), next));
                append(fingerprint, line.mode().name(), line.speaker().id().value(), line.speaker().name(),
                        Integer.toString(line.speaker().nameColor()), line.speaker().portraitId(), line.text());
            } else if (step instanceof Wait wait) {
                nodes.put("step_" + i, new StoryDefinition.Delay(wait.ticks(), next));
                append(fingerprint, "wait", Long.toString(wait.ticks()));
            } else if (step instanceof Await await) {
                nodes.put("step_" + i, new StoryDefinition.Await(await.event(), next));
                append(fingerprint, "await", await.event());
            }
        }
        nodes.put("end", new StoryDefinition.Finish());
        return new StoryDefinition(id, hash(fingerprint.toString()), steps.isEmpty() ? "end" : "step_0", nodes);
    }

    private static void append(StringBuilder out, String... values) {
        for (String value : values) out.append('|').append(value.length()).append(':').append(value);
    }
    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
}
