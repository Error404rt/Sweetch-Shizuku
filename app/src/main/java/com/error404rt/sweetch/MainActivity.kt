package com.error404rt.sweetch

import android.app.DownloadManager
import android.content.Context
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URL
import kotlin.math.cos
import kotlin.math.sin

private val Ink = Color(0xFF171719)
private val Panel = Color(0xFF222225)
private val Tile = Color(0xFF2B2B30)
private val Muted = Color(0xFFAAA9B0)
private val Red = Color(0xFFED2945)
private val RedDeep = Color(0xFF9F1830)
private val Paper = Color(0xFFF4F2F1)
private val PaperPanel = Color(0xFFFFFFFF)
private val PaperText = Color(0xFF242329)
private val PaperMuted = Color(0xFF77747D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SweetchHome(initialText = intent?.getCharSequenceExtra(android.content.Intent.EXTRA_TEXT)?.toString() ?: intent?.getCharSequenceExtra(android.content.Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()) }
    }
}

private enum class MenuStyle { WHEEL, PUZZLE }
private enum class WheelKind { CONVERT, TOOLS, ACTIONS }
private enum class ActionKind { DOWNLOAD, SHARE, COPY, OPEN, HISTORY, SETTINGS, JPEG, PNG, WEBP, COMPRESS, ROTATE }

private data class ActionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: ActionKind,
    val glyph: Glyph,
    val wheel: WheelKind = WheelKind.ACTIONS
)

private enum class Glyph { DOWNLOAD, SHARE, COPY, LINK, HISTORY, SETTINGS }

private val actions = listOf(
    ActionItem("jpeg", "JPEG", "Конвертировать в JPEG", ActionKind.JPEG, Glyph.COPY, WheelKind.CONVERT),
    ActionItem("png", "PNG", "Конвертировать в PNG", ActionKind.PNG, Glyph.COPY, WheelKind.CONVERT),
    ActionItem("webp", "WEBP", "Конвертировать в WEBP", ActionKind.WEBP, Glyph.COPY, WheelKind.CONVERT),
    ActionItem("compress", "Сжать", "Сохранить уменьшенную копию", ActionKind.COMPRESS, Glyph.DOWNLOAD, WheelKind.TOOLS),
    ActionItem("rotate", "Повернуть", "Поворот изображения на 90°", ActionKind.ROTATE, Glyph.SETTINGS, WheelKind.TOOLS),
    ActionItem("download", "Скачать", "Ссылка на файл или медиа", ActionKind.DOWNLOAD, Glyph.DOWNLOAD),
    ActionItem("share", "Поделиться", "Передать ссылку в приложение", ActionKind.SHARE, Glyph.SHARE),
    ActionItem("copy", "Копировать", "Скопировать ссылку", ActionKind.COPY, Glyph.COPY),
    ActionItem("open", "Открыть", "Открыть ссылку в браузере", ActionKind.OPEN, Glyph.LINK),
    ActionItem("history", "Загрузки", "История системных загрузок", ActionKind.HISTORY, Glyph.HISTORY),
    ActionItem("settings", "Настройки", "Режим меню и оформление", ActionKind.SETTINGS, Glyph.SETTINGS)
)

@Composable
private fun SweetchHome(initialText: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dark by rememberSaveable { mutableStateOf(true) }
    var style by rememberSaveable { mutableStateOf(MenuStyle.WHEEL) }
    var wheelKind by rememberSaveable { mutableStateOf(WheelKind.ACTIONS) }
    var selectedId by rememberSaveable { mutableStateOf("download") }
    var url by rememberSaveable { mutableStateOf(initialText) }
    var showUrlDialog by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("Выберите действие") }
    var selectedFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { picked ->
        selectedFiles = picked
        picked.forEach { uri -> try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { } }
        status = if (picked.isEmpty()) "Файлы не выбраны" else "Выбрано файлов: ${picked.size}"
    }
    val visibleActions = actions.filter { it.wheel == wheelKind }
    if (visibleActions.none { it.id == selectedId }) selectedId = visibleActions.first().id
    val selected = visibleActions.first { it.id == selectedId }
    val page = if (dark) Ink else Paper
    val panel = if (dark) Panel else PaperPanel
    val tile = if (dark) Tile else Color(0xFFF1EFF0)
    val text = if (dark) Color(0xFFF7F4F4) else PaperText
    val muted = if (dark) Muted else PaperMuted
    val edge = if (dark) Color(0x33FFFFFF) else Color(0x22242429)
    val accent = Red

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = accent, background = page, surface = panel) else lightColorScheme(primary = accent, background = page, surface = panel)) {
        Surface(Modifier.fillMaxSize(), color = page) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(Red, RedDeep))), contentAlignment = Alignment.Center) {
                        Text("S", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("SWEETCH", color = text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
                        Text("ANDROID · CONTEXT TOOLS", color = muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
                    }
                    Surface(Modifier.size(42.dp).clickable { dark = !dark }, shape = CircleShape, color = panel, border = androidx.compose.foundation.BorderStroke(1.dp, edge)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (dark) "☼" else "◐", color = accent, fontSize = 22.sp)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("КОНТЕКСТНОЕ МЕНЮ", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Нужное действие — сразу", color = text, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
                    Text("Выберите инструмент. Круговое меню сохраняет логику Sweetch, пазл — альтернативная раскладка.", color = muted, fontSize = 13.sp, lineHeight = 19.sp)
                }

                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(panel).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ModeTab("Круговое", style == MenuStyle.WHEEL, Modifier.weight(1f), text, muted, accent, edge) { style = MenuStyle.WHEEL }
                    ModeTab("Пазл", style == MenuStyle.PUZZLE, Modifier.weight(1f), text, muted, accent, edge) { style = MenuStyle.PUZZLE }
                }

                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(panel).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ModeTab("Конвертация", wheelKind == WheelKind.CONVERT, Modifier.weight(1f), text, muted, accent, edge) { wheelKind = WheelKind.CONVERT; selectedId = "jpeg" }
                    ModeTab("Инструменты", wheelKind == WheelKind.TOOLS, Modifier.weight(1f), text, muted, accent, edge) { wheelKind = WheelKind.TOOLS; selectedId = "compress" }
                    ModeTab("Действия", wheelKind == WheelKind.ACTIONS, Modifier.weight(1f), text, muted, accent, edge) { wheelKind = WheelKind.ACTIONS; selectedId = "download" }
                }

                Box(Modifier.fillMaxWidth().height(if (style == MenuStyle.WHEEL) 322.dp else 310.dp), contentAlignment = Alignment.Center) {
                    if (style == MenuStyle.WHEEL) {
                        RadialActions(visibleActions, selectedId, dark, text, muted, panel, tile, edge, accent) { item ->
                            selectedId = item.id
                            when (item.kind) {
                                ActionKind.JPEG, ActionKind.PNG, ActionKind.WEBP -> {
                                    val format = when (item.kind) { ActionKind.JPEG -> "JPEG"; ActionKind.PNG -> "PNG"; else -> "WEBP" }
                                    if (selectedFiles.isEmpty()) status = "Сначала выберите изображения"
                                    else scope.launch {
                                        status = "Конвертирую в $format…"
                                        val result = withContext(Dispatchers.IO) {
                                            var done = 0; var failed = 0
                                            selectedFiles.forEach { uri -> try { MediaFileOperations.convertImage(context, uri, format); done++ } catch (_: Exception) { failed++ } }
                                            done to failed
                                        }
                                        status = "Конвертация $format: ${result.first} из ${selectedFiles.size}" + if (result.second > 0) " · ошибок: ${result.second}" else ""
                                    }
                                }
                                ActionKind.COMPRESS, ActionKind.ROTATE -> {
                                    if (selectedFiles.isEmpty()) status = "Сначала выберите изображения"
                                    else scope.launch {
                                        status = if (item.kind == ActionKind.COMPRESS) "Сжимаю изображения…" else "Поворачиваю изображения…"
                                        val result = withContext(Dispatchers.IO) {
                                            var done = 0; var failed = 0
                                            selectedFiles.forEach { uri ->
                                                try {
                                                    if (item.kind == ActionKind.COMPRESS) MediaFileOperations.compressImage(context, uri)
                                                    else MediaFileOperations.rotateImage(context, uri, 90f)
                                                    done++
                                                } catch (_: Exception) { failed++ }
                                            }
                                            done to failed
                                        }
                                        status = "${item.title}: ${result.first} из ${selectedFiles.size}" + if (result.second > 0) " · ошибок: ${result.second}" else ""
                                    }
                                }
                                ActionKind.DOWNLOAD -> showUrlDialog = true
                                ActionKind.SETTINGS -> showSettings = true
                                ActionKind.HISTORY -> showHistory = true
                                ActionKind.COPY -> { if (url.isNotBlank()) copyText(context, url, status = { status = it }) else status = "Сначала укажите ссылку" }
                                ActionKind.OPEN -> openUrl(context, url) { status = it }
                                ActionKind.SHARE -> shareUrl(context, url) { status = it }
                            }
                        }
                    } else {
                        PuzzleActions(visibleActions, selectedId, dark, text, muted, panel, tile, edge, accent) { item ->
                            selectedId = item.id
                            when (item.kind) {
                                ActionKind.JPEG, ActionKind.PNG, ActionKind.WEBP -> {
                                    val format = when (item.kind) { ActionKind.JPEG -> "JPEG"; ActionKind.PNG -> "PNG"; else -> "WEBP" }
                                    if (selectedFiles.isEmpty()) status = "Сначала выберите изображения"
                                    else scope.launch {
                                        status = "Конвертирую в $format…"
                                        val result = withContext(Dispatchers.IO) {
                                            var done = 0; var failed = 0
                                            selectedFiles.forEach { uri -> try { MediaFileOperations.convertImage(context, uri, format); done++ } catch (_: Exception) { failed++ } }
                                            done to failed
                                        }
                                        status = "Конвертация $format: ${result.first} из ${selectedFiles.size}" + if (result.second > 0) " · ошибок: ${result.second}" else ""
                                    }
                                }
                                ActionKind.COMPRESS, ActionKind.ROTATE -> {
                                    if (selectedFiles.isEmpty()) status = "Сначала выберите изображения"
                                    else scope.launch {
                                        status = if (item.kind == ActionKind.COMPRESS) "Сжимаю изображения…" else "Поворачиваю изображения…"
                                        val result = withContext(Dispatchers.IO) {
                                            var done = 0; var failed = 0
                                            selectedFiles.forEach { uri ->
                                                try {
                                                    if (item.kind == ActionKind.COMPRESS) MediaFileOperations.compressImage(context, uri)
                                                    else MediaFileOperations.rotateImage(context, uri, 90f)
                                                    done++
                                                } catch (_: Exception) { failed++ }
                                            }
                                            done to failed
                                        }
                                        status = "${item.title}: ${result.first} из ${selectedFiles.size}" + if (result.second > 0) " · ошибок: ${result.second}" else ""
                                    }
                                }
                                ActionKind.DOWNLOAD -> showUrlDialog = true
                                ActionKind.SETTINGS -> showSettings = true
                                ActionKind.HISTORY -> showHistory = true
                                ActionKind.COPY -> { if (url.isNotBlank()) copyText(context, url) { status = it } else status = "Сначала укажите ссылку" }
                                ActionKind.OPEN -> openUrl(context, url) { status = it }
                                ActionKind.SHARE -> shareUrl(context, url) { status = it }
                            }
                        }
                    }
                }

                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = panel, border = androidx.compose.foundation.BorderStroke(1.dp, edge)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                                GlyphIcon(selected.glyph, accent, Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(selected.title, color = text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(selected.subtitle, color = muted, fontSize = 11.sp)
                            }
                            Text("●", color = accent, fontSize = 9.sp)
                        }
                        HorizontalDivider(color = edge)
                        Text("ССЫЛКА", color = muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("https://…", color = muted) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = text, unfocusedTextColor = text,
                                focusedBorderColor = accent, unfocusedBorderColor = edge,
                                cursorColor = accent, focusedContainerColor = tile, unfocusedContainerColor = tile
                            )
                        )
                        Button(
                            onClick = { showUrlDialog = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent)
                        ) {
                            GlyphIcon(Glyph.DOWNLOAD, Color.White, Modifier.size(18.dp))
                            Spacer(Modifier.width(9.dp))
                            Text("Загрузить по ссылке", fontWeight = FontWeight.Bold)
                        }
                        Text(status, color = muted, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoCard("01", "Прямые ссылки", "Загрузка файлов через Android DownloadManager", Modifier.weight(1f), panel, edge, accent, text, muted)
                    InfoCard("02", "Системная очередь", "Уведомления и управление загрузкой Android", Modifier.weight(1f), panel, edge, accent, text, muted)
                }
                Text("Sweetch · Android Edition", color = muted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            containerColor = panel,
            title = { Text("Загрузка по ссылке", color = text, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Android DownloadManager принимает прямые HTTP(S)-ссылки на файлы. Страницы видеосервисов требуют отдельного адаптера и здесь не притворяются поддерживаемыми.", color = muted, fontSize = 13.sp, lineHeight = 18.sp)
                    OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("URL") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showUrlDialog = false
                    enqueueDirectDownload(context, url) { status = it }
                }) { Text("Начать", color = accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showUrlDialog = false }) { Text("Отмена", color = muted) } }
        )
    }
    if (showSettings) {
        AlertDialog(onDismissRequest = { showSettings = false }, containerColor = panel, title = { Text("Настройки меню", color = text) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Форма меню", color = muted)
                ModeTab("Круговое", style == MenuStyle.WHEEL, Modifier.fillMaxWidth(), text, muted, accent, edge) { style = MenuStyle.WHEEL }
                ModeTab("Пазл", style == MenuStyle.PUZZLE, Modifier.fillMaxWidth(), text, muted, accent, edge) { style = MenuStyle.PUZZLE }
                Text("Тема: ${if (dark) "тёмная" else "светлая"}", color = muted)
            }
        }, confirmButton = { TextButton(onClick = { dark = !dark }) { Text("Сменить тему", color = accent) } }, dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Готово", color = muted) } })
    }
    if (showHistory) {
        AlertDialog(onDismissRequest = { showHistory = false }, containerColor = panel, title = { Text("Загрузки Android", color = text) }, text = {
            Text("Очередь и завершённые файлы доступны в системном приложении «Загрузки». Sweetch передаёт прямые ссылки системному DownloadManager, чтобы загрузки переживали закрытие приложения.", color = muted, fontSize = 13.sp, lineHeight = 19.sp)
        }, confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Понятно", color = accent) } })
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, modifier: Modifier, text: Color, muted: Color, accent: Color, edge: Color, onClick: () -> Unit) {
    Surface(modifier.height(44.dp).clickable(onClick = onClick), shape = RoundedCornerShape(12.dp), color = if (selected) accent.copy(alpha = .14f) else Color.Transparent, border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) accent else Color.Transparent)) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) text else muted, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun RadialActions(items: List<ActionItem>, selectedId: String, dark: Boolean, text: Color, muted: Color, panel: Color, tile: Color, edge: Color, accent: Color, onAction: (ActionItem) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val d = minOf(maxWidth, maxHeight).coerceAtMost(322.dp)
        val chip = 82.dp
        val radius = d * .34f
        Box(Modifier.size(d)) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2)
                val outer = size.minDimension * .47f
                val inner = size.minDimension * .22f
                items.forEachIndexed { i, item ->
                    val start = -90f + i * (360f / items.size) - (360f / items.size) / 2f + 2.2f
                    val sweep = 360f / items.size - 4.4f
                    val path = Path()
                    val rect = androidx.compose.ui.geometry.Rect(c.x - outer, c.y - outer, c.x + outer, c.y + outer)
                    val innerRect = androidx.compose.ui.geometry.Rect(c.x - inner, c.y - inner, c.x + inner, c.y + inner)
                    path.arcTo(rect, start, sweep, false)
                    val endAngle = Math.toRadians((start + sweep).toDouble())
                    path.lineTo(c.x + inner * cos(endAngle).toFloat(), c.y + inner * sin(endAngle).toFloat())
                    path.arcTo(innerRect, start + sweep, -sweep, false)
                    path.close()
                    drawPath(path, if (item.id == selectedId) accent.copy(alpha = .24f) else if (dark) tile else Color(0xFFE8E5E6))
                    drawPath(path, if (item.id == selectedId) accent else edge, style = Stroke(width = if (item.id == selectedId) 2.dp.toPx() else 1.dp.toPx()))
                }
                drawCircle(accent.copy(alpha = .12f), radius = inner * .78f, center = c)
            }
            items.forEachIndexed { i, item ->
                val angle = Math.toRadians(-90.0 + i * (360.0 / items.size))
                val x = d.value / 2f + radius.value * cos(angle).toFloat() - chip.value / 2f
                val y = d.value / 2f + radius.value * sin(angle).toFloat() - chip.value / 2f
                Column(
                    Modifier.offset(x.dp, y.dp).size(chip).clip(RoundedCornerShape(16.dp)).clickable { onAction(item) }.padding(5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GlyphIcon(item.glyph, if (item.id == selectedId) accent else text, Modifier.size(23.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(item.title, color = if (item.id == selectedId) accent else text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            Column(Modifier.align(Alignment.Center).width(d * .38f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("SWEETCH", color = text, fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.ExtraBold)
                Text(items.first { it.id == selectedId }.title, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                Text("ДЕЙСТВИЕ", color = muted, fontSize = 8.sp, letterSpacing = 1.4.sp)
            }
        }
    }
}

@Composable
private fun PuzzleActions(items: List<ActionItem>, selectedId: String, dark: Boolean, text: Color, muted: Color, panel: Color, tile: Color, edge: Color, accent: Color, onAction: (ActionItem) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { col, item ->
                    val chosen = item.id == selectedId
                    Column(
                        Modifier.weight(1f).height(94.dp).clip(RoundedCornerShape(18.dp)).background(if (chosen) accent.copy(alpha = .17f) else panel)
                            .border(1.dp, if (chosen) accent else edge, RoundedCornerShape(18.dp))
                            .clickable { onAction(item) }.padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(if (chosen) accent else tile), contentAlignment = Alignment.Center) {
                                GlyphIcon(item.glyph, if (chosen) Color.White else accent, Modifier.size(18.dp))
                            }
                            Text("0${row * 2 + col + 1}", color = muted, fontSize = 9.sp, letterSpacing = 1.sp)
                        }
                        Text(item.title, color = if (chosen) accent else text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(item.subtitle, color = muted, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlyphIcon(glyph: Glyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = (size.minDimension * .09f).coerceAtLeast(1.4f)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1, y1), Offset(x2, y2), sw, cap = StrokeCap.Round)
        when (glyph) {
            Glyph.DOWNLOAD -> {
                line(w*.5f,h*.12f,w*.5f,h*.62f); line(w*.28f,h*.43f,w*.5f,h*.65f); line(w*.72f,h*.43f,w*.5f,h*.65f)
                line(w*.2f,h*.77f,w*.8f,h*.77f); line(w*.2f,h*.77f,w*.2f,h*.88f); line(w*.8f,h*.77f,w*.8f,h*.88f)
            }
            Glyph.SHARE -> {
                drawCircle(color, sw*1.8f, Offset(w*.25f,h*.5f)); drawCircle(color, sw*1.8f, Offset(w*.72f,h*.24f)); drawCircle(color, sw*1.8f, Offset(w*.72f,h*.77f))
                line(w*.3f,h*.46f,w*.66f,h*.27f); line(w*.3f,h*.54f,w*.66f,h*.73f)
            }
            Glyph.COPY -> {
                drawRoundRect(color, Offset(w*.32f,h*.15f), Size(w*.5f,h*.57f), androidx.compose.ui.geometry.CornerRadius(sw*2), style=Stroke(sw))
                drawRoundRect(color, Offset(w*.16f,h*.31f), Size(w*.5f,h*.57f), androidx.compose.ui.geometry.CornerRadius(sw*2), style=Stroke(sw))
            }
            Glyph.LINK -> {
                drawRoundRect(color, Offset(w*.08f,h*.28f), Size(w*.52f,h*.34f), androidx.compose.ui.geometry.CornerRadius(h*.17f), style=Stroke(sw))
                drawRoundRect(color, Offset(w*.4f,h*.38f), Size(w*.52f,h*.34f), androidx.compose.ui.geometry.CornerRadius(h*.17f), style=Stroke(sw))
                line(w*.34f,h*.5f,w*.66f,h*.5f)
            }
            Glyph.HISTORY -> {
                drawArc(color, 210f, 300f, false, Offset(w*.14f,h*.14f), Size(w*.72f,h*.72f), style=Stroke(sw, cap=StrokeCap.Round))
                line(w*.12f,h*.2f,w*.12f,h*.43f); line(w*.12f,h*.2f,w*.35f,h*.2f); line(w*.5f,h*.3f,w*.5f,h*.5f); line(w*.5f,h*.5f,w*.67f,h*.59f)
            }
            Glyph.SETTINGS -> {
                drawCircle(color, w*.31f, Offset(w*.5f,h*.5f), style=Stroke(sw))
                drawCircle(color, w*.1f, Offset(w*.5f,h*.5f))
                for (i in 0..7) { val a = Math.PI*i/4; line(w*.5f+cos(a).toFloat()*w*.33f,h*.5f+sin(a).toFloat()*h*.33f,w*.5f+cos(a).toFloat()*w*.44f,h*.5f+sin(a).toFloat()*h*.44f) }
            }
        }
    }
}

@Composable
private fun InfoCard(number: String, title: String, body: String, modifier: Modifier, panel: Color, edge: Color, accent: Color, text: Color, muted: Color) {
    Surface(modifier, shape = RoundedCornerShape(15.dp), color = panel, border = androidx.compose.foundation.BorderStroke(1.dp, edge)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(number, color = accent, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp)
            Text(title, color = text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(body, color = muted, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

private fun enqueueDirectDownload(context: Context, rawUrl: String, result: (String) -> Unit) {
    val uri = try { Uri.parse(rawUrl.trim()) } catch (_: Exception) { null }
    if (uri == null || uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) {
        result("Введите корректную ссылку HTTP или HTTPS.")
        Toast.makeText(context, "Некорректная ссылка", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val request = DownloadManager.Request(uri)
            .setTitle(uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "Sweetch download")
            .setDescription("Загрузка добавлена через Sweetch")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeFileName(uri.lastPathSegment))
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        result("Загрузка добавлена в системную очередь Android. Проверяйте уведомления.")
        Toast.makeText(context, "Загрузка добавлена", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        result("Не удалось добавить загрузку: ${e.localizedMessage ?: "ошибка Android"}")
    }
}

private fun safeFileName(raw: String?): String {
    val name = raw?.substringAfterLast('/')?.take(120)?.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return name?.takeIf { it.isNotBlank() && it != "." && it != ".." } ?: "sweetch_download"
}

private fun copyText(context: Context, value: String, status: (String) -> Unit) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sweetch URL", value))
    status("Ссылка скопирована в буфер обмена.")
}

private fun openUrl(context: Context, raw: String, status: (String) -> Unit) {
    val uri = Uri.parse(raw.trim())
    if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) { status("Сначала введите корректную ссылку HTTP(S)."); return }
    try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)); status("Открываю ссылку…") }
    catch (_: Exception) { status("Не найдено приложение для открытия ссылки.") }
}

private fun shareUrl(context: Context, raw: String, status: (String) -> Unit) {
    if (raw.isBlank()) { status("Сначала введите ссылку."); return }
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, raw.trim()) }
        context.startActivity(android.content.Intent.createChooser(intent, "Поделиться ссылкой"))
        status("Открыто системное меню «Поделиться».")
    } catch (_: Exception) { status("Не удалось открыть меню «Поделиться».") }
}
