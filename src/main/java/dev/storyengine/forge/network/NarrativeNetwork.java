package dev.storyengine.forge.network;

import dev.storyengine.forge.client.NarrativeOverlay;
import dev.storyengine.core.StoryDefinition.DialogueMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Bounded server-to-client presentation protocol; no client commands or file paths are accepted. */
public final class NarrativeNetwork {
    private record Line(String speaker, String head, int color, String text, DialogueMode mode, boolean clear) {}
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("storyengine", "narrative"), () -> "3", "3"::equals, "3"::equals);
    public static void register() {
        CHANNEL.messageBuilder(Line.class, 0, NetworkDirection.PLAY_TO_CLIENT)
            .encoder((line, buf) -> { buf.writeUtf(line.speaker, 64); buf.writeUtf(line.head, 64); buf.writeInt(line.color); buf.writeUtf(line.text, 4096); buf.writeEnum(line.mode); buf.writeBoolean(line.clear); })
            .decoder(buf -> new Line(buf.readUtf(64), buf.readUtf(64), buf.readInt(), buf.readUtf(4096), buf.readEnum(DialogueMode.class), buf.readBoolean()))
            .consumerMainThread((line, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientHandler.accept(line)))
            .add();
    }
    private static final class ClientHandler {
        static void accept(Line line) {
            if (line.clear) NarrativeOverlay.clear();
            else NarrativeOverlay.enqueue(line.speaker, line.head, line.color, line.text, line.mode);
        }
    }
    public static void send(ServerPlayer player, String text) {
        send(player, "Narrator", "narrator", 0xFFE066, text);
    }
    public static void send(ServerPlayer player, String speaker, String head, int color, String text) {
        send(player, speaker, head, color, text, DialogueMode.SAY);
    }
    public static void send(ServerPlayer player, String speaker, String head, int color, String text, DialogueMode mode) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Line(speaker, head, color, text, mode, false));
    }
    public static void clear(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Line("", "", 0, "", DialogueMode.SAY, true));
    }
}
