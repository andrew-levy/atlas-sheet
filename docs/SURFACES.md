# Sheet material and transform

Better Sheet owns sheet behavior and geometry. Apps supply header/body/footer content and can choose a decorative material behind it:

```kotlin
BetterSheetHost(
    state = state,
    snapPoints = listOf(SheetSnapPoint.fraction("half", .5f), SheetSnapPoint.expanded()),
    sheetTransform = Modifier.sheetStackEffect { entry ->
        scaleX = 1f - .04f * entry.animatedDepth
    },
    sheetMaterial = { outline -> GlassMaterial(outline) },
    header = { MyHeader() },
    footer = { MyFooter() },
) { MyBody() }
```

`GlassMaterial` above is an app/renderer-provided composable. A renderer-free material is ordinary Compose UI:

```kotlin
sheetMaterial = { _ ->
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFF4F1E9), Color(0xFF91C6B8))),
        ),
    )
}
```

All three entry points (`BetterSheet`, `BetterSheetHost`, `BetterModalSheet`) expose:

```kotlin
sheetTransform: Modifier = Modifier,
sheetMaterial: (@Composable (outline: Shape) -> Unit)? = null,
```

`outline` is the host's resolved Compose `Shape`, including continuous tray morphs or mirrored top-sheet corners. Use it for a renderer's lens/highlight calculation. No material classes, scopes, enums or navigation flags are required.

The material is a **single composable layer**, measured by the host behind caller content. For standard bottom sheets it extends through the navigation area; expanded trays already include that area in their morphing surface. Gradients and backdrop textures therefore paint continuously without app-side navigation branches or duplicated material instances. Content, footer and detent positions do not change when selecting a material. The host clips child drawing above reserved navigation pixels so footer effects cannot spill into them.

`sheetTransform` applies outer decoration/stack transforms to the whole sheet, including its material. It does not transform the full-window scrim. `Modifier.sheetStackEffect` still requires a stack entry; it remains a no-op without one. A material composable can internally use a drawing modifier, graphics layer or a renderer-specific `Box`; it does not need to return a modifier to Better Sheet.

Null material keeps the existing solid background, shadow and navigation-cover rendering. A custom material keeps the host shadow, with the existing zero elevation for top sheets. Disable a renderer's own shadow to avoid doubling it. A custom standard bottom material's outline spans the navigation area as one surface; this can move custom bottom-corner decoration into that area. The default solid path is unchanged.

Treat the material slot as decoration: do not put app controls, focus, gestures or content semantics there. Header/body/footer remain the interactive slots. Material children receive fixed painting constraints and cannot determine sheet height. The host owns placement and clipping. It owns drag/snap, scroll handoff, keyboard sizing, dismissal and content safe areas independently of material.

Top materials include status-bar padding and do not create a bottom navigation extension. During IME display, the existing keyboard policy determines the available area; there is no extra material-only navigation extension underneath the IME. Modal materials live in the Dialog's Compose root: this API does not capture an Activity behind a separate window. Apps/renderers must arrange a valid backdrop source. Content colors, scrim and system-bar icons remain separate; `containerColor` still informs modal icon contrast even when a material replaces its fill.

## Migration

Rename the former `surfaceModifier` argument to `sheetTransform`. Replace the draft PR's modifier-returning `surface` callback with a `sheetMaterial` composable, wrapping a renderer's drawing modifier in a `Box` if needed. No navigation branching is required.

The transform argument rename is source breaking for named callers; public composable signatures also change, so compiled clients must be rebuilt. Release/version policy needs review before publishing. Positional final-content arguments may need naming or adjustment.
