# Liquid glass playground

Open **Liquid glass playground** in the sample gallery. Drag the clear lens over the
night-sky image, scroll the image under the stationary lens, and drag or tap the bottom
tabs. The selection lens samples the icon row itself, so icons bend as its edge crosses them.

This is a focused XML/View prototype, isolated from the Better Sheet library. It targets
the appearance of iOS Liquid Glass; it is not a verified pixel-for-pixel implementation
of Apple's private renderer.

## Use in an XML layout

Copy `GlassView.kt` and `LensShader.kt` into your app (adjust their package). Place the
source and overlay in the same window. They can be in different nested containers.
The source must not contain the glass view. Avoid cycles between glass backdrops.

```xml
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent">
    <ScrollView android:id="@+id/scene"
        android:layout_width="match_parent" android:layout_height="match_parent">
        <!-- Your normal XML content -->
    </ScrollView>
    <dev.better.sheet.sample.GlassView android:id="@+id/lens"
        android:layout_width="210dp" android:layout_height="144dp"
        android:layout_gravity="center" />
</FrameLayout>
```

```kotlin
val density = resources.displayMetrics.density
findViewById<GlassView>(R.id.lens).apply {
    backdrop = findViewById(R.id.scene)
    cornerRadius = 42f * density
    blurRadius = 1.2f * density
    refractionHeight = 32f * density
    refractionAmount = 40f * density
}
```

All numeric properties use pixels. `refractionHeight` is the width of the curved edge;
`refractionAmount` controls how far that edge pulls the sampled background inward.
The center remains mostly clear. `live = true` refreshes animated sources at a
bounded 20 fps, rather than invalidating in every pre-draw callback (which can
starve the UI and instrumentation). For static or scrollable backdrops, set
`live = false`; scrolling is observed automatically and lens movement already
invalidates the overlay. Explicitly call `invalidate()` when changing unrelated
static background content. The sample uses `live = false` on all three lenses.
Dragging, tab selection, and labels are demo responsibilities, not renderer APIs.

## Renderer and performance notes

| Property | Previous prototype | Updated renderer |
|---|---|---|
| API 33+ backdrop | Software draw into an ARGB bitmap, then texture upload | Hardware Canvas recording into a reusable RenderNode |
| Diffusion | Five offset shader samples | GPU blur composed before refraction; skipped at radius zero |
| Edge shape | Snell/bevel approximation | Rounded-rectangle signed-distance field and circular edge mapping |
| Color separation | None | Three channel samples near the curved edge |
| Capture bounds | Lens rectangle | Lens rectangle plus blur padding |
| Coordinate alignment | Sibling offsets and manual scale | Global matrix mapping, including nested transforms/scroll |
| Older Android/software canvas | Bitmap mesh | Unrefracted, lightly tinted backdrop fallback |
| Per-frame CPU bitmap allocation | None after resize, but bitmap redraw/upload each frame | None |
| Frame time / power on physical hardware | Not measured | Not measured; no 60/120 fps guarantee |

The renderer records `source.draw()` for every active lens. This still costs UI-thread
work; it does not reuse the source View's private display list. A selection lens over
a glass bar also records the bar's own backdrop. Keep the sampled hierarchy small.
GPU intermediates scale with the padded lens area, not the full screen. Blur requires
extra GPU intermediate storage; zero-blur lenses avoid that pass.

For a 210 × 144 dp lens at 3× density and 1.2 dp blur, the padded input is approximately
656 × 458 pixels: **1.15 MiB per RGBA8 surface**, excluding renderer overhead and additional
blur/effect buffers. This is a size estimate, not measured GPU memory. Larger radii and
multiple lenses increase cost. `live` trades idle power for automatic source refresh;
disable it when the backdrop is static. Listeners are removed on detach and the render
node display list is released.

The glass demo is a bounded, real-time on-device screen recording rather than
hundreds of blocking instrumentation screenshots. It is **not a performance benchmark**.
The Find My demos remain frame-stepped. For comparable
performance numbers, use a physical device, release build, fixed resolution and refresh
rate, and collect FrameMetrics/Perfetto during a real-time drag (without screenshots).
Report median/p95 frame duration, missed-frame count, device/API and thermal state.

## Compatibility and validation

Full refraction needs Android 13/API 33 and hardware acceleration. API 24–32 and software
Canvas use a tinted but undistorted fallback. SurfaceView, TextureView, separate windows,
and protected video are outside this prototype's capture contract. A normal ImageView,
ScrollView, text, and Canvas-based icons are supported.

`GlassRenderingTest` checks live scrolling, real pixel changes from refraction, resize/
reattach, drag handling and recursive-backdrop rejection. The API 24/35 CI matrix runs it.
The `glass` recording uses `adb shell screenrecord` with scripted gestures to show
the lens, scrolling and icon-refraction sequence. Recreate:

```bash
python3 scripts/record-demos.py glass
```

The MP4 is captured at device resolution using Android's built-in H.264 screen
recorder; the GIF and detail PNG are derived from the recording. The demo capture
has a short bounded timeout and does not depend on UIAutomator screenshots.

## Credits

The edge mapping and rounded-rectangle gradient adapt the Apache-2.0 shader work in
[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass), specifically
`backdrop/src/commonMain/kotlin/com/kyant/backdrop/internal/Shaders.kt` on `kmp`.
The source copyright/license notice is retained in `LensShader.kt`; this repository's
Apache-2.0 license applies. The View renderer, simplified controls and demo are local
implementations, with no dependency on Kyant's Compose components. The nebula wallpaper
was generated for this demo.
