package com.tayf.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.tayf.app.core.AppDispatchers
import com.tayf.app.ui.nav.TayfNavGraph
import com.tayf.app.ui.theme.TayfTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Single activity (FragmentActivity so BiometricPrompt works). Lock handling lives in the nav graph. */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val container = (applicationContext as TayfApp).container
        lifecycleScope.launch { withContext(AppDispatchers.io) { container.fileStore.clearTemp() } }
        setContent {
            TayfTheme {
                val navController = rememberNavController()
                TayfNavGraph(container, navController)
            }
        }
    }
}
