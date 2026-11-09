package dev.better.sheet.findmy

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.better.sheet.BetterModalSheet
import dev.better.sheet.BetterSheetHost
import dev.better.sheet.SheetBehavior
import dev.better.sheet.SheetBodyMode
import dev.better.sheet.SheetKeyboardBehavior
import dev.better.sheet.SheetPresentation
import dev.better.sheet.SheetSnapPoint
import dev.better.sheet.SheetStackState
import dev.better.sheet.rememberSheetStackState
import dev.better.sheet.sheetStackEffect
import kotlin.math.pow
import dev.better.sheet.SheetState
import dev.better.sheet.rememberSheetState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

internal data class Friend(
    val id: String,
    val name: String,
    val initials: String,
    val color: Color,
    val detail: String,
    val address: String,
    val live: Boolean,
    val latitude: Double,
    val longitude: Double,
    val onMap: Boolean = true,
)

internal val friends = listOf(
    Friend("kim", "Kim Martin", "KM", Color(0xFFC4A6F6), "Live", "1008 Brightskies St, Morrisville, NC 27560", true, 35.9144, -78.8123),
    Friend("maya", "Maya Chen", "MC", Color(0xFF64B5F6), "Home · 12m ago", "88 Gemfor St, Morrisville", false, 35.9172, -78.8168),
    Friend("jon", "Jon Ellis", "JE", Color(0xFFFF9F0A), "Work · now", "400 Trilogy Blvd", false, 35.9184, -78.8082),
    Friend("priya", "Priya Shah", "PS", Color(0xFFFF6B8A), "School · 1h ago", "15 Smallwood Dr", false, 35.9116, -78.8160),
    Friend("alex", "Alex Kim", "AK", Color(0xFF34C759), "No location found", "Location off", false, 35.9108, -78.8074),
    Friend("sam", "Sam Rivera", "SR", Color(0xFFE0B03A), "Gym · 25m ago", "220 Alexander Dr", false, 35.9196, -78.8128),
    Friend("riley", "Riley Brooks", "RB", Color(0xFF8E8E93), "Home · 3h ago", "19 Gemfor St", false, 35.9080, -78.8200, onMap = false),
    Friend("casey", "Casey Nguyen", "CN", Color(0xFF5E5CE6), "Work · 2h ago", "900 Kit Creek Rd", false, 35.9088, -78.8040, onMap = false),
    Friend("morgan", "Morgan Lee", "ML", Color(0xFFFF375F), "School · yesterday", "4 Trilogy Blvd", false, 35.9065, -78.8140, onMap = false),
    Friend("taylor", "Taylor Brooks", "TB", Color(0xFF64B5F6), "No location found", "Location off", false, 35.9072, -78.8088, onMap = false),
    Friend("jordan", "Jordan Hale", "JH", Color(0xFFFF9F0A), "Airport · 5h ago", "RDU", false, 35.9055, -78.8110, onMap = false),
    Friend("avery", "Avery Cole", "AC", Color(0xFF34C759), "Home · yesterday", "77 Brightskies St", false, 35.9060, -78.8065, onMap = false),
)

private val Live = Color(0xFF30D158)
private val SheetCorner = 36.dp

private class FindColors(
    val night: Boolean,
    val canvas: Color,
    val sheet: Color,
    val ink: Color,
    val mist: Color,
    val fill: Color,
    val fillStrong: Color,
    val hairline: Color,
    val handle: Color,
    val chip: Color,
    val titleShadow: Color,
    val link: Color,
    val danger: Color,
)

private val NightColors = FindColors(
    night = true,
    canvas = Color(0xFF101114),
    sheet = Color(0xFF1C1C1E),
    ink = Color.White,
    mist = Color(0x99EBEBF5),
    fill = Color.White.copy(alpha = 0.08f),
    fillStrong = Color.White.copy(alpha = 0.10f),
    hairline = Color.White.copy(alpha = 0.12f),
    handle = Color.White.copy(alpha = 0.28f),
    chip = Color.White.copy(alpha = 0.16f),
    titleShadow = Color.Black.copy(alpha = 0.45f),
    link = Color(0xFF0A84FF),
    danger = Color(0xFFFF453A),
)

private val DayColors = FindColors(
    night = false,
    canvas = Color(0xFFE7EEF4),
    sheet = Color.White,
    ink = Color(0xFF1C1C1E),
    mist = Color(0x993C3C43),
    fill = Color.Black.copy(alpha = 0.05f),
    fillStrong = Color.Black.copy(alpha = 0.06f),
    hairline = Color.Black.copy(alpha = 0.10f),
    handle = Color.Black.copy(alpha = 0.18f),
    chip = Color.Black.copy(alpha = 0.06f),
    titleShadow = Color.White.copy(alpha = 0.85f),
    link = Color(0xFF007AFF),
    danger = Color(0xFFFF3B30),
)

private val LocalFindColors = staticCompositionLocalOf { NightColors }

@Composable
private fun findColors(): FindColors = LocalFindColors.current

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
// Handle (20) + tabs (68) + floating tray gap (12). Contextual footers live in the body,
// so neither the collapsed anchor nor the tab bar moves when the selected tab changes.
private val CollapsedHeight = 100.dp
private val MainSnapPoints = listOf(
    SheetSnapPoint.fixed("collapsed", CollapsedHeight),
    SheetSnapPoint.fixed("peek", 340.dp),
    SheetSnapPoint.fraction("medium", 0.56f),
    SheetSnapPoint.expanded(),
)

/**
 * Instrumented tests move a sheet to a named detent through this.
 * Latest request is replayed so a collector that starts on the next frame still sees it.
 */
object DemoControls {
    private val requests = MutableSharedFlow<Pair<String, String>>(replay = 1, extraBufferCapacity = 1)
    fun go(sheet: String, detent: String) { requests.tryEmit(sheet to detent) }

    @Composable
    fun Bind(sheet: String, state: SheetState, enabled: Boolean) {
        LaunchedEffect(sheet, enabled) {
            if (!enabled) return@LaunchedEffect
            requests.collect { (target, detent) ->
                if (target == sheet) state.snapTo(detent)
            }
        }
    }
}

@Composable
internal fun FindMyApp() {
    val mapController = remember { TownMapController() }
    val colors = if (mapController.night) NightColors else DayColors
    val view = LocalView.current
    LaunchedEffect(colors.night) {
        val activity = view.context.findActivity() as? ComponentActivity ?: return@LaunchedEffect
        val style = if (colors.night) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    MaterialTheme(
        colorScheme = if (colors.night) {
            darkColorScheme(primary = colors.link, onPrimary = Color.White, surface = colors.sheet, onSurface = colors.ink)
        } else {
            lightColorScheme(primary = colors.link, onPrimary = Color.White, surface = colors.sheet, onSurface = colors.ink)
        },
    ) {
        CompositionLocalProvider(LocalFindColors provides colors, LocalContentColor provides colors.ink) {
        val scope = rememberCoroutineScope()
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val people = rememberSheetState(initialSnapPoint = "medium")
        val stack = rememberSheetStackState()
        var nearbyOpen by rememberSaveable { mutableStateOf(false) }
        val personState = rememberSheetState(initialSnapPoint = "content", initiallyVisible = false)
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        var tab by rememberSaveable { mutableStateOf("People") }
        var contactFor by rememberSaveable { mutableStateOf<String?>(null) }
        var notifyOpen by rememberSaveable { mutableStateOf(false) }
        var stopSharing by rememberSaveable { mutableStateOf(false) }
        var places by rememberSaveable { mutableStateOf(false) }
        var contactProgress by remember { mutableFloatStateOf(0f) }
        var notifyProgress by remember { mutableFloatStateOf(0f) }
        var stopProgress by remember { mutableFloatStateOf(0f) }
        val selected = friends.find { it.id == selectedId }
        val overlay = nearbyOpen || contactFor != null || notifyOpen || stopSharing
        val peopleFront = selected == null && !overlay

        LaunchedEffect(selectedId) { mapController.recenter(friends.find { it.id == selectedId }) }
        DemoControls.Bind("people", people, enabled = peopleFront && tab == "People")
        DemoControls.Bind("chat", people, enabled = peopleFront && tab == "Chat")
        DemoControls.Bind("person", personState, enabled = selected != null && !overlay)
        val density = LocalDensity.current
        val imeVisible = WindowInsets.ime.getBottom(density) > WindowInsets.navigationBars.getBottom(density)
        BackHandler(enabled = peopleFront && people.targetSnapPoint != "collapsed") {
            if (imeVisible) { focus.clearFocus(); keyboard?.hide() }
            else scope.launch { people.snapTo("collapsed") }
        }
        SideEffect {
            if (contactFor == null) contactProgress = 0f
            if (!notifyOpen) notifyProgress = 0f
            if (!stopSharing) stopProgress = 0f
        }

        BoxWithConstraints(Modifier.fillMaxSize().background(colors.canvas)) {
            val density = LocalDensity.current
            val topInset = maxOf(
                WindowInsets.systemBars.getTop(density),
                WindowInsets.displayCutout.getTop(density),
            )
            val bottomInset = WindowInsets.navigationBars.getBottom(density)
            val available = (constraints.maxHeight - topInset - bottomInset).coerceAtLeast(1)
            val sheetLift = maxOf(
                people.progress,
                if (selected != null) personState.progress else 0f,
                contactProgress,
                notifyProgress,
                stopProgress,
            )
            val controlsClear = sheetLift * (available - with(density) { 24.dp.toPx() }) + bottomInset
            SideEffect { mapController.insets(controlsClear.toInt()) }

            TownMap(
                controller = mapController,
                selectedId = selectedId,
                onSelect = { selectedId = it.id; places = false },
                modifier = Modifier.fillMaxSize(),
            )

            val titleAlpha = if (selected != null) 0f else ((0.78f - people.progress) / 0.22f).coerceIn(0f, 1f)
            if (titleAlpha > 0.02f) {
                Text(
                    tab,
                    modifier = Modifier.statusBarsPadding().padding(start = 20.dp, top = 8.dp),
                    color = colors.ink.copy(alpha = titleAlpha),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(shadow = Shadow(colors.titleShadow, Offset(0f, 1f), blurRadius = 8f)),
                )
            }
            Column(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MapButton(onClick = { mapController.toggleStyle() }) {
                    Icon(Icons.Filled.Layers, contentDescription = "Map style", tint = colors.ink, modifier = Modifier.size(20.dp))
                }
                MapButton(onClick = { mapController.recenter(selected) }) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "My location", tint = colors.ink, modifier = Modifier.size(20.dp))
                }
                MapButton(onClick = { nearbyOpen = true }) {
                    Icon(Icons.Filled.PersonSearch, contentDescription = "Nearby people", tint = colors.ink,
                        modifier = Modifier.size(20.dp).testTag("open-nearby"))
                }
            }

            MainSheet(
                state = people,
                front = peopleFront,
                tab = tab,
                onTabSelected = {
                    if (it != tab) { focus.clearFocus(); keyboard?.hide() }
                    tab = it
                    if (people.targetSnapPoint == "collapsed") scope.launch { people.snapTo("medium") }
                },
                collapsedFraction = with(density) { CollapsedHeight.toPx() } /
                    (available - with(density) { 24.dp.toPx() }).coerceAtLeast(1f),
                onSelect = { selectedId = it.id; places = false },
            )
            if (selected != null) {
                PersonSheet(
                    friend = selected,
                    state = personState,
                    stack = stack,
                    front = !overlay,
                    places = places,
                    onTogglePlaces = { places = !places },
                    onContact = {
                        scope.launch {
                            personState.snapTo("stack")
                            contactFor = selected.id
                        }
                    },
                    onHideLocation = { stopSharing = true },
                    onClosed = {
                        selectedId = null
                        places = false
                    },
                )
            }

        }

        if (nearbyOpen) {
            NearbyPeopleSheet(
                onDismissed = { nearbyOpen = false },
                onSelect = { selectedId = it.id; places = false },
            )
        }
        contactFor?.let { id ->
            val friend = friends.first { it.id == id }
            ContactSheet(
                friend = friend,
                stack = stack,
                onNotify = { notifyOpen = true },
                onDismissed = {
                    contactFor = null
                    scope.launch { personState.snapTo("content") }
                },
                onProgress = { contactProgress = it },
            )
        }
        if (notifyOpen && selected != null) {
            NotifySheet(friend = selected, stack = stack, onDismissed = { notifyOpen = false }, onProgress = { notifyProgress = it })
        }
        if (stopSharing && selected != null) {
            StopSharingSheet(friend = selected, onDismissed = { stopSharing = false }, onProgress = { stopProgress = it })
        }
        }
    }
}

// Each incoming sheet covers 75% of the scaled sheet beneath it. Multiplying by the
// recession factor keeps that ratio true when a third sheet pushes BOTH earlier sheets back.
private const val StackScale = 0.96f
private const val StackHeightRatio = 0.75f * StackScale
private const val StackBaseHeight = 0.9f

private fun Modifier.recedeInStack(): Modifier = sheetStackEffect { entry ->
    scaleX = StackScale.pow(entry.animatedDepth)
    scaleY = scaleX
    cornerRadius = SheetCorner
}

@Composable
private fun NearbyPeopleSheet(onDismissed: () -> Unit, onSelect: (Friend) -> Unit) {
    val colors = findColors()
    val state = rememberSheetState("medium")
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val points = remember {
        listOf(SheetSnapPoint.fraction("peek", 0.4f), SheetSnapPoint.fraction("medium", 0.56f), SheetSnapPoint.expanded())
    }
    BetterModalSheet(
        state = state,
        snapPoints = points,
        onDismissed = {
            selectedId?.let { id -> friends.find { it.id == id }?.let(onSelect) }
            onDismissed()
        },
        title = "Nearby people",
        bodyMode = SheetBodyMode.Fill,
        containerColor = colors.sheet,
        contentColor = colors.ink,
        scrimColor = Color.Black.copy(alpha = 0.22f),
        shape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner),
        sheetTransform = Modifier.testTag("nearby-surface"),
        dragHandle = { Handle() },
        header = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Nearby people", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.ink)
                        Text("Find your friends around town", fontSize = 14.sp, color = colors.mist)
                    }
                    CloseButton(Modifier.testTag("close-nearby")) { scope.launch { state.hide() } }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("peek" to "Peek", "medium" to "Half", "expanded" to "Full").forEach { (id, label) ->
                        val selected = state.targetSnapPoint == id
                        Button(
                            onClick = { scope.launch { state.snapTo(id) } },
                            modifier = Modifier.weight(1f).testTag("nearby-snap-$id"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) colors.link else colors.fillStrong,
                                contentColor = if (selected) Color.White else colors.ink,
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        ) { Text(label, fontSize = 14.sp) }
                    }
                }
            }
        },
        footer = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("${friends.size} friends", Modifier.weight(1f), color = colors.mist, fontSize = 14.sp)
                Button(onClick = { scope.launch { state.hide() } }) { Text("Done") }
            }
        },
    ) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("nearby-list"), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(friends, key = { it.id }) { friend ->
                PersonRow(friend) { selectedId = friend.id; scope.launch { state.hide() } }
            }
        }
    }
}

@Composable
private fun MainSheet(
    state: SheetState,
    front: Boolean,
    tab: String,
    onTabSelected: (String) -> Unit,
    collapsedFraction: Float,
    onSelect: (Friend) -> Unit,
) {
    val colors = findColors()
    val peopleList = rememberLazyListState()
    val history = remember { chatHistory() }
    val chatList = rememberLazyListState(initialFirstVisibleItemIndex = history.lastIndex)
    var draft by rememberSaveable { mutableStateOf("") }
    val sent = remember { mutableStateListOf<ChatLine>() }
    val messages = history + sent
    LaunchedEffect(sent.size) { if (sent.isNotEmpty()) chatList.pinToEnd() }
    val expansion = ((state.progress - collapsedFraction) / (1f - collapsedFraction)).coerceIn(0f, 1f)
    // Drive the effect directly from sheet position: the final quarter above the tab bar.
    val closing = (1f - expansion / 0.25f).coerceIn(0f, 1f)
    BetterSheetHost(
        state = state,
        snapPoints = MainSnapPoints,
        presentation = SheetPresentation.Tray(),
        behavior = SheetBehavior(
            keyboardBehavior = if (tab == "Chat") SheetKeyboardBehavior.Expand else SheetKeyboardBehavior.Ignore,
            scrimEnabled = false,
            dismissOnDrag = false,
            dismissOnBack = false,
            dismissOnScrim = false,
            draggable = front,
        ),
        bodyMode = SheetBodyMode.Fill,
        title = tab,
        containerColor = colors.sheet,
        contentColor = colors.ink,
        scrimColor = Color.Unspecified,
        dragHandle = {
            Box(Modifier.testTag(if (front) "sheet-header" else "main-handle")) { Handle() }
        },
        footer = { MainTabs(tab, onTabSelected) },
    ) {
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.fillMaxSize().blur((16f * closing).dp)
                .graphicsLayer { alpha = 1f - closing }
                .then(if (expansion <= 0.001f) Modifier.clearAndSetSemantics {} else Modifier),
            transitionSpec = {
                val direction = if (targetState == "Chat") 1 else -1
                (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { direction * it / 12 })
                    .togetherWith(fadeOut(tween(140)) + slideOutHorizontally(tween(260)) { -direction * it / 12 })
                    .using(null)
            },
            label = "People and chat",
        ) { selectedTab ->
            Column(Modifier.fillMaxSize()) {
                TabTitle(if (selectedTab == "People") "People" else "Friends", Modifier.revealHeight(1f - closing))
                if (selectedTab == "People") {
                    LazyColumn(
                        state = peopleList,
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag(if (front) "people-list" else "covered-people-list"),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                    ) {
                        items(friends, key = { it.id }) { friend ->
                            PersonRow(friend, Modifier.testTag("person-${friend.id}"), onClick = { onSelect(friend) })
                        }
                    }
                    MeRow(Modifier.revealHeight(1f - closing).testTag("people-footer"))
                } else {
                    ChatContent(messages, chatList, Modifier.weight(1f).fillMaxWidth())
                    ChatComposer(
                        draft = draft,
                        onDraftChange = { draft = it },
                        onSend = {
                            if (draft.isNotBlank()) {
                                sent.add(ChatLine(draft.trim(), mine = true))
                                draft = ""
                            }
                        },
                        modifier = Modifier.revealHeight(1f - closing),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        color = findColors().ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun MainTabs(tab: String, onTabSelected: (String) -> Unit) {
    val colors = findColors()
    Row(
        Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("main-tabs").semantics { stateDescription = tab },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("People", "Chat").forEach { name ->
            val selected = tab == name
            val background by animateColorAsState(
                if (selected) colors.link.copy(alpha = 0.12f) else Color.Transparent,
                tween(220), label = "Selected tab",
            )
            Column(
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(28.dp)).background(background)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onTabSelected(name) })
                    .testTag("tab-${name.lowercase()}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val tint = if (selected) colors.link else colors.mist
                Icon(if (name == "People") Icons.Filled.Person else Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Text(name, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Shrink contextual actions with the drag, keeping the permanent tabs at a fixed height. */
private fun Modifier.revealHeight(fraction: Float): Modifier = this.clipToBounds().layout { measurable, constraints ->
    val child = measurable.measure(constraints.copy(minHeight = 0))
    layout(child.width, (child.height * fraction).toInt()) { child.placeRelative(0, 0) }
}

@Composable
private fun PersonSheet(
    friend: Friend,
    state: SheetState,
    stack: SheetStackState,
    front: Boolean,
    places: Boolean,
    onTogglePlaces: () -> Unit,
    onContact: () -> Unit,
    onHideLocation: () -> Unit,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = findColors()
    val scope = rememberCoroutineScope()
    LaunchedEffect(friend.id) { state.show("content") }
    BetterModalSheet(
        state = state,
        stackState = stack,
        onDismissed = onClosed,
        snapPoints = listOf(SheetSnapPoint.fitContent(), SheetSnapPoint.fraction("stack", StackBaseHeight), SheetSnapPoint.expanded()),
        sheetTransform = Modifier.recedeInStack().testTag("stack-profile-surface"),
        modifier = modifier,
        behavior = SheetBehavior(
            keyboardBehavior = SheetKeyboardBehavior.Ignore,
            scrimEnabled = false,
            draggable = front,
            dismissOnScrim = false,
        ),
        bodyMode = SheetBodyMode.Scroll,
        title = friend.name,
        containerColor = colors.sheet,
        contentColor = colors.ink,
        scrimColor = Color.Unspecified,
        shape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner),
        dragHandle = { Handle() },
        header = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("sheet-header")) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(friend.name, Modifier.testTag("stack-base-title"), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = colors.ink)
                        Spacer(Modifier.height(4.dp))
                        Text(friend.address, color = colors.mist, fontSize = 15.sp, lineHeight = 20.sp)
                        if (friend.live) {
                            Spacer(Modifier.height(2.dp))
                            Text("Live", color = Live, fontWeight = FontWeight.SemiBold)
                        } else {
                            Spacer(Modifier.height(2.dp))
                            Text(friend.detail, color = colors.mist)
                        }
                    }
                    CloseButton(Modifier.testTag("stack-close-base")) { scope.launch { state.hide() } }
                }
                Spacer(Modifier.height(14.dp))
            }
        },
        footer = {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).testTag(if (front) "footer" else "person-footer")) {
                Text(
                    "Hide Location",
                    color = colors.link,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(onClick = onHideLocation).testTag("hide-location"),
                )
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).testTag("fit-body"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard(
                    title = "Contact",
                    subtitle = "Info",
                    modifier = Modifier.weight(1f).testTag("stack-open-details"),
                    onClick = onContact,
                ) { Icon(Icons.Filled.Person, contentDescription = null, tint = colors.link, modifier = Modifier.size(22.dp)) }
                ActionCard(
                    title = "Find",
                    subtitle = if (places) "Nearby" else "Places",
                    modifier = Modifier.weight(1f).testTag(if (front) "primary-action" else "find-places"),
                    onClick = onTogglePlaces,
                ) { Icon(Icons.Filled.Navigation, contentDescription = null, tint = colors.link, modifier = Modifier.size(20.dp)) }
            }
            if (places) {
                nearbyPlaces.forEach { (name, distance) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.fillStrong).padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(name, color = colors.ink, fontWeight = FontWeight.Medium)
                        Text(distance, color = colors.mist)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.fillStrong).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(colors.fill), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Navigation, contentDescription = null, tint = colors.link, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("My Location", color = colors.ink, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text("Always sharing", color = colors.mist, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private val nearbyPlaces = listOf("Dunkin'" to "0.2 mi", "Food Lion" to "0.6 mi", "Morrisville Library" to "1.1 mi")

@Composable
private fun ChatComposer(draft: String, onDraftChange: (String) -> Unit, onSend: () -> Unit, modifier: Modifier = Modifier) {
    val colors = findColors()
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).testTag("chat-footer"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            draft, onDraftChange,
            modifier = Modifier.weight(1f).testTag("composer"),
            placeholder = { Text("Message") }, maxLines = 3,
            shape = RoundedCornerShape(24.dp), colors = glassFieldColors(),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(colors.link).clickable(onClick = onSend).testTag("send"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ChatContent(messages: List<ChatLine>, list: LazyListState, modifier: Modifier = Modifier) {
    val colors = findColors()
    LazyColumn(
        state = list,
        modifier = modifier.testTag("chat-list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(messages.size, key = { it }) { index ->
            val line = messages[index]
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.mine) Arrangement.End else Arrangement.Start) {
                Text(
                    line.text,
                    modifier = Modifier.fillMaxWidth(0.78f).clip(RoundedCornerShape(18.dp))
                        .background(if (line.mine) colors.link else colors.fillStrong)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    color = if (line.mine) Color.White else colors.ink,
                )
            }
        }
    }
}

private data class ChatLine(val text: String, val mine: Boolean)

private fun chatHistory(): List<ChatLine> {
    val script = listOf(
        "Heading to Brightskies now." to false,
        "I'm at the Dunkin' on Alexander." to true,
        "Kendra's live if you want to meet there." to false,
        "Five minutes out." to true,
        "Priya is still at school." to false,
        "We can swing by after." to true,
        "Jon just got to the office." to false,
        "Tell him we're on Trilogy." to true,
        "Sam's at the gym until 6." to false,
        "Leave the pin up so I can find you." to true,
    )
    return buildList {
        repeat(4) { pass ->
            script.forEach { (text, mine) -> add(ChatLine(if (pass == 0) text else "$text ($pass)", mine)) }
        }
    }
}

@Composable
private fun ContactSheet(friend: Friend, stack: SheetStackState, onNotify: () -> Unit, onDismissed: () -> Unit, onProgress: (Float) -> Unit = {}) {
    val colors = findColors()
    val scope = rememberCoroutineScope()
    val state = rememberSheetState(initialSnapPoint = "medium")
    val fields = listOf("Mobile", "Email", "Address", "Note", "Relationship", "Nickname")
    SideEffect { onProgress(state.progress) }
    BetterModalSheet(
        state = state,
        stackState = stack,
        sheetTransform = Modifier.recedeInStack().testTag("stack-contact-surface"),
        snapPoints = listOf(SheetSnapPoint.fraction("medium", StackBaseHeight * StackHeightRatio), SheetSnapPoint.expanded()),
        onDismissed = onDismissed,
        behavior = SheetBehavior(keyboardBehavior = SheetKeyboardBehavior.Resize),
        bodyMode = SheetBodyMode.Scroll,
        title = "Contact Info",
        containerColor = colors.sheet,
        contentColor = colors.ink,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        shape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner),
        dragHandle = { Handle() },
        header = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Contact Info", Modifier.testTag("stack-details-title"), color = colors.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(friend.name, color = colors.mist)
                }
                CloseButton(Modifier.testTag("stack-close-details")) { scope.launch { state.hide() } }
            }
        },
        footer = {
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = onNotify,
                    modifier = Modifier.fillMaxWidth().testTag("stack-open-confirm"),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.link, contentColor = Color.White),
                ) { Text("Notify Me") }
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Saved on this device. Notify Me asks before it shares a place.", color = colors.mist)
            fields.forEachIndexed { index, label ->
                var value by rememberSaveable { mutableStateOf("") }
                OutlinedTextField(
                    value,
                    { value = it },
                    modifier = Modifier.fillMaxWidth().testTag("field-$index"),
                    label = { Text(label) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = glassFieldColors(),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NotifySheet(friend: Friend, stack: SheetStackState, onDismissed: () -> Unit, onProgress: (Float) -> Unit = {}) {
    val colors = findColors()
    val scope = rememberCoroutineScope()
    val state = rememberSheetState(initialSnapPoint = "notification")
    SideEffect { onProgress(state.progress) }
    BetterModalSheet(
        state = state,
        stackState = stack,
        sheetTransform = Modifier.recedeInStack().testTag("stack-notify-surface"),
        snapPoints = listOf(SheetSnapPoint.fraction("notification", StackBaseHeight * StackHeightRatio * StackHeightRatio)),
        onDismissed = onDismissed,
        title = "Notify Me",
        containerColor = colors.sheet,
        contentColor = colors.ink,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        shape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner),
        dragHandle = { Handle() },
        header = {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Notify Me", Modifier.testTag("stack-confirm-title"), color = colors.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(friend.name, color = colors.mist)
                Spacer(Modifier.height(8.dp))
            }
        },
        footer = {
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = { scope.launch { state.hide() } },
                    modifier = Modifier.fillMaxWidth().testTag("stack-send-review"),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.link, contentColor = Color.White),
                ) { Text("Turn On") }
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Know when ${friend.name.substringBefore(' ')} arrives or leaves. Notifications stay on until you turn them off.",
                color = colors.ink.copy(alpha = 0.9f), fontSize = 16.sp, lineHeight = 23.sp)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.fill).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Navigation, contentDescription = null, tint = colors.link, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Brightskies", color = colors.ink, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text("Arrives or leaves", color = colors.mist, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun StopSharingSheet(friend: Friend, onDismissed: () -> Unit, onProgress: (Float) -> Unit = {}) {
    val colors = findColors()
    val scope = rememberCoroutineScope()
    val state = rememberSheetState(initialSnapPoint = "content")
    SideEffect { onProgress(state.progress) }
    BetterModalSheet(
        state = state,
        snapPoints = listOf(SheetSnapPoint.fitContent()),
        onDismissed = onDismissed,
        behavior = SheetBehavior(dismissOnDrag = false, dismissOnScrim = false, dismissOnBack = false),
        title = "Stop Sharing Location",
        containerColor = colors.sheet,
        contentColor = colors.ink,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        shape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner),
        dragHandle = { Handle() },
        header = {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Stop Sharing Location", Modifier.testTag("sheet-title"), color = colors.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
            }
        },
        footer = {
            Column(Modifier.fillMaxWidth().padding(16.dp).testTag("footer"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { scope.launch { state.hide() } },
                    modifier = Modifier.fillMaxWidth().testTag("primary-action"),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.danger, contentColor = Color.White),
                ) { Text("Stop Sharing") }
                Box(Modifier.fillMaxWidth().clickable { scope.launch { state.hide() } }, contentAlignment = Alignment.Center) {
                    Text("Cancel", color = colors.link, modifier = Modifier.padding(12.dp))
                }
            }
        },
    ) {
        Text(
            "${friend.name.substringBefore(' ')} won't see your location anymore. You can start sharing again from My Location.",
            Modifier.padding(horizontal = 20.dp),
            color = colors.ink.copy(alpha = 0.9f),
        )
    }
}

@Composable
private fun Handle() {
    val colors = findColors()
    Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(36.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(colors.handle))
    }
}

@Composable
private fun MeRow(modifier: Modifier = Modifier) {
    val colors = findColors()
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.hairline))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(colors.link), contentAlignment = Alignment.Center) {
                Text("Me", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Me", color = colors.ink, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text("This device · Always sharing", color = colors.mist, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PersonRow(friend: Friend, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = findColors()
    Column(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(friend.color), contentAlignment = Alignment.Center) {
                Text(friend.initials, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(friend.name, color = colors.ink, fontWeight = FontWeight.Medium, fontSize = 17.sp)
                Text(friend.detail, color = if (friend.live) Live else colors.mist, fontSize = 13.sp)
            }
        }
        Box(Modifier.padding(start = 72.dp).fillMaxWidth().height(0.5.dp).background(colors.hairline))
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val colors = findColors()
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(colors.fill).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon()
        Column {
            Text(title, color = colors.ink, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Text(subtitle, color = colors.mist, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CloseButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = findColors()
    Box(
        modifier.size(30.dp).clip(CircleShape).background(colors.chip).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.ink, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun MapButton(onClick: () -> Unit, icon: @Composable () -> Unit) {
    val colors = findColors()
    Box(
        Modifier.size(44.dp).shadow(6.dp, CircleShape).clip(CircleShape)
            .background(colors.sheet)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun glassFieldColors(): TextFieldColors {
    val colors = findColors()
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.ink,
        unfocusedTextColor = colors.ink,
        focusedContainerColor = colors.fill,
        unfocusedContainerColor = colors.fill,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        cursorColor = colors.link,
        focusedLabelColor = colors.mist,
        unfocusedLabelColor = colors.mist,
        focusedPlaceholderColor = colors.mist,
        unfocusedPlaceholderColor = colors.mist,
    )
}

/** Puts the last item on the bottom edge without pinning later sheet growth to that edge. */
private suspend fun LazyListState.pinToEnd() {
    val last = layoutInfo.totalItemsCount - 1
    if (last < 0) return
    scrollToItem(last)
    val item = layoutInfo.visibleItemsInfo.lastOrNull() ?: return
    val delta = (item.offset + item.size - layoutInfo.viewportEndOffset).toFloat()
    if (delta > 1f) scroll { scrollBy(delta) }
}

