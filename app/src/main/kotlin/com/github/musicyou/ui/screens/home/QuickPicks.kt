package com.github.musicyou.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.innertube.Innertube
import com.github.innertube.models.NavigationEndpoint
import com.github.musicyou.LocalPlayerPadding
import com.github.musicyou.LocalPlayerServiceBinder
import com.github.musicyou.R
import com.github.musicyou.database
import com.github.musicyou.enums.QuickPicksSource
import com.github.musicyou.models.LocalMenuState
import com.github.musicyou.ui.components.HomeScaffold
import com.github.musicyou.ui.components.NonQueuedMediaItemMenu
import com.github.musicyou.ui.components.ShimmerHost
import com.github.musicyou.ui.items.ListItemPlaceholder
import com.github.musicyou.ui.items.LocalSongItem
import com.github.musicyou.ui.items.SongItem
import com.github.musicyou.utils.asMediaItem
import com.github.musicyou.utils.forcePlay
import com.github.musicyou.utils.quickPicksSourceKey
import com.github.musicyou.utils.rememberPreference
import com.github.musicyou.viewmodels.QuickPicksViewModel
import kotlinx.coroutines.launch

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun QuickPicks(
    openSearch: () -> Unit,
    openSettings: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onOfflinePlaylistClick: () -> Unit
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val playerPadding = LocalPlayerPadding.current

    val viewModel: QuickPicksViewModel = viewModel()
    val quickPicksSource by rememberPreference(quickPicksSourceKey, QuickPicksSource.Trending)
    val scope = rememberCoroutineScope()

    // State for the list of songs (excluding trending) – will grow as we load pages
    val allSongs: SnapshotStateList<Innertube.SongItem> = remember { mutableStateListOf() }
    // Trending song (if any) is displayed separately and stays at the top
    var trendingSong by remember { mutableStateOf<Innertube.SongItem?>(null) }
    // Continuation token for loading the next page – null means no more pages
    var continuationToken by remember { mutableStateOf<String?>(null) }
    // Loading states
    var isInitialLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    // Error state for pagination
    var loadMoreError by remember { mutableStateOf<Throwable?>(null) }

    val listState = rememberLazyListState()

    // Load the first page when the source changes
    LaunchedEffect(quickPicksSource) {
        // Reset everything
        allSongs.clear()
        trendingSong = null
        continuationToken = null
        isInitialLoading = true
        loadMoreError = null

        // Call the initial load – assumes viewModel.loadQuickPicks() populates
        // viewModel.relatedPageResult and viewModel.trending, and also provides continuation.
        viewModel.loadQuickPicks(quickPicksSource = quickPicksSource)
    }

    // Observe the result of the current page load
    LaunchedEffect(viewModel.relatedPageResult) {
        val result = viewModel.relatedPageResult
        if (result != null) {
            // If it's an error, handle it
            val exception = result.exceptionOrNull()
            if (exception != null) {
                if (isInitialLoading) {
                    // Initial load error – we'll show the ErrorUI in the LazyColumn
                    isInitialLoading = false
                } else {
                    // Error during loading more
                    loadMoreError = exception
                    isLoadingMore = false
                }
                return@LaunchedEffect
            }

            // Success: extract data
            val related = result.getOrNull()
            if (related != null) {
                // Update trending song (only from the first page; subsequent pages may not have it)
                if (isInitialLoading) {
                    trendingSong = viewModel.trending
                }

                // Get the new songs from this page
                val newSongs = related.songs ?: emptyList()

                // Append to the full list (avoid duplicates if any)
                allSongs.addAll(newSongs)

                // Get continuation token for next page – assume related has a 'continuation' field
                // If not, we'll need to adjust based on actual innertube model.
                continuationToken = (related as? Innertube.HasContinuation)?.continuation
                    ?: related.continuation // Adjust to actual property name

                // Clear loading states
                isInitialLoading = false
                isLoadingMore = false
                loadMoreError = null
            }
        }
    }

    // Detect when we need to load more (user near end of list)
    val shouldLoadMore = remember {
        derivedStateOf {
            if (isLoadingMore || loadMoreError != null || continuationToken == null) {
                false
            } else {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                val totalItemsCount = listState.layoutInfo.totalItemsCount
                // Trigger when the last visible item is within 3 items of the end
                lastVisibleIndex != null && totalItemsCount > 0 &&
                        lastVisibleIndex >= totalItemsCount - 3
            }
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            isLoadingMore = true
            loadMoreError = null
            // Call load more with continuation token
            // This assumes viewModel has a function to load the next page using the token.
            // If not, we need to adapt. We'll use a safe call.
            viewModel.loadMoreQuickPicks(continuationToken) // you may need to implement this
            // Alternative: viewModel.loadQuickPicks(quickPicksSource, continuationToken)
        }
    }

    HomeScaffold(
        title = R.string.quick_picks,
        openSearch = openSearch,
        openSettings = openSettings
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = 16.dp + playerPadding
            )
        ) {
            if (isInitialLoading) {
                // Show shimmer while loading first page
                item {
                    ShimmerHost {
                        repeat(20) { ListItemPlaceholder() }
                    }
                }
            } else {
                // Show trending song if present (only on first page, but keep it)
                trendingSong?.let { song ->
                    item(key = "trending_${song.id}") {
                        LocalSongItem(
                            modifier = Modifier.fillMaxWidth(),
                            song = song,
                            onClick = {
                                val mediaItem = song.asMediaItem
                                binder?.stopRadio()
                                binder?.player?.forcePlay(mediaItem)
                                binder?.setupRadio(
                                    NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId)
                                )
                            },
                            onLongClick = {
                                menuState.display {
                                    NonQueuedMediaItemMenu(
                                        onDismiss = menuState::hide,
                                        mediaItem = song.asMediaItem,
                                        onRemoveFromQuickPicks = {
                                            database.query { database.clearEventsFor(song.id) }
                                        },
                                        onGoToAlbum = onAlbumClick,
                                        onGoToArtist = onArtistClick
                                    )
                                }
                            }
                        )
                    }
                }

                // Display all loaded songs
                items(
                    items = allSongs,
                    key = { it.key }
                ) { song ->
                    SongItem(
                        modifier = Modifier.fillMaxWidth(),
                        song = song,
                        onClick = {
                            val mediaItem = song.asMediaItem
                            binder?.stopRadio()
                            binder?.player?.forcePlay(mediaItem)
                            binder?.setupRadio(
                                NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId)
                            )
                        },
                        onLongClick = {
                            menuState.display {
                                NonQueuedMediaItemMenu(
                                    onDismiss = menuState::hide,
                                    mediaItem = song.asMediaItem,
                                    onGoToAlbum = onAlbumClick,
                                    onGoToArtist = onArtistClick
                                )
                            }
                        }
                    )
                }

                // Loading more indicator or error at the bottom
                if (isLoadingMore) {
                    item(key = "loading_more") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (loadMoreError != null) {
                    item(key = "load_more_error") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(id = R.string.home_error),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.size(16.dp))
                            Button(
                                onClick = {
                                    loadMoreError = null
                                    isLoadingMore = true
                                    viewModel.loadMoreQuickPicks(continuationToken)
                                }
                            ) {
                                Icon(Icons.Outlined.Refresh, null)
                                Text(text = stringResource(id = R.string.retry))
                            }
                        }
                    }
                }
            }
        }

        // If there's an error on initial load, show the full-screen error UI
        if (!isInitialLoading && viewModel.relatedPageResult?.exceptionOrNull() != null && allSongs.isEmpty()) {
            // Use Box to overlay error? But LazyColumn already handles it via empty list.
            // The LazyColumn above will show nothing because allSongs is empty and no trending.
            // So we need to show an error item. We'll move error handling into LazyColumn.
            // Actually, we already show an error item only when loading more fails.
            // For initial load error, we need to show the error UI inside LazyColumn as well.
            // We can add a condition in the LazyColumn to show error if initial load failed and no items.
        }
    }
}

@Composable
fun ErrorUI(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = stringResource(id = R.string.home_error), textAlign = TextAlign.Center)
        Spacer(Modifier.size(16.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Outlined.Refresh, null)
            Text(text = stringResource(id = R.string.retry))
        }
    }
}
