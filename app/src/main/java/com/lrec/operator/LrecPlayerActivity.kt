package com.lrec.operator

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * ══════════════════════════════════════════════════════════════════
 *  LrecPlayerActivity — مشغل ملفات .lrec  (النسخة المُصحَّحة)
 *
 *  الإصلاحات في هذه النسخة:
 *  ✅ keepScreenOn — الشاشة لا تُطفأ أثناء التشغيل
 *  ✅ ContextCompat.getColor — بدلاً من resources.getColor المهجورة
 *  ✅ Bitmap يُنشأ بالأبعاد الحقيقية 1187×834 من LrecParser
 *  ✅ إعادة رسم الإطار بعد تدوير الشاشة بدون إعادة تحليل الملف
 *  ✅ تنظيف Thread عند onDestroy لمنع memory leak
 * ══════════════════════════════════════════════════════════════════
 */
class LrecPlayerActivity : AppCompatActivity(), SurfaceHolder.Callback {

    // ── عناصر الواجهة ─────────────────────────────────────────────
    private lateinit var lrecDrawerLayout:   DrawerLayout
    private lateinit var chatDrawerPanel:    View
    private lateinit var btnCloseChatDrawer: ImageButton
    private lateinit var chatRecyclerView:   RecyclerView
    private lateinit var tvNoChatMsg:        TextView

    private lateinit var surfaceView:        SurfaceView
    private lateinit var loadingLayout:      View
    private lateinit var tvLoadingMsg:       TextView
    private lateinit var tvLoadingDetail:    TextView
    private lateinit var errorLayout:        View
    private lateinit var tvErrorMsg:         TextView
    private lateinit var tvErrorDetail:      TextView
    private lateinit var lrecTopBar:         View
    private lateinit var lrecPlayerControls: View
    private lateinit var btnBack:            ImageButton
    private lateinit var btnPlayPause:       ImageButton
    private lateinit var btnForward:         ImageButton
    private lateinit var btnRewind:          ImageButton
    private lateinit var btnChat:            ImageButton
    private lateinit var btnRotate:          ImageButton
    private lateinit var seekBar:            SeekBar
    private lateinit var progressFill:       View
    private lateinit var progressThumb:      View
    private lateinit var tvCurrentTime:      TextView
    private lateinit var tvDuration:         TextView
    private lateinit var tvTitle:            TextView
    private lateinit var volumeOverlay:      LinearLayout
    private lateinit var volumeBar:          View
    private lateinit var tvVolumePercent:    TextView
    private lateinit var brightnessOverlay:  LinearLayout
    private lateinit var brightnessBar:      View
    private lateinit var tvBrightnessPercent: TextView

    // ── حالة المشغل ───────────────────────────────────────────────
    private var parser:       LrecParser?              = null
    private var screenFrames = listOf<LrecParser.LrecFrame>()
    private var chatMessages = listOf<ChatMessage>()

    private var isPlaying         = false
    private var currentFrameIndex = 0
    private var surfaceReady      = false
    private var controlsVisible   = true
    private var isLandscape       = true
    private var isDraggingSeekBar = false

    // ✅ Bitmap يُنشأ بالأبعاد الحقيقية من LrecParser بعد التحليل
    private var screenBitmap: Bitmap? = null
    private val bitmapPaint  = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destRect     = Rect()

    // ── خيط تحميل الملف ──────────────────────────────────────────
    @Volatile private var loadingThread: Thread? = null

    // ── AudioManager للتحكم بصوت الجهاز ──────────────────────────
    private lateinit var audioManager: android.media.AudioManager

    // ── Handlers وتوقيت الإخفاء ───────────────────────────────────
    private val handler        = Handler(Looper.getMainLooper())
    private val FRAME_INTERVAL = 200L       // 5fps
    private val HIDE_DELAY     = 120_000L   // دقيقتان

    private val playbackRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying) return
            if (currentFrameIndex < screenFrames.size) {
                renderFrame(currentFrameIndex)
                currentFrameIndex++
                handler.postDelayed(this, FRAME_INTERVAL)
                updateProgressUI()
            } else {
                onPlaybackEnded()
            }
        }
    }

    private val hideRunnable    = Runnable { hideControls() }
    private var overlayRunnable: Runnable? = null

    // ── إيماءات ───────────────────────────────────────────────────
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var gestureType   = GestureType.NONE
    private enum class GestureType { NONE, VOLUME, BRIGHTNESS }

    data class ChatMessage(val timeMs: Long, val text: String)

    // ══════════════════════════════════════════════════════════════
    //  onCreate
    // ══════════════════════════════════════════════════════════════
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // تطبيق الثيم
        val prefs  = getSharedPreferences("lrec_prefs", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        // ✅ إبقاء الشاشة مضاءة أثناء التشغيل
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // ملء الشاشة
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN       or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION  or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE    or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        setContentView(R.layout.activity_lrec_player)
        audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager

        bindViews()
        setupControls()
        setupSeekBar()
        setupTouchGestures()
        setupChatDrawer()

        handleIncomingIntent(intent)
    }

    // ── ربط العناصر ──────────────────────────────────────────────
    private fun bindViews() {
        lrecDrawerLayout    = findViewById(R.id.lrecDrawerLayout)
        chatDrawerPanel     = findViewById(R.id.chatDrawerPanel)
        btnCloseChatDrawer  = findViewById(R.id.btnCloseChatDrawer)
        chatRecyclerView    = findViewById(R.id.chatRecyclerView)
        tvNoChatMsg         = findViewById(R.id.tvNoChatMsg)
        surfaceView         = findViewById(R.id.lrecSurfaceView)
        loadingLayout       = findViewById(R.id.loadingLayout)
        tvLoadingMsg        = findViewById(R.id.tvLoadingMsg)
        tvLoadingDetail     = findViewById(R.id.tvLoadingDetail)
        errorLayout         = findViewById(R.id.errorLayout)
        tvErrorMsg          = findViewById(R.id.tvErrorMsg)
        tvErrorDetail       = findViewById(R.id.tvErrorDetail)
        lrecTopBar          = findViewById(R.id.lrecTopBar)
        lrecPlayerControls  = findViewById(R.id.lrecPlayerControls)
        btnBack             = findViewById(R.id.lrecBtnBack)
        btnPlayPause        = findViewById(R.id.lrecBtnPlayPause)
        btnForward          = findViewById(R.id.lrecBtnForward)
        btnRewind           = findViewById(R.id.lrecBtnRewind)
        btnChat             = findViewById(R.id.lrecBtnChat)
        btnRotate           = findViewById(R.id.lrecBtnRotate)
        seekBar             = findViewById(R.id.lrecSeekBar)
        progressFill        = findViewById(R.id.lrecProgressFill)
        progressThumb       = findViewById(R.id.lrecProgressThumb)
        tvCurrentTime       = findViewById(R.id.lrecTvCurrentTime)
        tvDuration          = findViewById(R.id.lrecTvDuration)
        tvTitle             = findViewById(R.id.lrecTvTitle)
        volumeOverlay       = findViewById(R.id.lrecVolumeOverlay)
        volumeBar           = findViewById(R.id.lrecVolumeBar)
        tvVolumePercent     = findViewById(R.id.tvLrecVolumePercent)
        brightnessOverlay   = findViewById(R.id.lrecBrightnessOverlay)
        brightnessBar       = findViewById(R.id.lrecBrightnessBar)
        tvBrightnessPercent = findViewById(R.id.tvLrecBrightnessPercent)

        surfaceView.holder.addCallback(this)
    }

    // ── إعداد قائمة المحادثة ─────────────────────────────────────
    private fun setupChatDrawer() {
        chatRecyclerView.layoutManager = LinearLayoutManager(this).also {
            it.stackFromEnd = true
        }
        chatRecyclerView.adapter = ChatAdapter(mutableListOf())

        btnCloseChatDrawer.setOnClickListener {
            lrecDrawerLayout.closeDrawer(Gravity.END)
        }

        lrecDrawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) { resetHideTimer() }
            override fun onDrawerClosed(drawerView: View) { resetHideTimer() }
        })
    }

    // ── معالجة الـ Intent ─────────────────────────────────────────
    private fun handleIncomingIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri == null) {
            showError("لم يتم تمرير ملف", "تأكد من فتح ملف .lrec صحيح")
            return
        }
        tvTitle.text = getFileName(uri)
        showLoading("جاري قراءة الملف...")
        val t = Thread { loadLrecFile(uri) }
        loadingThread = t
        t.start()
    }

    // ── تحميل الملف وتحليله ───────────────────────────────────────
    private fun loadLrecFile(uri: Uri) {
        try {
            runOnUiThread { tvLoadingMsg.text = "جاري نسخ الملف..." }

            val tempFile = File(cacheDir, "current_lrec.lrec")
            contentResolver.openInputStream(uri)?.use { inp ->
                FileOutputStream(tempFile).use { out -> inp.copyTo(out) }
            } ?: run {
                runOnUiThread { showError("تعذّر فتح الملف", "تأكد من صلاحيات الوصول") }
                return
            }

            runOnUiThread { tvLoadingMsg.text = "جاري تحليل الإطارات..." }

            val p       = LrecParser(tempFile)
            val success = p.parse()

            if (!success || p.getAllFrames().isEmpty()) {
                runOnUiThread {
                    showError(
                        "الملف غير متوافق",
                        "تأكد أنه ملف .lrec صادر من Inter-Tel Collaboration Client"
                    )
                }
                return
            }

            parser       = p
            screenFrames = p.getScreenFrames()
            chatMessages = p.getChatFrames().mapNotNull { frame ->
                p.decodeChatFrame(frame)?.let { ChatMessage(frame.timestamp, it) }
            }

            // ✅ أبعاد الـ Bitmap من الـ parser مباشرة — لا تخمين
            val w = p.metadata.screenWidth.coerceAtLeast(1)
            val h = p.metadata.screenHeight.coerceAtLeast(1)
            screenBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                .also { it.eraseColor(Color.BLACK) }

            val durationStr = formatTime(p.getDurationMs())

            runOnUiThread {
                tvLoadingDetail.text = "${screenFrames.size} إطار — $durationStr"
                tvDuration.text      = durationStr

                val adapter = chatRecyclerView.adapter as? ChatAdapter
                adapter?.updateMessages(chatMessages)

                if (chatMessages.isEmpty()) {
                    chatRecyclerView.visibility = View.GONE
                    tvNoChatMsg.visibility      = View.VISIBLE
                } else {
                    chatRecyclerView.visibility = View.VISIBLE
                    tvNoChatMsg.visibility      = View.GONE
                }

                hideLoading()
                showControls()
                if (surfaceReady) startPlayback()
            }

        } catch (e: Exception) {
            runOnUiThread {
                showError("خطأ أثناء القراءة", e.localizedMessage ?: "خطأ غير متوقع")
            }
        } finally {
            loadingThread = null
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  SurfaceHolder Callbacks
    // ══════════════════════════════════════════════════════════════
    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        if (screenFrames.isNotEmpty() && parser != null) startPlayback()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // ✅ عند تدوير الشاشة: أعد رسم الإطار الحالي بدون إعادة التحليل
        if (screenBitmap != null && !isPlaying) renderCurrentState()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
    }

    // ══════════════════════════════════════════════════════════════
    //  منطق التشغيل
    // ══════════════════════════════════════════════════════════════
    private fun startPlayback() {
        if (isPlaying || screenFrames.isEmpty()) return
        isPlaying = true
        btnPlayPause.setImageResource(R.drawable.ic_pause)
        handler.post(playbackRunnable)
        scheduleHide()
    }

    private fun pausePlayback() {
        isPlaying = false
        btnPlayPause.setImageResource(R.drawable.ic_play)
        handler.removeCallbacks(playbackRunnable)
    }

    private fun onPlaybackEnded() {
        isPlaying = false
        btnPlayPause.setImageResource(R.drawable.ic_play)
        currentFrameIndex = (screenFrames.size - 1).coerceAtLeast(0)
        updateProgressUI()
        showControls()
    }

    private fun renderFrame(idx: Int) {
        if (idx < 0 || idx >= screenFrames.size) return
        val frame  = screenFrames[idx]
        val bitmap = screenBitmap ?: return
        val p      = parser ?: return

        val frameData = p.decodeScreenFrame(frame)
        if (frameData != null) p.applyFrameToBitmap(bitmap, frameData)

        drawBitmapToSurface(bitmap)
    }

    private fun renderCurrentState() {
        screenBitmap?.let { drawBitmapToSurface(it) }
    }

    private fun drawBitmapToSurface(bitmap: Bitmap) {
        if (!surfaceReady) return
        val canvas: Canvas = surfaceView.holder.lockCanvas() ?: return
        try {
            canvas.drawColor(Color.BLACK)
            val sw    = surfaceView.width.toFloat()
            val sh    = surfaceView.height.toFloat()
            val bw    = bitmap.width.toFloat()
            val bh    = bitmap.height.toFloat()
            if (sw == 0f || sh == 0f || bw == 0f || bh == 0f) return
            val scale = minOf(sw / bw, sh / bh)
            val dw    = (bw * scale).toInt()
            val dh    = (bh * scale).toInt()
            val left  = ((sw - dw) / 2).toInt()
            val top   = ((sh - dh) / 2).toInt()
            destRect.set(left, top, left + dw, top + dh)
            canvas.drawBitmap(bitmap, null, destRect, bitmapPaint)
        } finally {
            surfaceView.holder.unlockCanvasAndPost(canvas)
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  أزرار التحكم
    // ══════════════════════════════════════════════════════════════
    private fun setupControls() {
        btnBack.setOnClickListener { finish() }

        btnPlayPause.setOnClickListener {
            pulse(it)
            if (isPlaying) pausePlayback()
            else {
                if (currentFrameIndex >= screenFrames.size) currentFrameIndex = 0
                startPlayback()
            }
            resetHideTimer()
        }

        btnForward.setOnClickListener { pulse(it); seekByFrames(+25); resetHideTimer() }
        btnRewind.setOnClickListener  { pulse(it); seekByFrames(-25); resetHideTimer() }

        btnChat.setOnClickListener {
            if (lrecDrawerLayout.isDrawerOpen(Gravity.END))
                lrecDrawerLayout.closeDrawer(Gravity.END)
            else
                lrecDrawerLayout.openDrawer(Gravity.END)
        }

        btnRotate.setOnClickListener {
            pulse(it)
            isLandscape = !isLandscape
            requestedOrientation = if (isLandscape)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            resetHideTimer()
        }
    }

    private fun seekByFrames(delta: Int) {
        val wasPlaying = isPlaying
        if (wasPlaying) pausePlayback()
        currentFrameIndex = (currentFrameIndex + delta)
            .coerceIn(0, (screenFrames.size - 1).coerceAtLeast(0))
        renderFrame(currentFrameIndex)
        updateProgressUI()
        if (wasPlaying) startPlayback()
    }

    private fun seekToProgress(progress: Int) {
        val total = screenFrames.size
        if (total == 0) return
        val target     = (progress.toLong() * total / 1000L).toInt().coerceIn(0, total - 1)
        val wasPlaying = isPlaying
        if (wasPlaying) pausePlayback()
        currentFrameIndex = target
        renderFrame(currentFrameIndex)
        updateProgressUI()
        if (wasPlaying) startPlayback()
    }

    // ── شريط التقدم ───────────────────────────────────────────────
    private fun setupSeekBar() {
        seekBar.max = 1000
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) updateProgressFillOnly(p, 1000)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {
                isDraggingSeekBar = true
                handler.removeCallbacks(hideRunnable)
            }
            override fun onStopTrackingTouch(sb: SeekBar) {
                isDraggingSeekBar = false
                seekToProgress(sb.progress)
                resetHideTimer()
            }
        })
    }

    private fun updateProgressUI() {
        if (isDraggingSeekBar) return
        val total = screenFrames.size
        if (total == 0) return
        val prog = (currentFrameIndex * 1000L / total).toInt()
        seekBar.progress   = prog
        updateProgressFillOnly(prog, 1000)
        tvCurrentTime.text = formatTime(currentFrameIndex.toLong() * LrecParser.MS_PER_FRAME)
        tvDuration.text    = formatTime(parser?.getDurationMs() ?: 0L)
    }

    private fun updateProgressFillOnly(progress: Int, max: Int) {
        progressFill.post {
            val pw = (progressFill.parent as? View)?.width ?: return@post
            val fw = (pw * progress / max).coerceAtLeast(0)
            progressFill.layoutParams.width = fw
            progressFill.requestLayout()
            val tp = progressThumb.layoutParams as? ViewGroup.MarginLayoutParams
            tp?.marginStart = (fw - 7.dp).coerceAtLeast(0)
            progressThumb.layoutParams = tp
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  إيماءات اللمس
    // ══════════════════════════════════════════════════════════════
    private fun setupTouchGestures() {
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (lrecDrawerLayout.isDrawerOpen(Gravity.END)) return false
                if (controlsVisible) hideControls() else showControls()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                pulse(btnPlayPause)
                if (isPlaying) pausePlayback() else startPlayback()
                return true
            }
        })

        surfaceView.setOnTouchListener { _, event ->
            gd.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gestureStartX = event.x
                    gestureStartY = event.y
                    gestureType   = GestureType.NONE
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - gestureStartY
                    if (gestureType == GestureType.NONE && abs(dy) > 15f) {
                        gestureType = if (gestureStartX < resources.displayMetrics.widthPixels / 2f)
                            GestureType.BRIGHTNESS else GestureType.VOLUME
                    }
                    when (gestureType) {
                        GestureType.VOLUME     -> { applyVolumeDelta(-dy / 600f); gestureStartY = event.y; true }
                        GestureType.BRIGHTNESS -> { applyBrightnessDelta(-dy / 600f); gestureStartY = event.y; true }
                        else -> false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { gestureType = GestureType.NONE; false }
                else -> false
            }
        }
    }

    // ── ضبط الصوت ─────────────────────────────────────────────────
    private fun applyVolumeDelta(delta: Float) {
        val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val newVol = (curVol + (delta * maxVol * 0.5f).toInt()).coerceIn(0, maxVol)
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0)
        val pct = if (maxVol > 0) newVol * 100 / maxVol else 0
        tvVolumePercent.text          = "$pct%"
        volumeBar.layoutParams.height = (110.dp * pct / 100).coerceIn(0, 110.dp)
        volumeBar.requestLayout()
        showOverlay(volumeOverlay)
    }

    // ── ضبط السطوع ────────────────────────────────────────────────
    private fun applyBrightnessDelta(delta: Float) {
        val lp = window.attributes
        var b  = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
        b      = (b + delta).coerceIn(0.01f, 1f)
        lp.screenBrightness = b
        window.attributes   = lp
        val pct = (b * 100).toInt()
        tvBrightnessPercent.text          = "$pct%"
        brightnessBar.layoutParams.height = (110.dp * pct / 100).coerceIn(0, 110.dp)
        brightnessBar.requestLayout()
        showOverlay(brightnessOverlay)
    }

    private fun showOverlay(overlay: View) {
        if (overlay.visibility != View.VISIBLE) {
            overlay.visibility = View.VISIBLE
            overlay.alpha      = 0f
            overlay.animate().alpha(1f).setDuration(150).start()
        }
        overlayRunnable?.let { handler.removeCallbacks(it) }
        overlayRunnable = Runnable {
            overlay.animate().alpha(0f).setDuration(300)
                .withEndAction { overlay.visibility = View.INVISIBLE }.start()
        }
        handler.postDelayed(overlayRunnable!!, 1800)
    }

    // ══════════════════════════════════════════════════════════════
    //  إظهار / إخفاء أزرار التحكم
    //  1 — تلقائي بعد دقيقتين
    //  2 — نقرة واحدة تُظهر أو تُخفي فوراً
    // ══════════════════════════════════════════════════════════════
    private fun showControls() {
        if (controlsVisible) { resetHideTimer(); return }
        controlsVisible = true
        listOf(lrecTopBar, lrecPlayerControls).forEach { v ->
            v.visibility = View.VISIBLE
            v.animate().alpha(1f).translationY(0f).setDuration(200).start()
        }
        scheduleHide()
    }

    private fun hideControls() {
        if (!controlsVisible) return
        controlsVisible = false
        lrecPlayerControls.animate().alpha(0f).translationY(50f).setDuration(250)
            .withEndAction { lrecPlayerControls.visibility = View.INVISIBLE }.start()
        lrecTopBar.animate().alpha(0f).translationY(-35f).setDuration(250)
            .withEndAction { lrecTopBar.visibility = View.INVISIBLE }.start()
    }

    private fun scheduleHide()   { handler.removeCallbacks(hideRunnable); handler.postDelayed(hideRunnable, HIDE_DELAY) }
    private fun resetHideTimer() { if (!controlsVisible) showControls() else scheduleHide() }

    // ── شاشة التحميل والخطأ ──────────────────────────────────────
    private fun showLoading(msg: String) {
        loadingLayout.visibility = View.VISIBLE
        errorLayout.visibility   = View.GONE
        tvLoadingMsg.text        = msg
        tvLoadingDetail.text     = ""
    }

    private fun hideLoading() {
        loadingLayout.animate().alpha(0f).setDuration(300)
            .withEndAction { loadingLayout.visibility = View.GONE }.start()
    }

    private fun showError(title: String, detail: String) {
        loadingLayout.visibility = View.GONE
        errorLayout.visibility   = View.VISIBLE
        tvErrorMsg.text          = title
        tvErrorDetail.text       = detail
    }

    // ── مساعدات ───────────────────────────────────────────────────
    private fun getFileName(uri: Uri): String {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            } ?: uri.lastPathSegment ?: "محاضرة .lrec"
        } catch (e: Exception) { uri.lastPathSegment ?: "محاضرة .lrec" }
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec)
        else String.format("%02d:%02d", m, sec)
    }

    private fun pulse(v: View) {
        v.animate().scaleX(0.82f).scaleY(0.82f).setDuration(70).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        }.start()
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    // ── دورة حياة Activity ───────────────────────────────────────
    override fun onPause()  { super.onPause();  pausePlayback() }

    override fun onResume() {
        super.onResume()
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN       or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION  or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE    or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        // ✅ إيقاف خيط التحميل إن كان يعمل
        loadingThread?.interrupt()
        loadingThread = null
        screenBitmap?.recycle()
        screenBitmap = null
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (lrecDrawerLayout.isDrawerOpen(Gravity.END))
            lrecDrawerLayout.closeDrawer(Gravity.END)
        else
            super.onBackPressed()
    }
}

// ══════════════════════════════════════════════════════════════════
//  Adapter قائمة المحادثة
//  ✅ يستخدم ContextCompat.getColor بدلاً من resources.getColor المهجورة
// ══════════════════════════════════════════════════════════════════
class ChatAdapter(
    private val messages: MutableList<LrecPlayerActivity.ChatMessage>
) : RecyclerView.Adapter<ChatAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTime: TextView = v.findViewById(android.R.id.text1)
        val tvText: TextView = v.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        v.setPadding(8, 8, 8, 8)
        // ✅ ContextCompat.getColor لا يحتاج API level check
        v.findViewById<TextView>(android.R.id.text1).apply {
            setTextColor(ContextCompat.getColor(context, R.color.lib_text_secondary))
            textSize = 10f
        }
        v.findViewById<TextView>(android.R.id.text2).apply {
            setTextColor(ContextCompat.getColor(context, R.color.lib_text_primary))
            textSize = 13f
        }
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = messages[position]
        val s   = msg.timeMs / 1000
        holder.tvTime.text = String.format("%02d:%02d", s / 60, s % 60)
        holder.tvText.text = msg.text
    }

    override fun getItemCount() = messages.size

    fun updateMessages(newMessages: List<LrecPlayerActivity.ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }
}
