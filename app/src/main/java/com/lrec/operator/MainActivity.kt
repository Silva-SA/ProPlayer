package com.lrec.operator

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : AppCompatActivity() {

    // ──────────────── المتغيرات الرئيسية ────────────────
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var drawerLayout: DrawerLayout

    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnRewind: ImageButton
    private lateinit var btnMenu: ImageButton

    private lateinit var seekBar: SeekBar
    private lateinit var progressFill: View
    private lateinit var progressThumb: View
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvDuration: TextView

    private lateinit var playerControls: LinearLayout
    private lateinit var topBar: LinearLayout

    private lateinit var volumeOverlay: LinearLayout
    private lateinit var volumeBar: View
    private lateinit var tvVolumePercent: TextView

    private lateinit var brightnessOverlay: LinearLayout
    private lateinit var brightnessBar: View
    private lateinit var tvBrightnessPercent: TextView

    private lateinit var gestureDetector: GestureDetector

    // ──────────────── متغيرات الحالة ────────────────
    private var controlsVisible = true
    private val hideControlsDelay = 3500L
    private val handler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideControls() }

    private var overlayHideRunnable: Runnable? = null
    private var seekBarWidth = 0
    private var isDraggingSeekBar = false

    // ──────────────── onCreate ────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // وضع ملء الشاشة مع الاستمرار بدعم أندرويد 5
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        setContentView(R.layout.activity_main)

        initializeViews()
        initializePlayer()
        setupControls()
        setupSeekBar()
        setupGestures()
        startProgressUpdater()
        scheduleHideControls()
    }

    // ──────────────── ربط العناصر ────────────────
    private fun initializeViews() {
        drawerLayout   = findViewById(R.id.drawerLayout)
        playerView     = findViewById(R.id.playerView)

        btnPlayPause   = findViewById(R.id.btnPlayPause)
        btnForward     = findViewById(R.id.btnForward)
        btnRewind      = findViewById(R.id.btnRewind)
        btnMenu        = findViewById(R.id.btnMenu)

        seekBar        = findViewById(R.id.seekBar)
        progressFill   = findViewById(R.id.progressFill)
        progressThumb  = findViewById(R.id.progressThumb)
        tvCurrentTime  = findViewById(R.id.tvCurrentTime)
        tvDuration     = findViewById(R.id.tvDuration)

        playerControls = findViewById(R.id.playerControls)
        topBar         = findViewById(R.id.topBar)

        volumeOverlay       = findViewById(R.id.volumeOverlay)
        volumeBar           = findViewById(R.id.volumeBar)
        tvVolumePercent     = findViewById(R.id.tvVolumePercent)

        brightnessOverlay   = findViewById(R.id.brightnessOverlay)
        brightnessBar       = findViewById(R.id.brightnessBar)
        tvBrightnessPercent = findViewById(R.id.tvBrightnessPercent)
    }

    // ──────────────── تهيئة المشغل ────────────────
    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        playerView.useController = false

        val mediaItem = MediaItem.fromUri(
            Uri.parse("https://www.example.com/sample.mp4")
        )
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        player.playbackParameters = PlaybackParameters(1.0f)

        // مستمع أحداث المشغل
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayPauseIcon(isPlaying)
            }
        })
    }

    // ──────────────── أزرار التحكم ────────────────
    private fun setupControls() {

        btnPlayPause.setOnClickListener {
            animateButtonClick(it)
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
            resetHideTimer()
        }

        btnForward.setOnClickListener {
            animateButtonClick(it)
            player.seekTo(player.currentPosition + 10_000L)
            resetHideTimer()
        }

        btnRewind.setOnClickListener {
            animateButtonClick(it)
            player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
            resetHideTimer()
        }

        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
            resetHideTimer()
        }

        // النقر على الشاشة يظهر/يخفي أدوات التحكم
        playerView.setOnClickListener {
            if (controlsVisible) {
                hideControls()
            } else {
                showControls()
            }
        }
    }

    // ──────────────── شريط التقدم ────────────────
    private fun setupSeekBar() {
        seekBar.max = 1000

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = player.duration
                    if (duration > 0) {
                        val newPosition = (duration * progress / 1000L)
                        player.seekTo(newPosition)
                        updateProgressBarUI(progress, 1000)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isDraggingSeekBar = true
                handler.removeCallbacks(hideControlsRunnable)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isDraggingSeekBar = false
                resetHideTimer()
            }
        })
    }

    // ──────────────── تحديث التقدم ────────────────
    private val progressUpdater = object : Runnable {
        override fun run() {
            if (!isDraggingSeekBar) {
                val position = player.currentPosition
                val duration = player.duration

                if (duration > 0) {
                    val progress = (position * 1000L / duration).toInt()
                    seekBar.progress = progress
                    updateProgressBarUI(progress, 1000)
                }

                tvCurrentTime.text = formatTime(position)
                tvDuration.text = formatTime(if (duration > 0) duration else 0)
            }
            handler.postDelayed(this, 500)
        }
    }

    private fun startProgressUpdater() {
        handler.post(progressUpdater)
    }

    private fun updateProgressBarUI(progress: Int, max: Int) {
        progressFill.post {
            val parentWidth = (progressFill.parent as? View)?.width ?: return@post
            val fillWidth = (parentWidth * progress / max)
            val params = progressFill.layoutParams
            params.width = fillWidth.coerceAtLeast(0)
            progressFill.layoutParams = params

            // تحريك المؤشر الدائري
            val thumbParams = progressThumb.layoutParams as? ViewGroup.MarginLayoutParams
            thumbParams?.marginStart = (fillWidth - 7.dpToPx()).coerceAtLeast(0)
            progressThumb.layoutParams = thumbParams
        }
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()

    // ──────────────── الإيماءات (صوت + سطوع) ────────────────
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
                    val percent = -distanceY / 800f

                    if (x < screenWidth / 2) {
                        adjustBrightness(percent)
                    } else {
                        adjustVolume(percent)
                    }
                    return true
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (controlsVisible) hideControls() else showControls()
                    return true
                }
            })

        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    // ──────────────── التحكم بالصوت مع Overlay ────────────────
    private fun adjustVolume(fraction: Float) {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val change = (fraction * max).toInt()
        val newVolume = (current + change).coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)

        val percent = if (max > 0) (newVolume * 100 / max) else 0
        showVolumeOverlay(percent)
        resetHideTimer()
    }

    private fun showVolumeOverlay(percent: Int) {
        tvVolumePercent.text = "$percent%"

        // تحديث ارتفاع شريط الصوت
        val barParentHeight = 120.dpToPx()
        val fillHeight = (barParentHeight * percent / 100).coerceIn(0, barParentHeight)
        val p = volumeBar.layoutParams
        p.height = fillHeight
        volumeBar.layoutParams = p

        if (volumeOverlay.visibility != View.VISIBLE) {
            volumeOverlay.visibility = View.VISIBLE
            volumeOverlay.alpha = 0f
            volumeOverlay.animate().alpha(1f).setDuration(200).start()
        }
        scheduleHideOverlay(volumeOverlay)
    }

    // ──────────────── التحكم بالسطوع مع Overlay ────────────────
    private fun adjustBrightness(fraction: Float) {
        val layoutParams = window.attributes
        var brightness = layoutParams.screenBrightness
        if (brightness < 0f) brightness = 0.5f
        brightness = (brightness + fraction).coerceIn(0.01f, 1f)
        layoutParams.screenBrightness = brightness
        window.attributes = layoutParams

        val percent = (brightness * 100).toInt()
        showBrightnessOverlay(percent)
        resetHideTimer()
    }

    private fun showBrightnessOverlay(percent: Int) {
        tvBrightnessPercent.text = "$percent%"

        val barParentHeight = 120.dpToPx()
        val fillHeight = (barParentHeight * percent / 100).coerceIn(0, barParentHeight)
        val p = brightnessBar.layoutParams
        p.height = fillHeight
        brightnessBar.layoutParams = p

        if (brightnessOverlay.visibility != View.VISIBLE) {
            brightnessOverlay.visibility = View.VISIBLE
            brightnessOverlay.alpha = 0f
            brightnessOverlay.animate().alpha(1f).setDuration(200).start()
        }
        scheduleHideOverlay(brightnessOverlay)
    }

    private fun scheduleHideOverlay(overlay: View) {
        overlayHideRunnable?.let { handler.removeCallbacks(it) }
        overlayHideRunnable = Runnable {
            overlay.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction { overlay.visibility = View.INVISIBLE }
                .start()
        }
        handler.postDelayed(overlayHideRunnable!!, 1500)
    }

    // ──────────────── إظهار/إخفاء أدوات التحكم بأنيميشن ────────────────
    private fun showControls() {
        if (controlsVisible) return
        controlsVisible = true

        playerControls.visibility = View.VISIBLE
        topBar.visibility = View.VISIBLE

        playerControls.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .start()

        topBar.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .start()

        scheduleHideControls()
    }

    private fun hideControls() {
        if (!controlsVisible) return
        controlsVisible = false

        playerControls.animate()
            .alpha(0f)
            .translationY(60f)
            .setDuration(300)
            .withEndAction { playerControls.visibility = View.INVISIBLE }
            .start()

        topBar.animate()
            .alpha(0f)
            .translationY(-40f)
            .setDuration(300)
            .withEndAction { topBar.visibility = View.INVISIBLE }
            .start()
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, hideControlsDelay)
    }

    private fun resetHideTimer() {
        if (!controlsVisible) showControls()
        else scheduleHideControls()
    }

    // ──────────────── تحديث أيقونة تشغيل/إيقاف ────────────────
    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    // ──────────────── أنيميشن نقرة الزر ────────────────
    private fun animateButtonClick(view: View) {
        view.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(80)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120)
                    .start()
            }
            .start()
    }

    // ──────────────── تنسيق الوقت ────────────────
    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    // ──────────────── دورة حياة التطبيق ────────────────
    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onResume() {
        super.onResume()
        if (player.playbackState != Player.STATE_ENDED) {
            player.play()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.release()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
