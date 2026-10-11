package io.github.duwenji.sanpoguide.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PromptTemplatesTest {
    private fun templates(vararg files: Pair<String, String>) = PromptTemplates { name ->
        files.toMap()[name] ?: error("no file $name")
    }

    private fun render(text: String, vars: Map<String, Any?> = emptyMap()) =
        templates("t" to text).render("t", vars)

    @Test
    fun `substitutes variables`() {
        assertEquals("こんにちは、太郎さん", render("こんにちは、{{name}}さん", mapOf("name" to "太郎")))
    }

    @Test
    fun `section renders only when the value is present and non-empty`() {
        val text = "A{{#x}}[{{x}}]{{/x}}B"
        assertEquals("A[1]B", render(text, mapOf("x" to 1)))
        assertEquals("AB", render(text, mapOf("x" to null)))
        assertEquals("AB", render(text, mapOf("x" to "")))
        assertEquals("AB", render(text, mapOf("x" to false)))
    }

    @Test
    fun `inverted section renders when the value is absent`() {
        val text = "{{^x}}none{{/x}}{{#x}}some{{/x}}"
        assertEquals("none", render(text, mapOf("x" to null)))
        assertEquals("some", render(text, mapOf("x" to "v")))
    }

    @Test
    fun `list section repeats per item`() {
        assertEquals("- a\n- b", render("{{#items}}\n- {{.}}\n{{/items}}\n", mapOf("items" to listOf("a", "b"))))
    }

    @Test
    fun `map section exposes its keys, falling back to outer ones`() {
        val vars = mapOf("walk" to mapOf("km" to "1.2"), "unit" to "km")
        assertEquals("1.2km", render("{{#walk}}{{km}}{{unit}}{{/walk}}", vars))
    }

    @Test
    fun `standalone tags and comments leave no blank lines`() {
        val text = "{{! header comment }}\nfirst\n{{#x}}\nmiddle\n{{/x}}\nlast\n"
        assertEquals("first\nlast", render(text, mapOf("x" to null)))
        assertEquals("first\nmiddle\nlast", render(text, mapOf("x" to true)))
    }

    @Test
    fun `includes partials with the same variables`() {
        val t = templates("main" to "# rules\n{{> part}}\nend\n", "part" to "- be {{tone}}\n")
        assertEquals("# rules\n- be kind\nend", t.render("main", mapOf("tone" to "kind")))
    }

    @Test
    fun `missing variable fails instead of rendering empty`() {
        assertThrows(IllegalArgumentException::class.java) { render("{{typo}}") }
    }

    @Test
    fun `values containing braces are not re-read as tags`() {
        assertEquals("said {{x}}", render("said {{v}}", mapOf("v" to "{{x}}")))
    }
}
