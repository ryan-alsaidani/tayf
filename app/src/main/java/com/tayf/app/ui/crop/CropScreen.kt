package com.tayf.app.ui.crop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tayf.app.AppContainer
import com.tayf.app.R
import com.tayf.app.ui.components.CornerEditor
import com.tayf.app.ui.components.EmptyState
import com.tayf.app.ui.components.LoadingBox
import com.tayf.app.ui.components.TayfButton
import com.tayf.app.ui.components.TayfTopBar
import com.tayf.app.ui.theme.TayfBrown
import com.tayf.app.ui.theme.TayfIcons

@Composable
fun CropScreen(container: AppContainer, navController: NavHostController, pageId: Long) {
    val vm: CropViewModel = viewModel(key = "crop-$pageId", factory = CropViewModel.factory(container, pageId))
    val state by vm.state.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) navController.popBackStack() }

    Column(Modifier.fillMaxSize().background(TayfBrown)) {
        TayfTopBar(stringResource(R.string.crop_title), onBack = { navController.popBackStack() })
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
            val bmp = state.bitmap
            when {
                state.loading || state.saving -> LoadingBox()
                bmp == null || state.failed -> EmptyState(stringResource(R.string.error_generic))
                else -> CornerEditor(bmp, state.quad, vm::setQuad, Modifier.fillMaxSize())
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TayfButton(stringResource(R.string.crop_auto), vm::autoDetect, Modifier.weight(1f), enabled = state.bitmap != null && !state.saving, icon = TayfIcons.Scan, outlined = true)
            TayfButton(stringResource(R.string.crop_reset), vm::reset, Modifier.weight(1f), enabled = state.bitmap != null && !state.saving, outlined = true)
            TayfButton(stringResource(R.string.common_apply), vm::applyCrop, Modifier.weight(1f), enabled = state.bitmap != null && !state.saving)
        }
    }
}

