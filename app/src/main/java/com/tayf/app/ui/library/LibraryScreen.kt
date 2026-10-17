package com.tayf.app.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tayf.app.AppContainer
import com.tayf.app.R
import com.tayf.app.core.DocumentModel
import com.tayf.app.core.Formatters
import com.tayf.app.core.FolderModel
import com.tayf.app.core.PageModel
import com.tayf.app.core.ScanMode
import com.tayf.app.core.SortOrder
import com.tayf.app.ui.components.ConfirmDialog
import com.tayf.app.ui.components.EmptyState
import com.tayf.app.ui.components.InputDialog
import com.tayf.app.ui.components.LoadingBox
import com.tayf.app.ui.components.PageImage
import com.tayf.app.ui.components.TayfButton
import com.tayf.app.ui.components.TayfChip
import com.tayf.app.ui.components.TayfTopBar
import com.tayf.app.ui.components.toast
import com.tayf.app.ui.nav.Routes
import com.tayf.app.ui.theme.TayfBrown
import com.tayf.app.ui.theme.TayfDanger
import com.tayf.app.ui.theme.TayfGold
import com.tayf.app.ui.theme.TayfIcons
import com.tayf.app.ui.theme.TayfSurface
import com.tayf.app.ui.theme.TayfText
import com.tayf.app.ui.theme.TayfTextDim

private sealed interface LibDialog {
    object NewFolder : LibDialog
    data class RenameDoc(val doc: DocumentModel) : LibDialog
    data class MoveDoc(val doc: DocumentModel) : LibDialog
    data class DeleteDoc(val doc: DocumentModel) : LibDialog
    data class DeleteFolder(val folder: FolderModel) : LibDialog
}

@Composable
fun LibraryScreen(container: AppContainer, navController: NavHostController) {
    val context = LocalContext.current
    val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
    val docs by vm.documents.collectAsState()
    val folders by vm.folders.collectAsState()
    val folderId by vm.folderId.collectAsState()
    val favorites by vm.favoritesOnly.collectAsState()
    val sort by vm.sort.collectAsState()
    val importing by vm.importing.collectAsState()
    val message by vm.message.collectAsState()
    val hijri by container.settings.hijriDates.collectAsState(initial = false)

    var dialog by remember { mutableStateOf<LibDialog?>(null) }
    var menuDocId by remember { mutableStateOf<Long?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var scanSheet by remember { mutableStateOf(false) }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPdf(uri) { id -> navController.navigate(Routes.document(id)) }
    }
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        toast(context, m)
        vm.clearMessage()
    }

    Box(Modifier.fillMaxSize().background(TayfBrown)) {
        Column(Modifier.fillMaxSize()) {
            TayfTopBar(
                title = stringResource(R.string.app_name), onBack = null,
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.SEARCH) }) { Icon(TayfIcons.Search, stringResource(R.string.search_title), tint = TayfGold) }
                    IconButton(onClick = { navController.navigate(Routes.EXPIRING) }) { Icon(TayfIcons.Expiry, stringResource(R.string.expiring_title), tint = TayfGold) }
                    IconButton(onClick = { navController.navigate(Routes.VAULT) }) { Icon(TayfIcons.Vault, stringResource(R.string.vault_title), tint = TayfGold) }
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) { Icon(TayfIcons.Settings, stringResource(R.string.settings_title), tint = TayfGold) }
                }
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { TayfChip(stringResource(R.string.library_all), folderId == null && !favorites, { vm.selectFolder(null); if (favorites) vm.toggleFavorites() }) }
                item { TayfChip(stringResource(R.string.library_favorites), favorites, { vm.toggleFavorites() }) }
                items(folders) { f ->
                    Box {
                        TayfChip(f.name, folderId == f.id, { vm.selectFolder(if (folderId == f.id) null else f.id) })
                    }
                }
                item { TayfChip("+ " + stringResource(R.string.library_new_folder), false, { dialog = LibDialog.NewFolder }) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (folderId != null) {
                    val f = folders.firstOrNull { it.id == folderId }
                    if (f != null) TextButton(onClick = { dialog = LibDialog.DeleteFolder(f) }) {
                        Text(stringResource(R.string.library_delete_folder), color = TayfDanger)
                    }
                }
                Box(Modifier.weight(1f))
                Box {
                    TextButton(onClick = { sortMenu = true }) { Text(stringResource(sortLabel(sort)), color = TayfGold) }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        for (s in SortOrder.values()) {
                            DropdownMenuItem(text = { Text(stringResource(sortLabel(s))) }, onClick = { vm.setSort(s); sortMenu = false })
                        }
                    }
                }
            }
            val list = docs
            when {
                list == null -> LoadingBox()
                list.isEmpty() -> EmptyState(stringResource(R.string.library_empty), TayfIcons.Scan)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(list, key = { it.id }) { doc ->
                        Box {
                            DocCard(container, doc, hijri,
                                onClick = { navController.navigate(Routes.document(doc.id)) },
                                onLongClick = { menuDocId = doc.id })
                            DropdownMenu(expanded = menuDocId == doc.id, onDismissRequest = { menuDocId = null }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.common_rename)) }, onClick = { menuDocId = null; dialog = LibDialog.RenameDoc(doc) })
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (doc.isFavorite) R.string.library_unfavorite else R.string.library_favorite)) },
                                    onClick = { menuDocId = null; vm.toggleFavorite(doc) }
                                )
                                DropdownMenuItem(text = { Text(stringResource(R.string.library_move)) }, onClick = { menuDocId = null; dialog = LibDialog.MoveDoc(doc) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.common_delete), color = TayfDanger) }, onClick = { menuDocId = null; dialog = LibDialog.DeleteDoc(doc) })
                            }
                        }
                    }
                }
            }
        }
        TayfButton(
            text = stringResource(R.string.library_scan), onClick = { scanSheet = true },
            icon = TayfIcons.Camera,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp)
        )
        if (importing) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xAA000000))) { LoadingBox() }
    }

    if (scanSheet) {
        ModalBottomSheet(onDismissRequest = { scanSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                for ((mode, label) in listOf(
                    ScanMode.SINGLE to R.string.mode_single, ScanMode.BATCH to R.string.mode_batch,
                    ScanMode.ID_CARD to R.string.mode_id_card, ScanMode.BOOK to R.string.mode_book,
                    ScanMode.WHITEBOARD to R.string.mode_whiteboard, ScanMode.LONG to R.string.mode_long
                )) {
                    SheetRow(stringResource(label)) { scanSheet = false; navController.navigate(Routes.camera(mode)) }
                }
                SheetRow(stringResource(R.string.library_import_pdf)) { scanSheet = false; pdfPicker.launch(arrayOf("application/pdf")) }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        LibDialog.NewFolder -> InputDialog(
            stringResource(R.string.library_new_folder), stringResource(R.string.library_folder_name), stringResource(R.string.common_create),
            onConfirm = { vm.createFolder(it); dialog = null }, onDismiss = { dialog = null }
        )
        is LibDialog.RenameDoc -> InputDialog(
            stringResource(R.string.common_rename), stringResource(R.string.document_name), stringResource(R.string.common_save),
            onConfirm = { vm.renameDocument(d.doc.id, it); dialog = null }, onDismiss = { dialog = null }, initial = d.doc.name
        )
        is LibDialog.DeleteDoc -> ConfirmDialog(
            stringResource(R.string.common_delete), stringResource(R.string.library_delete_doc_confirm, d.doc.name), stringResource(R.string.common_delete),
            onConfirm = { vm.deleteDocument(d.doc.id); dialog = null }, onDismiss = { dialog = null }
        )
        is LibDialog.DeleteFolder -> ConfirmDialog(
            stringResource(R.string.library_delete_folder), stringResource(R.string.library_delete_folder_confirm, d.folder.name), stringResource(R.string.common_delete),
            onConfirm = { vm.deleteFolder(d.folder.id); dialog = null }, onDismiss = { dialog = null }
        )
        is LibDialog.MoveDoc -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.library_move)) },
            text = {
                Column {
                    TextButton(onClick = { vm.moveDocument(d.doc.id, null); dialog = null }) { Text(stringResource(R.string.library_no_folder), color = TayfGold) }
                    for (f in folders) TextButton(onClick = { vm.moveDocument(d.doc.id, f.id); dialog = null }) { Text(f.name, color = TayfGold) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
private fun SheetRow(label: String, onClick: () -> Unit) {
    Text(
        label, color = TayfText, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

private fun sortLabel(s: SortOrder): Int = when (s) {
    SortOrder.DATE_DESC -> R.string.sort_date_desc
    SortOrder.DATE_ASC -> R.string.sort_date_asc
    SortOrder.NAME_ASC -> R.string.sort_name_asc
    SortOrder.NAME_DESC -> R.string.sort_name_desc
}

@Composable
private fun DocCard(container: AppContainer, doc: DocumentModel, hijri: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val first by produceState<PageModel?>(null, doc.id, doc.updatedAt, doc.pageCount) {
        value = container.documents.getPages(doc.id).firstOrNull()
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.clip(shape).background(TayfSurface).combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.8f).background(TayfBrown)) {
            val p = first
            if (p != null) PageImage(p.thumbPath, Modifier.fillMaxSize(), ContentScale.Crop)
            if (doc.isFavorite) Icon(TayfIcons.Star, null, tint = TayfGold, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp))
        }
        Column(Modifier.padding(10.dp)) {
            Text(doc.name, color = TayfText, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.library_pages_date, doc.pageCount, Formatters.formatDate(doc.updatedAt, hijri)),
                color = TayfTextDim, style = MaterialTheme.typography.bodySmall, maxLines = 1
            )
        }
    }
}
