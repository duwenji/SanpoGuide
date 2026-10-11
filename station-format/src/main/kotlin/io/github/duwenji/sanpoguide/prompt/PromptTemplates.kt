package io.github.duwenji.sanpoguide.prompt

import java.util.concurrent.ConcurrentHashMap

/**
 * Renders prompt files written in a small subset of Mustache:
 *
 * - `{{name}}` inserts a value; `{{.}}` is the current list item
 * - `{{#name}}...{{/name}}` renders its body if the value is present and non-empty:
 *   once for a string, number, true or map (whose keys become visible inside), or once per
 *   item for a list
 * - `{{^name}}...{{/name}}` renders its body if the value is missing, empty or false
 * - `{{> path}}` includes another prompt file
 * - `{{! ... }}` is a comment
 *
 * A tag alone on its line removes that whole line, so sections don't leave blank lines behind.
 * Every name used must be passed in (use null for "absent"), so a typo fails loudly instead of
 * silently producing an incomplete prompt.
 *
 * @param load returns the text of a prompt file, given its path without the `.md` extension
 */
class PromptTemplates(private val load: (String) -> String) {
    private val sources = ConcurrentHashMap<String, String>()

    fun render(name: String, vars: Map<String, Any?> = emptyMap()): String =
        renderText(source(name), listOf(vars))
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
            .replace(ESCAPED_OPEN, "{{")

    private fun source(name: String): String = sources.getOrPut(name) {
        val text = load(name).replace("\r\n", "\n")
        prepare(if (text.endsWith("\n")) text else "$text\n")
    }

    /** Drops comments and the line breaks of standalone tags, once per file. */
    private fun prepare(text: String): String = text
        .replace(STANDALONE_COMMENT, "")
        .replace(COMMENT, "")
        .replace(STANDALONE_TAG, "$1")

    private fun renderText(text: String, ctx: List<Map<String, Any?>>): String {
        val withSections = SECTION.replace(text) { m ->
            val (kind, name, body) = m.destructured
            val value = lookup(name, ctx)
            when {
                kind == "^" -> if (truthy(value)) "" else renderText(body, ctx)
                !truthy(value) -> ""
                value is List<*> -> value.joinToString("") { item -> renderText(body, ctx + itemContext(item)) }
                value is Map<*, *> -> renderText(body, ctx + itemContext(value))
                else -> renderText(body, ctx)
            }
        }
        val withPartials = PARTIAL.replace(withSections) { m -> renderText(source(m.groupValues[1]), ctx) }
        // Inserted values (e.g. earlier model output) are protected from being read as tags by
        // the passes that run over the already-rendered text.
        return VARIABLE.replace(withPartials) { m ->
            lookup(m.groupValues[1], ctx)?.toString().orEmpty().replace("{{", ESCAPED_OPEN)
        }
    }

    private fun lookup(name: String, ctx: List<Map<String, Any?>>): Any? {
        for (scope in ctx.asReversed()) {
            if (name in scope) return scope[name]
        }
        throw IllegalArgumentException("Prompt variable \"$name\" was not provided")
    }

    @Suppress("UNCHECKED_CAST")
    private fun itemContext(item: Any?): Map<String, Any?> =
        if (item is Map<*, *>) item as Map<String, Any?> else mapOf("." to item)

    private fun truthy(value: Any?): Boolean = when (value) {
        null -> false
        is Boolean -> value
        is CharSequence -> value.isNotEmpty()
        is Collection<*> -> value.isNotEmpty()
        else -> true
    }

    private companion object {
        const val ESCAPED_OPEN = ""
        val STANDALONE_COMMENT = Regex("(?m)^[ \\t]*\\{\\{![\\s\\S]*?\\}\\}[ \\t]*\\n")
        val COMMENT = Regex("\\{\\{![\\s\\S]*?\\}\\}")
        val STANDALONE_TAG = Regex("(?m)^[ \\t]*(\\{\\{[#^/>][^}]*\\}\\})[ \\t]*\\n")
        val SECTION = Regex("\\{\\{([#^])\\s*([\\w.]+)\\s*\\}\\}([\\s\\S]*?)\\{\\{/\\s*\\2\\s*\\}\\}")
        val PARTIAL = Regex("\\{\\{>\\s*([\\w/.-]+)\\s*\\}\\}")
        val VARIABLE = Regex("\\{\\{\\s*([\\w.]+)\\s*\\}\\}")
    }
}
