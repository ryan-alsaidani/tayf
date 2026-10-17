package com.tayf.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tayf.app.AppContainer
import com.tayf.app.R
import com.tayf.app.core.DocumentModel
import com.tayf.app.core.Formatters
import com.tayf.app.ui.components.EmptyState
import com.tayf.app.ui.components.LoadingBox
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
import androidx.compose.runtime.collectAsState

private const val DAY_MS = 24L * 60L * 60L * 1000L

@Composable
fun ExpiringScreen(container: AppContainer, navController: NavHostController) {
    var days by remember { mutableIntStateOf(30) }
    val hijri by container.settings.hijriDates.collectAsState(initial = false)
    val docs by produceState<List<DocumentModel>?>(null, days) {
        value = null
        value = container.documents.listExpiring(days)
    }

    Column(Modifier.fillMaxSize().background(TayfBrown)) {
        TayfTopBar(stringResource(R.string.expiring_title), onBack = { navController.popBackStack() })
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (d in listOf(30, 60, 90, 365)) TayfChip(stringResource(R.string.expiring_within_days, d), days == d, { days = d })
        }
        val list = docs
        when {
            list == null -> LoadingBox()
            list.isEmpty() -> EmptyState(stringResource(R.string.expiring_empty), TayfIcons.Expiry)
            else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { doc ->
                    val expiry = doc.expiryDate ?: 0L
                    val left = Math.floorDiv(expiry - System.currentTimeMillis(), DAY_MS).toInt()
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TayfSurface)
                            .clickable { navController.navigate(Routes.document(doc.id)) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(doc.name, color = TayfText, style = MaterialTheme.typography.titleMedium)
                            Text(Formatters.formatDate(expiry, hijri), color = TayfTextDim, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            if (left < 0) stringResource(R.string.expiring_expired) else stringResource(R.string.expiring_days_left, left),
                            color = if (left < 0) TayfDanger else TayfGold, style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
