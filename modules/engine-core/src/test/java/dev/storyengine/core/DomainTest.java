package dev.storyengine.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DomainTest {
    @Test
    void storyIdsRequireNamespaceAndLowercasePath() {
        assertEquals("demo:temple", new StoryId("demo:temple").value());
        for (String invalid : new String[] {"temple", ":temple", "demo:", "Demo:temple", "a:b c"}) {
            assertThrows(IllegalArgumentException.class, () -> new StoryId(invalid));
        }
        assertThrows(NullPointerException.class, () -> new StoryId(null));
    }

    @Test
    void waitResumesThroughReadyAndTerminalStatesCannotRestart() {
        assertTrue(FiberState.READY.canTransitionTo(FiberState.RUNNING));
        assertTrue(FiberState.RUNNING.canTransitionTo(FiberState.WAITING));
        assertTrue(FiberState.WAITING.canTransitionTo(FiberState.READY));
        assertFalse(FiberState.WAITING.canTransitionTo(FiberState.RUNNING));
        for (FiberState state : FiberState.values()) {
            if (state.isTerminal()) {
                for (FiberState target : FiberState.values()) {
                    assertFalse(state.canTransitionTo(target));
                }
            }
        }
    }
}
