package com.gallery.app.ui.components
import com.gallery.app.ui.i18n.strings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
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
 * The bar shown while another app is picking a photo: a close cross (or a back arrow inside an
 * album), the title in the middle and, for a caller that allows several items, a tick that returns
 * the whole selection. It replaces the gallery's own big title so picking clearly feels like a
 * different mode, the way the system gallery does it.
 */
@Composable
fun PickerTopBar(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** Set inside an album: the left icon goes back to the album list instead of cancelling the pick. */
    onBack: (() -> Unit)? = null,
    /** Shown instead of "Please select" when the picker is inside an album (its name). */
    title: String? = null,
    /** Items ticked so far in a multi-select pick; 0 shows "Please select". */
    selectedCount: Int = 0,
    /** A multi-select caller: show the confirm tick. */
    showConfirm: Boolean = false,
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit = {}
) {
    val palette = LocalAppTheme.current.palette
    val barTitle = when {
        selectedCount > 0 -> strings.selectionTitle(selectedCount)
        !title.isNullOrBlank() -> title
        else -> strings.pleaseSelect
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.background)
            .statusBarsPadding()
            .height(64.dp),
        contentAlignment = Alignment.Center
    ) {
        ThemedIconButton(
            icon = if (onBack != null) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Close,
            contentDescription = if (onBack != null) strings.back else strings.cancel,
            onClick = onBack ?: onCancel,
            tint = palette.accent,
            hazeState = null,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp)
        )
        Text(
            text = barTitle,
            color = palette.onBackground,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (showConfirm) {
            ThemedIconButton(
                icon = Icons.Rounded.Check,
                contentDescription = strings.done,
                onClick = onConfirm,
                tint = palette.accent,
                enabled = confirmEnabled,
                hazeState = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
            )
        }
    }
}
