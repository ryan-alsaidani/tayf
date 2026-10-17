package com.tayf.app.ui.nav

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tayf.app.AppContainer
import com.tayf.app.core.ScanMode
import com.tayf.app.ui.annotate.AnnotateScreen
import com.tayf.app.ui.camera.CameraScreen
import com.tayf.app.ui.components.findActivity
import com.tayf.app.ui.crop.CropScreen
import com.tayf.app.ui.document.DocumentScreen
import com.tayf.app.ui.editor.PageEditorScreen
import com.tayf.app.ui.library.ExpiringScreen
import com.tayf.app.ui.library.LibraryScreen
import com.tayf.app.ui.library.SearchScreen
import com.tayf.app.ui.privacy.LockScreen
import com.tayf.app.ui.privacy.ProtectedCopyScreen
import com.tayf.app.ui.privacy.VaultScreen
import com.tayf.app.ui.settings.SettingsScreen
import com.tayf.app.ui.translate.LanguagePacksScreen
import com.tayf.app.ui.translate.TranslateScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private fun NavBackStackEntry.longArg(name: String): Long = arguments?.getLong(name) ?: 0L

/** App navigation + PIN lock handling (the lock screen is shown again when the app comes back after the timeout). */
@Composable
fun TayfNavGraph(container: AppContainer, navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val start = remember { if (container.pinManager.isLockedNow()) Routes.LOCK else Routes.LIBRARY }

    DisposableEffect(context) {
        val owner = context.findActivity() as? LifecycleOwner
        var stoppedAt = 0L
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    stoppedAt = SystemClock.elapsedRealtime()
                    container.vault.lock()
                    scope.launch {
                        if (container.settings.lockTimeoutSeconds.first() == 0) container.pinManager.lock()
                    }
                }
                Lifecycle.Event.ON_START -> if (stoppedAt != 0L) {
                    scope.launch {
                        val away = (SystemClock.elapsedRealtime() - stoppedAt) / 1000
                        if (away >= container.settings.lockTimeoutSeconds.first()) container.pinManager.lock()
                        if (container.pinManager.isLockedNow() && navController.currentDestination?.route != Routes.LOCK) {
                            navController.navigate(Routes.LOCK) { launchSingleTop = true }
                        }
                    }
                }
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.LIBRARY) { LibraryScreen(container, navController) }
        composable(
            Routes.CAMERA,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("docId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { entry ->
            val mode = runCatching { ScanMode.valueOf(entry.arguments?.getString("mode").orEmpty()) }.getOrDefault(ScanMode.SINGLE)
            val docId = entry.longArg("docId")
            CameraScreen(container, navController, mode, if (docId > 0) docId else null)
        }
        composable(Routes.CROP, arguments = listOf(navArgument("pageId") { type = NavType.LongType })) { entry ->
            CropScreen(container, navController, entry.longArg("pageId"))
        }
        composable(Routes.EDITOR, arguments = listOf(navArgument("pageId") { type = NavType.LongType })) { entry ->
            PageEditorScreen(container, navController, entry.longArg("pageId"))
        }
        composable(Routes.DOCUMENT, arguments = listOf(navArgument("docId") { type = NavType.LongType })) { entry ->
            DocumentScreen(container, navController, entry.longArg("docId"))
        }
        composable(Routes.SEARCH) { SearchScreen(container, navController) }
        composable(Routes.EXPIRING) { ExpiringScreen(container, navController) }
        composable(Routes.ANNOTATE, arguments = listOf(navArgument("pageId") { type = NavType.LongType })) { entry ->
            AnnotateScreen(container, navController, entry.longArg("pageId"))
        }
        composable(Routes.PROTECTED_COPY, arguments = listOf(navArgument("pageId") { type = NavType.LongType })) { entry ->
            ProtectedCopyScreen(container, navController, entry.longArg("pageId"))
        }
        composable(Routes.VAULT) { VaultScreen(container, navController) }
        composable(Routes.LOCK) { LockScreen(container, navController) }
        composable(Routes.SETTINGS) { SettingsScreen(container, navController) }
        composable(Routes.TRANSLATE, arguments = listOf(navArgument("docId") { type = NavType.LongType })) { entry ->
            TranslateScreen(container, navController, entry.longArg("docId"))
        }
        composable(Routes.LANGUAGE_PACKS) { LanguagePacksScreen(container, navController) }
    }
}
