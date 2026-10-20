package com.error404rt.sweetch

internal enum class WheelKind { CONVERT, TOOLS, ACTIONS }
internal enum class ActionKind {
    DOWNLOAD, SHARE, COPY, OPEN, HISTORY, SETTINGS,
    JPEG, PNG, WEBP, MP3, M4A, WAV, MP4, WEBM, GIF,
    COMPRESS, ROTATE, VIDEO_COMPRESS, THUMBNAIL, AUDIO_COMPRESS, AUDIO_NORMALIZE
}
internal enum class Glyph { DOWNLOAD, SHARE, COPY, LINK, HISTORY, SETTINGS }

internal data class ActionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: ActionKind,
    val glyph: Glyph,
    val wheel: WheelKind = WheelKind.ACTIONS
)

internal object ActionCatalog {
    val items = listOf(
        ActionItem("jpeg", "JPEG", "Конвертировать в JPEG", ActionKind.JPEG, Glyph.COPY, WheelKind.CONVERT),
        ActionItem("png", "PNG", "Конвертировать в PNG", ActionKind.PNG, Glyph.COPY, WheelKind.CONVERT),
        ActionItem("webp", "WEBP", "Конвертировать в WEBP", ActionKind.WEBP, Glyph.COPY, WheelKind.CONVERT),
        ActionItem("mp3", "MP3", "Извлечь или конвертировать аудио", ActionKind.MP3, Glyph.SHARE, WheelKind.CONVERT),
        ActionItem("m4a", "M4A", "Конвертировать аудио в M4A", ActionKind.M4A, Glyph.SHARE, WheelKind.CONVERT),
        ActionItem("wav", "WAV", "Конвертировать аудио в WAV", ActionKind.WAV, Glyph.SHARE, WheelKind.CONVERT),
        ActionItem("mp4", "MP4", "Конвертировать видео в MP4", ActionKind.MP4, Glyph.LINK, WheelKind.CONVERT),
        ActionItem("webm", "WEBM", "Конвертировать видео в WEBM", ActionKind.WEBM, Glyph.LINK, WheelKind.CONVERT),
        ActionItem("gif", "GIF", "Создать GIF из видео", ActionKind.GIF, Glyph.LINK, WheelKind.CONVERT),
        ActionItem("compress", "Сжать", "Сжать изображение", ActionKind.COMPRESS, Glyph.DOWNLOAD, WheelKind.TOOLS),
        ActionItem("rotate", "Повернуть", "Поворот изображения на 90°", ActionKind.ROTATE, Glyph.SETTINGS, WheelKind.TOOLS),
        ActionItem("video_compress", "Видео", "Сжать видео в MP4", ActionKind.VIDEO_COMPRESS, Glyph.DOWNLOAD, WheelKind.TOOLS),
        ActionItem("thumbnail", "Кадр", "Извлечь миниатюру из видео", ActionKind.THUMBNAIL, Glyph.COPY, WheelKind.TOOLS),
        ActionItem("audio_compress", "Аудио", "Сжать аудио до MP3 128k", ActionKind.AUDIO_COMPRESS, Glyph.DOWNLOAD, WheelKind.TOOLS),
        ActionItem("audio_normalize", "Громкость", "Нормализовать громкость аудио", ActionKind.AUDIO_NORMALIZE, Glyph.SETTINGS, WheelKind.TOOLS),
        ActionItem("download", "Скачать", "Ссылка на файл или медиа", ActionKind.DOWNLOAD, Glyph.DOWNLOAD),
        ActionItem("share", "Поделиться", "Передать ссылку в приложение", ActionKind.SHARE, Glyph.SHARE),
        ActionItem("copy", "Копировать", "Скопировать ссылку", ActionKind.COPY, Glyph.COPY),
        ActionItem("open", "Открыть", "Открыть ссылку в браузере", ActionKind.OPEN, Glyph.LINK),
        ActionItem("history", "Загрузки", "История системных загрузок", ActionKind.HISTORY, Glyph.HISTORY),
        ActionItem("settings", "Настройки", "Режим меню и оформление", ActionKind.SETTINGS, Glyph.SETTINGS)
    )
}
