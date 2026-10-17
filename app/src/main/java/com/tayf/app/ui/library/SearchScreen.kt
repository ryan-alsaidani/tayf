package com.tayf.app.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tayf.app.AppContainer
import com.tayf.app.R
import com.tayf.app.core.PageModel
import com.tayf.app.core.SearchHit
import com.tayf.app.ui.components.EmptyState
import com.tayf.app.ui.components.LoadingBox
import com.tayf.app.ui.components.TayfTopBar
import com.tayf.app.ui.components.rememberPageBitmap
import com.tayf.app.ui.nav.Routes
import com.tayf.app.ui.theme.TayfBrown
import com.tayf.app.ui.theme.TayfGold
import com.tayf.app.ui.theme.TayfIcons
import com.tayf.app.ui.theme.TayfSurface
import com.tayf.app.ui.theme.TayfText
import com.tayf.app.ui.theme.TayfTextDim

@Composable
fun SearchScreen(container: AppContainer, navController: NavHostController) {
    val vm: SearchViewModel = viewModel(factory = SearchViewModel.factory(container))
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()

    Column(Modifier.fillMaxSize().background(TayfBrown)) {
        TayfTopBar(stringResource(R.string.search_title), onBack = { navController.popBackStack() })
        OutlinedTextField(
            value = query, onValueChange = vm::setQuery, singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        val list = results
        when {
            query.isBlank() -> EmptyState(stringResource(R.string.search_prompt), TayfIcons.Search)
            list == null -> LoadingBox()
            list.isEmpty() -> EmptyState(stringResource(R.string.search_no_results), TayfIcons.Search)
            else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.pageId }) { hit ->
                    HitCard(container, hit) { navController.navigate(Routes.document(hit.documentId)) }
                }
            }
        }
    }
}

@Composable
private fun HitCard(container: AppContainer, hit: SearchHit, onClick: () -> Unit) {
    val page by produceState<PageModel?>(null, hit.pageId) { value = container.documents.getPage(hit.pageId) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TayfSurface).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        val p = page
        if (p != null) HighlightedThumb(p.thumbPath, hit, Modifier.width(110.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(hit.documentName, color = TayfText, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.search_page_n, hit.pageIndex + 1), color = TayfGold, style = MaterialTheme.typography.labelMedium)
            Text(hit.snippet, color = TayfTextDim, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HighlightedThumb(path: String, hit: SearchHit, modifier: Modifier) {
    val bmp by rememberPageBitmap(path, 600)
    val b = bmp ?: return
    Box(modifier.aspectRatio(b.width.toFloat() / b.height.toFloat()).clip(RoundedCornerShape(6.dp))) {
        Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Canvas(Modifier.fillMaxSize()) {
            for (r in hit.boxes) {
                val tl = Offset(r.left * size.width, r.top * size.height)
                val sz = Size((r.right - r.left) * size.width, (r.bottom - r.top) * size.height)
                drawRect(Color(0x55FFD54A), tl, sz)
                drawRect(TayfGold, tl, sz, style = Stroke(width = 2f))
            }
        }
    }
}
