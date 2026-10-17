package com.tayf.app.ui.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tayf.app.AppContainer
import com.tayf.app.R
import com.tayf.app.core.AppDispatchers
import com.tayf.app.core.BitmapIO
import com.tayf.app.core.DocumentModel
import com.tayf.app.core.Outcome
import com.tayf.app.core.SortOrder
import com.tayf.app.privacy.VaultCrypto
import com.tayf.app.ui.components.EmptyState
import com.tayf.app.ui.components.LoadingBox
import com.tayf.app.ui.components.TayfButton
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class VaultStage { LOADING, CREATE, UNLOCK, OPEN }

@Composable
fun VaultScreen(container: AppContainer, navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(VaultStage.LOADING) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        stage = when {
            container.vault.isUnlocked() -> VaultStage.OPEN
            container.vault.isInitialized() -> VaultStage.UNLOCK
            else -> VaultStage.CREATE
        }
    }

    Column(Modifier.fillMaxSize().background(TayfBrown)) {
        TayfTopBar(
            stringResource(R.string.vault_title), onBack = { navController.popBackStack() },
            actions = {
                if (stage == VaultStage.OPEN) {
                    IconButton(onClick = { container.vault.lock(); navController.popBackStack() }) {
                        Icon(TayfIcons.Lock, stringResource(R.string.vault_lock), tint = TayfGold)
                    }
                }
            }
        )
        when (stage) {
            VaultStage.LOADING -> LoadingBox()
            VaultStage.CREATE, VaultStage.UNLOCK -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(TayfIcons.Vault, null, tint = TayfGold, modifier = Modifier.padding(bottom = 12.dp))
                Text(
                    stringResource(if (stage == VaultStage.CREATE) R.string.vault_create_hint else R.string.vault_unlock_hint),
                    color = TayfTextDim, modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it; error = false }, singleLine = true,
                    label = { Text(stringResource(R.string.vault_password)) },
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                )
                if (stage == VaultStage.CREATE) {
                    OutlinedTextField(
                        value = confirm, onValueChange = { confirm = it; error = false }, singleLine = true,
                        label = { Text(stringResource(R.string.vault_password_confirm)) },
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
                if (error) Text(stringResource(R.string.vault_wrong), color = TayfDanger, modifier = Modifier.padding(top = 8.dp))
                TayfButton(
                    stringResource(if (stage == VaultStage.CREATE) R.string.common_create else R.string.vault_unlock),
                    {
                        scope.launch {
                            if (stage == VaultStage.CREATE) {
                                if (password != confirm || container.vault.create(password) !is Outcome.Ok) error = true else { password = ""; confirm = ""; stage = VaultStage.OPEN }
                            } else {
                                if (container.vault.unlock(password)) { password = ""; stage = VaultStage.OPEN } else error = true
                            }
                        }
                    },
                    Modifier.fillMaxWidth().padding(top = 16.dp), enabled = password.isNotEmpty()
                )
            }
            VaultStage.OPEN -> {
                val folderId by produceState<Long?>(null) {
                    val existing = container.documents.observeFolders(true)
                    var id: Long? = null
                    existing.collect { list ->
                        id = list.firstOrNull { it.isVault }?.id
                        if (id == null) id = container.documents.createFolder(context.getString(R.string.vault_title), true)
                        value = id
                    }
                }
                val fid = folderId
                if (fid == null) LoadingBox() else {
                    val docs by remember(fid) { container.documents.observeDocuments(fid, SortOrder.DATE_DESC, false) }.collectAsState(initial = emptyList())
                    Column(Modifier.fillMaxSize()) {
                        Box0(Modifier.weight(1f)) {
                            if (docs.isEmpty()) EmptyState(stringResource(R.string.vault_empty), TayfIcons.Vault) else LazyColumn(
                                contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(docs, key = { it.id }) { doc ->
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TayfSurface)
                                            .clickable { navController.navigate(Routes.document(doc.id)) }.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(doc.name, color = TayfText, style = MaterialTheme.typography.titleMedium)
                                            Text(stringResource(R.string.vault_pages, doc.pageCount), color = TayfTextDim, style = MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(onClick = {
                                            scope.launch {
                                                busy = true
                                                restoreDocument(container, doc)
                                                busy = false
                                            }
                                        }) { Text(stringResource(R.string.vault_restore), color = TayfGold) }
                                    }
                                }
                            }
                        }
                        TayfButton(
                            stringResource(R.string.vault_add), { addOpen = true },
                            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), icon = TayfIcons.Add, enabled = !busy
                        )
                    }
                    if (busy) LoadingBox()
                    if (addOpen) {
                        val candidates by remember { container.documents.observeDocuments(null, SortOrder.DATE_DESC, false) }.collectAsState(initial = emptyList())
                        AlertDialog(
                            onDismissRequest = { addOpen = false },
                            title = { Text(stringResource(R.string.vault_add)) },
                            text = {
                                if (candidates.isEmpty()) Text(stringResource(R.string.library_empty)) else LazyColumn {
                                    items(candidates.take(40), key = { it.id }) { c ->
                                        TextButton(onClick = {
                                            addOpen = false
                                            scope.launch {
                                                busy = true
                                                moveToVault(container, c, fid)
                                                busy = false
                                                toast(context, R.string.vault_moved)
                                            }
                                        }) { Text(c.name, color = TayfGold) }
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = { TextButton(onClick = { addOpen = false }) { Text(stringResource(R.string.common_cancel)) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Box0(modifier: Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier) { content() }
}

private suspend fun moveToVault(container: AppContainer, doc: DocumentModel, vaultFolderId: Long) {
    for (page in container.documents.getPages(doc.id)) {
        val enc = container.vault.encryptPageFiles(page)
        if (enc is Outcome.Ok) container.documents.updatePage(enc.value)
    }
    container.documents.moveDocument(doc.id, vaultFolderId)
}

private suspend fun restoreDocument(container: AppContainer, doc: DocumentModel) {
    for (page in container.documents.getPages(doc.id)) {
        var updated = page
        suspend fun plain(path: String, target: File): String {
            if (!path.endsWith(VaultCrypto.EXT)) return path
            val r = container.vault.decryptToTemp(path)
            if (r !is Outcome.Ok) return path
            withContext(AppDispatchers.io) {
                r.value.copyTo(target, overwrite = true)
                r.value.delete()
                File(path).delete()
            }
            return target.absolutePath
        }
        updated = updated.copy(
            originalPath = plain(page.originalPath, container.fileStore.newOriginalFile()),
            processedPath = plain(page.processedPath, container.fileStore.newProcessedFile()),
            thumbPath = plain(page.thumbPath, container.fileStore.newThumbFile())
        )
        container.documents.updatePage(updated)
    }
    container.documents.moveDocument(doc.id, null)
}
