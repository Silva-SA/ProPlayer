package com.example.lrecoperator

import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var gestureDetector: GestureDetector

    private var isAdjustingVolume = false
    private var isAdjustingBrightness = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.player_view)

        initializePlayer()
        setupGestures()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        val videoUri = Uri.parse("https://www.example.com/sample.mp4")
        val mediaItem = MediaItem.fromUri(videoUri)

        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true

        // ضبط سرعة التشغيل (الطريقة الصحيحة في Media3)
        player?.playbackParameters = PlaybackParameters(1.5f)
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this,
            object : GestureDetector.SimpleOnGestureListener() {

                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean {

                    val screenWidth = resources.displayMetrics.widthPixels
                    val xPosition = e1?.x ?: 0f
                    val fraction = -distanceY / 1000f

                    if (xPosition < screenWidth / 2) {
                        isAdjustingBrightness = true
                        adjustBrightness(fraction)
                    } else {
                        isAdjustingVolume = true
                        adjustVolume(fraction)
                    }

                    return true
                }
            })

        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun adjustBrightness(fraction: Float) {
        val layoutParams = window.attributes

        var currentBrightness = layoutParams.screenBrightness
        if (currentBrightness < 0f) {
            currentBrightness = 0.5f
        }

        val newBrightness = (currentBrightness + fraction).coerceIn(0f, 1f)
        layoutParams.screenBrightness = newBrightness
        window.attributes = layoutParams
    }

    private fun adjustVolume(fraction: Float) {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val change = (fraction * maxVol).toInt()
        val newVol = (currentVol + change).coerceIn(0, maxVol)

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
