package com.example.sudokuocr

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewAnimationUtils
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import org.opencv.android.OpenCVLoader
import kotlin.math.hypot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val mainVm = ViewModelProvider(this)[MainViewModel::class.java]
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!OpenCVLoader.initLocal()) {
            Log.e("OpenCV", "Failed to initialize OpenCV")
        } else {
            Log.d("OpenCV", "OpenCV initialized successfully")
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        setContent { SudokuApp(mainVm) }

        // Hold the splash on-screen until MainViewModel signals ready.
        // If needsSplash, MainViewModel delays isReady by AVD_DURATION_MS so the
        // animated icon (including the text slide-in) finishes before we exit.
        splashScreen.setKeepOnScreenCondition { !mainVm.isReady.value }

        splashScreen.setOnExitAnimationListener { screen ->
            // Iris-close: circular reveal shrinking from full-screen to zero.
            // A concurrent alpha fade (starting at 80% through) ensures the view
            // is already transparent when doOnEnd fires — no visible flash.
            val splashView = screen.view
            val cx = splashView.width  / 2
            val cy = splashView.height / 2
            val startRadius = hypot(cx.toDouble(), cy.toDouble()).toFloat()

            val iris = ViewAnimationUtils.createCircularReveal(splashView, cx, cy, startRadius, 0f)
                .apply {
                    duration     = 650
                    interpolator = DecelerateInterpolator()
                }

            val fade = android.animation.ObjectAnimator
                .ofFloat(splashView, View.ALPHA, 1f, 0f)
                .apply {
                    duration   = 130                // short fade at the very end
                    startDelay = 325                // starts at 80% of iris duration
                }

            android.animation.AnimatorSet().apply {
                playTogether(iris, fade)
                doOnEnd {
                    splashView.visibility = View.INVISIBLE
                    screen.remove()
                }
                start()
            }
        }
    }
}