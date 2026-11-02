package dev.storyengine.script;

import dev.storyengine.core.StoryId;
import dev.storyengine.core.StoryDefinition.PortraitMode;
import java.util.Objects;

/** Character description independent of a physical Minecraft entity. */
public abstract class StoryNPC {
    private final StoryId id;
    private final String name;
    private final int nameColor;
    private final String portraitId;
    private final PortraitMode portraitMode;

    protected StoryNPC(String id, String name, int nameColor, String portraitId) {
        this(id, name, nameColor, portraitId, PortraitMode.CUSTOM_TEXTURE);
    }

    protected StoryNPC(String id, String name, int nameColor, String portraitId, PortraitMode portraitMode) {
        this.portraitMode = Objects.requireNonNull(portraitMode, "portraitMode");
        this.id = new StoryId(id);
        this.name = Objects.requireNonNull(name, "name").trim();
        if (this.name.isEmpty() || this.name.length() > 64) throw new IllegalArgumentException("name must have 1..64 characters");
        if (nameColor < 0 || nameColor > 0xFFFFFF) throw new IllegalArgumentException("nameColor must be RGB");
        this.nameColor = nameColor;
        this.portraitId = Objects.requireNonNull(portraitId, "portraitId");
        if (!portraitId.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid portraitId");
    }

    public final StoryId id() { return id; }
    public final String name() { return name; }
    public final int nameColor() { return nameColor; }
    public final String portraitId() { return portraitId; }
    public final PortraitMode portraitMode() { return portraitMode; }
}
