package dev.storyengine.forge;

import dev.storyengine.core.StoryDefinition;
import dev.storyengine.script.StoryNPC;
import dev.storyengine.script.StoryScriptBuilder;

/** Built-in presentation check; independent of user-editable scripts. */
final class ChatDemoStory {
    private ChatDemoStory() {}
    private static final class Narrator extends StoryNPC {
        Narrator() { super("demo:narrator", "Narrator", 0xFFE066, "narrator"); }
    }

    static StoryDefinition create() {
        var script = new StoryScriptBuilder("demo:chat_preview");
        var narrator = script.npc(new Narrator());
        narrator.say("Проверка сюжетного чата. Эта реплика должна появиться по центру внизу экрана.");
        script.waitTicks(260);
        narrator.say("Текст появляется постепенно. Слева должна быть голова рассказчика.");
        script.waitTicks(240);
        narrator.say("Проверка завершена. Окно само исчезнет через несколько секунд.");
        return script.build();
    }
}
