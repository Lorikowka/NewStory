package dev.storyengine.forge.client;

import dev.storyengine.forge.StoryFiles;
import dev.storyengine.core.StoryDefinition.DialogueMode;
import dev.storyengine.core.StoryDefinition.PortraitMode;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import javax.imageio.ImageIO;

/** Client-only presentation. No NPC lookup: heads are local PNG assets with a built-in fallback. */
@Mod.EventBusSubscriber(modid = "storyengine", value = Dist.CLIENT)
public final class NarrativeOverlay {
    private record Line(String speaker, String head, int color, String text, PortraitMode portraitMode) {}
    private static final ArrayDeque<Line> queue = new ArrayDeque<>();
    private static final ResourceLocation FALLBACK = new ResourceLocation("minecraft", "textures/entity/player/wide/steve.png");
    private static final int HEAD_SIZE = 16;
    private static final int MAX_TEXT_WIDTH = 260;
    private static Line current;
    private static int age;
    private static ResourceLocation portrait;
    public static void enqueue(String speaker, String head, int color, String text, DialogueMode mode, PortraitMode portraitMode) {
        String presented = switch (mode) {
            case SAY -> text;
            case THOUGHT -> "(" + text + ")";
            case WHISPER -> "(шёпотом) " + text;
        };
        if (queue.size() < 32) queue.add(new Line(speaker, head, color & 0xFFFFFF, presented, portraitMode));
    }
    public static void clear() {
        queue.clear(); current = null; age = 0;
        if (portrait != null) Minecraft.getInstance().getTextureManager().release(portrait);
        portrait = null;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.isPaused() || mc.level == null) return;
        if (current != null && ++age >= Math.min(2400, 80 + current.text.codePointCount(0, current.text.length()) * 2)) {
            current = null;
            if (portrait != null) mc.getTextureManager().release(portrait);
            portrait = null;
        }
        if (current == null && !queue.isEmpty()) {
            current = queue.remove(); age = 0;
            portrait = current.portraitMode == PortraitMode.CUSTOM_TEXTURE ? loadHead(current.head) : null;
        }
    }
    private static ResourceLocation loadHead(String name) {
        if (!name.matches("[a-z0-9_-]{1,64}")) return null;
        Path file = StoryFiles.root().resolve("assets/heads/" + name + ".png");
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > 262144) return null;
            byte[] bytes;
            try (InputStream input = Files.newInputStream(file)) { bytes = input.readNBytes(262145); }
            if (bytes.length > 262144) return null;
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) return null;
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    if (!reader.getFormatName().equalsIgnoreCase("png") || reader.getWidth(0) > 256 || reader.getHeight(0) > 256) return null;
                } finally { reader.dispose(); }
            }
            NativeImage image = NativeImage.read(bytes);
            return Minecraft.getInstance().getTextureManager().register("storyengine_head", new DynamicTexture(image));
        } catch (IOException | RuntimeException ex) {
            com.mojang.logging.LogUtils.getLogger().warn("Cannot load narrative head {}: {}", name, ex.toString());
            return null;
        }
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (current == null || mc.screen != null || mc.options.hideGui || mc.player == null) return;
        var gui = event.getGuiGraphics();
        int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
        boolean showHead = current.portraitMode != PortraitMode.NONE;
        int iconSpace = showHead ? 22 : 0;
        int wrap = Math.max(20, Math.min(MAX_TEXT_WIDTH, width - 20 - iconSpace));
        var full = Component.empty()
                .append(Component.literal("[" + current.speaker + "]")
                        .withStyle(style -> style.withColor(current.color)))
                .append(Component.literal(": " + current.text)
                        .withStyle(style -> style.withColor(0xFFFFFF)));
        List<FormattedCharSequence> lines = mc.font.split(full, wrap);
        int maxLines = Math.max(1, (height - 80) / 10);
        int lineCount = Math.min(lines.size(), maxLines);
        int textWidth = lines.stream().mapToInt(mc.font::width).max().orElse(20);
        int boxWidth = Math.min(width - 8, Math.max(20, textWidth) + 12 + iconSpace);
        int boxHeight = Math.max(showHead ? HEAD_SIZE : 0, lineCount * 10) + 12;
        int left = (width - boxWidth) / 2, top = Math.max(4, height - 50 - boxHeight);
        gui.fill(left, top, left + boxWidth, top + boxHeight, 0xB0101010);
        int iconY = top + (boxHeight - HEAD_SIZE) / 2;
        if (showHead) {
            if (portrait == null) gui.blit(FALLBACK, left + 6, iconY, HEAD_SIZE, HEAD_SIZE, 8, 8, 8, 8, 64, 64);
            else gui.blit(portrait, left + 6, iconY, HEAD_SIZE, HEAD_SIZE, 0, 0, 1, 1, 1, 1);
        }
        int visible = current.speaker.codePointCount(0, current.speaker.length()) + 4 + age * 2;
        int[] remaining = {visible};
        int firstLine = 0;
        int revealedLine = 0;
        int[] count = {0};
        for (int i = 0; i < lines.size(); i++) {
            if (count[0] < visible) revealedLine = i;
            lines.get(i).accept((index, style, point) -> { count[0]++; return true; });
        }
        firstLine = Math.max(0, revealedLine - lineCount + 1);
        for (int i = 0; i < firstLine; i++) {
            lines.get(i).accept((index, style, point) -> { remaining[0]--; return true; });
        }
        // Full-text wrapping keeps the box stable while characters appear.
        for (int i = firstLine; i < firstLine + lineCount; i++) {
            var text = Component.empty();
            lines.get(i).accept((index, style, codePoint) -> {
                if (remaining[0]-- <= 0) return false;
                text.append(Component.literal(new String(Character.toChars(codePoint))).setStyle(style)); return true;
            });
            gui.drawString(mc.font, text, left + 6 + iconSpace, top + 6 + (i - firstLine) * 10, 0xFFFFFF, true);
        }
    }
}
