import dev.storyengine.script.StoryNPC

class Guide : StoryNPC("story:guide", "Проводник", 0x36612F, "guide")

val guide = npc<Guide>()
guide.say("Нажми правой кнопкой по любому блоку, чтобы продолжить.")
awaitEvent("engine:block_interact")
guide.say("Отлично. Идём дальше.")
