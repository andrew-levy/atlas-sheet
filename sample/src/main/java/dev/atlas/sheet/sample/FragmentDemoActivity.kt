package dev.atlas.sheet.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import dev.atlas.sheet.*
import kotlinx.coroutines.launch

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
                        Text("The ComposeView is the fragment view. No Dialog and no external visibility if.")
                        Button(onClick = {
                            if (supportFragmentManager.findFragmentByTag("sheet") == null) {
                                supportFragmentManager.beginTransaction()
                                    .add(R.id.fragment_sheet_container, RootSheetFragment(), "sheet").commit()
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

class RootSheetFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            id = R.id.fragment_sheet_content
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    val state = rememberSheetState(initialDetent = "medium")
                    val scope = rememberCoroutineScope()
                    var text by remember { mutableStateOf("") }
                    AtlasSheet(
                        state = state,
                        detents = listOf(SheetDetent.fraction("medium", .6f), SheetDetent.expanded()),
                        title = "Fragment sheet",
                        onDismissed = {
                            parentFragmentManager.beginTransaction().remove(this@RootSheetFragment).commit()
                        },
                        header = { Text("Fragment sheet", Modifier.padding(24.dp).testTag("fragment-header")) },
                        footer = {
                            Button(onClick = { scope.launch { state.hide() } },
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
}
