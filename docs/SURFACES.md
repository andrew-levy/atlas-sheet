# Custom sheet surfaces

`surfaceModifier` decorates the outer sheet: stack transforms, bounds observation and similar effects belong there. It cannot replace the inner solid fill or see the resolved tray shape. The optional `surface` callback fills that gap without adding a material class or renderer dependency. It is available on `BetterSheet`, `BetterSheetHost` and `BetterModalSheet`.

```kotlin
BetterSheetHost(
    state = state,
    snapPoints = listOf(SheetSnapPoint.fraction("half", .5f), SheetSnapPoint.expanded()),
    surfaceModifier = Modifier.sheetStackEffect { entry ->
        scaleX = 1f - .04f * entry.animatedDepth
    },
    surface = { shape, isNavigationBar ->
        if (isNavigationBar) {
            // Continue the body's gradient endpoint in the separate bottom cap.
            Modifier.background(Color(0xFF91C6B8))
        } else {
            Modifier.background(
                Brush.verticalGradient(listOf(Color(0xFFF4F1E9), Color(0xFF91C6B8))),
                shape,
            )
        }
    },
    contentColor = Color(0xFF183C3A),
    header = { MyHeader() },
    footer = { MyFooter() },
) { MyBody() }
```

The callback type is `(@Composable (shape: Shape, isNavigationBar: Boolean) -> Modifier)?`. The host supplies its resolved shape, including continuous tray morphs or mirrored top-sheet corners. The flag distinguishes the separate rectangular navigation cap used by standard bottom sheets. An app already knows its chosen edge and presentation; those need no extra callback scope.

Return drawing modifiers that preserve `drawContent()` and do not change sizing, padding, placement, touch handling or semantics. Better Sheet still owns those, plus shape clipping, detents, dismissal, scrolling, keyboard policy and sticky content slots. For glass, use the supplied shape for the renderer's lens/highlights and arrange a valid backdrop source in the app.

| Host configuration | Material behavior |
|---|---|
| Standard bottom | Body receives its sheet shape and `false`. A separate rectangular cap receives `RectangleShape` and `true` when the IME does not cover navigation. |
| Floating/expanded tray | One body receives the continuously morphing outline and `false`, including its navigation extension at expansion. Child pixels are clipped above the reserved navigation space to prevent footer effects leaking into it. |
| Top | Body receives the existing mirrored default shape and `false`, including status-bar padding. No bottom navigation cap. |
| Modal | Same callback in the Dialog's Compose root. It does not capture an Activity behind that Dialog. |

Null retains the original solid fill and navigation cover. The existing host shadow is always retained, including for custom paint; top sheets keep zero elevation. Configure a material renderer to omit its own shadow to avoid doubling it. There is no separate shadow-policy API in this change. `surfaceModifier` continues to handle outer decoration and stack effects, independently of paint.

Each invocation has local drawing coordinates. A gradient should explicitly continue its final color into the navigation cap, as above; an absolute-coordinate backdrop can sample the corresponding position. The callback may run more than once and should have no lifecycle side effects. Paint itself is clipped by the host.

Content colors, scrim, system-bar icons and input policies remain separate. Set `containerColor` appropriately for modal system-bar contrast decisions even when custom paint replaces its fill.

Named arguments and trailing content lambdas remain source compatible. Existing compiled clients must be rebuilt because public composable signatures change; release/version policy needs review before publication. Explicit positional final-content arguments need naming or adjustment.
