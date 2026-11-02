package dev.storyengine.core;

import java.util.Map;
import java.util.Objects;

/** Immutable compiled graph. Callers may safely share definitions between runtime instances. */
public record StoryDefinition(StoryId id, String contentHash, String entry, Map<String, Node> nodes) {
    public StoryDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(contentHash); Objects.requireNonNull(entry);
        nodes = Map.copyOf(nodes);
        if (!nodes.containsKey(entry)) throw new IllegalArgumentException("Missing entry: " + entry);
        for (Node node : nodes.values()) {
            if (node.next() != null && !nodes.containsKey(node.next()))
                throw new IllegalArgumentException("Missing target: " + node.next());
        }
    }

    public sealed interface Node permits Finish, Jump, Await, Delay, Action, Dialogue {
        String next();
    }
    public record Finish() implements Node { public String next() { return null; } }
    public record Jump(String next) implements Node {
        public Jump { Objects.requireNonNull(next); }
    }
    public record Await(String event, String next) implements Node {
        public Await { Objects.requireNonNull(event); Objects.requireNonNull(next); }
    }
    public record Delay(long ticks, String next) implements Node {
        public Delay {
            if (ticks < 1) throw new IllegalArgumentException("Delay must be positive");
            Objects.requireNonNull(next);
        }
    }
    public record Action(String type, String value, String next) implements Node {
        public Action { Objects.requireNonNull(type); Objects.requireNonNull(value); Objects.requireNonNull(next); }
    }
    public enum PortraitMode { CUSTOM_TEXTURE, NONE, FALLBACK }
    public record Speaker(StoryId id, String name, int color, String portraitId, PortraitMode portraitMode) {
        public Speaker(StoryId id, String name, int color, String portraitId) {
            this(id, name, color, portraitId, PortraitMode.CUSTOM_TEXTURE);
        }
        public Speaker {
            Objects.requireNonNull(portraitMode);
            Objects.requireNonNull(id); Objects.requireNonNull(name); Objects.requireNonNull(portraitId);
            if (name.isBlank() || name.length() > 64) throw new IllegalArgumentException("Invalid speaker name");
            if (color < 0 || color > 0xFFFFFF) throw new IllegalArgumentException("Invalid speaker color");
            if (!portraitId.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid portrait ID");
        }
    }
    public enum DialogueMode { SAY, THOUGHT, WHISPER }
    public record Dialogue(Speaker speaker, DialogueMode mode, String text, String next) implements Node {
        public Dialogue {
            Objects.requireNonNull(speaker); Objects.requireNonNull(mode);
            Objects.requireNonNull(text); Objects.requireNonNull(next);
            if (text.isBlank() || text.length() > 4096) throw new IllegalArgumentException("Invalid dialogue text");
        }
    }
}
