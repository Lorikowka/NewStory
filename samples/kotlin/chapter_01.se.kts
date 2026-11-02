import dev.storyengine.script.StoryNPC

class Oren : StoryNPC("story:oren", "Орен", 0x36612F, "oren")

val oren = npc<Oren>()
oren.say("Что это за место?")
wait(2.seconds)
oren.say("Тише... Нас могут услышать.")
