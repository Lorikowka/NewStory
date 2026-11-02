package dev.storyengine.core.cinematic;

import java.util.*;

/** Single-owner resource arbitration. Scene IDs are immutable ownership tokens. */
public final class ResourceLeases {
    private final Map<String, UUID> owners = new HashMap<>();
    /** All-or-nothing acquisition; re-acquisition by the same scene is idempotent. */
    public boolean acquire(UUID scene, Set<String> resources) {
        Objects.requireNonNull(scene); Set<String> requested = Set.copyOf(resources);
        for (String resource : requested) {
            UUID owner = owners.get(resource);
            if (owner != null && !owner.equals(scene)) return false;
        }
        for (String resource : requested) owners.put(resource, scene);
        return true;
    }
    /** Idempotent cleanup for cancellation, timeout, logout and exceptions. */
    public void releaseAll(UUID scene) { owners.values().removeIf(Objects.requireNonNull(scene)::equals); }
    public Optional<UUID> owner(String resource) { return Optional.ofNullable(owners.get(resource)); }
}
