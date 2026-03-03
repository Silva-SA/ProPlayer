package com.lrec.operator

import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView

    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnRewind: ImageButton

    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        initializePlayer()
        setupControls()
        setupGestures()
    }

    private fun initializeViews() {
        playerView = findViewById(R.id.playerView)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnForward = findViewById(R.id.btnForward)
        btnRewind = findViewById(R.id.btnRewind)
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        val mediaItem = MediaItem.fromUri(
            Uri.parse("https://www.example.com/sample.mp4")
        )

        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        player.playbackParameters = PlaybackParameters(1.0f)
    }

    private fun setupControls() {

        btnPlayPause.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
                btnPlayPause.setImageResource(R.drawable.ic_play)
            } else {
                player.play()
                btnPlayPause.setImageResource(R.drawable.ic_pause)
            }
        }

        btnForward.setOnClickListener {
            player.seekTo(player.currentPosition + 10000)
        }

        btnRewind.setOnClickListener {
            player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
        }
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
                    val x = e1?.x ?: 0f
                    val percent = -distanceY / 1000f

                    if (x < screenWidth / 2) {
                        adjustBrightness(percent)
                    } else {
                        adjustVolume(percent)
                    }

                    return true
                }
            })

        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun adjustVolume(fraction: Float) {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val change = (fraction * max).toInt()
        val newVolume = (current + change).coerceIn(0, max)

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
    }

    private fun adjustBrightness(fraction: Float) {
        val layoutParams = window.attributes
        var brightness = layoutParams.screenBrightness

        if (brightness < 0f) brightness = 0.5f

        brightness = (brightness + fraction).coerceIn(0f, 1f)
        layoutParams.screenBrightness = brightness
        window.attributes = layoutParams
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
