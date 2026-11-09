package dev.better.sheet.sample

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.better.sheet.*
import kotlinx.coroutines.launch

internal enum class MaterialExample(val title: String, val detail: String, val code: String) {
    Default("Solid default", "The existing solid sheet. Nothing extra supplied.", "No material or transform"),
    Material("Material only", "A gradient behind the same header, body and footer.", "sheetMaterial = { outline -> … }"),
    Transform("Transform only", "Default paint. Cover the sheet to see it recede.", "sheetTransform = stackEffect"),
    Both("Material + transform", "The gradient and stack recession work together.", "sheetMaterial + sheetTransform"),
}

@Composable
internal fun MaterialExamples(close: () -> Unit) {
    var selected by remember { mutableStateOf<MaterialExample?>(null) }
    var tray by remember { mutableStateOf(false) }
    BackHandler(selected == null, close)
    LazyColumn(
        Modifier.fillMaxSize().background(Color(0xFFF5F6FB)).safeDrawingPadding().testTag("materials-page"),
        contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = close) { Text("‹ All examples") }
            Text("Material & transforms", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Same content. Four ways to present it.", color = Color(0xFF68728A))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!tray, { tray = false }, label = { Text("Standard") }, modifier = Modifier.testTag("materials-standard"))
                FilterChip(tray, { tray = true }, label = { Text("Floating tray") }, modifier = Modifier.testTag("materials-tray"))
            }
        }
        items(MaterialExample.entries) { example ->
            Card(
                Modifier.fillMaxWidth().testTag("material-open-${example.name}").clickable { selected = example },
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(example.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(example.detail, style = MaterialTheme.typography.bodySmall, color = Color(0xFF68728A))
                    Text(example.code, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = Color(0xFF6554E8))
                }
            }
        }
        item { Text("Try dragging, snapping, scrolling and entering a note. The gradient is included here; a glass renderer can use the same material slot.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF68728A)) }
    }
    selected?.let { example ->
        key(example, tray) {
            MaterialExampleSheet(example, if (tray) SheetPresentation.Tray() else SheetPresentation.Standard) { selected = null }
        }
    }
}

/** App-provided composable paint. The host clips it and carries it through navigation space. */
@Composable
private fun GradientSheetMaterial(outline: Shape) {
    Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(0xFFFFE9CA), Color(0xFFE4D9FF), Color(0xFFBFE6DF))),
        outline,
    ))
}

@Composable
private fun MaterialExampleSheet(example: MaterialExample, presentation: SheetPresentation, close: () -> Unit) {
    val state = rememberSheetState("half")
    val cover = rememberSheetState("detail", initiallyVisible = false)
    val stack = rememberSheetStackState()
    val scope = rememberCoroutineScope()
    val points = remember { listOf(SheetSnapPoint.fixed("peek", 300.dp), SheetSnapPoint.fraction("half", .68f), SheetSnapPoint.expanded()) }
    val behavior = remember { SheetBehavior(keyboardBehavior = SheetKeyboardBehavior.Expand) }
    val transforms = example == MaterialExample.Transform || example == MaterialExample.Both
    var note by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    val stackEffect = Modifier.sheetStackEffect { entry ->
        scaleX = 1f - .06f * entry.animatedDepth
        scaleY = 1f - .04f * entry.animatedDepth
        translationY = (-12).dp * entry.animatedDepth
        cornerRadius = (28 + 8 * entry.animatedDepth).dp
    }
    val header: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(example.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(state.currentSnapPoint ?: "Opening", color = Color(0xFF6554E8), modifier = Modifier.testTag("material-example-status"))
                }
                TextButton({ scope.launch { state.hide() } }, Modifier.testTag("material-example-close")) { Text("Close") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("peek", "half", "expanded").forEach { id ->
                    FilterChip(state.currentSnapPoint == id, { scope.launch { state.snapTo(id) } },
                        label = { Text(id.replaceFirstChar { it.uppercase() }) }, modifier = Modifier.testTag("material-snap-$id"))
                }
            }
        }
    }
    val footer: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).testTag("material-example-footer")) {
            Button(
                onClick = { if (transforms) scope.launch { cover.show("detail") } else saved = !saved },
                modifier = Modifier.fillMaxWidth().testTag("material-primary-action"),
            ) { Text(if (transforms) "Cover this sheet" else if (saved) "✓ Saved" else "Save selection") }
        }
    }
    val content: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your UI stays in your control. This body scrolls; the header and action stay pinned.")
            OutlinedTextField(note, { note = it }, label = { Text("Add a note") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("material-example-note"))
            repeat(8) { i ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .5f))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Item ${i + 1}", fontWeight = FontWeight.Medium)
                        Text("Identical content in every example", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    // Keep the four real public calls explicit so the examples can be copied independently.
    when (example) {
        MaterialExample.Default -> BetterModalSheet(state, points, onDismissed = close,
            title = example.title, behavior = behavior, presentation = presentation,
            header = header, footer = footer, content = content)
        MaterialExample.Material -> BetterModalSheet(state, points, onDismissed = close,
            title = example.title, behavior = behavior, presentation = presentation,
            sheetMaterial = { outline -> GradientSheetMaterial(outline) },
            header = header, footer = footer, content = content)
        MaterialExample.Transform -> BetterModalSheet(state, points, onDismissed = close,
            title = example.title, behavior = behavior, presentation = presentation,
            stackState = stack, sheetTransform = stackEffect,
            header = header, footer = footer, content = content)
        MaterialExample.Both -> BetterModalSheet(state, points, onDismissed = close,
            title = example.title, behavior = behavior, presentation = presentation,
            stackState = stack, sheetTransform = stackEffect,
            sheetMaterial = { outline -> GradientSheetMaterial(outline) },
            header = header, footer = footer, content = content)
    }
    if (transforms) BetterModalSheet(cover, listOf(SheetSnapPoint.fixed("detail", 240.dp)),
        stackState = stack, sheetTransform = stackEffect,
        sheetMaterial = if (example == MaterialExample.Both) ({ outline -> GradientSheetMaterial(outline) }) else null,
        title = "Stack preview", header = {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text("Stack preview", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("material-cover-title"))
                Text("The sheet behind shrinks and moves. Its content and material move together.")
            }
        }, footer = {
            TextButton({ scope.launch { cover.hide() } }, Modifier.fillMaxWidth().testTag("material-cover-close")) { Text("Close top sheet") }
        }) { Spacer(Modifier.height(12.dp)) }
}
