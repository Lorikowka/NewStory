package dev.storyengine.forge;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.storyengine.compiler.StoryCompiler;
import dev.storyengine.core.*;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Server-owned bridge; never loads client renderer classes on a dedicated server. */
@Mod(StoryEngineMod.MOD_ID)
public final class StoryEngineMod {
    public static final String MOD_ID = "storyengine";
    private final Map<UUID, StoryInstanceId> players = new HashMap<>();
    private StoryRuntime runtime;
    private StoryDefinition temple;
    private Map<String, StoryDefinition> localScripts = Map.of();
    private CompletableFuture<LocalScriptLibrary.Result> pendingCompilation;
    private net.minecraft.commands.CommandSourceStack compilationSource;
    private String validationId;
    private final StoryDefinition chatDemo = ChatDemoStory.create();
    private long tick;

    public StoryEngineMod() { StoryFiles.initialize(); dev.storyengine.forge.network.NarrativeNetwork.register(); MinecraftForge.EVENT_BUS.register(this); }

    @SubscribeEvent
    public void starting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        tick = 0;
        players.clear();
        pendingCompilation = null;
        compilationSource = null;
        validationId = null;
        runtime = new StoryRuntime(new StoryPlatform() {
            @Override public boolean apply(StoryInstanceId id, String key, String type, String value) {
                ServerPlayer player = playerFor(server, id);
                if (player == null) return false;
                if (type.equals("engine:title")) player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(value)));
                else if (type.equals("engine:message")) dev.storyengine.forge.network.NarrativeNetwork.send(player, value);
                else return false;
                return true;
            }
            @Override public boolean applyDialogue(StoryInstanceId id, String key, StoryDefinition.Dialogue dialogue) {
                ServerPlayer player = playerFor(server, id);
                if (player == null) return false;
                var speaker = dialogue.speaker();
                dev.storyengine.forge.network.NarrativeNetwork.send(player, speaker.name(), speaker.portraitId(), speaker.color(), dialogue.text(), dialogue.mode());
                return true;
            }
        }, 256, 128);
        try {
            temple = new StoryCompiler().compile(StoryFiles.root().resolve("scripts/temple.json"),
                    Set.of("engine:title", "engine:message"));
        } catch (IOException | RuntimeException ex) {
            temple = null;
            com.mojang.logging.LogUtils.getLogger().error("Cannot load external temple story; /story chatdemo remains available", ex);
        }
        var loaded = LocalScriptLibrary.load(StoryFiles.root().resolve("scripts"));
        localScripts = loaded.stories();
        loaded.errors().forEach(message -> com.mojang.logging.LogUtils.getLogger().warn("Story script: {}", message));
        com.mojang.logging.LogUtils.getLogger().info("Loaded {} local Kotlin stories", localScripts.size());
    }

    @SubscribeEvent
    public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("story").requires(source -> source.hasPermission(2))
            .then(Commands.literal("reload").executes(ctx -> {
                if (runtime == null) return 0;
                if (pendingCompilation != null) {
                    ctx.getSource().sendFailure(Component.literal("Script compilation is already running"));
                    return 0;
                }
                var previous = localScripts;
                var directory = StoryFiles.root().resolve("scripts");
                compilationSource = ctx.getSource();
                validationId = null;
                pendingCompilation = LocalScriptLibrary.reloadAsync(directory, previous);
                ctx.getSource().sendSuccess(() -> Component.literal("Reloading trusted local Kotlin scripts..."), false);
                return 1;
            }))
            .then(Commands.literal("list").executes(ctx -> {
                String names = (temple == null ? "" : "demo:temple, ") + "demo:chat_preview (/story chatdemo)";
                if (!localScripts.isEmpty()) names += ", " + String.join(", ", new TreeSet<>(localScripts.keySet()));
                String result = names;
                ctx.getSource().sendSuccess(() -> Component.literal(result), false); return 1;
            }))
            .then(Commands.literal("validate").executes(ctx -> {
                ctx.getSource().sendSuccess(() -> Component.literal(temple == null ? "Not loaded" : "VALID demo:temple " + temple.contentHash()), false); return temple == null ? 0 : 1;
            }).then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                String id = StringArgumentType.getString(ctx, "id");
                if (id.startsWith("local:")) {
                    if (runtime == null) return 0;
                    if (pendingCompilation != null) {
                        ctx.getSource().sendFailure(Component.literal("Script compilation is already running"));
                        return 0;
                    }
                    compilationSource = ctx.getSource();
                    validationId = id;
                    pendingCompilation = LocalScriptLibrary.validateAsync(StoryFiles.root().resolve("scripts"), id);
                    ctx.getSource().sendSuccess(() -> Component.literal("Validating current file for " + id + "..."), false);
                    return 1;
                }
                StoryDefinition story = definition(id);
                if (story == null) { ctx.getSource().sendFailure(Component.literal("Unknown story: " + id)); return 0; }
                ctx.getSource().sendSuccess(() -> Component.literal("VALID " + id + " " + story.contentHash()), false);
                return 1;
            })))
            .then(Commands.literal("start").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                if (runtime == null || temple == null) return 0;
                start(player, temple);
                player.sendSystemMessage(Component.literal("Story started. Use /story signal demo:enter_temple to approach the temple."));
                return 1;
            }).then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                String id = StringArgumentType.getString(ctx, "id");
                StoryDefinition story = definition(id);
                if (runtime == null || story == null) { ctx.getSource().sendFailure(Component.literal("Unknown story: " + id)); return 0; }
                start(player, story);
                ctx.getSource().sendSuccess(() -> Component.literal("Story started: " + id), false);
                return 1;
            })))
            .then(Commands.literal("chatdemo").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                if (runtime == null) return 0;
                start(player, chatDemo);
                ctx.getSource().sendSuccess(() -> Component.literal("Chat demo started: three automatic lines."), false);
                return 1;
            }))
            .then(Commands.literal("stop").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException(); stop(player.getUUID()); dev.storyengine.forge.network.NarrativeNetwork.clear(player); return 1;
            }))
            .then(Commands.literal("inspect").executes(ctx -> {
                var id = players.get(ctx.getSource().getPlayerOrException().getUUID());
                String state = id == null ? "No active story" : runtime.inspect(id).map(Object::toString).orElse("No active story");
                ctx.getSource().sendSuccess(() -> Component.literal(state), false); return 1;
            }))
            .then(Commands.literal("signal").then(Commands.argument("type", StringArgumentType.greedyString()).executes(ctx -> {
                var id = players.get(ctx.getSource().getPlayerOrException().getUUID());
                return id != null && runtime.publish(new StoryRuntime.Event(id, StringArgumentType.getString(ctx, "type"))) ? 1 : 0;
            }))));
    }
    private void start(ServerPlayer player, StoryDefinition story) {
        stop(player.getUUID());
        dev.storyengine.forge.network.NarrativeNetwork.clear(player);
        var id = new StoryInstanceId(UUID.randomUUID());
        runtime.start(id, story);
        players.put(player.getUUID(), id);
    }
    private StoryDefinition definition(String id) {
        if ("demo:temple".equals(id)) return temple;
        if ("demo:chat_preview".equals(id)) return chatDemo;
        return localScripts.get(id);
    }
    private ServerPlayer playerFor(MinecraftServer server, StoryInstanceId instance) {
        UUID playerId = players.entrySet().stream().filter(e -> e.getValue().equals(instance))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        return playerId == null ? null : server.getPlayerList().getPlayer(playerId);
    }
    private void stop(UUID player) {
        var id = players.remove(player);
        if (id != null && runtime != null) { runtime.cancel(id); runtime.forget(id); }
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide) stop(event.getEntity().getUUID());
    }
    @SubscribeEvent public void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player.getUUID()); dev.storyengine.forge.network.NarrativeNetwork.clear(player);
        }
    }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { stop(event.getEntity().getUUID()); if (event.getEntity() instanceof ServerPlayer player) dev.storyengine.forge.network.NarrativeNetwork.clear(player); }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public void interact(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player) || runtime == null) return;
        var id = players.get(player.getUUID());
        if (id != null && runtime.inspect(id).map(view -> "engine:block_interact".equals(view.waitingEvent())).orElse(false)) {
            if (!runtime.publish(new StoryRuntime.Event(id, "engine:block_interact"))) {
                com.mojang.logging.LogUtils.getLogger().warn("Story event queue full; block interaction was not delivered");
            }
        }
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && runtime != null) {
            if (pendingCompilation != null && pendingCompilation.isDone()) {
                try {
                    var result = pendingCompilation.join();
                    if (validationId != null) {
                        if (result.errors().isEmpty()) {
                            var checked = result.stories().get(validationId);
                            String message = "VALID file scripts/" + validationId.substring(6) + ".se.kts " + checked.contentHash()
                                    + " (not activated; use /story reload)";
                            compilationSource.sendSuccess(() -> Component.literal(message), false);
                        } else {
                            result.errors().forEach(message -> compilationSource.sendFailure(Component.literal(message)));
                        }
                    } else if (result.errors().isEmpty()) {
                        localScripts = result.stories();
                        int count = localScripts.size();
                        compilationSource.sendSuccess(() -> Component.literal("Reloaded " + count + " Kotlin stories. Active stories keep their previous graph."), false);
                    } else {
                        compilationSource.sendFailure(Component.literal("Reload rejected; previous catalog retained. See server log."));
                        result.errors().forEach(message -> com.mojang.logging.LogUtils.getLogger().warn("Story reload: {}", message));
                    }
                } catch (RuntimeException ex) {
                    com.mojang.logging.LogUtils.getLogger().error("Story compilation failed; previous catalog retained", ex);
                    compilationSource.sendFailure(Component.literal("Script compilation failed; previous catalog retained. See server log."));
                } finally {
                    pendingCompilation = null;
                    compilationSource = null;
                    validationId = null;
                }
            }
            runtime.tick(tick++, 100, 1000, 20);
        }
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) {
        players.clear(); runtime = null; temple = null; localScripts = Map.of();
        if (pendingCompilation != null) pendingCompilation.cancel(true);
        pendingCompilation = null; compilationSource = null; validationId = null;
    }
}
