package dev.atlas.sheet.findmy

import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import kotlin.math.roundToInt

internal val HomeTarget = LatLng(35.9162, -78.8124)
private const val STYLE_NIGHT = "https://tiles.openfreemap.org/styles/fiord"
private const val STYLE_DAY = "https://tiles.openfreemap.org/styles/liberty"
private val MeDot = LatLng(35.9131, -78.8102)

/**
 * Real streets from OpenFreeMap (OpenStreetMap), drawn with MapLibre.
 * Google Maps needs an API key this sample does not ship.
 */
internal class TownMapController {
    var map: MapLibreMap? = null
    var night by mutableStateOf(true)
        private set

    fun recenter(selected: Friend?) {
        val target = selected?.let { LatLng(it.latitude, it.longitude) } ?: HomeTarget
        move(target, if (selected == null) 14.35 else 16.1)
    }

    fun toggleStyle() {
        val map = map ?: return
        night = !night
        map.setStyle(if (night) STYLE_NIGHT else STYLE_DAY)
    }

    fun insets(bottomPx: Int) {
        map?.uiSettings?.apply {
            setLogoMargins(20, 0, 0, bottomPx + 8)
            setAttributionMargins(20, 0, 0, bottomPx + 8)
        }
    }

    internal fun attach(map: MapLibreMap) {
        this.map = map
        map.setStyle(STYLE_NIGHT)
        map.uiSettings.apply {
            isTiltGesturesEnabled = false
            isCompassEnabled = false
            isRotateGesturesEnabled = false
            attributionGravity = Gravity.BOTTOM or Gravity.START
            logoGravity = Gravity.BOTTOM or Gravity.START
        }
        move(HomeTarget, 14.35, animate = false)
    }

    private fun move(target: LatLng, zoom: Double, animate: Boolean = true) {
        val map = map ?: return
        val update = CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(target).zoom(zoom).bearing(0.0).tilt(0.0).build(),
        )
        if (animate) map.animateCamera(update, 650) else map.moveCamera(update)
    }
}

@Composable
internal fun TownMap(
    controller: TownMapController,
    selectedId: String?,
    onSelect: (Friend) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val night = controller.night
    val backdrop = if (night) Color(0xFF1C1C1E) else Color(0xFFF4F6F8)
    val mapView = remember {
        runCatching {
            MapLibre.getInstance(context)
            val options = MapLibreMapOptions.createFromAttributes(context, null)
                .textureMode(true)
                .foregroundLoadColor(0xFF1C1C1E.toInt())
                .camera(
                    CameraPosition.Builder().target(HomeTarget).zoom(14.35).build(),
                )
            MapView(context, options).apply {
                onCreate(null)
                setBackgroundColor(0xFF1C1C1E.toInt())
                getMapAsync { controller.attach(it) }
            }
        }.getOrNull()
    }
    if (mapView == null) {
        Box(modifier.background(backdrop))
        return
    }
    SideEffect { mapView.setBackgroundColor(backdrop.toArgb()) }
    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    var pins by remember { mutableStateOf<Map<String, Offset>>(emptyMap()) }
    var me by remember { mutableStateOf<Offset?>(null) }
    DisposableEffect(mapView) {
        var move: MapLibreMap.OnCameraMoveListener? = null
        mapView.getMapAsync { map ->
            val publish = {
                val projection = map.projection
                pins = friends.filter { it.onMap }.associate { friend ->
                    val point = projection.toScreenLocation(LatLng(friend.latitude, friend.longitude))
                    friend.id to Offset(point.x.toFloat(), point.y.toFloat())
                }
                val dot = projection.toScreenLocation(MeDot)
                me = Offset(dot.x.toFloat(), dot.y.toFloat())
            }
            move = MapLibreMap.OnCameraMoveListener { publish() }
            map.addOnCameraMoveListener(move)
            publish()
        }
        onDispose {
            move?.let { listener -> mapView.getMapAsync { it.removeOnCameraMoveListener(listener) } }
        }
    }
    Box(modifier.semantics { contentDescription = "Map" }) {
        AndroidView(factory = { mapView }, modifier = Modifier.matchParentSize())
        me?.let { point ->
            Box(
                Modifier.offset {
                    IntOffset((point.x - 9.dp.toPx()).roundToInt(), (point.y - 9.dp.toPx()).roundToInt())
                }.size(18.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF0A84FF)))
            }
        }
        friends.filter { it.onMap }.forEach { friend ->
            val point = pins[friend.id] ?: return@forEach
            MapPin(
                friend = friend,
                selected = friend.id == selectedId,
                modifier = Modifier.offset {
                    IntOffset((point.x - 18.dp.toPx()).roundToInt(), (point.y - 46.dp.toPx()).roundToInt())
                },
                onClick = { onSelect(friend) },
            )
        }
    }
}

@Composable
private fun MapPin(friend: Friend, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(36.dp, 52.dp).clickable(onClick = onClick).semantics { contentDescription = friend.name }) {
        Box(
            Modifier.align(Alignment.TopCenter).size(if (selected) 36.dp else 32.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(if (selected) 32.dp else 28.dp).clip(CircleShape).background(friend.color), contentAlignment = Alignment.Center) {
                Text(friend.initials, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).size(12.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (selected) Color(0xFF0A84FF) else friend.color))
        }
    }
}