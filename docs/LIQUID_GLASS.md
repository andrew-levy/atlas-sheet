# Liquid glass playground

Open **Liquid glass playground** in the sample gallery. Drag the clear lens over the
night-sky image, scroll the image under the stationary lens, and drag or tap the bottom
tabs. The selection lens samples the icon row itself, so icons bend as its edge crosses them.

This is a focused XML/View prototype, isolated from the Better Sheet library. It targets
the appearance of iOS Liquid Glass; it is not a verified pixel-for-pixel implementation
of Apple's private renderer.

## Use in an XML layout

Copy `GlassView.kt`, `GlassMotion.kt`, and `LensShader.kt` into your app (adjust their package). Place the
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
The center remains mostly clear. By default `live = false`: scrolling is
observed and interactive glass motion runs only while a spring is active.
Enable `live = true` only for *continuously animated* backdrops, such as video
or custom Canvas animations. That option refreshes at display vsync and adds
ongoing capture/GPU cost. Neither mode uses an endless pre-draw invalidation.
Dragging and tab selection remain host interaction responsibilities; touch
deformation is built into the glass surface once `isClickable = true`.

## Jelly, morphing, and merging

`GlassView` itself has no dependency on a menu, Compose or Expo. The shape
is expressed in **local pixels**. For an ordinary bounded view, the default
shape fills its rectangle. For morphing between multiple controls, use a
full-parent transparent `GlassView` above the background and specify a small
local glass region. Its effect capture is cropped around the actual shape.

```kotlin
val dp = resources.displayMetrics.density
glass.apply {
    backdrop = scene                     // does not contain this overlay
    live = false                          // on-demand for static/scrolling scenes
    jellyEnabled = true
    isClickable = true                    // receives touch press/drag/release
    setShape(RectF(48f*dp, 100f*dp, 160f*dp, 154f*dp), 27f*dp)
    morphTo(RectF(24f*dp, 120f*dp, 310f*dp, 330f*dp), 38f*dp)
    mergeWith(RectF(145f*dp, 108f*dp, 252f*dp, 165f*dp), 28f*dp, 38f*dp)
    // glass.separate() when the joined shape should split again
}
```

`pressAt(x,y)`, `dragTo(x,y)` and `releaseTouch()` are available when
another component owns touch handling. The built-in spring controller drives
jelly deformation, size/radius interpolation and smooth SDF unions. It uses
Choreographer only until all springs settle. API 24–32 render a simple
hard-union translucent fallback instead of AGSL refraction/morph blending.

The sample's **MERGE → EXPAND → RESET** control demonstrates a merging two-lobe
surface, a larger morphing glass sheet, and separation back into a small pill.
The draggable central lens and icon-refracting tab selector remain independent
examples.

## Renderer and performance notes

| Property | Previous prototype | Updated renderer |
|---|---|---|
| API 33+ backdrop | Software draw into an ARGB bitmap, then texture upload | Hardware Canvas recording into a reusable RenderNode |
| Diffusion | Five offset shader samples | GPU blur composed before refraction; skipped at radius zero |
| Edge shape | Snell/bevel approximation | Dynamic rounded signed-distance field with smooth union and independent lobes |
| Color separation | None | Three channel samples at the inner and outer refracting edges |
| Capture bounds | Lens rectangle | Lens rectangle plus blur padding |
| Coordinate alignment | Sibling offsets and manual scale | Global matrix mapping, including nested transforms/scroll |
| Older Android/software canvas | Bitmap mesh | Unrefracted, lightly tinted backdrop fallback |
| Motion | Rigid drag and scale | Jelly swell/stretch, spring morph, smooth lobe merge/split | 
| Animation scheduling | No model | Choreographer while unsettled; no idle frame loop |
| Per-frame CPU bitmap allocation | None after resize, but bitmap redraw/upload each frame | None |
| Frame time / power on physical hardware | Not measured | Not measured; no 60/120 fps guarantee |

The renderer records `source.draw()` for each dirty/animating lens. This still costs UI-thread
work; it does not reuse the source View's private display list. A selection lens over
a glass bar also records the bar's own backdrop. Keep the sampled hierarchy small.
GPU intermediates scale with the padded lens area, not the full screen. Blur requires
extra GPU intermediate storage; zero-blur lenses avoid that pass.

For a 210 × 144 dp lens at 3× density and 1.2 dp blur, a cropped
GPU capture grows to cover the refraction extent as well as the blur padding.
The intermediate surface size therefore depends on refraction distance and
any merged second lobe; it should be profiled rather than assumed constant. This is a size estimate, not measured GPU memory. Larger radii and
multiple lenses increase cost. `live` trades idle power for automatic source refresh;
disable it when the backdrop is static. Listeners are removed on detach and the render
node display list is released.

The glass demo is a bounded, real-time on-device screen recording rather than
hundreds of blocking instrumentation screenshots. It includes lens drag,
background scrolling, refracting tab selection, and merge/morph/reset stages. It is **not a performance benchmark**.
The Find My demos remain frame-stepped. For comparable
performance numbers, use a physical device, release build, fixed resolution and refresh
rate, and collect FrameMetrics/Perfetto during a real-time drag (without screenshots).
Report median/p95 frame duration, missed-frame count, device/API and thermal state.

## Compatibility and validation

Full refraction needs Android 13/API 33 and hardware acceleration. API 24–32 and software
Canvas use a tinted but undistorted fallback. SurfaceView, TextureView, separate windows,
and protected video are outside this prototype's capture contract. A normal ImageView,
ScrollView, text, and Canvas-based icons are supported.

`GlassRenderingTest` checks live scrolling, real pixel changes from refraction
against high-contrast stripes (rather than an almost uniform nebula), resize/
reattach, drag handling, liquid morphing/merging, and recursive-backdrop rejection. The API 24/35 CI matrix runs it.
The `glass` recording uses `adb shell screenrecord` with scripted gestures to show
the lens, scrolling and icon-refraction sequence. Recreate:

```bash
python3 scripts/record-demos.py glass
```

The MP4 is captured at device resolution using Android's built-in H.264 screen
recorder (19 seconds); the GIF and detail PNG are derived from the recording. The demo capture
has a short bounded timeout and does not depend on UIAutomator screenshots.

## Credits

The edge mapping and rounded-rectangle gradient adapt the Apache-2.0 shader work in
[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass), specifically
`backdrop/src/commonMain/kotlin/com/kyant/backdrop/internal/Shaders.kt` on `kmp`.
The source copyright/license notice is retained in `LensShader.kt`; this repository's
Apache-2.0 license applies. The View renderer, simplified controls and demo are local
implementations, with no dependency on Kyant's Compose components. The nebula wallpaper
was generated for this demo.

## Fidelity and ownership

The physics/morphing design takes inspiration from the MIT-licensed
[rit3zh/expo-glass-custom-menu](https://github.com/rit3zh/expo-glass-custom-menu/tree/fresh-main),
while the earlier bevel math was adapted from Apache-2.0 Kyant shaders with
attribution retained. Our Kotlin implementation is standalone and uses no Expo
runtime. Apple's private iOS Liquid Glass renderer, compositor behavior and
cross-process blur are unavailable on Android; exact pixel parity is neither
verified nor claimed. Compare real-device footage and measured frame times
before shipping.
