package com.tayf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tayf.app.ui.theme.TayfGoldLight

val PickerColors: List<Int> = listOf(
    0xFFC9A24B.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFE5645A.toInt(),
    0xFF3D7BFF.toInt(), 0xFF2E9E5B.toInt(), 0xFF6B4423.toInt(), 0xFF8B0000.toInt()
)

@Composable
fun ColorPickerRow(selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (c in PickerColors) {
            val isSel = c == selected
            Row(
                modifier = Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                    .border(if (isSel) 3.dp else 1.dp, if (isSel) TayfGoldLight else Color(0x55FFFFFF), CircleShape)
                    .clickable { onPick(c) }
            ) {}
        }
    }
}
