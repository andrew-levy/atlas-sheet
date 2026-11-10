# Sheet surface and transform

Better Sheet owns geometry, detents, drag/scroll handling, dismissal, and keyboard policy. Two hooks customize its appearance independently:

```kotlin
sheetTransform: Modifier = Modifier,
sheetSurface: (@Composable (outline: Shape) -> Modifier)? = null,
```

`sheetTransform` is the **outer** modifier for scaling, offsetting, or decorating the complete sheet; it does not affect the scrim. `sheetSurface` is a **drawing modifier callback** used instead of the solid container fill, behind header/body/footer. The host owns sizing, clipping, and shadow. Null keeps the default solid sheet.

Both parameters are available on `BetterSheet`, `BetterSheetHost`, and `BetterModalSheet`.

## Example

```kotlin
BetterModalSheet(
    state = state,
    snapPoints = snapPoints,
    sheetTransform = Modifier.sheetStackEffect { entry ->
        scaleX = 1f - .04f * entry.animatedDepth
    },
    sheetSurface = { outline ->
        Modifier.background(
            Brush.verticalGradient(listOf(Color(0xFFF4F1E9), Color(0xFF91C6B8))),
            shape = outline,
        )
    },
    header = { MyHeader() },
    footer = { MyFooter() },
) { MyBody() }
```

A drawing-based glass renderer can be returned from `sheetSurface`, using `outline` as its resolved shape. Better Sheet does not depend on a glass library; a renderer that exposes only a composable rather than a modifier would need an app-side adaptation.

## Painting behavior

`outline` tracks the current tray morph and mirrored top-sheet corners. Use painting-only modifiers such as `background`, `drawBehind`, `drawWithContent`, or a renderer's draw modifier. Do not use modifiers that change layout, pointer input, or semantics; those are owned by the host.

The host applies `sheetSurface` to one fixed-size internal layer behind caller content. For standard bottom sheets, it includes navigation-bar space, so gradients and backdrop textures paint continuously without duplicate callbacks or region branching. Expanded trays already include navigation space in their geometry. Host clipping keeps sticky-footer effects out of reserved navigation pixels; snap point and footer positions do not change.

Custom paint retains the host shadow. Disable renderer-owned shadows to avoid doubling them. Top sheets retain zero elevation. In a custom standard-bottom material, bottom-corner decoration can extend into the navigation area. A null `sheetSurface` keeps the original background and navigation cover.

Top sheets include status-bar padding and no bottom navigation extension. The keyboard policy still controls resizing. Modal sheets render inside a Dialog window; this API does not capture the Activity's pixels behind it. Apps/renderers must provide a valid backdrop source. `containerColor` continues to inform modal system-bar icon contrast.

## Sample

Open **Better Sheet** → **Material & transforms** and compare **Solid default**, **Material only**, **Transform only**, and **Material + transform** with either Standard or Floating tray geometry. Working calls: `samples/sample/src/main/java/dev/better/sheet/sample/MaterialExamples.kt`.

```sh
adb shell am start -n dev.better.sheet.sample/.MainActivity --es demo Materials
```

## Migration

Rename the previous `surfaceModifier` to `sheetTransform`. The draft PR's `sheetMaterial` composable has been replaced with `sheetSurface`, which returns a drawing `Modifier` instead of emitting UI (for example, `Modifier.background(brush, outline)` rather than a `Box`).

Named callers need to migrate the transform argument; public composable signature changes also require recompilation and release/version review.
