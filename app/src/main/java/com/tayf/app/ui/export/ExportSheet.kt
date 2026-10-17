package com.tayf.app.ui.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tayf.app.AppContainer
import com.tayf.app.R
import com.tayf.app.core.ExportFormat
import com.tayf.app.core.Formatters
import com.tayf.app.core.PageSize
import com.tayf.app.export.PrintHelper
import com.tayf.app.ui.components.SectionLabel
import com.tayf.app.ui.components.TayfButton
import com.tayf.app.ui.components.TayfChip
import com.tayf.app.ui.components.findActivity
import com.tayf.app.ui.components.toast
import com.tayf.app.ui.theme.TayfDanger
import com.tayf.app.ui.theme.TayfGold
import com.tayf.app.ui.theme.TayfIcons
import com.tayf.app.ui.theme.TayfText
import com.tayf.app.ui.theme.TayfTextDim

/** Bottom sheet: format + options -> build file(s) -> share / save / print. pageIds == null exports the whole document. */
@Composable
fun ExportSheet(container: AppContainer, documentId: Long, pageIds: List<Long>?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val vm: ExportViewModel = viewModel(
        key = "export-$documentId-${pageIds?.hashCode()}",
        factory = ExportViewModel.factory(container, documentId, pageIds)
    )
    val s by vm.state.collectAsState()

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(vm.mime(s.format))) { uri ->
        if (uri != null) vm.saveTo(context, uri)
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.saveToTree(context, uri)
    }
    LaunchedEffect(s.messageRes) {
        val m = s.messageRes ?: return@LaunchedEffect
        toast(context, m)
        vm.clearMessage()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.export_title), color = TayfGold, style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                stringResource(R.string.export_pages_count, s.pages.size), color = TayfTextDim,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
            SectionLabel(stringResource(R.string.export_format))
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ExportFormat.values().toList()) { f -> TayfChip(f.name, s.format == f, { vm.setFormat(f) }) }
            }

            val textFormat = s.format == ExportFormat.TXT || s.format == ExportFormat.DOCX || s.format == ExportFormat.XLSX
            if (textFormat && !s.hasText) {
                Text(stringResource(R.string.export_no_text), color = TayfDanger, modifier = Modifier.padding(16.dp))
            }

            if (s.format == ExportFormat.PDF) {
                SectionLabel(stringResource(R.string.export_page_size))
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PageSize.values().toList()) { p -> TayfChip(if (p == PageSize.AUTO) stringResource(R.string.export_size_auto) else p.name, s.pageSize == p, { vm.setPageSize(p) }) }
                }
                SwitchRow(stringResource(R.string.export_searchable), s.searchable) { vm.setSearchable(it) }
                OutlinedTextField(
                    value = s.password, onValueChange = vm::setPassword, singleLine = true,
                    label = { Text(stringResource(R.string.export_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            if (s.format == ExportFormat.PDF || s.format == ExportFormat.JPG) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.export_quality, s.quality), color = TayfText, modifier = Modifier.padding(end = 12.dp))
                    Slider(
                        value = s.quality.toFloat(), onValueChange = { vm.setQuality(it.toInt()) }, valueRange = 40f..100f,
                        modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = TayfGold, activeTrackColor = TayfGold)
                    )
                }
                SwitchRow(stringResource(R.string.export_compress), s.compress) { vm.setCompress(it) }
                if (s.compress) {
                    OutlinedTextField(
                        value = s.targetKb, onValueChange = vm::setTargetKb, singleLine = true,
                        label = { Text(stringResource(R.string.export_target_kb)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            if (s.errorText != null) {
                Text(s.errorText.orEmpty(), color = TayfDanger, modifier = Modifier.padding(16.dp))
            }

            if (s.working) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = TayfGold)
                }
            } else if (s.files.isEmpty()) {
                TayfButton(
                    stringResource(R.string.export_create), vm::export, Modifier.fillMaxWidth().padding(16.dp),
                    enabled = s.pages.isNotEmpty() && !(textFormat && !s.hasText)
                )
            } else {
                Text(
                    stringResource(R.string.export_ready, s.files.size, Formatters.formatBytes(s.files.sumOf { it.length() })),
                    color = TayfGold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TayfButton(stringResource(R.string.export_share), { vm.share(context) }, Modifier.weight(1f), icon = TayfIcons.Share)
                    TayfButton(
                        stringResource(R.string.export_save),
                        { if (s.files.size == 1) createDoc.launch(s.files[0].name) else pickTree.launch(null) },
                        Modifier.weight(1f), icon = TayfIcons.Export, outlined = true
                    )
                }
                if (s.format == ExportFormat.PDF || s.format == ExportFormat.JPG || s.format == ExportFormat.PNG) {
                    TayfButton(
                        stringResource(R.string.export_print),
                        {
                            val activity = context.findActivity()
                            if (activity != null) PrintHelper.printPages(activity, s.pages, s.doc?.name ?: "Tayf")
                        },
                        Modifier.fillMaxWidth().padding(16.dp), icon = TayfIcons.Print, outlined = true
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TayfText, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = TayfGold, checkedThumbColor = com.tayf.app.ui.theme.TayfBrown)
        )
    }
}
