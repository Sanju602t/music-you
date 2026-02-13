package com.github.musicyou.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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

    LaunchedEffect(quickPicksSource) {
        viewModel.loadQuickPicks(quickPicksSource = quickPicksSource)
    }

    HomeScaffold(
        title = R.string.quick_picks,
        openSearch = openSearch,
        openSettings = openSettings
    ) {
        BoxWithConstraints {
            // LazyColumn use kiya hai taaki upar se neeche full screen scroll ho
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp + playerPadding)
            ) {
                viewModel.relatedPageResult?.getOrNull()?.let { related ->
                    // Trending Song Sabse upar
                    viewModel.trending?.let { song ->
                        item {
                            LocalSongItem(
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
                                                database.query {
                                                    database.clearEventsFor(song.id)
                                                }
                                            },
                                            onGoToAlbum = onAlbumClick,
                                            onGoToArtist = onArtistClick
                                        )
                                    }
                                }
                            )
                        }
                    }

                    // Baki saare songs ki vertical list
                    items(
                        items = related.songs?.dropLast(if (viewModel.trending == null) 0 else 1) ?: emptyList(),
                        key = Innertube.SongItem::key
                    ) { song ->
                        SongItem(
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
                } ?: item {
                    // Error state handled inside item
                    viewModel.relatedPageResult?.exceptionOrNull()?.let {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = stringResource(R.string.home_error), textAlign = TextAlign.Center)
                            Spacer(Modifier.size(16.dp))
                            Button(onClick = { scope.launch { viewModel.loadQuickPicks(quickPicksSource) } }) {
                                Icon(Icons.Outlined.Refresh, null)
                                Text(stringResource(R.string.retry))
                            }
                        }
                    } ?: ShimmerHost {
                        // Loading state: Full screen vertical placeholders
                        repeat(10) {
                            ListItemPlaceholder()
                        }
                    }
                }
            }
        }
    }
}
