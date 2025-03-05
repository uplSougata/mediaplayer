package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mediaPlayerViewModel: MediaPlayerViewModel = viewModel()
            MediaPlayerScreen(mediaPlayerViewModel)
        }
    }
}


// Media item data class (video or image)
data class MediaItemData(val url: String, val isVideo: Boolean)

@Composable
fun VideoPlayer(exoPlayer: ExoPlayer) {
    // Support only MP4 WEBM
    AndroidView(
        factory = {
            PlayerView(it).apply {
                player = exoPlayer
                useController = false
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}



@Composable
fun ImagePlayer(imageUrl: String, onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(3000) // Show image for 60 seconds
        onTimeout()
    }
// Support only JPEG PNG WEBP
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = rememberAsyncImagePainter(
                model = imageUrl
            ),
            contentDescription = "Loaded Image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
    }
}


@Composable
fun MediaPlayerScreen(viewModel: MediaPlayerViewModel = viewModel()) {
    val currentMedia by viewModel.currentMedia.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val controlsVisible by viewModel.controlsVisible.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()  // Observe downloading state

    val exoPlayer = remember { viewModel.getExoPlayer() }
    Log.d("Main Activity", "MediaPlayerScreen: $isDownloading")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { viewModel.toggleControls() },
        contentAlignment = Alignment.Center
    ) {
        if (isDownloading) {
            // Show Loader when downloading
            CircularProgressIndicator(color = Color.White)
        } else {
            // Show Video or Image when ready
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (currentMedia.isVideo) {
                    VideoPlayer(exoPlayer)
                } else {
                    ImagePlayer(currentMedia.url) { viewModel.moveToNextMedia() }
                }

                if (controlsVisible) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Slider(
                            value = progress,
                            onValueChange = { viewModel.updateProgress(it) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.Red,
                                inactiveTrackColor = Color.Gray
                            )
                        )

                        MediaControls(
                            isPlaying = isPlaying,
                            onPlayPause = { viewModel.togglePlayPause() },
                            onNext = { viewModel.moveToNextMedia() },
                            onPrevious = { viewModel.moveToPreviousMedia() }
                        )
                    }
                }
            }
        }
    }
}



// Custom Player Controls UI
@Composable
fun MediaControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    painter = painterResource(id = R.drawable.arrow_back),
                    contentDescription = "Play Previous",
                    tint = Color.White
                )
            }

            IconButton(onClick = onPlayPause) {
                if (isPlaying) {
                    Icon(
                        painter = painterResource(id = R.drawable.pause),
                        contentDescription = "Play Icon",
                        tint = Color.White
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.play_arrow),
                        contentDescription = "Pause Icon",
                        tint = Color.White
                    )
                }
            }

            IconButton(onClick = onNext) {
                Icon(
                    painter = painterResource(id = R.drawable.arrow_forward),
                    contentDescription = "Play Next",
                    tint = Color.White
                )
            }
        }
    }
}

