package com.example.sanpoguide.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommonsFileTest {
    @Test
    fun `file from the wikimedia_commons tag`() {
        assertEquals("Tsurugaoka Hachimangu.jpg", CommonsFile.fromTags(mapOf("wikimedia_commons" to "File:Tsurugaoka Hachimangu.jpg")))
        // A category is not a photo.
        assertNull(CommonsFile.fromTags(mapOf("wikimedia_commons" to "Category:Tsurugaoka Hachimangu")))
    }

    @Test
    fun `file from a Commons image link`() {
        assertEquals(
            "鶴岡八幡宮 本宮.jpg",
            CommonsFile.fromTags(mapOf("image" to "https://commons.wikimedia.org/wiki/File:%E9%B6%B4%E5%B2%A1%E5%85%AB%E5%B9%A1%E5%AE%AE_%E6%9C%AC%E5%AE%AE.jpg")),
        )
        assertEquals(
            "Daibutsu Kamakura.jpg",
            CommonsFile.fromTags(mapOf("image" to "https://upload.wikimedia.org/wikipedia/commons/a/ab/Daibutsu_Kamakura.jpg")),
        )
        assertNull(CommonsFile.fromTags(mapOf("image" to "https://example.com/photo.jpg")))
    }

    @Test
    fun `wikidata id only when well formed`() {
        assertEquals("Q1146088", CommonsFile.wikidataId(mapOf("wikidata" to "Q1146088")))
        assertNull(CommonsFile.wikidataId(mapOf("wikidata" to "Q1146088;Q2")))
        assertNull(CommonsFile.wikidataId(emptyMap()))
    }

    @Test
    fun `credits lose their markup`() {
        assertEquals(
            "Taro Yamada & friends",
            CommonsFile.plainText("<a href=\"//commons.wikimedia.org/wiki/User:Taro\" title=\"User:Taro\">Taro Yamada</a> &amp; friends\n"),
        )
        assertNull(CommonsFile.plainText("<span></span>"))
        assertNull(CommonsFile.plainText(null))
    }
}
