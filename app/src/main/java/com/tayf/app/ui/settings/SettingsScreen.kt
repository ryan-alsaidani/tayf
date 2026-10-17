package com.tayf.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tayf.app.AppContainer
import com.tayf.app.R
import com.tayf.app.core.FilterType
import com.tayf.app.core.PageSize
import com.tayf.app.ui.components.InputDialog
import com.tayf.app.ui.components.LoadingBox
import com.tayf.app.ui.components.SectionLabel
import com.tayf.app.ui.components.TayfButton
import com.tayf.app.ui.components.TayfChip
import com.tayf.app.ui.components.TayfTopBar
import com.tayf.app.ui.components.toast
import com.tayf.app.ui.components.toastText
import com.tayf.app.ui.nav.Routes
import com.tayf.app.ui.theme.TayfBrown
import com.tayf.app.ui.theme.TayfGold
import com.tayf.app.ui.theme.TayfText
import com.tayf.app.ui.theme.TayfTextDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SettingsDialog { SET_PIN, CLEAR_PIN, BACKUP_PASSWORD, RESTORE_PASSWORD }

@Composable
fun SettingsScreen(container: AppContainer, navController: NavHostController) {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val hijri by vm.hijri.collectAsState()
    val pageSize by vm.pageSize.collectAsState()
    val filter by vm.filter.collectAsState()
    val autoCapture by vm.autoCapture.collectAsState()
    val guard by vm.qualityGuard.collectAsState()
    val pinEnabled by vm.pinEnabled.collectAsState()
    val biometric by vm.biometric.collectAsState()
    val timeout by vm.lockTimeout.collectAsState()
    val quality by vm.jpegQuality.collectAsState()
    val ui by vm.ui.collectAsState()

    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var pendingBackupUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { pendingBackupUri = uri; dialog = SettingsDialog.BACKUP_PASSWORD }
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingRestoreUri = uri; dialog = SettingsDialog.RESTORE_PASSWORD }
    }

    LaunchedEffect(ui.messageRes, ui.errorText) {
        ui.messageRes?.let { toast(context, it) }
        ui.errorText?.let { toastText(context, it) }
        if (ui.messageRes != null || ui.errorText != null) vm.consumeMessage()
    }

    Column(Modifier.fillMaxSize().background(TayfBrown)) {
        TayfTopBar(stringResource(R.string.settings_title), onBack = { navController.popBackStack() })
        if (ui.busy) LoadingBox() else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            SectionLabel(stringResource(R.string.settings_general))
            SwitchRow(stringResource(R.string.settings_hijri), hijri) { vm.setHijri(it) }
            Label(stringResource(R.string.settings_page_size))
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PageSize.values().toList()) { p -> TayfChip(if (p == PageSize.AUTO) stringResource(R.string.export_size_auto) else p.name, pageSize == p, { vm.setPageSize(p) }) }
            }
            Label(stringResource(R.string.settings_default_filter))
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FilterType.values().toList()) { f ->
                    TayfChip(
                        stringResource(when (f) {
                            FilterType.ORIGINAL -> R.string.filter_original
                            FilterType.BLACK_WHITE -> R.string.filter_bw
                            FilterType.GRAY -> R.string.filter_gray
                            FilterType.ENHANCED_COLOR -> R.string.filter_enhanced
                        }), filter == f, { vm.setFilter(f) }
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_jpeg_quality, quality), color = TayfText, modifier = Modifier.padding(end = 12.dp))
                Slider(
                    value = quality.toFloat(), onValueChange = { vm.setJpegQuality(it.toInt()) }, valueRange = 50f..100f,
                    modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = TayfGold, activeTrackColor = TayfGold)
                )
            }

            SectionLabel(stringResource(R.string.settings_camera))
            SwitchRow(stringResource(R.string.settings_auto_capture), autoCapture) { vm.setAutoCapture(it) }
            SwitchRow(stringResource(R.string.settings_quality_guard), guard) { vm.setQualityGuard(it) }

            SectionLabel(stringResource(R.string.settings_security))
            SwitchRow(stringResource(R.string.settings_pin), pinEnabled) { on -> dialog = if (on) SettingsDialog.SET_PIN else SettingsDialog.CLEAR_PIN }
            if (pinEnabled) {
                SwitchRow(stringResource(R.string.settings_biometric), biometric) { vm.setBiometric(it) }
                Label(stringResource(R.string.settings_lock_timeout))
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(0, 30, 60, 300)) { t ->
                        TayfChip(if (t == 0) stringResource(R.string.settings_lock_immediately) else stringResource(R.string.settings_lock_seconds, t), timeout == t, { vm.setLockTimeout(t) })
                    }
                }
            }

            SectionLabel(stringResource(R.string.settings_language_packs))
            TayfButton(stringResource(R.string.settings_language_packs), { navController.navigate(Routes.LANGUAGE_PACKS) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), outlined = true)

            SectionLabel(stringResource(R.string.settings_backup))
            Text(stringResource(R.string.settings_backup_hint), color = TayfTextDim, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TayfButton(stringResource(R.string.settings_backup_create), {
                    backupPicker.launch("tayf-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".tayfbak")
                }, Modifier.weight(1f))
                TayfButton(stringResource(R.string.settings_backup_restore), { restorePicker.launch(arrayOf("*/*")) }, Modifier.weight(1f), outlined = true)
            }

            SectionLabel(stringResource(R.string.settings_about))
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
            }
            Text(stringResource(R.string.settings_version, version), color = TayfTextDim, modifier = Modifier.padding(horizontal = 16.dp))
            Text(stringResource(R.string.settings_offline_note), color = TayfTextDim, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }

    when (dialog) {
        null -> Unit
        SettingsDialog.SET_PIN -> InputDialog(
            stringResource(R.string.settings_pin_set_title), stringResource(R.string.settings_pin_hint), stringResource(R.string.common_save),
            onConfirm = { if (it.length in 4..8 && it.all { c -> c.isDigit() }) { vm.enablePin(it); dialog = null } },
            onDismiss = { dialog = null }, password = true, numeric = true
        )
        SettingsDialog.CLEAR_PIN -> InputDialog(
            stringResource(R.string.settings_pin_clear_title), stringResource(R.string.settings_pin_current), stringResource(R.string.common_ok),
            onConfirm = { vm.disablePin(it); dialog = null }, onDismiss = { dialog = null }, password = true, numeric = true
        )
        SettingsDialog.BACKUP_PASSWORD -> OptionalPasswordDialog(
            stringResource(R.string.settings_backup_create),
            onConfirm = { pw -> pendingBackupUri?.let { vm.backup(it, pw) }; dialog = null },
            onDismiss = { dialog = null }
        )
        SettingsDialog.RESTORE_PASSWORD -> OptionalPasswordDialog(
            stringResource(R.string.settings_backup_restore),
            onConfirm = { pw -> pendingRestoreUri?.let { vm.restore(it, pw) }; dialog = null },
            onDismiss = { dialog = null }
        )
    }

    if (ui.restartNeeded) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.settings_restore_done_title)) },
            text = { Text(stringResource(R.string.settings_restore_done)) },
            confirmButton = {
                TextButton(onClick = { android.os.Process.killProcess(android.os.Process.myPid()) }) {
                    Text(stringResource(R.string.common_ok), color = TayfGold)
                }
            }
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = TayfTextDim, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TayfText, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = TayfGold, checkedThumbColor = TayfBrown))
    }
}

@Composable
private fun OptionalPasswordDialog(title: String, onConfirm: (String?) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(stringResource(R.string.settings_backup_password_hint), color = TayfTextDim)
                OutlinedTextField(
                    value = value, onValueChange = { value = it }, singleLine = true,
                    label = { Text(stringResource(R.string.export_password)) },
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.ifBlank { null }) }) { Text(stringResource(R.string.common_ok), color = TayfGold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}
