package dev.storyengine.core;

import dev.storyengine.core.cinematic.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CinematicTest {
    @Test void cameraClampsAndTakesShortestRotation() {
        var a = new CameraTrack.Pose(0, 0, 0, 350, 0, 60);
        var b = new CameraTrack.Pose(10, 20, 30, 10, 20, 80);
        var track = new CameraTrack(List.of(new CameraTrack.Keyframe(0, a), new CameraTrack.Keyframe(10, b)));
        assertEquals(a, track.sample(-1)); assertEquals(b, track.sample(99));
        assertEquals(5, track.sample(5).x()); assertEquals(360, track.sample(5).yaw());
        assertEquals(70, track.sample(5).fov());
        assertThrows(IllegalArgumentException.class, () -> track.sample(Double.NaN));
    }
    @Test void rejectsDuplicateTimesAndInvalidPose() {
        var pose = new CameraTrack.Pose(0, 0, 0, 0, 0, 70);
        assertThrows(IllegalArgumentException.class, () -> new CameraTrack(List.of(
                new CameraTrack.Keyframe(1, pose), new CameraTrack.Keyframe(1, pose))));
        assertThrows(IllegalArgumentException.class, () -> new CameraTrack.Pose(0, 0, 0, 0, 91, 70));
    }
    @Test void leaseConflictsDoNotPartiallyAcquireAndCleanupIsIdempotent() {
        var leases = new ResourceLeases(); var a = new UUID(0, 1); var b = new UUID(0, 2);
        assertTrue(leases.acquire(a, Set.of("camera")));
        assertFalse(leases.acquire(b, Set.of("camera", "input")));
        assertTrue(leases.owner("input").isEmpty());
        leases.releaseAll(a); leases.releaseAll(a);
        assertTrue(leases.acquire(b, Set.of("camera", "input")));
    }
}
