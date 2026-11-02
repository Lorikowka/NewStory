package dev.storyengine.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SignalCommandTest {
    @Test void acceptsNamespacedEventWithoutQuotes() throws Exception {
        var dispatcher = new CommandDispatcher<StringBuilder>();
        dispatcher.register(LiteralArgumentBuilder.<StringBuilder>literal("signal")
            .then(RequiredArgumentBuilder.<StringBuilder, String>argument("type", StringArgumentType.greedyString())
                .executes(context -> {
                    context.getSource().append(StringArgumentType.getString(context, "type"));
                    return 1;
                })));
        var result = new StringBuilder();
        assertEquals(1, dispatcher.execute("signal demo:enter_temple", result));
        assertEquals("demo:enter_temple", result.toString());
    }
}
