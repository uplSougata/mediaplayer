package com.example.myapplication

import android.annotation.SuppressLint
import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class MediaPlayerViewModel(application: Application) : AndroidViewModel(application) {
    @SuppressLint("StaticFieldLeak")
    private val context = application.applicationContext

    private val mediaList = listOf(
        MediaItemData("https://www.w3schools.com/html/mov_bbb.mp4", isVideo = true),
        MediaItemData("https://www.w3schools.com/html/img_chania.jpg", isVideo = false),
        MediaItemData(
            "https://www.learningcontainer.com/wp-content/uploads/2020/05/sample-mp4-file.mp4",
            isVideo = true
        ),
        MediaItemData("https://www.w3schools.com/html/pic_trulli.jpg", isVideo = false)
    )
    private var imageTimerJob: Job? = null
    private var videoProgressJob: Job? = null
    private var progressJob: Job? = null
    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _duration = MutableStateFlow(3000L) // Default for images
    val duration: StateFlow<Long> = _duration

    private val _currentMedia = MutableStateFlow(mediaList[0])
    val currentMedia: StateFlow<MediaItemData> = _currentMedia

    private val _controlsVisible = MutableStateFlow(true)
    val controlsVisible: StateFlow<Boolean> = _controlsVisible

    private val exoPlayer = ExoPlayer.Builder(context).build()
    private val downloadedMedia = mutableMapOf<String, String>()

    private val _isDownloading = MutableStateFlow(true)
    val isDownloading: StateFlow<Boolean> = _isDownloading

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress

    private val IMAGE_DISPLAY_DURATION = 3000L
    init {
        viewModelScope.launch {
            downloadAllMedia()
        }

    }

    private suspend fun downloadAllMedia() {
        var downloadedCount = 0
        var allFilesExist = true

        mediaList.forEach { media ->
            val fileName = media.url.substringAfterLast('/')
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

            if (file.exists()) {
                downloadedMedia[media.url] = file.absolutePath
                downloadedCount++
            } else {
                allFilesExist = false
                downloadFile(media.url, file) {
                    downloadedMedia[media.url] = file.absolutePath
                    downloadedCount++
                    _downloadProgress.value = (downloadedCount * 100) / mediaList.size
                    if (downloadedCount == mediaList.size) {
                        _isDownloading.value = false
                        loadMedia()
                    }
                }
            }
        }

        if (allFilesExist) {
            _isDownloading.value = false
            loadMedia()
        }
    }

    private fun downloadFile(url: String, file: File, onComplete: () -> Unit) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading media")
            .setDestinationUri(Uri.fromFile(file))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        viewModelScope.launch {
            delay(3000) // Simulate download time
            onComplete()
        }
    }

    private fun loadMedia() {
        val media = mediaList[_currentIndex.value]
        val localPath = downloadedMedia[media.url] ?: media.url
        _currentMedia.value = MediaItemData(localPath, media.isVideo)
        setupPlayer()
    }

    private fun setupPlayer() {
        stopAllJobs() // Ensure previous jobs are canceled
        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        val mediaItem = MediaItem.fromUri(Uri.parse(_currentMedia.value.url))
        exoPlayer.setMediaItem(mediaItem)

        if (_currentMedia.value.isVideo) {
            setupVideoPlayer()
        } else {
            setupImagePlayer()
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    moveToNextMedia()
                }
            }
        })
        startProgressUpdater()
    }

    private fun setupVideoPlayer() {
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        viewModelScope.launch {
            while (exoPlayer.duration <= 0) {
                delay(100)
            }
            _duration.value = exoPlayer.duration
            startProgressUpdater()
        }
    }

    private fun setupImagePlayer() {
        _duration.value = 3000L // 3 seconds for images
        startProgressUpdater()
    }



    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
        if (_isPlaying.value) exoPlayer.play() else exoPlayer.pause()
    }

    fun moveToNextMedia() {
        _currentIndex.value = (_currentIndex.value + 1) % mediaList.size
        loadMedia()
    }

    fun moveToPreviousMedia() {
        _currentIndex.value =
            if (_currentIndex.value > 0) _currentIndex.value - 1 else mediaList.size - 1
        loadMedia()
    }

    fun updateProgress(newProgress: Float) {
        _progress.value = newProgress
        if (_currentMedia.value.isVideo) {
            exoPlayer.seekTo((newProgress * _duration.value).toLong())
        }
    }


    private fun startProgressUpdater() {
        stopProgressUpdater() // Stop any previous job to prevent multiple updates

        progressJob = viewModelScope.launch {
            while (isActive) {
                val duration = exoPlayer.duration.takeIf { it > 0 } ?: 1L // Avoid division by zero
                val currentPosition = exoPlayer.currentPosition

                if (_currentMedia.value.isVideo) {
                    if (exoPlayer.isPlaying) { // ✅ Ensure ExoPlayer is playing
                        _progress.value = currentPosition / duration.toFloat() // Normalize progress
                    }
                } else {
                    // Image progress (simulate a fixed duration for image display)
                    val steps = IMAGE_DISPLAY_DURATION / 100
                    for (i in 0 until steps.toInt()) {
                        if (!isActive) break // ✅ Stops execution if coroutine is canceled
                        delay(100)
                        _progress.value = (i + 1) / steps.toFloat()
                    }
                    moveToNextMedia()
                    return@launch
                }

                delay(500) // Update every 500ms
            }
        }
    }




    private fun stopProgressUpdater() {
        progressJob?.cancel()
        progressJob = null
    }

    fun toggleControls() {
        _controlsVisible.value = true
        hideControlsAfterDelay()
    }

    private fun hideControlsAfterDelay() {
        viewModelScope.launch {
            delay(4000L)
            _controlsVisible.value = false
        }
    }

    private fun stopAllJobs() {
        imageTimerJob?.cancel()
        videoProgressJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        stopAllJobs()
        exoPlayer.stop()
        exoPlayer.release()
    }

    fun getExoPlayer() = exoPlayer
}

