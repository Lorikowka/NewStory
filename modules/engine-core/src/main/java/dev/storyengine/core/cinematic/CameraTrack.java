package dev.storyengine.core.cinematic;

import java.util.*;

/** Pure immutable camera track. Sampling uses supplied scene time, never a system clock. */
public record CameraTrack(List<Keyframe> frames) {
    public record Pose(double x, double y, double z, double yaw, double pitch, double fov) {
        public Pose {
            for (double value : new double[] {x, y, z, yaw, pitch, fov})
                if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite camera pose");
            if (fov < 1 || fov > 179 || pitch < -90 || pitch > 90) throw new IllegalArgumentException("Invalid camera angle");
        }
    }
    public record Keyframe(double time, Pose pose) {
        public Keyframe {
            if (!Double.isFinite(time) || time < 0) throw new IllegalArgumentException("Invalid keyframe time");
            Objects.requireNonNull(pose);
        }
    }
    public CameraTrack {
        frames = List.copyOf(frames);
        if (frames.isEmpty() || frames.size() > 4096) throw new IllegalArgumentException("Use 1..4096 keyframes");
        double previous = -1;
        for (Keyframe frame : frames) {
            if (frame.time() <= previous) throw new IllegalArgumentException("Keyframes must strictly increase");
            previous = frame.time();
        }
    }
    /** Clamps outside track duration and uses smoothstep with shortest-path yaw interpolation. */
    public Pose sample(double time) {
        if (!Double.isFinite(time)) throw new IllegalArgumentException("Invalid scene time");
        if (time <= frames.get(0).time()) return frames.get(0).pose();
        int last = frames.size() - 1;
        if (time >= frames.get(last).time()) return frames.get(last).pose();
        int low = 0, high = last;
        while (high - low > 1) {
            int mid = (low + high) >>> 1;
            if (frames.get(mid).time() <= time) low = mid; else high = mid;
        }
        Keyframe a = frames.get(low), b = frames.get(high);
        double t = (time - a.time()) / (b.time() - a.time());
        t = t * t * (3 - 2 * t);
        Pose p = a.pose(), q = b.pose();
        double deltaYaw = ((q.yaw() - p.yaw()) % 360 + 540) % 360 - 180;
        return new Pose(lerp(p.x(), q.x(), t), lerp(p.y(), q.y(), t), lerp(p.z(), q.z(), t),
                p.yaw() + deltaYaw * t, lerp(p.pitch(), q.pitch(), t), lerp(p.fov(), q.fov(), t));
    }
    private static double lerp(double a, double b, double t) { return a * (1 - t) + b * t; }
}
