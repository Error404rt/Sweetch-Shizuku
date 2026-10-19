package com.error404rt.sweetch

/**
 * Shared catalog keeps the original wheel and the puzzle menu in sync.
 * Download handlers will be connected to these stable action IDs in a later step.
 */
internal data class MenuAction(
    val id: String,
    val title: String,
    val glyph: String,
    val description: String
)

internal object ActionCatalog {
    val items = listOf(
        MenuAction("download", "Скачать", "↓", "Загрузка доступного контента"),
        MenuAction("audio", "Аудио", "♫", "Извлечение и обработка звука"),
        MenuAction("gallery", "Галерея", "▧", "Изображения и коллекции"),
        MenuAction("text", "Текст", "T", "Копирование и действия с текстом"),
        MenuAction("files", "Файлы", "▤", "Файловые операции и конвертация"),
        MenuAction("more", "Ещё", "•••", "Дополнительные контекстные действия")
    )
}
