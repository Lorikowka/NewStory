package dev.storyengine.compiler;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CompilerTest {
    private final StoryCompiler compiler = new StoryCompiler();
    private String source(String nodes) {
        return "{\"schemaVersion\":1,\"id\":\"demo:test\",\"entry\":\"start\",\"nodes\":" + nodes + "}";
    }
    @Test void acceptsWaitCycleButRejectsBusyCycleAndMissingTarget() {
        assertNotNull(compiler.compile(source("{\"start\":{\"await\":{\"type\":\"go\"},\"next\":\"start\"}}"), Set.of()));
        var cycle = assertThrows(CompilationException.class, () -> compiler.compile(source("{\"start\":{\"next\":\"start\"}}"), Set.of()));
        assertTrue(cycle.getMessage().contains("CYCLE"));
        var missing = assertThrows(CompilationException.class, () -> compiler.compile(source("{\"start\":{\"next\":\"missing\"}}"), Set.of()));
        assertTrue(missing.path().contains("next"));
    }
    @Test void rejectsAmbiguousOrPermissiveJson() {
        for (String nodes : new String[] {
                "{\"start\":{\"finish\":true,\"finish\":true}}",
                "{\"start\":{\"finish\":\"true\"}}",
                "{\"start\":{\"finish\":true,\"next\":\"start\"}}",
                "{\"start\":{\"finish\":true,\"unknown\":1}}",
                "{\"start\":{\"delay\":1.5,\"next\":\"start\"}}",
                "{\"start\":{\"delay\":0,\"next\":\"start\"}}",
                "{'start':{'finish':true}}"}) {
            assertThrows(CompilationException.class, () -> compiler.compile(source(nodes), Set.of()), nodes);
        }
    }
    @Test void capabilitiesAndDefinitionsAreImmutable() {
        String source = source("{\"start\":{\"action\":{\"type\":\"engine:message\",\"value\":\"hi\"},\"next\":\"end\"},\"end\":{\"finish\":true}}");
        assertThrows(CompilationException.class, () -> compiler.compile(source, Set.of()));
        var story = compiler.compile(source, Set.of("engine:message"));
        assertThrows(UnsupportedOperationException.class, () -> story.nodes().clear());
        assertEquals(64, story.contentHash().length());
    }
}
