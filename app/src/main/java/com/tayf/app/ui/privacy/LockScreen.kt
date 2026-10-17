package com.tayf.app.ui.privacy

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import com.tayf.app.AppContainer
import com.tayf.app.R
import com.tayf.app.ui.components.findActivity
import com.tayf.app.ui.nav.Routes
import com.tayf.app.ui.theme.TayfBrown
import com.tayf.app.ui.theme.TayfDanger
import com.tayf.app.ui.theme.TayfGold
import com.tayf.app.ui.theme.TayfIcons
import com.tayf.app.ui.theme.TayfSurface
import com.tayf.app.ui.theme.TayfText
import kotlinx.coroutines.launch

@Composable
fun LockScreen(container: AppContainer, navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val biometricEnabled by container.settings.biometricEnabled.collectAsState(initial = false)

    BackHandler { context.findActivity()?.finish() }

    fun unlock() {
        container.pinManager.markUnlocked()
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else {
            navController.navigate(Routes.LIBRARY) { popUpTo(Routes.LOCK) { inclusive = true } }
        }
    }

    fun submit() {
        if (pin.length < 4) return
        scope.launch {
            if (container.pinManager.verify(pin)) unlock() else { error = true; pin = "" }
        }
    }

    val title = stringResource(R.string.lock_biometric_title)
    val cancel = stringResource(R.string.common_cancel)
    fun showBiometric() {
        val activity = context.findActivity() as? FragmentActivity ?: return
        val can = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) return
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlock() }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(cancel)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info)
    }
    LaunchedEffect(biometricEnabled) { if (biometricEnabled) showBiometric() }

    Column(
        Modifier.fillMaxSize().background(TayfBrown).navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(TayfIcons.Lock, null, tint = TayfGold, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.lock_title), color = TayfText, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.height(24.dp)) {
            for (i in 0 until maxOf(4, pin.length)) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(if (i < pin.length) TayfGold else TayfSurface))
            }
        }
        Text(
            if (error) stringResource(R.string.lock_wrong) else "", color = TayfDanger,
            modifier = Modifier.height(32.dp).padding(top = 8.dp)
        )
        for (row in listOf("123", "456", "789")) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                for (c in row) Key(c.toString()) { if (pin.length < 8) { pin += c; error = false } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            Key("⌫") { pin = pin.dropLast(1) }
            Key("0") { if (pin.length < 8) { pin += "0"; error = false } }
            Key("✓", highlight = true) { submit() }
        }
        if (biometricEnabled) {
            TextButton(onClick = { showBiometric() }) { Text(stringResource(R.string.lock_use_biometric), color = TayfGold) }
        }
    }
}

@Composable
private fun Key(label: String, highlight: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(if (highlight) TayfGold else TayfSurface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (highlight) TayfBrown else TayfText, style = MaterialTheme.typography.titleLarge)
    }
}
