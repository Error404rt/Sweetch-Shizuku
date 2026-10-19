package com.error404rt.sweetch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SweetchApp() }
    }
}

private enum class MenuMode { WHEEL, PUZZLE }

private data class SweetchPalette(
    val page: Color,
    val panel: Color,
    val tile: Color,
    val hub: Color,
    val text: Color,
    val muted: Color,
    val edge: Color,
    val accent: Color,
    val accentSecondary: Color
)

private fun paletteFor(dark: Boolean) = if (dark) {
    SweetchPalette(
        page = Color(0xFF0D0D11),
        panel = Color(0xFF131317),
        tile = Color(0xFF202025),
        hub = Color(0xFF101014),
        text = Color(0xFFFAF6F7),
        muted = Color(0xFFAA9DA3),
        edge = Color(0x3DFFFFFF),
        accent = Color(0xFFEB2440),
        accentSecondary = Color(0xFFAC102D)
    )
} else {
    SweetchPalette(
        page = Color(0xFFEDF4FC),
        panel = Color(0xFFF8FCFF),
        tile = Color(0xFFFFFFFF),
        hub = Color(0xFFF4FAFF),
        text = Color(0xFF152940),
        muted = Color(0xFF556D85),
        edge = Color(0x40296FAD),
        accent = Color(0xFF1771CB),
        accentSecondary = Color(0xFF42A5EF)
    )
}

@Composable
private fun SweetchApp() {
    var darkTheme by rememberSaveable { mutableStateOf(true) }
    var menuMode by rememberSaveable { mutableStateOf(MenuMode.WHEEL) }
    var selectedActionId by rememberSaveable { mutableStateOf<String?>(null) }

    val palette = paletteFor(darkTheme)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val selectedAction = ActionCatalog.items.firstOrNull { it.id == selectedActionId }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent,
            secondary = palette.accentSecondary,
            background = palette.page,
            surface = palette.panel,
            onBackground = palette.text,
            onSurface = palette.text
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            secondary = palette.accentSecondary,
            background = palette.page,
            surface = palette.panel,
            onBackground = palette.text,
            onSurface = palette.text
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = palette.page) {
            Scaffold(
                containerColor = palette.page,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
            ) { scaffoldPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Header(
                        palette = palette,
                        darkTheme = darkTheme,
                        onToggleTheme = { darkTheme = !darkTheme }
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "КОНТЕКСТНЫЕ ДЕЙСТВИЯ",
                            color = palette.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.7.sp
                        )
                        Text(
                            text = "Одно касание — нужный инструмент",
                            color = palette.text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Сохраняем характер оригинала и добавляем новый формат меню.",
                            color = palette.muted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MenuModeButton(
                            modifier = Modifier.weight(1f),
                            title = "Круговое",
                            mark = "◎",
                            selected = menuMode == MenuMode.WHEEL,
                            palette = palette,
                            onClick = { menuMode = MenuMode.WHEEL }
                        )
                        MenuModeButton(
                            modifier = Modifier.weight(1f),
                            title = "Пазл",
                            mark = "🧩",
                            selected = menuMode == MenuMode.PUZZLE,
                            palette = palette,
                            onClick = { menuMode = MenuMode.PUZZLE }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(318.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (menuMode == MenuMode.WHEEL) {
                            WheelMenu(
                                palette = palette,
                                selectedActionId = selectedActionId,
                                onAction = { action ->
                                    selectedActionId = action.id
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Выбрано: " + action.title)
                                    }
                                }
                            )
                        } else {
                            PuzzleMenu(
                                palette = palette,
                                selectedActionId = selectedActionId,
                                onAction = { action ->
                                    selectedActionId = action.id
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Выбрано: " + action.title)
                                    }
                                }
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = palette.panel,
                        border = androidx.compose.foundation.BorderStroke(1.dp, palette.edge)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(palette.accent, palette.accentSecondary)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("S", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = if (selectedAction == null) "Sweetch: предпросмотр меню" else selectedAction.title,
                                    color = palette.text,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (selectedAction == null) {
                                        "Выберите пункт меню для предпросмотра."
                                    } else {
                                        selectedAction.description
                                    },
                                    color = palette.muted,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            Text(
                                text = if (selectedAction == null) "···" else "✓",
                                color = palette.accent,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "ЭТО ЭТАП UI-КАРКАСА • Загрузчики и системные действия подключаются отдельными модулями.",
                        color = palette.muted,
                        fontSize = 9.sp,
                        lineHeight = 14.sp,
                        letterSpacing = 0.4.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    palette: SweetchPalette,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(9.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(palette.accent, palette.accentSecondary))),
            contentAlignment = Alignment.Center
        ) {
            Text("S", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("SWEETCH", color = palette.text, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = 1.1.sp)
            Text("ANDROID EDITION", color = palette.muted, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.8.sp)
        }
        Surface(
            shape = CircleShape,
            color = palette.panel,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.edge),
            modifier = Modifier.size(42.dp).clickable(onClick = onToggleTheme)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (darkTheme) "☼" else "◐",
                    fontSize = 21.sp,
                    color = palette.accent
                )
            }
        }
    }
}

@Composable
private fun MenuModeButton(
    modifier: Modifier,
    title: String,
    mark: String,
    selected: Boolean,
    palette: SweetchPalette,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier.height(46.dp).clickable(onClick = onClick),
        shape = shape,
        color = if (selected) palette.accent.copy(alpha = 0.15f) else palette.panel,
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 1.4.dp else 1.dp,
            if (selected) palette.accent else palette.edge
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(mark, color = if (selected) palette.accent else palette.muted, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                color = if (selected) palette.text else palette.muted,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun WheelMenu(
    palette: SweetchPalette,
    selectedActionId: String?,
    onAction: (MenuAction) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val wheelSize = minOf(maxWidth, maxHeight).coerceAtMost(320.dp)
        val center = wheelSize / 2f
        val chipSize = 64.dp
        val orbit = wheelSize * 0.32f

        Box(modifier = Modifier.size(wheelSize)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerPoint = Offset(size.width / 2f, size.height / 2f)
                val ringRadius = minOf(size.width, size.height) * 0.405f
                drawCircle(
                    color = palette.edge,
                    radius = ringRadius,
                    center = centerPoint,
                    style = Stroke(width = 1.4.dp.toPx())
                )
                drawCircle(
                    color = palette.edge.copy(alpha = 0.45f),
                    radius = ringRadius * 0.72f,
                    center = centerPoint,
                    style = Stroke(width = 1.dp.toPx())
                )
                ActionCatalog.items.forEachIndexed { index, action ->
                    val isSelected = action.id == selectedActionId
                    drawArc(
                        color = if (isSelected) palette.accent else palette.accent.copy(alpha = 0.58f),
                        startAngle = -120f + index * 60f,
                        sweepAngle = 39f,
                        useCenter = false,
                        topLeft = Offset(centerPoint.x - ringRadius, centerPoint.y - ringRadius),
                        size = Size(ringRadius * 2f, ringRadius * 2f),
                        style = Stroke(width = if (isSelected) 4.dp.toPx() else 2.6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            ActionCatalog.items.forEachIndexed { index, action ->
                val angle = Math.toRadians(-90.0 + index * 60.0)
                val left = center.value + orbit.value * cos(angle).toFloat() - chipSize.value / 2f
                val top = center.value + orbit.value * sin(angle).toFloat() - chipSize.value / 2f
                WheelActionChip(
                    modifier = Modifier.offset(x = Dp(left), y = Dp(top)).size(chipSize),
                    action = action,
                    selected = action.id == selectedActionId,
                    palette = palette,
                    onClick = { onAction(action) }
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(82.dp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(palette.hub, palette.panel)))
                    .border(1.5.dp, palette.accent.copy(alpha = 0.75f), CircleShape)
                    .clickable { onAction(ActionCatalog.items.last()) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("S", color = palette.accent, fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Text("MENU", color = palette.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                }
            }
        }
    }
}

@Composable
private fun WheelActionChip(
    modifier: Modifier,
    action: MenuAction,
    selected: Boolean,
    palette: SweetchPalette,
    onClick: () -> Unit
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .shadow(if (selected) 11.dp else 5.dp, shape)
            .clip(shape)
            .background(
                if (selected) Brush.linearGradient(listOf(palette.accent, palette.accentSecondary))
                else Brush.linearGradient(listOf(palette.tile, palette.panel))
            )
            .border(1.dp, if (selected) palette.accent else palette.edge, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                action.glyph,
                color = if (selected) Color.White else palette.accent,
                fontWeight = FontWeight.Bold,
                fontSize = if (action.glyph.length > 1) 16.sp else 20.sp
            )
            Text(
                action.title,
                color = if (selected) Color.White else palette.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 8.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PuzzleMenu(
    palette: SweetchPalette,
    selectedActionId: String?,
    onAction: (MenuAction) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        ActionCatalog.items.chunked(3).forEachIndexed { rowIndex, rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                rowActions.forEachIndexed { columnIndex, action ->
                    val index = rowIndex * 3 + columnIndex
                    PuzzleActionPiece(
                        modifier = Modifier.weight(1f).height(93.dp),
                        action = action,
                        variant = index,
                        selected = action.id == selectedActionId,
                        palette = palette,
                        onClick = { onAction(action) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PuzzleActionPiece(
    modifier: Modifier,
    action: MenuAction,
    variant: Int,
    selected: Boolean,
    palette: SweetchPalette,
    onClick: () -> Unit
) {
    val shape = remember(variant) { PuzzlePieceShape(variant) }
    Box(
        modifier = modifier
            .shadow(if (selected) 9.dp else 4.dp, shape)
            .clip(shape)
            .background(
                if (selected) Brush.linearGradient(listOf(palette.accent, palette.accentSecondary))
                else Brush.linearGradient(listOf(palette.tile, palette.panel))
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        if (selected) palette.accent else palette.edge,
                        if (selected) palette.accentSecondary else palette.edge.copy(alpha = 0.55f)
                    )
                ),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                action.glyph,
                color = if (selected) Color.White else palette.accent,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                action.title,
                color = if (selected) Color.White else palette.text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
