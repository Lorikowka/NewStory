package dev.storyengine.core;

import java.util.Objects;
import java.util.UUID;

/** Immutable identity supplied by the caller, independent of the runtime clock. */
public record StoryInstanceId(UUID value) {
    public StoryInstanceId {
        Objects.requireNonNull(value, "value");
    }
}
