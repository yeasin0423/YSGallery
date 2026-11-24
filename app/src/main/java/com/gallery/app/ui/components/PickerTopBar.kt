package com.gallery.app.ui.components
import com.gallery.app.ui.i18n.strings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallery.app.ui.design.ThemedIconButton
import com.gallery.app.ui.theme.LocalAppTheme

/**
 * The bar shown while another app is picking a photo: a close cross, "Please select" in the middle.
 * It replaces the gallery's own big title so picking clearly feels like a different mode, the way
 * the system gallery does it.
 */
@Composable
fun PickerTopBar(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppTheme.current.palette
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.background)
            .statusBarsPadding()
            .height(64.dp),
        contentAlignment = Alignment.Center
    ) {
        ThemedIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = strings.cancel,
            onClick = onCancel,
            tint = palette.accent,
            hazeState = null,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp)
        )
        Text(
            text = strings.pleaseSelect,
            color = palette.onBackground,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
