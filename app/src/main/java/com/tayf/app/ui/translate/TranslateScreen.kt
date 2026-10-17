package com.tayf.app.ui.translate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tayf.app.AppContainer
import com.tayf.app.R
import com.tayf.app.core.ExportFormat
import com.tayf.app.ui.components.EmptyState
import com.tayf.app.ui.components.LoadingBox
import com.tayf.app.ui.components.TayfButton
import com.tayf.app.ui.components.TayfChip
import com.tayf.app.ui.components.TayfTopBar
import com.tayf.app.ui.nav.Routes
import com.tayf.app.ui.theme.TayfBrown
import com.tayf.app.ui.theme.TayfDanger
import com.tayf.app.ui.theme.TayfGold
import com.tayf.app.ui.theme.TayfIcons
import com.tayf.app.ui.theme.TayfSurface
import com.tayf.app.ui.theme.TayfText
import com.tayf.app.ui.theme.TayfTextDim

@Composable
fun TranslateScreen(container: AppContainer, navController: NavHostController, docId: Long) {
    val context = LocalContext.current
    val vm: TranslateViewModel = viewModel(key = "translate-$docId", factory = TranslateViewModel.factory(container, docId))
    val s by vm.state.collectAsState()
    var fromMenu by remember { mutableStateOf(false) }
    var toMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(TayfBrown)) {
        TayfTopBar(stringResource(R.string.translate_title), onBack = { navController.popBackStack() })
        when {
            s.loading -> LoadingBox()
            s.pages.isEmpty() -> EmptyState(stringResource(R.string.document_empty))
            else -> Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState())) {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.pages.indices.toList()) { i -> TayfChip(stringResource(R.string.translate_page_n, i + 1), s.pageIndex == i, { vm.selectPage(i) }) }
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { fromMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.translate_from) + ": " + (vm.languages.firstOrNull { it.code == s.source }?.displayName ?: stringResource(R.string.translate_auto)), maxLines = 1)
                        }
                        DropdownMenu(expanded = fromMenu, onDismissRequest = { fromMenu = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.translate_auto)) }, onClick = { vm.setSource(null); fromMenu = false })
                            for (l in vm.languages) DropdownMenuItem(text = { Text(l.displayName) }, onClick = { vm.setSource(l.code); fromMenu = false })
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { toMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.translate_to) + ": " + (vm.languages.firstOrNull { it.code == s.target }?.displayName ?: s.target), maxLines = 1)
                        }
                        DropdownMenu(expanded = toMenu, onDismissRequest = { toMenu = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                            for (l in vm.languages) DropdownMenuItem(text = { Text(l.displayName) }, onClick = { vm.setTarget(l.code); toMenu = false })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TayfButton(stringResource(R.string.translate_page), vm::translateCurrent, Modifier.weight(1f), enabled = !s.working && !s.downloading)
                    TayfButton(stringResource(R.string.translate_all), vm::translateAll, Modifier.weight(1f), enabled = !s.working && !s.downloading, outlined = true)
                }
                if (s.working || s.downloading) LoadingBox(Modifier.heightIn(max = 64.dp))
                s.noTextRes?.let { Text(stringResource(it), color = TayfDanger, modifier = Modifier.padding(16.dp)) }
                s.errorText?.let { Text(it, color = TayfDanger, modifier = Modifier.padding(16.dp)) }
                if (s.missingModels.isNotEmpty()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.translate_models_needed, s.missingModels.joinToString(", ")), color = TayfGold)
                        TayfButton(stringResource(R.string.translate_download_models), vm::downloadMissingAndRetry, Modifier.padding(top = 8.dp), enabled = !s.downloading)
                    }
                }
                TextButtonLink(stringResource(R.string.settings_language_packs)) { navController.navigate(Routes.LANGUAGE_PACKS) }
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextPanel(stringResource(R.string.translate_original), s.pages.getOrNull(s.pageIndex)?.ocrText.orEmpty(), Modifier.weight(1f))
                    TextPanel(stringResource(R.string.translate_result), s.translations[s.pageIndex].orEmpty(), Modifier.weight(1f))
                }
                if (s.translations.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TayfButton("TXT", { vm.exportTranslation(context, ExportFormat.TXT) }, Modifier.weight(1f), icon = TayfIcons.Export, outlined = true)
                        TayfButton("DOCX", { vm.exportTranslation(context, ExportFormat.DOCX) }, Modifier.weight(1f), icon = TayfIcons.Export, outlined = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextButtonLink(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = 8.dp)) { Text(label, color = TayfGold) }
}

@Composable
private fun TextPanel(title: String, body: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(TayfSurface).padding(10.dp)) {
        Text(title, color = TayfGold, style = MaterialTheme.typography.labelLarge)
        Text(
            body.ifBlank { "—" }, color = if (body.isBlank()) TayfTextDim else TayfText,
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
            modifier = Modifier.padding(top = 6.dp).heightIn(min = 120.dp)
        )
    }
}
