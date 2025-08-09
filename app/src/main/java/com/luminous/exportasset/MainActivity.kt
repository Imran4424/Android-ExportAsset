package com.luminous.exportasset

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min

enum class BgMode { White, Transparent }

class MainActivity : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContent { DrawingApp() }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingApp() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        // ---- state ----
        var exportSize by remember { mutableIntStateOf(256) }
        var bgMode by remember { mutableStateOf(BgMode.White) } // default = White
        val sizes = listOf(128, 256, 512, 1024)

        // Drawing state
        val strokes = remember { mutableStateListOf<StrokeData>() }
        var currentStroke by remember { mutableStateOf<StrokeData?>(null) }
        var canvasPxSize by remember { mutableStateOf(IntSize(0, 0)) }

        val widthFraction = 0.012f

        // --- simple preview size map so the on-screen canvas changes with dropdown ---
        val previewSizeDp = remember(exportSize) {
                when (exportSize) {
                        128 -> 220.dp
                        256 -> 300.dp
                        512 -> 360.dp
                        else -> 400.dp        // 1024
                }
        }

        var sizeMenuOpen by remember { mutableStateOf(false) }

        MaterialTheme {
                Scaffold(
                        topBar = {
                                TopAppBar(
                                        title = {
                                                Text("Export Asset", fontWeight = FontWeight.Bold)
                                        }
                                )
                        }
                ) { padding ->

                        Column(
                                Modifier
                                        .fillMaxSize()
                                        .padding(padding)
                                        .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {

                                Row(
                                        modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Spacer(Modifier.weight(1f))
                                        // Segmented control: White / Transparent
                                        SingleChoiceSegmentedButtonRow {
                                                SegmentedButton(
                                                        selected = bgMode == BgMode.White,
                                                        onClick = { bgMode = BgMode.White },
                                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                                ) { Text("White") }

                                                SegmentedButton(
                                                        selected = bgMode == BgMode.Transparent,
                                                        onClick = { bgMode = BgMode.Transparent },
                                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                                ) { Text("Transparent") }
                                        }
                                        Spacer(Modifier.weight(1f))
                                }

                                // ---- Navigation bar (under app bar) ----
                                Row(
                                        modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        // left: size dropdown
                                        Box {
                                                OutlinedButton(
                                                        onClick = { sizeMenuOpen = true },
                                                        shape = RoundedCornerShape(12.dp)
                                                ) {
                                                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                                        Spacer(Modifier.width(6.dp))
                                                        Text("Size: $exportSize")
                                                }
                                                DropdownMenu(expanded = sizeMenuOpen, onDismissRequest = { sizeMenuOpen = false }) {
                                                        sizes.forEach { s ->
                                                                DropdownMenuItem(
                                                                        text = { Text("$s × $s px") },
                                                                        onClick = { exportSize = s; sizeMenuOpen = false }
                                                                )
                                                        }
                                                }
                                        }

                                        Spacer(Modifier.weight(1f))


                                        // right: actions
                                        IconButton(onClick = { strokes.clear(); currentStroke = null }) {
                                                Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear")
                                        }
                                        IconButton(onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                        val bgColor: Int? = when (bgMode) {
                                                                BgMode.White -> Color.White.toArgb()
                                                                BgMode.Transparent -> null
                                                        }
                                                        val bmp = renderBitmap(strokes, exportSize, exportSize, backgroundColor = bgColor)
                                                        val time = java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS", java.util.Locale.US)
                                                                .format(java.util.Date())
                                                        val uri = savePng(context, bmp, "canvas_${exportSize}_$time.png")
                                                        withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, if (uri != null) "PNG saved" else "Save failed", Toast.LENGTH_SHORT).show()
                                                        }
                                                }
                                        }) { Icon(Icons.Filled.Image, contentDescription = "Export PNG") }

                                        IconButton(onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                        val bgColor: Int? = when (bgMode) {
                                                                BgMode.White -> Color.White.toArgb()
                                                                BgMode.Transparent -> null
                                                        }
                                                        val svg = renderSvg(strokes, exportSize, exportSize, backgroundColor = bgColor)
                                                        val time = java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS", java.util.Locale.US)
                                                                .format(java.util.Date())
                                                        val ok = saveSvg(context, svg, "canvas_${exportSize}_$time.svg")
                                                        withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, if (ok) "SVG saved" else "Save failed", Toast.LENGTH_SHORT).show()
                                                        }
                                                }
                                        }) { Icon(Icons.Filled.Download, contentDescription = "Export SVG") }
                                }

                                // ---- Canvas (size reacts to exportSize) ----
                                Box(
                                        modifier = Modifier
                                                .fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Box(
                                                modifier = Modifier
                                                        .size(previewSizeDp) // <-- key change: preview follows export size
                                                        .border(2.dp, Color.Black, RoundedCornerShape(16.dp))
                                                        .background(Color(0xFFF7F7F7), RoundedCornerShape(16.dp))
                                                        .padding(8.dp)
                                        ) {
                                                // recompose Canvas when preview size changes so onSizeChanged fires
                                                key(previewSizeDp) {
                                                        Canvas(
                                                                modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .onSizeChanged { canvasPxSize = it }
                                                                        .pointerInput(Unit) {
                                                                                detectDragGestures(
                                                                                        onDragStart = { pos ->
                                                                                                if (canvasPxSize.width == 0 || canvasPxSize.height == 0) return@detectDragGestures
                                                                                                val norm = pos.toNormalized(canvasPxSize)
                                                                                                currentStroke = StrokeData(mutableStateListOf(norm), widthFraction, Color.Black)
                                                                                        },
                                                                                        onDrag = { change, _ ->
                                                                                                val s = currentStroke ?: return@detectDragGestures
                                                                                                s.points.add(change.position.toNormalized(canvasPxSize))
                                                                                        },
                                                                                        onDragEnd = {
                                                                                                currentStroke?.let { strokes.add(it) }
                                                                                                currentStroke = null
                                                                                        },
                                                                                        onDragCancel = { currentStroke = null }
                                                                                )
                                                                        }
                                                        ) {
                                                                val size = this.size
                                                                strokes.forEach { s -> drawStroke(s, size) }
                                                                currentStroke?.let { drawStroke(it, size) }
                                                        }
                                                }
                                        }
                                }
                        }
                }
        }
}

// ---- helpers local to UI file ----
private fun Offset.toNormalized(size: IntSize): Offset =
        Offset(x / size.width.toFloat(), y / size.height.toFloat())

private fun Offset.fromNormalized(size: Size): Offset =
        Offset(x * size.width, y * size.height)

private fun Path.buildFrom(points: List<Offset>, size: Size) {
        if (points.isEmpty()) return
        val first = points.first().fromNormalized(size)
        moveTo(first.x, first.y)
        for (i in 1 until points.size) {
                val p = points[i].fromNormalized(size)
                lineTo(p.x, p.y)
        }
}

private fun DrawScope.drawStroke(s: StrokeData, size: Size) {
        val path = Path().apply { buildFrom(s.points, size) }
        val strokeWidthPx = s.widthFraction * min(size.width, size.height)
        drawPath(path = path, color = s.color, style = Stroke(width = strokeWidthPx))
}