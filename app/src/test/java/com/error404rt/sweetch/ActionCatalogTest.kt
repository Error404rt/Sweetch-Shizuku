package com.error404rt.sweetch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionCatalogTest {
    @Test
    fun actionIdsAreUniqueAndStable() {
        val ids = ActionCatalog.items.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            listOf("download", "audio", "gallery", "text", "files", "more"),
            ids
        )
    }

    @Test
    fun bothMenuModesShareSixActions() {
        assertEquals(6, ActionCatalog.items.size)
        assertTrue(ActionCatalog.items.all { it.title.isNotBlank() && it.glyph.isNotBlank() })
    }
}
