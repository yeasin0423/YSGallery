package com.gallery.app.ui.screens
import com.gallery.app.ui.i18n.strings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.gallery.app.model.MediaItem
import com.gallery.app.ui.components.MediaGridItem
import com.gallery.app.ui.components.PickerTopBar
import com.gallery.app.ui.design.ThemedEmptyState
import com.gallery.app.ui.design.ThemedTopBar
import com.gallery.app.ui.theme.LocalAppTheme
import dev.chrisbanes.haze.HazeState
import com.gallery.app.ui.design.HazeSource
import com.gallery.app.ui.design.MediaZoomTransition

/**
 * Favorites: the grid scrolls underneath the frosted top bar; tapping opens the
 * fullscreen viewer as an overlay so the scroll position survives.
 */
@Composable
fun FavoritesScreen(
    favoriteItems: List<MediaItem>,
    onBack: () -> Unit,
    onTrashItem: (MediaItem) -> Unit,
    onDeletePermanently: (MediaItem) -> Unit = {},
    onRenameItem: (MediaItem, String) -> Unit,
    isFavorite: (Long) -> Boolean,
    onToggleFavorite: (Long) -> Unit,
    modifier: Modifier = Modifier,
    columnCount: Int = 4,
    /** Whether deleting skips the trash; only the viewer's confirmation wording needs it. */
    deleteDirectly: Boolean = false,
    /** Another app is picking a photo: tapping a tile hands it back instead of opening the viewer. */
    pickMode: Boolean = false,
    /** The caller takes several items: tiles are tickable and the bar confirms the whole selection. */
    pickMultiple: Boolean = false,
    pickSelectedIds: Set<Long> = emptySet(),
    /** In pick mode, which kinds the caller accepts; the grid hides the rest. */
    pickImages: Boolean = true,
    pickVideos: Boolean = true,
    onTogglePick: (MediaItem) -> Unit = {},
    onConfirmPick: () -> Unit = {},
    onPickItem: (MediaItem) -> Unit = {},
    onCancelPick: () -> Unit = {}
) {
    var selectedDetailIndex by remember { mutableStateOf<Int?>(null) }
    val gridState = rememberLazyGridState()
    val theme = LocalAppTheme.current
    val favoritesHaze = remember { HazeState() }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // While the fullscreen viewer covers everything, the grid below it is not drawn at all.
    var gridCoveredByViewer by remember { mutableStateOf(false) }
    // A multi-select pick turns the grid into tickable tiles.
    val pickSelectionMode = pickMode && pickMultiple
    val displayFavorites = if (pickMode && (!pickImages || !pickVideos)) {
        // The caller only takes one kind: hide the other, so a photo picker never offers videos.
        favoriteItems.filter { if (it.isVideo) pickVideos else pickImages }
    } else {
        favoriteItems
    }

    BackHandler {
        if (selectedDetailIndex != null) {
            selectedDetailIndex = null
        } else if (pickMode) {
            onCancelPick()
        } else {
            onBack()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (displayFavorites.isEmpty()) {
            ThemedEmptyState(
                icon = Icons.Rounded.FavoriteBorder,
                title = strings.noFavorites,
                message = strings.noFavoritesMessage
            )
        } else {
            HazeSource(
                favoritesHaze,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (gridCoveredByViewer) 0f else 1f }
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = theme.shapes.gridHorizontalPadding,
                        end = theme.shapes.gridHorizontalPadding,
                        // The first row starts below the top bar.
                        top = statusBarTop + 72.dp,
                        bottom = navigationBottom + 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(theme.shapes.gridSpacing),
                    verticalArrangement = Arrangement.spacedBy(theme.shapes.gridSpacing)
                ) {
                    itemsIndexed(
                        items = displayFavorites,
                        key = { _, item -> item.id },
                        contentType = { _, _ -> "media_tile" }
                    ) { index, item ->
                        MediaGridItem(
                            item = item,
                            isFavorite = isFavorite(item.id),
                            isSelectionMode = pickSelectionMode,
                            isSelected = pickSelectionMode && item.id in pickSelectedIds,
                            onClick = {
                                if (pickSelectionMode) onTogglePick(item) else selectedDetailIndex = index
                            },
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clipToBounds()
                                .animateItem()
                        )
                    }
                }
            }
        }

        if (pickMode) {
            PickerTopBar(
                onCancel = onCancelPick,
                selectedCount = if (pickMultiple) pickSelectedIds.size else 0,
                showConfirm = pickMultiple,
                confirmEnabled = pickSelectedIds.isNotEmpty(),
                onConfirm = onConfirmPick,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        } else {
            ThemedTopBar(
                title = strings.favorites,
                subtitle = strings.items(displayFavorites.size),
                onBack = onBack,
                hazeState = favoritesHaze,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        com.gallery.app.ui.design.BottomEdgeFade(Modifier.align(Alignment.BottomCenter))

        // Fullscreen Overlay Detail Viewer
        val detailIndexHolder = remember { IntArray(1) }
        selectedDetailIndex?.let { detailIndexHolder[0] = it }
        MediaZoomTransition(
            visible = selectedDetailIndex != null,
            onCoveringChange = { gridCoveredByViewer = it }
        ) {
                MediaDetailScreen(
                    mediaList = displayFavorites,
                    initialIndex = detailIndexHolder[0],
                    onBack = { selectedDetailIndex = null },
                    onTrashItem = onTrashItem,
                    onDeletePermanently = onDeletePermanently,
                    onRenameItem = onRenameItem,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    deleteDirectly = deleteDirectly,
                    pickMode = pickMode,
                    onPickConfirm = onPickItem
                )
        }
    }
}
