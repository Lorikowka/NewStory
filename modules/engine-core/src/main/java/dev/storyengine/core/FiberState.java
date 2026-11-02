package dev.storyengine.core;

/** Lifecycle contract. Terminal states cannot be resumed. */
public enum FiberState {
    READY, RUNNING, WAITING, COMPLETED, FAILED, CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    /** Returns whether a state change is legal; repeated cancellation is handled by callers. */
    public boolean canTransitionTo(FiberState target) {
        if (target == null || isTerminal()) {
            return false;
        }
        return switch (this) {
            case READY -> target == RUNNING || target == CANCELLED || target == FAILED;
            case RUNNING -> target == READY || target == WAITING || target.isTerminal();
            case WAITING -> target == READY || target == CANCELLED || target == FAILED;
            default -> false;
        };
    }
}
