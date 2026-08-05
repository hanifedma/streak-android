package com.hanifedma.streak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.hanifedma.streak.ui.StreakApp
import com.hanifedma.streak.ui.StreakViewModel

class MainActivity : ComponentActivity() {

    private val vm: StreakViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Holds the system splash until the first frame, so the app never
        // flashes an empty window while Firebase auth resolves from its cache.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // The *window's* width, not the device's — the right input for
            // landscape phones, foldables and split-screen, where "is this a
            // tablet" is the wrong question. containerSize (rather than
            // Configuration.screenWidthDp) is measured from the actual window,
            // so a freeform window adapts as it is dragged.
            val windowInfo = LocalWindowInfo.current
            val density = LocalDensity.current
            val widthDp = with(density) { windowInfo.containerSize.width.toDp().value.toInt() }
            StreakApp(vm = vm, widthDp = widthDp)
        }
    }
}
