package dev.storyengine.core;

/** Platform effects execute on the owning runtime thread. A false result fails only that instance.
 * Implementations must deduplicate retryable effects by key; the engine does not promise exactly-once I/O. */
@FunctionalInterface
public interface StoryPlatform {
    boolean apply(StoryInstanceId instance, String idempotencyKey, String type, String value);
    /** Rich dialogue path; legacy platforms can still render the plain text. */
    default boolean applyDialogue(StoryInstanceId instance, String idempotencyKey, StoryDefinition.Dialogue dialogue) {
        return apply(instance, idempotencyKey, "engine:message", dialogue.text());
    }
}
