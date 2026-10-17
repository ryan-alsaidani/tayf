package com.tayf.app.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tayf.app.AppContainer
import com.tayf.app.R
import com.tayf.app.core.FilterType
import com.tayf.app.ui.components.EmptyState
import com.tayf.app.ui.components.LoadingBox
import com.tayf.app.ui.components.TayfButton
import com.tayf.app.ui.components.TayfChip
import com.tayf.app.ui.components.TayfTopBar
import com.tayf.app.ui.nav.Routes
import com.tayf.app.ui.theme.TayfBrown
import com.tayf.app.ui.theme.TayfGold
import com.tayf.app.ui.theme.TayfIcons
import com.tayf.app.ui.theme.TayfText

@Composable
fun PageEditorScreen(container: AppContainer, navController: NavHostController, pageId: Long) {
    val vm: PageEditorViewModel = viewModel(key = "editor-$pageId", factory = PageEditorViewModel.factory(container, pageId))
    val state by vm.state.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) navController.popBackStack() }

    Column(Modifier.fillMaxSize().background(TayfBrown)) {
        TayfTopBar(stringResource(R.string.editor_title), onBack = { navController.popBackStack() })
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            val preview = state.preview
            when {
                state.loading || state.saving -> LoadingBox()
                state.failed || preview == null -> EmptyState(stringResource(R.string.error_generic))
                else -> {
                    Image(
                        bitmap = preview.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit
                    )
                    if (state.rendering) CircularProgressIndicator(color = TayfGold, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                }
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FilterType.values().toList()) { f ->
                    TayfChip(stringResource(filterLabel(f)), state.filter == f, { vm.setFilter(f) })
                }
            }
            LabeledSlider(R.string.editor_brightness, state.brightness, -1f..1f) { vm.setBrightness(it) }
            LabeledSlider(R.string.editor_contrast, state.contrast, 0.5f..2f) { vm.setContrast(it) }
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { TayfButton(stringResource(R.string.editor_rotate_left), { vm.rotate(-90) }, icon = TayfIcons.Rotate, outlined = true) }
                item { TayfButton(stringResource(R.string.editor_rotate_right), { vm.rotate(90) }, icon = TayfIcons.Rotate, outlined = true) }
                item { TayfButton(stringResource(R.string.editor_shadows), { vm.removeShadows() }, outlined = true) }
                item { TayfButton(stringResource(R.string.crop_title), { navController.navigate(Routes.crop(pageId)) }, icon = TayfIcons.Crop, outlined = true) }
                item { TayfButton(stringResource(R.string.annotate_title), { navController.navigate(Routes.annotate(pageId)) }, icon = TayfIcons.Draw, outlined = true) }
                item { TayfButton(stringResource(R.string.protected_title), { navController.navigate(Routes.protectedCopy(pageId)) }, icon = TayfIcons.Blur, outlined = true) }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp)) {
                TayfButton(
                    stringResource(R.string.common_save), vm::save, Modifier.fillMaxWidth(),
                    enabled = state.dirty && !state.saving && state.preview != null
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: Int, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), color = TayfText, modifier = Modifier.padding(end = 12.dp).width(96.dp))
        Slider(
            modifier = Modifier.weight(1f),
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = TayfGold, activeTrackColor = TayfGold)
        )
    }
}

private fun filterLabel(f: FilterType): Int = when (f) {
    FilterType.ORIGINAL -> R.string.filter_original
    FilterType.BLACK_WHITE -> R.string.filter_bw
    FilterType.GRAY -> R.string.filter_gray
    FilterType.ENHANCED_COLOR -> R.string.filter_enhanced
}
