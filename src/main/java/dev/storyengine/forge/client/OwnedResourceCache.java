package dev.storyengine.forge.client;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.Consumer;

/** Thread-confined LRU cache. Owns distinct resources and releases them on replacement or eviction. */
final class OwnedResourceCache<K, V> {
    private final int capacity;
    private final Consumer<V> release;
    private final LinkedHashMap<K, V> entries = new LinkedHashMap<>(16, 0.75f, true);

    OwnedResourceCache(int capacity, Consumer<V> release) {
        if (capacity < 1) throw new IllegalArgumentException("Positive capacity required");
        this.capacity = capacity;
        this.release = Objects.requireNonNull(release);
    }

    V get(K key) { return entries.get(key); }

    void put(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        V previous = entries.put(key, value);
        if (previous != null && !previous.equals(value)) release.accept(previous);
        if (entries.size() > capacity) {
            var iterator = entries.entrySet().iterator();
            V oldest = iterator.next().getValue();
            iterator.remove();
            release.accept(oldest);
        }
    }

    void clear() {
        var resources = java.util.List.copyOf(entries.values());
        entries.clear();
        RuntimeException failure = null;
        for (V resource : resources) {
            try { release.accept(resource); }
            catch (RuntimeException ex) {
                if (failure == null) failure = ex;
                else if (failure != ex) failure.addSuppressed(ex);
            }
        }
        if (failure != null) throw failure;
    }
}
