package com.tayf.app.ui.document

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tayf.app.AppContainer
import com.tayf.app.R
import com.tayf.app.core.Formatters
import com.tayf.app.core.ScanMode
import com.tayf.app.ui.components.ConfirmDialog
import com.tayf.app.ui.components.EmptyState
import com.tayf.app.ui.components.InputDialog
import com.tayf.app.ui.components.LoadingBox
import com.tayf.app.ui.components.PageThumb
import com.tayf.app.ui.components.TayfButton
import com.tayf.app.ui.components.TayfTopBar
import com.tayf.app.ui.export.ExportSheet
import com.tayf.app.ui.nav.Routes
import com.tayf.app.ui.theme.TayfBrown
import com.tayf.app.ui.theme.TayfDanger
import com.tayf.app.ui.theme.TayfGold
import com.tayf.app.ui.theme.TayfIcons
import com.tayf.app.ui.theme.TayfSurface
import com.tayf.app.ui.theme.TayfText
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

private enum class DocDialog { RENAME, DELETE_DOC, DELETE_PAGES, SPLIT, MERGE, EXPIRY }

@Composable
fun DocumentScreen(container: AppContainer, navController: NavHostController, docId: Long) {
    val vm: DocumentViewModel = viewModel(key = "doc-$docId", factory = DocumentViewModel.factory(container, docId))
    val doc by vm.document.collectAsState()
    val pages by vm.pages.collectAsState()
    val selection by vm.selection.collectAsState()
    val candidates by vm.mergeCandidates.collectAsState()
    val hijri by container.settings.hijriDates.collectAsState(initial = false)

    var dialog by remember { mutableStateOf<DocDialog?>(null) }
    var menu by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }
    val selecting = selection.isNotEmpty()

    Box(Modifier.fillMaxSize().background(TayfBrown)) {
        Column(Modifier.fillMaxSize()) {
            if (selecting) {
                TayfTopBar(
                    title = stringResource(R.string.document_selected, selection.size),
                    onBack = { vm.clearSelection() },
                    actions = {
                        if (selection.size == 1) {
                            IconButton(onClick = { vm.moveSelected(-1) }) { Text("▲", color = TayfGold) }
                            IconButton(onClick = { vm.moveSelected(1) }) { Text("▼", color = TayfGold) }
                        }
                        IconButton(onClick = { dialog = DocDialog.SPLIT }) { Icon(TayfIcons.Folder, stringResource(R.string.document_split), tint = TayfGold) }
                        IconButton(onClick = { exportOpen = true }) { Icon(TayfIcons.Export, stringResource(R.string.export_title), tint = TayfGold) }
                        IconButton(onClick = { dialog = DocDialog.DELETE_PAGES }) { Icon(TayfIcons.Delete, stringResource(R.string.common_delete), tint = TayfDanger) }
                    }
                )
            } else {
                TayfTopBar(
                    title = doc?.name ?: "",
                    onBack = { navController.popBackStack() },
                    actions = {
                        IconButton(onClick = { exportOpen = true }) { Icon(TayfIcons.Export, stringResource(R.string.export_title), tint = TayfGold) }
                        IconButton(onClick = { navController.navigate(Routes.translate(docId)) }) { Icon(TayfIcons.Text, stringResource(R.string.translate_title), tint = TayfGold) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(TayfIcons.More, null, tint = TayfGold) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.common_rename)) }, onClick = { menu = false; dialog = DocDialog.RENAME })
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (doc?.isFavorite == true) R.string.library_unfavorite else R.string.library_favorite)) },
                                    onClick = { menu = false; vm.toggleFavorite() }
                                )
                                DropdownMenuItem(text = { Text(stringResource(R.string.document_set_expiry)) }, onClick = { menu = false; dialog = DocDialog.EXPIRY })
                                DropdownMenuItem(text = { Text(stringResource(R.string.document_merge)) }, onClick = { menu = false; dialog = DocDialog.MERGE })
                                DropdownMenuItem(text = { Text(stringResource(R.string.document_select_all)) }, onClick = { menu = false; vm.selectAll() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.common_delete), color = TayfDanger) }, onClick = { menu = false; dialog = DocDialog.DELETE_DOC })
                            }
                        }
                    }
                )
            }
            val expiry = doc?.expiryDate
            if (expiry != null && !selecting) {
                Text(
                    stringResource(R.string.document_expires_on, Formatters.formatDate(expiry, hijri)),
                    color = TayfGold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
            val list = pages
            when {
                list == null -> LoadingBox()
                list.isEmpty() -> EmptyState(stringResource(R.string.document_empty), TayfIcons.Scan)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(list, key = { it.id }) { page ->
                        PageThumb(
                            page = page, selected = page.id in selection,
                            onClick = { if (selecting) vm.toggleSelect(page.id) else navController.navigate(Routes.editor(page.id)) },
                            onLongClick = { vm.toggleSelect(page.id) }
                        )
                    }
                }
            }
        }
        if (!selecting) {
            TayfButton(
                stringResource(R.string.document_add_pages), { navController.navigate(Routes.camera(ScanMode.BATCH, docId)) },
                icon = TayfIcons.Add,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp)
            )
        }
    }

    if (exportOpen) {
        ExportSheet(container, docId, if (selecting) selection.toList() else null) { exportOpen = false }
    }

    when (dialog) {
        null -> Unit
        DocDialog.RENAME -> InputDialog(
            stringResource(R.string.common_rename), stringResource(R.string.document_name), stringResource(R.string.common_save),
            onConfirm = { vm.rename(it); dialog = null }, onDismiss = { dialog = null }, initial = doc?.name.orEmpty()
        )
        DocDialog.DELETE_DOC -> ConfirmDialog(
            stringResource(R.string.common_delete), stringResource(R.string.library_delete_doc_confirm, doc?.name.orEmpty()),
            stringResource(R.string.common_delete),
            onConfirm = { dialog = null; vm.deleteDocument { navController.popBackStack() } }, onDismiss = { dialog = null }
        )
        DocDialog.DELETE_PAGES -> ConfirmDialog(
            stringResource(R.string.common_delete), stringResource(R.string.document_delete_pages_confirm, selection.size),
            stringResource(R.string.common_delete),
            onConfirm = { vm.deleteSelected(); dialog = null }, onDismiss = { dialog = null }
        )
        DocDialog.SPLIT -> InputDialog(
            stringResource(R.string.document_split), stringResource(R.string.document_name), stringResource(R.string.common_create),
            onConfirm = { name -> dialog = null; vm.splitSelected(name) { id -> navController.navigate(Routes.document(id)) } },
            onDismiss = { dialog = null }, initial = (doc?.name.orEmpty() + " 2")
        )
        DocDialog.MERGE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.document_merge)) },
            text = {
                if (candidates.isEmpty()) Text(stringResource(R.string.library_empty)) else Column {
                    for (c in candidates.take(12)) {
                        TextButton(onClick = { vm.mergeFrom(c.id); dialog = null }) { Text(c.name, color = TayfGold) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.common_cancel)) } },
            containerColor = TayfSurface, titleContentColor = TayfText
        )
        DocDialog.EXPIRY -> {
            val state = rememberDatePickerState(initialSelectedDateMillis = doc?.expiryDate)
            DatePickerDialog(
                onDismissRequest = { dialog = null },
                confirmButton = {
                    TextButton(onClick = {
                        val sel = state.selectedDateMillis
                        if (sel != null) {
                            val date = Instant.ofEpochMilli(sel).atZone(ZoneOffset.UTC).toLocalDate()
                            vm.setExpiry(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                        }
                        dialog = null
                    }) { Text(stringResource(R.string.common_save), color = TayfGold) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { vm.setExpiry(null); dialog = null }) { Text(stringResource(R.string.document_clear_expiry), color = TayfDanger) }
                        TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.common_cancel)) }
                    }
                }
            ) { DatePicker(state = state) }
        }
    }
}
