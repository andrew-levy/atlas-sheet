# Custom sheet surfaces

Better Sheet owns geometry, detents, drag/scroll handoff, dismissal, keyboard policy and sticky content slots. Apps provide their UI and can optionally replace the solid material through `surface` on `BetterSheet`, `BetterSheetHost` or `BetterModalSheet`. No renderer dependency is imposed by the core library.

```kotlin
val material = remember {
    SheetSurface {
        val paint = when (region) {
            SheetSurfaceRegion.Body -> Brush.verticalGradient(listOf(Color(0xFFF4F1E9), Color(0xFF91C6B8)))
            // Continue the gradient's terminal color in the separate bottom cap.
            SheetSurfaceRegion.NavigationBar -> Brush.linearGradient(listOf(Color(0xFF91C6B8), Color(0xFF91C6B8)))
        }
        Modifier.background(paint)
    }
}
BetterSheetHost(
    state = state,
    snapPoints = listOf(SheetSnapPoint.fraction("half", .5f), SheetSnapPoint.expanded()),
    surface = material,
    contentColor = Color(0xFF183C3A),
    header = { MyHeader() },
    footer = { MyFooter() },
) { MyBody() }
```

The callback receives the resolved `shape`, opening `edge`, `presentation`, and `region`. Return a modifier that draws behind/around its content. It must preserve `drawContent()` and must not change sizing, padding, placement, touch handling or semantics. The host still clips content to its shape. For glass, use the supplied shape in the renderer's own lens/highlight calculation; the app must arrange a valid backdrop source.

| Host configuration | Material behavior |
|---|---|
| Standard bottom | Body uses the supplied sheet shape. A separate rectangular NavigationBar region continues paint behind the navigation area when the IME does not cover it. |
| Floating/expanded tray | One Body region follows the continuously morphing outline and includes the navigation extension at expansion. Child pixels are clipped above the reserved navigation space, preventing footer shadows from leaking into it. |
| Top | Body receives the existing mirrored default shape and includes status-bar padding. No separate bottom navigation region is painted. |
| Modal | Same surface API in the Dialog's Compose root. Window boundaries remain; the API does not capture an Activity behind a Dialog. |

Null retains the original solid background, shadow and navigation cover. A custom surface preserves the default shadow, unless it chooses `SheetSurface(shadow = SheetSurfaceShadow.None) { ... }`; use None when supplying your own shadow through the outer `surfaceModifier` (surface paint is clipped by the host). Top sheets retain their existing zero elevation. `surfaceModifier` continues to decorate the outer surface and can still observe bounds or apply stack effects. Content colors, scrim, system-bar icon appearance and input policies remain separate: set `containerColor` appropriately for modal system-bar contrast decisions even when custom paint replaces that fill.

Each region has its own local drawing coordinates. Gradients should explicitly continue their final color into NavigationBar, as above; backdrop renderers can sample absolute coordinates. The callback can run more than once; keep it free of lifecycle side effects.

This is an additive Kotlin source API for named arguments and trailing content lambdas. Existing compiled clients must be rebuilt because the public composable signatures change; release/version compatibility policy should be decided before publishing. Positional calls that explicitly pass the final content argument must use a named `content` argument or account for the new parameter.
