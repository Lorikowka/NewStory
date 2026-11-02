import dev.storyengine.script.StoryNPC
import dev.storyengine.core.StoryDefinition.PortraitMode

class Oren : StoryNPC("story:oren", "Орен", 0x36612F, "oren")
class Voice : StoryNPC("story:voice", "Голос", 0xBBBBBB, "voice", PortraitMode.NONE)
class Guide : StoryNPC("story:guide", "Проводник", 0x66BBDD, "guide", PortraitMode.FALLBACK)

npc<Oren>().say("Мой портрет берётся из oren.png, если файл есть.")
wait(6.seconds)
npc<Voice>().think("У этой реплики нет портрета и отступа под него.")
wait(6.seconds)
npc<Guide>().say("Здесь всегда стандартная голова, даже если guide.png существует.")
