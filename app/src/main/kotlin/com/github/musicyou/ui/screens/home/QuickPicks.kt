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

    // State for accumulated songs (excluding trending)
    val allSongs: SnapshotStateList<Innertube.SongItem> = remember { mutableStateListOf() }
    var trendingSong by remember { mutableStateOf<Innertube.SongItem?>(null) }
    var continuationToken by remember { mutableStateOf<String?>(null) }
    var isInitialLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var loadMoreError by remember { mutableStateOf<Throwable?>(null) }

    val listState = rememberLazyListState()

    // Load first page when source changes
    LaunchedEffect(quickPicksSource) {
        allSongs.clear()
        trendingSong = null
        continuationToken = null
        isInitialLoading = true
        loadMoreError = null
        viewModel.loadQuickPicks(quickPicksSource = quickPicksSource)
    }

    // Observe page result
    LaunchedEffect(viewModel.relatedPageResult) {
        val result = viewModel.relatedPageResult
        if (result != null) {
            val exception = result.exceptionOrNull()
            if (exception != null) {
                if (isInitialLoading) {
                    isInitialLoading = false
                } else {
                    loadMoreError = exception
                    isLoadingMore = false
                }
                return@LaunchedEffect
            }

            val related = result.getOrNull()
            if (related != null) {
                if (isInitialLoading) {
                    trendingSong = viewModel.trending
                }

                val newSongs = related.songs ?: emptyList()
                allSongs.addAll(newSongs)

                // Extract continuation token – adjust field name if needed
                continuationToken = related.continuation

                isInitialLoading = false
                isLoadingMore = false
                loadMoreError = null
            }
        }
    }

    // Detect end of list and load more
    val shouldLoadMore = remember {
        derivedStateOf {
            if (isLoadingMore || loadMoreError != null || continuationToken == null) {
                false
            } else {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                val totalItemsCount = listState.layoutInfo.totalItemsCount
                lastVisibleIndex != null && totalItemsCount > 0 &&
                        lastVisibleIndex >= totalItemsCount - 3
            }
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            isLoadingMore = true
            loadMoreError = null
            // Assumes ViewModel has this method – add it if missing
            viewModel.loadMoreQuickPicks(continuationToken)
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
                item {
                    ShimmerHost {
                        repeat(20) { ListItemPlaceholder() }
                    }
                }
            } else {
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
