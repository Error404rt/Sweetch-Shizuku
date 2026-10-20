package com.error404rt.sweetch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionCatalogTest {
    @Test
    fun actionIdsAreUniqueAndStable() {
        val ids = ActionCatalog.items.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.containsAll(listOf("download", "share", "copy", "open", "history", "settings")))
    }

    @Test
    fun catalogContainsOriginalThreeOperationWheels() {
        assertTrue(ActionCatalog.items.any { it.wheel == WheelKind.CONVERT })
        assertTrue(ActionCatalog.items.any { it.wheel == WheelKind.TOOLS })
        assertTrue(ActionCatalog.items.any { it.wheel == WheelKind.ACTIONS })
    }

    @Test
    fun conversionAndToolActionsHaveRealOperationKinds() {
        assertTrue(ActionCatalog.items.any { it.kind == ActionKind.JPEG })
        assertTrue(ActionCatalog.items.any { it.kind == ActionKind.MP3 })
        assertTrue(ActionCatalog.items.any { it.kind == ActionKind.MP4 })
        assertTrue(ActionCatalog.items.any { it.kind == ActionKind.COMPRESS })
        assertTrue(ActionCatalog.items.any { it.kind == ActionKind.VIDEO_COMPRESS })
        assertTrue(ActionCatalog.items.any { it.kind == ActionKind.PDF_TO_IMAGES })
        assertTrue(ActionCatalog.items.any { it.kind == ActionKind.IMAGES_TO_PDF })
    }
}
