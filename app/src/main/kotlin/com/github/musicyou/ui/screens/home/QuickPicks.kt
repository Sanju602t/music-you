package com.github.musicyou.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.github.musicyou.ui.components.TextPlaceholder
import com.github.musicyou.ui.items.ListItemPlaceholder
import com.github.musicyou.ui.items.LocalSongItem
import com.github.musicyou.ui.items.SongItem
import com.github.musicyou.ui.styling.Dimensions
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

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(bottom = 8.dp)

    LaunchedEffect(quickPicksSource) {
        viewModel.loadQuickPicks(quickPicksSource = quickPicksSource)
    }

    HomeScaffold(
        title = R.string.quick_picks,
        openSearch = openSearch,
        openSettings = openSettings
    ) {
        BoxWithConstraints {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = 16.dp + playerPadding)
            ) {
                viewModel.relatedPageResult?.getOrNull()?.let { related ->
                    Text(
                        text = stringResource(id = R.string.quick_picks),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = sectionTextModifier
                    )

                    // LazyRow use kiya hai normal horizontal scrolling ke liye (YouTube style)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        viewModel.trending?.let { song ->
                            item {
                                LocalSongItem(
                                    modifier = Modifier.width(280.dp), // Fixed width for horizontal scrolling
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

                        items(
                            items = related.songs?.dropLast(if (viewModel.trending == null) 0 else 1)
                                ?: emptyList(),
                            key = Innertube.SongItem::key
                        ) { song ->
                            SongItem(
                                modifier = Modifier.width(280.dp),
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
                    }
                } ?: viewModel.relatedPageResult?.exceptionOrNull()?.let {
                    Text(
                        text = stringResource(id = R.string.home_error),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(all = 16.dp)
                    )

                    Row(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    viewModel.loadQuickPicks(quickPicksSource)
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.Refresh, null)
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(text = stringResource(id = R.string.retry))
                        }

                        FilledTonalButton(onClick = onOfflinePlaylistClick) {
                            Icon(Icons.Outlined.DownloadForOffline, null)
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(text = stringResource(id = R.string.offline))
                        }
                    }
                } ?: ShimmerHost {
                    TextPlaceholder(modifier = sectionTextModifier)
                    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                        repeat(2) {
                            ListItemPlaceholder(modifier = Modifier.width(280.dp))
                        }
                    }
                }
            }
        }
    }
}
