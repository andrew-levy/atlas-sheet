package dev.atlas.sheet

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.fragment.app.DialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * DialogFragment adapter whose dismiss()/dismissAllowingStateLoss() wait for the sheet exit.
 * Render AtlasSheet with the supplied state in SheetContent. Do not wrap it in AtlasModalSheet.
 * dismissNow() retains DialogFragment's synchronous, unanimated semantics.
 */
public abstract class AtlasSheetDialogFragment : DialogFragment() {
    protected open val initialDetent: String = "expanded"
    private var sheetState: SheetState? = null
    private var sheetScope: CoroutineScope? = null
    private var closing = false
    private var allowingStateLoss = false
    private var removed = false

    @Composable
    protected abstract fun SheetContent(state: SheetState)

    final override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        id = dev.atlas.sheet.R.id.atlas_fragment_sheet
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val state = rememberSheetState(initialDetent)
            val scope = rememberCoroutineScope()
            DisposableEffect(state, scope) {
                sheetState = state
                sheetScope = scope
                onDispose { sheetState = null; sheetScope = null }
            }
            LaunchedEffect(state) {
                snapshotFlow {
                    state.hasPresented && state.currentDetent == null &&
                        !state.isVisible && !state.isAnimationRunning
                }.collect { hidden -> if (hidden) finishDismissal() }
            }
            SheetContent(state)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setWindowAnimations(0)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            val host = WindowCompat.getInsetsController(requireActivity().window, requireActivity().window.decorView)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = host.isAppearanceLightStatusBars
                isAppearanceLightNavigationBars = host.isAppearanceLightNavigationBars
            }
        }
        // AtlasSheet owns Back and outside-touch dismissal, including the exit animation.
        dialog?.setCanceledOnTouchOutside(false)
    }

    override fun dismiss() { requestDismissal(false) }

    override fun dismissAllowingStateLoss() { requestDismissal(true) }

    private fun requestDismissal(allowStateLoss: Boolean) {
        allowingStateLoss = allowingStateLoss || allowStateLoss
        if (closing || removed) return
        closing = true
        val state = sheetState
        val scope = sheetScope
        if (state == null || scope == null || state.geometry == null) {
            finishDismissal()
        } else {
            scope.launch {
                try { state.hide(); finishDismissal() }
                finally { if (!removed) closing = false }
            }
        }
    }

    private fun finishDismissal() {
        if (removed) return
        removed = true
        if (allowingStateLoss) super.dismissAllowingStateLoss() else super.dismiss()
    }

    override fun onDestroyView() {
        sheetState = null
        sheetScope = null
        closing = false
        allowingStateLoss = false
        removed = false
        super.onDestroyView()
    }
}
