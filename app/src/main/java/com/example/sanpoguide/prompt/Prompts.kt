package com.example.sanpoguide.prompt

import android.content.res.AssetManager

/**
 * The app's prompt files, kept under `assets/prompts/` (see docs/prompts.md).
 * Paths are listed here so code and files can't drift apart silently; a unit test renders
 * every one of them.
 */
object Prompts {
    const val ASSET_DIR = "prompts"

    object Guide {
        const val SYSTEM = "guide/system"
        const val USER = "guide/user"
    }

    object Talk {
        const val SYSTEM = "companion/system"
        const val SITUATION = "companion/situation"
    }

    /** One file per walk event, in `companion/events/` and `fallback/companion/`. */
    enum class Event(val file: String) { START("start"), REVISIT("revisit"), MILESTONE("milestone"), REST("rest"), FINISH("finish") }

    fun event(e: Event) = "companion/events/${e.file}"

    object Fallback {
        const val GUIDE = "fallback/guide"
        const val NEARBY = "fallback/nearby"
        fun companion(e: Event) = "fallback/companion/${e.file}"
    }

    object ConnectionTest {
        const val SYSTEM = "connection_test/system"
        const val USER = "connection_test/user"
    }

    fun fromAssets(assets: AssetManager) = PromptTemplates { name ->
        assets.open("$ASSET_DIR/$name.md").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
