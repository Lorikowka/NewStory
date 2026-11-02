package dev.storyengine.forge.client;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OwnedResourceCacheTest {
    @Test void reusesResourcesAndEvictsLeastRecentlyUsed() {
        var released = new ArrayList<String>();
        var cache = new OwnedResourceCache<String, String>(2, released::add);
        cache.put("a", "texture-a");
        cache.put("b", "texture-b");
        assertEquals("texture-a", cache.get("a"));
        cache.put("c", "texture-c");
        assertNull(cache.get("b"));
        assertEquals(List.of("texture-b"), released);
        cache.clear();
        cache.clear();
        assertEquals(List.of("texture-b", "texture-a", "texture-c"), released);
    }

    @Test void replacingResourceReleasesPreviousOnlyOnce() {
        var released = new ArrayList<String>();
        var cache = new OwnedResourceCache<String, String>(1, released::add);
        cache.put("a", "old");
        cache.put("a", "old");
        cache.put("a", "new");
        assertEquals(List.of("old"), released);
        cache.clear();
        assertEquals(List.of("old", "new"), released);
        assertThrows(IllegalArgumentException.class, () -> new OwnedResourceCache<String, String>(0, released::add));
    }

    @Test void cleanupAttemptsEveryResourceEvenWhenOneReleaseFails() {
        var released = new ArrayList<String>();
        var cache = new OwnedResourceCache<String, String>(2, value -> {
            released.add(value);
            if (value.equals("bad")) throw new IllegalStateException("release failed");
        });
        cache.put("a", "bad");
        cache.put("b", "good");
        assertThrows(IllegalStateException.class, cache::clear);
        assertEquals(List.of("bad", "good"), released);
        assertNull(cache.get("a"));
        assertDoesNotThrow(cache::clear);
    }
}
