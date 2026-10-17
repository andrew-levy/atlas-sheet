package dev.atlas.sheet.sample

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.atlas.sheet.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher

private val Ink = Color(0xFF172033)
private val Accent = Color(0xFF6554E8)
private val Paper = Color(0xFFF5F6FB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        val autoOpen = intent.getStringExtra(EXTRA_DEMO)?.let { Demo.valueOf(it) }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Accent, background = Paper, surface = Color.White, onSurface = Ink)) {
                Gallery(autoOpen)
            }
        }
    }
    companion object {
        /** Demo name to open shortly after launch; used by real-clock timing tests. */
        const val EXTRA_DEMO = "demo"
    }
}

/** Real-clock opening timings, from the open request to the sheet settling at its detent. */
data class OpenTiming(val firstMotionMillis: Long, val settledMillis: Long, val longestFrameMillis: Long, val frames: Int)

object OpenTimings {
    val results = ConcurrentHashMap<String, OpenTiming>()
}

enum class Demo(val label: String, val subtitle: String) {
    Detents("Detents", "Peek, medium, expanded • scroll handoff"),
    Chat("Chat", "Lazy list • composer above keyboard"),
    Fit("Content fit", "Intrinsic size • live content changes"),
    Stack("Stacked", "Modal sheets layered on one another"),
    Form("Sticky actions", "Long form • keyboard resize • pinned CTA"),
    Persistent("Persistent", "Inline sheet • interactive background"),
    Locked("Protected flow", "Disable user dismissal • explicit close"),
    Heavy("Heavy content", "3,000 messages • CPU parsing on every core • slow database"),
}

@Composable
private fun Gallery(autoOpen: Demo?) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf<Demo?>(null) }
    var session by remember { mutableIntStateOf(0) }
    var openedAt by remember { mutableLongStateOf(0L) }
    if (autoOpen != null) LaunchedEffect(autoOpen) {
        delay(800)
        openedAt = SystemClock.uptimeMillis()
        selected = autoOpen
    }
    Box(Modifier.fillMaxSize().background(Paper)) {
        LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("gallery"), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("ATLAS / COMPOSE", color = Accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Text("A sheet for\nevery surface.", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(12.dp))
                Text("Named snapPoints. Sticky chrome.\nContent that stays in your control.", color = Color(0xFF68728A))
                Spacer(Modifier.height(14.dp))
            }
            items(Demo.entries) { demo ->
                Card(Modifier.fillMaxWidth().testTag("open-${demo.name}").clickable {
                    openedAt = SystemClock.uptimeMillis()
                    session++
                    selected = demo
                }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(demo.label, fontWeight = FontWeight.SemiBold, color = Ink)
                            Spacer(Modifier.height(6.dp))
                            Text(demo.subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF68728A))
                        }
                        Text("↗", color = Accent, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            item {
                Button(onClick = { context.startActivity(Intent(context, FragmentDemoActivity::class.java)) }) {
                    Text("Fragment root example")
                }
            }
            item { Text("0.1.0 • Android 24+ • Foundation powered", color = Color(0xFF68728A), style = MaterialTheme.typography.labelSmall) }
        }
        selected?.let { demo ->
            key(demo, session) {
                val token = session
                DemoSheet(demo, openedAt) { if (session == token) selected = null }
            }
        }
    }
}

@Composable
private fun DemoSheet(demo: Demo, openedAt: Long, close: () -> Unit) {
    if (demo == Demo.Stack) {
        StackedSheets(close)
        return
    }
    val scope = rememberCoroutineScope()
    val initial = if (demo == Demo.Fit) "content" else if (demo == Demo.Persistent) "peek" else "medium"
    val state = rememberSheetState(initialSnapPoint = initial)
    val snapPoints = remember(demo) {
        if (demo == Demo.Fit) listOf(SheetSnapPoint.fitContent(), SheetSnapPoint.expanded())
        else listOf(SheetSnapPoint.fixed("peek", 260.dp), SheetSnapPoint.fraction("medium", 0.6f), SheetSnapPoint.expanded())
    }
    val behavior = remember(demo) {
        SheetBehavior(
            keyboardBehavior = if (demo == Demo.Chat || demo == Demo.Heavy) SheetKeyboardBehavior.Expand else SheetKeyboardBehavior.Resize,
            expandBeforeScroll = false,
            dismissOnDrag = demo != Demo.Locked,
            dismissOnScrim = demo != Demo.Locked,
            dismissOnBack = demo != Demo.Locked,
        )
    }
    var message by rememberSaveable { mutableStateOf("") }
    var extra by rememberSaveable { mutableStateOf(false) }
    val chatList = rememberLazyListState()
    var heavyThread by remember { mutableStateOf<List<HeavyMessage>?>(null) }
    if (demo == Demo.Heavy) LaunchedEffect(Unit) { heavyThread = loadHeavyThread() }
    LaunchedEffect(state) { recordOpenTiming(demo, state, openedAt) }
    val messages = remember {
        mutableStateListOf(
            "Welcome to Atlas Sheet.",
            "Try scrolling, then opening the keyboard.",
            "The composer stays visible at every detent.",
            "Sounds good. Let's ship it.",
        ).apply { repeat(36) { add("Message ${it + 1}: shared context stays scrollable.") } }
    }
    val header: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("sheet-header")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(demo.label, modifier = Modifier.testTag("sheet-title"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    val status = if (demo == Demo.Heavy) heavyThread?.let { "%,d messages".format(it.size) } ?: "Loading thread…" else null
                    Text(listOfNotNull(state.currentSnapPoint ?: "Opening", status).joinToString(" • "), color = Accent, style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("sheet-status"))
                }
                TextButton(onClick = { scope.launch { state.hide() } }, modifier = Modifier.testTag("close")) { Text("Close") }
            }
            if (demo != Demo.Fit) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("peek", "medium", "expanded").forEach { id ->
                        FilterChip(selected = state.currentSnapPoint == id, onClick = { scope.launch { state.animateTo(id) } }, label = { Text(id.replaceFirstChar { it.uppercase() }) }, modifier = Modifier.testTag("detent-$id"))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    val footer: @Composable () -> Unit = {
        Surface(shadowElevation = 6.dp) {
            if (demo == Demo.Chat || demo == Demo.Heavy) {
                Row(Modifier.fillMaxWidth().padding(16.dp).testTag("footer"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(message, { message = it }, placeholder = { Text("Message the team") }, modifier = Modifier.weight(1f).testTag("composer"), maxLines = 3, shape = RoundedCornerShape(24.dp))
                    Button(onClick = {
                        if (message.isNotBlank()) {
                            if (demo == Demo.Heavy) heavyThread = heavyThread.orEmpty() + HeavyMessage("You", message, "sent") else {
                                messages.add(message)
                                chatList.requestScrollToItem(messages.lastIndex)
                            }
                            message = ""
                        }
                    }, modifier = Modifier.testTag("send")) { Text("Send") }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(16.dp).testTag("footer")) {
                    Button(onClick = {
                        if (demo == Demo.Fit) extra = !extra else scope.launch { state.hide() }
                    }, modifier = Modifier.fillMaxWidth().testTag("primary-action")) {
                        Text(if (demo == Demo.Fit) if (extra) "Show fewer options" else "Show more options" else if (demo == Demo.Locked) "Complete & close" else "Continue")
                    }
                }
            }
        }
    }
    val body: @Composable ColumnScope.() -> Unit = {
        when (demo) {
            Demo.Heavy -> HeavyThreadBody(heavyThread)
            Demo.Chat -> LazyColumn(state = chatList, modifier = Modifier.fillMaxSize().testTag("body-list"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(messages.size, key = { it }) { i ->
                    val mine = i % 2 == 0
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Text(messages[i], Modifier.widthIn(max = 280.dp).background(if (mine) Accent else Paper, RoundedCornerShape(18.dp)).padding(16.dp), color = if (mine) Color.White else Ink)
                    }
                }
            }
            Demo.Fit -> Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp).testTag("fit-body"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Content determines this detent. Add options to watch the sheet resize.", color = Color(0xFF68728A))
                repeat(if (extra) 8 else 2) { i -> Text("${i + 1}. Workspace option", Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(12.dp)).padding(16.dp)) }
            }
            Demo.Form -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Your actions stay pinned while fields scroll into view.", color = Color(0xFF68728A))
                repeat(12) { i ->
                    var field by rememberSaveable { mutableStateOf("") }
                    OutlinedTextField(field, { field = it }, label = { Text("Field ${i + 1}") }, modifier = Modifier.fillMaxWidth().testTag("field-$i"))
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize().testTag("body-list"), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(if (demo == Demo.Locked) "Try Back, the scrim, and dragging down. This flow only closes explicitly." else "Drag the handle or scroll this list. The footer remains outside the scrolling viewport.", color = Color(0xFF68728A)) }
                items(100, key = { it }) { i ->
                    Row(Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(14.dp)).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("%02d".format(i + 1), color = Accent, fontWeight = FontWeight.Bold)
                        Column { Text("Workspace item ${i + 1}", fontWeight = FontWeight.Medium); Text("A lazy, bounded content viewport", style = MaterialTheme.typography.bodySmall, color = Color(0xFF68728A)) }
                    }
                }
            }
        }
    }
    val mode = if (demo == Demo.Fit || demo == Demo.Form) SheetBodyMode.Scroll else SheetBodyMode.Fill
    if (demo == Demo.Persistent) {
        AtlasSheetHost(state, snapPoints, behavior = behavior, bodyMode = mode, title = demo.label, scrimColor = Color.Unspecified, header = header, footer = footer, content = body)
        LaunchedEffect(state) { snapshotFlow { state.currentSnapPoint }.collect { if (it == null) close() } }
    } else {
        AtlasModalSheet(state, snapPoints, close, behavior = behavior, bodyMode = mode, title = demo.label, header = header, footer = footer, content = body)
    }
}

private val Muted = Color(0xFF68728A)

/** App-defined recession; Atlas only supplies stack lifecycle and gesture progress. */
@Composable
private fun StackedSheets(close: () -> Unit) {
    val stackState = rememberSheetStackState()
    val plans = rememberSheetState()
    val details = rememberSheetState(initiallyVisible = false)
    val review = rememberSheetState(initialSnapPoint = "content", initiallyVisible = false)
    val scope = rememberCoroutineScope()
    val effect = Modifier.sheetStackEffect { entry ->
        val depth = entry.animatedDepth
        scaleX = (1f - 0.06f * depth).coerceAtLeast(0.7f)
        scaleY = (1f - 0.04f * depth).coerceAtLeast(0.8f)
        translationY = (-12).dp * depth
        cornerRadius = (28 + 8 * depth).dp
    }
    val closePlans: () -> Unit = { scope.launch { plans.hide() }; Unit }
    val closeDetails: () -> Unit = { scope.launch { details.hide() }; Unit }
    val closeReview: () -> Unit = { scope.launch { review.hide() }; Unit }
    AtlasModalSheet(
        state = plans, stackState = stackState,
        snapPoints = listOf(SheetSnapPoint.fraction("expanded", 0.92f)),
        surfaceModifier = effect, title = "Plans", onDismissed = close,
        header = { StackHeader("Plans", "This week", "stack-close-base", Modifier.testTag("stack-base-title"), closePlans) },
        footer = { StackFooter { Text("3 plans", color = Muted, modifier = Modifier.testTag("footer")) } },
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Every new sheet pushes earlier sheets farther back: 100%, 94%, then 88% width.", color = Muted)
            PlanRow("01", "Design review", "Today · 2:00 PM")
            PlanRow("02", "Atlas mobile", "In progress · tap to open", Modifier.testTag("stack-open-details")) { scope.launch { details.show() } }
            PlanRow("03", "Ship checklist", "Friday · release")
            PlanRow("04", "Office hours", "Next week")
        }
    }
    AtlasModalSheet(
        state = details, stackState = stackState,
        snapPoints = listOf(SheetSnapPoint.fraction("expanded", 0.58f)),
        surfaceModifier = effect, title = "Atlas mobile",
        header = { StackHeader("Atlas mobile", "In progress", "stack-close-details", Modifier.testTag("stack-details-title"), closeDetails) },
        footer = { StackFooter {
            Button(onClick = { scope.launch { review.show() } }, modifier = Modifier.fillMaxWidth().testTag("stack-open-confirm")) { Text("Request review") }
        } },
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Three normal modals share one stack state. Each sheet controls its own visibility.", color = Muted)
            DetailRow("Owner", "Maya Chen")
            DetailRow("Status", "In progress")
            DetailRow("Due", "Friday")
        }
    }
    AtlasModalSheet(
        state = review, stackState = stackState,
        snapPoints = listOf(SheetSnapPoint.fitContent()), surfaceModifier = effect, title = "Request review",
        header = { StackHeader("Request review", "Two reviewers", "stack-close-confirm", Modifier.testTag("stack-confirm-title"), closeReview) },
        footer = { StackFooter {
            Button(onClick = closeReview, modifier = Modifier.fillMaxWidth().testTag("stack-send-review")) { Text("Send request") }
        } },
    ) {
        Text("Both sheets beneath this one recede. Closing it restores each layer smoothly.",
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = Muted)
    }
}

@Composable
private fun StackHeader(title: String, subtitle: String, closeTag: String, titleModifier: Modifier = Modifier, close: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, titleModifier, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Accent, style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = close, modifier = Modifier.testTag(closeTag)) { Text("Close") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StackFooter(content: @Composable RowScope.() -> Unit) {
    Surface(shadowElevation = 6.dp) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun PlanRow(index: String, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().background(Paper, RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(index, color = Accent, fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        if (onClick != null) Text("↗", color = Accent, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(14.dp)).padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted)
        Text(value, fontWeight = FontWeight.Medium, color = Ink)
    }
}

class HeavyMessage(val author: String, val text: String, val digest: String)

object HeavyLoad {
    /**
     * Normal-priority workers on every core compete with the UI and render threads and delay the
     * sheet on small devices; background priority leaves frame production first in line.
     */
    @Volatile var backgroundPriority = true
    private val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    val background = Executors.newFixedThreadPool(cores) { work ->
        Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); work.run() }, "heavy-load")
    }.asCoroutineDispatcher()
    val dispatcher get() = if (backgroundPriority) background else Dispatchers.Default
}

/**
 * Simulates opening a large conversation: a slow database read plus CPU-heavy decoding of every
 * message, spread over all cores, all while the sheet is presenting.
 */
private suspend fun loadHeavyThread(count: Int = 3000, rounds: Int = 400): List<HeavyMessage> = withContext(HeavyLoad.dispatcher) {
    coroutineScope {
        val database = async { delay(900) }
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val decoded = (0 until cores).map { core ->
            async {
                val sha = MessageDigest.getInstance("SHA-256")
                (core until count step cores).map { i ->
                    var bytes = "message-$i".toByteArray()
                    repeat(rounds) { bytes = sha.digest(bytes) }
                    i to HeavyMessage(
                        author = if (i % 3 == 0) "You" else listOf("Maya", "Jon", "Priya")[i % 3],
                        text = "Message ${i + 1}: decoded ${rounds} hash rounds for a long synced thread.",
                        digest = bytes.take(4).joinToString("") { "%02x".format(it) },
                    )
                }
            }
        }.awaitAll().flatten().sortedBy { it.first }.map { it.second }
        database.await()
        decoded
    }
}

@Composable
private fun HeavyThreadBody(thread: List<HeavyMessage>?) {
    if (thread == null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp).testTag("heavy-loading"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(8) { i ->
                Box(Modifier.fillMaxWidth(if (i % 2 == 0) .7f else .55f).height(52.dp).align(if (i % 2 == 0) Alignment.End else Alignment.Start).alpha(.6f).background(Color(0xFFE3E6F0), RoundedCornerShape(18.dp)))
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("body-list"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(thread.size, key = { it }) { i ->
            val message = thread[i]
            val mine = message.author == "You"
            Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                Text(message.text, Modifier.widthIn(max = 280.dp).background(if (mine) Accent else Paper, RoundedCornerShape(18.dp)).padding(16.dp), color = if (mine) Color.White else Ink)
                Text("${message.author} • ${message.digest}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF68728A))
            }
        }
    }
}

/** Samples every real frame from the open request until the sheet settles. */
private suspend fun recordOpenTiming(demo: Demo, state: dev.atlas.sheet.SheetState, openedAt: Long) {
    if (openedAt == 0L) return
    var firstMotion = -1L
    var previous = SystemClock.uptimeMillis()
    var longest = 0L
    var frames = 0
    while (true) {
        withFrameMillis { }
        val now = SystemClock.uptimeMillis()
        if (state.progress > 0.01f) {
            if (firstMotion < 0) firstMotion = now else longest = maxOf(longest, now - previous)
            frames++
        }
        previous = now
        if (firstMotion >= 0 && state.currentSnapPoint != null && !state.isAnimationRunning) break
    }
    OpenTimings.results[demo.name] = OpenTiming(firstMotion - openedAt, previous - openedAt, longest, frames)
}
