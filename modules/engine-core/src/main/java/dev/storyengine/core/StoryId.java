package dev.storyengine.core;

import java.util.Objects;

/** Immutable, case-sensitive namespace:path identifier; safe to share across threads. */
public record StoryId(String value) {
    public StoryId {
        Objects.requireNonNull(value, "value");
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Expected namespace:path story ID: " + value);
        }
    }
}
