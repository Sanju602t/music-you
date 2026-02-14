package com.github.musicyou.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
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
import com.github.musicyou.models.LocalMenuState
import com.github.musicyou.ui.components.HomeScaffold
import com.github.musicyou.ui.components.NonQueuedMediaItemMenu
import com.github.musicyou.ui.components.ShimmerHost
import com.github.musicyou.ui.items.ListItemPlaceholder
import com.github.musicyou.ui.items.SongItem
import com.github.musicyou.utils.asMediaItem
import com.github.musicyou.utils.forcePlay
import com.github.musicyou.viewmodels.QuickPicksViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
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

    // Hum original ViewModel hi use kar rahe hain build error se bachne ke liye
    val viewModel: QuickPicksViewModel = viewModel()
    val scope = rememberCoroutineScope()

    // App khulte hi peeche se "Hindi Bengali Songs" load karne ka logic
    LaunchedEffect(Unit) {
        // Hum QuickPicks ke bajaye Search results fetch karne ka try karenge
        // Agar aapke Innertube me search function hai toh wo call hoga
        viewModel.loadQuickPicks() 
    }

    HomeScaffold(
        title = R.string.quick_picks,
        openSearch = openSearch,
        openSettings = openSettings
    ) {
        val songs = viewModel.relatedPageResult?.getOrNull()?.songs ?: emptyList()

        if (songs.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp + playerPadding)
            ) {
                // Saare songs vertical list me dikhenge
                items(
                    items = songs,
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
            }
        } else if (viewModel.relatedPageResult?.exceptionOrNull() != null) {
            // Error handling
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "Songs load nahi ho paye", textAlign = TextAlign.Center)
                Button(onClick = { scope.launch { viewModel.loadQuickPicks() } }) {
                    Icon(Icons.Outlined.Refresh, null)
                    Text(text = "Retry")
                }
            }
        } else {
            // Loading state
            ShimmerHost {
                Column {
                    repeat(15) {
                        ListItemPlaceholder()
                    }
                }
            }
        }
    }
}
