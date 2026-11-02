import dev.storyengine.script.StoryNPC

class Oren : StoryNPC("story:oren", "Орен", 0x36612F, "oren")
class Mira : StoryNPC("story:mira", "Мира", 0x66BBDD, "mira")

val oren = npc<Oren>()
val mira = npc<Mira>()
oren.say("Здесь кто-то был.")
wait(5.seconds)
oren.think("Лучше держаться рядом.")
wait(5.seconds)
mira.whisper("Я слышу шаги за стеной.")
