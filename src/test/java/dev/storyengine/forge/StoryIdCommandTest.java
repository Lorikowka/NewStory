package dev.storyengine.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoryIdCommandTest {
    @Test void acceptsNamespacedStoryIdsForStartAndValidate() throws Exception {
        var dispatcher = new CommandDispatcher<StringBuilder>();
        for (String command : new String[]{"start", "validate"}) {
            dispatcher.register(LiteralArgumentBuilder.<StringBuilder>literal(command)
                    .then(RequiredArgumentBuilder.<StringBuilder, ResourceLocation>argument("id", ResourceLocationArgument.id())
                            .executes(context -> {
                                context.getSource().append(context.getArgument("id", ResourceLocation.class));
                                return 1;
                            })));
            var source = new StringBuilder();
            assertEquals(1, dispatcher.execute(command + " local:portrait_modes", source));
            assertEquals("local:portrait_modes", source.toString());
        }
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute("start local:portrait_modes unexpected", new StringBuilder()));
    }
}
