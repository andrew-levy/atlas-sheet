package dev.atlas.sheet.sample

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import dev.atlas.sheet.*

class FragmentDemoActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val root = FrameLayout(this)
        val background = ComposeView(this).apply {
            id = R.id.fragment_demo_content
            setContent {
                MaterialTheme {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
                        Text("Fragment root sheet", style = MaterialTheme.typography.headlineMedium)
                        Text("Call dismiss() directly; the adapter waits for the sheet exit.")
                        Button(onClick = {
                            if (supportFragmentManager.findFragmentByTag("sheet") == null) {
                                RootSheetFragment().show(supportFragmentManager, "sheet")
                            }
                        }, modifier = Modifier.testTag("open-fragment")) { Text("Open fragment sheet") }
                    }
                }
            }
        }
        root.addView(background, FrameLayout.LayoutParams(-1, -1))
        root.addView(FrameLayout(this).apply { id = R.id.fragment_sheet_container }, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }
}

class RootSheetFragment : AtlasSheetDialogFragment() {
    override val initialDetent = "medium"
    internal var observedState: SheetState? = null

    @Composable
    override fun SheetContent(state: SheetState) {
        SideEffect { observedState = state }
        MaterialTheme {
            var text by remember { mutableStateOf("") }
            AtlasSheet(
                state = state,
                detents = listOf(SheetDetent.fraction("medium", .6f), SheetDetent.expanded()),
                title = "Fragment sheet",
                header = { Text("Fragment sheet", Modifier.padding(24.dp).testTag("fragment-header")) },
                footer = {
                    Button(onClick = { dismiss() },
                        modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("close-fragment")) {
                        Text("Close fragment")
                    }
                },
            ) {
                OutlinedTextField(text, { text = it }, label = { Text("Keyboard-aware input") }, modifier = Modifier.padding(24.dp))
                repeat(20) { Text("Fragment item $it", Modifier.padding(24.dp)) }
            }
        }
    }
}
