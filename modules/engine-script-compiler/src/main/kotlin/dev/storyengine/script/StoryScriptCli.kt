package dev.storyengine.script

import java.nio.file.Path

fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: <script.se.kts> <namespace:story_id>" }
    val graph = KotlinStoryCompiler().compile(Path.of(args[0]), args[1])
    println("VALID ${graph.id().value()} ${graph.contentHash()} nodes=${graph.nodes().size}")
}
