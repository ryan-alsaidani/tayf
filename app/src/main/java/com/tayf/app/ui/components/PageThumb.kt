package com.tayf.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tayf.app.core.PageModel
import com.tayf.app.privacy.VaultCrypto
import com.tayf.app.ui.theme.TayfBrown
import com.tayf.app.ui.theme.TayfGold
import com.tayf.app.ui.theme.TayfSurface
import com.tayf.app.ui.theme.TayfText
import java.io.File

/** Image of a stored file; transparently handles encrypted vault files. */
@Composable
fun PageImage(path: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit, maxSide: Int = 800) {
    if (path.endsWith(VaultCrypto.EXT)) {
        val bmp by rememberPageBitmap(path, maxSide)
        val b = bmp
        if (b != null) {
            Image(bitmap = b.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = contentScale)
        } else {
            Box(modifier)
        }
    } else {
        AsyncImage(model = File(path), contentDescription = null, modifier = modifier, contentScale = contentScale)
    }
}

@Composable
fun PageThumb(
    page: PageModel, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .aspectRatio(0.72f)
            .clip(shape)
            .background(TayfSurface)
            .border(if (selected) 2.dp else 1.dp, if (selected) TayfGold else TayfSurface, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        PageImage(page.thumbPath, Modifier.fillMaxSize(), ContentScale.Crop)
        Box(
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(CircleShape)
                .background(TayfBrown.copy(alpha = 0.8f)).padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text("${page.position + 1}", color = TayfText, style = MaterialTheme.typography.labelMedium)
        }
        if (selected) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape)
                    .background(TayfGold).padding(horizontal = 7.dp, vertical = 1.dp)
            ) {
                Text("✓", color = TayfBrown, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
