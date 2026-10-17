package com.tayf.app.ui.translate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tayf.app.AppContainer
import com.tayf.app.R
import com.tayf.app.core.Outcome
import com.tayf.app.ocr.LangInfo
import com.tayf.app.ocr.PackState
import com.tayf.app.ui.components.SectionLabel
import com.tayf.app.ui.components.TayfTopBar
import com.tayf.app.ui.components.toastText
import com.tayf.app.ui.theme.TayfBrown
import com.tayf.app.ui.theme.TayfDanger
import com.tayf.app.ui.theme.TayfGold
import com.tayf.app.ui.theme.TayfOk
import com.tayf.app.ui.theme.TayfSurface
import com.tayf.app.ui.theme.TayfText
import com.tayf.app.ui.theme.TayfTextDim
import kotlinx.coroutines.launch

@Composable
fun LanguagePacksScreen(container: AppContainer, navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val langs = container.languagePacks.supportedLanguages()
    val active by container.languagePacks.activeOcrLanguages.collectAsState(initial = setOf("ar", "en"))

    Column(Modifier.fillMaxSize().background(TayfBrown)) {
        TayfTopBar(stringResource(R.string.settings_language_packs), onBack = { navController.popBackStack() })
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionLabel(stringResource(R.string.packs_ocr)) }
            item { Text(stringResource(R.string.packs_ocr_hint), color = TayfTextDim, modifier = Modifier.padding(horizontal = 16.dp)) }
            items(langs, key = { "ocr-" + it.code }) { l ->
                val state by container.languagePacks.ocrState(l.code).collectAsState(initial = PackState.NotInstalled)
                PackRow(
                    lang = l, state = state, checked = l.code in active,
                    onChecked = { on -> scope.launch { container.languagePacks.setActiveOcrLanguages(if (on) active + l.code else active - l.code) } },
                    onDownload = {
                        scope.launch {
                            val r = container.languagePacks.downloadOcrPack(l.code)
                            if (r is Outcome.Fail) toastText(context, r.message)
                        }
                    },
                    onDelete = { scope.launch { container.languagePacks.deleteOcrPack(l.code) } },
                    canDelete = l.ocrEngine == "tesseract" && l.tessCode != "ara"
                )
            }
            item { SectionLabel(stringResource(R.string.packs_translation)) }
            item { Text(stringResource(R.string.packs_translation_hint), color = TayfTextDim, modifier = Modifier.padding(horizontal = 16.dp)) }
            items(langs.filter { it.canTranslate }, key = { "tr-" + it.code }) { l ->
                val state by container.translator.modelState(l.code).collectAsState(initial = PackState.NotInstalled)
                PackRow(
                    lang = l, state = state, checked = null, onChecked = {},
                    onDownload = {
                        scope.launch {
                            val r = container.translator.downloadModel(l.code)
                            if (r is Outcome.Fail) toastText(context, r.message)
                        }
                    },
                    onDelete = { scope.launch { container.translator.deleteModel(l.code) } },
                    canDelete = l.code != "en"
                )
            }
        }
    }
}

@Composable
private fun PackRow(
    lang: LangInfo, state: PackState, checked: Boolean?, onChecked: (Boolean) -> Unit,
    onDownload: () -> Unit, onDelete: () -> Unit, canDelete: Boolean
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(TayfSurface).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (checked != null) {
            Checkbox(
                checked = checked, onCheckedChange = onChecked, enabled = state == PackState.Installed,
                colors = CheckboxDefaults.colors(checkedColor = TayfGold, checkmarkColor = TayfBrown)
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(lang.displayName, color = TayfText)
            when (state) {
                is PackState.Downloading -> {
                    if (state.progress > 0f) LinearProgressIndicator(progress = { state.progress }, color = TayfGold, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    else LinearProgressIndicator(color = TayfGold, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
                is PackState.Failed -> Text(state.message, color = TayfDanger, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                PackState.Installed -> Text(stringResource(R.string.packs_installed), color = TayfOk, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                PackState.NotInstalled -> Text(stringResource(R.string.packs_not_installed), color = TayfTextDim, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
        when (state) {
            PackState.Installed -> if (canDelete) TextButton(onClick = onDelete) { Text(stringResource(R.string.common_delete), color = TayfDanger) }
            is PackState.Downloading -> Unit
            else -> TextButton(onClick = onDownload) { Text(stringResource(R.string.packs_download), color = TayfGold) }
        }
    }
}
