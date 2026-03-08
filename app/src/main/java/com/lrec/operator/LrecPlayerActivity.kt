package com.lrec.operator

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File

class LrecPlayerActivity : AppCompatActivity() {

    // ── Views ──────────────────────────────────────────────────────────
    private lateinit var imageViewScreen : ImageView
    private lateinit var progressBar     : ProgressBar
    private lateinit var seekBar         : SeekBar
    private lateinit var btnPlay         : ImageButton
    private lateinit var btnPause        : ImageButton
    private lateinit var btnStop         : ImageButton
    private lateinit var tvCurrentTime   : TextView
    private lateinit var tvTotalTime     : TextView
    private lateinit var tvFrameInfo     : TextView
    private lateinit var layoutLoading   : View

    // ── الحالة ─────────────────────────────────────────────────────────
    private var parser       : LrecParser? = null
    private var frames       : List<LrecParser.LrecFrame> = emptyList()
    private var screenBitmap : Bitmap? = null

    private var currentFrameIdx = 0
    private var isPlaying       = false
    private var isParsed        = false

    private val handler        = Handler(Looper.getMainLooper())
    private var loadingThread  : Thread? = null

    // ── الثوابت ────────────────────────────────────────────────────────
    private val FRAME_INTERVAL_MS = LrecParser.MS_PER_FRAME // 200ms (5fps)

    // ══════════════════════════════════════════════════════════════════
    //  onCreate
    // ══════════════════════════════════════════════════════════════════
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lrec_player)

        // يمنع إطفاء الشاشة أثناء التشغيل
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        bindViews()
        setupControls()

        // اسم الملف من Intent
        val fileName = intent.getStringExtra("file_name") ?: run {
            showError("لم يتم تمرير اسم الملف")
            return
        }

        val filePath = getFileFromUri(fileName)
        if (filePath == null) {
            showError("لا يمكن فتح الملف: $fileName")
            return
        }

        loadFile(filePath)
    }

    override fun onDestroy() {
        super.onDestroy()
        isPlaying = false
        handler.removeCallbacksAndMessages(null)

        // تنظيف thread التحميل
        loadingThread?.interrupt()
        loadingThread = null

        screenBitmap?.recycle()
        screenBitmap = null

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        pausePlayback()
    }

    // ══════════════════════════════════════════════════════════════════
    //  ربط الـ Views
    // ══════════════════════════════════════════════════════════════════
    private fun bindViews() {
        imageViewScreen  = findViewById(R.id.imageViewScreen)
        progressBar      = findViewById(R.id.progressBar)
        seekBar          = findViewById(R.id.seekBar)
        btnPlay          = findViewById(R.id.btnPlay)
        btnPause         = findViewById(R.id.btnPause)
        btnStop          = findViewById(R.id.btnStop)
        tvCurrentTime    = findViewById(R.id.tvCurrentTime)
        tvTotalTime      = findViewById(R.id.tvTotalTime)
        tvFrameInfo      = findViewById(R.id.tvFrameInfo)
        layoutLoading    = findViewById(R.id.layoutLoading)
    }

    // ══════════════════════════════════════════════════════════════════
    //  إعداد أزرار التحكم
    // ══════════════════════════════════════════════════════════════════
    private fun setupControls() {
        btnPlay.setOnClickListener  { startPlayback() }
        btnPause.setOnClickListener { pausePlayback() }
        btnStop.setOnClickListener  { stopPlayback() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && isParsed) {
                    val wasPlaying = isPlaying
                    if (wasPlaying) pausePlayback()
                    currentFrameIdx = ((progress / 100.0) * (frames.size - 1)).toInt()
                        .coerceIn(0, frames.lastIndex)
                    renderCurrentFrame()
                    updateTimeDisplay()
                    if (wasPlaying) startPlayback()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar)  {}
        })

        setControlsEnabled(false)
    }

    // ══════════════════════════════════════════════════════════════════
    //  تحميل الملف
    // ══════════════════════════════════════════════════════════════════
    private fun loadFile(file: File) {
        layoutLoading.visibility = View.VISIBLE
        progressBar.visibility   = View.VISIBLE

        loadingThread = Thread {
            try {
                val p = LrecParser(file)
                val ok = p.parse()

                if (!ok || p.getTotalFrames() == 0) {
                    handler.post { showError("فشل تحليل الملف أو لا يحتوي على إطارات") }
                    return@Thread
                }

                parser = p
                frames = p.getScreenFrames()

                // أنشئ الـ bitmap بالأبعاد الحقيقية
                val bmp = Bitmap.createBitmap(
                    p.metadata.screenWidth,
                    p.metadata.screenHeight,
                    Bitmap.Config.ARGB_8888
                )

                // ارسم الإطار الأول فوراً
                val firstFrame = p.decodeScreenFrame(frames[0])
                if (firstFrame != null) {
                    p.applyFrameToBitmap(bmp, firstFrame)
                }

                screenBitmap = bmp

                handler.post {
                    isParsed = true
                    layoutLoading.visibility = View.GONE
                    progressBar.visibility   = View.GONE

                    imageViewScreen.setImageBitmap(bmp)

                    val durationMs = p.getDurationMs()
                    tvTotalTime.text   = formatTime(durationMs)
                    tvFrameInfo.text   = "الإطارات: ${frames.size} | ${p.metadata.screenWidth}×${p.metadata.screenHeight}"
                    seekBar.max        = 100

                    setControlsEnabled(true)
                    updateTimeDisplay()
                }
            } catch (e: InterruptedException) {
                // thread أُلغي عند onDestroy
            } catch (e: Exception) {
                handler.post { showError("خطأ: ${e.message}") }
            }
        }.also { it.start() }
    }

    // ══════════════════════════════════════════════════════════════════
    //  التشغيل / الإيقاف
    // ══════════════════════════════════════════════════════════════════
    private fun startPlayback() {
        if (!isParsed || frames.isEmpty() || isPlaying) return
        if (currentFrameIdx >= frames.lastIndex) currentFrameIdx = 0
        isPlaying = true
        scheduleNextFrame()
    }

    private fun pausePlayback() {
        isPlaying = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun stopPlayback() {
        isPlaying = false
        handler.removeCallbacksAndMessages(null)
        currentFrameIdx = 0
        renderCurrentFrame()
        updateTimeDisplay()
        seekBar.progress = 0
    }

    // ── Runnable التشغيل ──────────────────────────────────────────────
    private val playbackRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying || currentFrameIdx >= frames.size) {
                isPlaying = false
                return
            }

            renderCurrentFrame()
            updateTimeDisplay()

            currentFrameIdx++
            if (currentFrameIdx < frames.size) {
                handler.postDelayed(this, FRAME_INTERVAL_MS)
            } else {
                isPlaying = false
                currentFrameIdx = frames.lastIndex
                updateTimeDisplay()
            }
        }
    }

    private fun scheduleNextFrame() {
        handler.post(playbackRunnable)
    }

    // ══════════════════════════════════════════════════════════════════
    //  رسم الإطار الحالي
    // ══════════════════════════════════════════════════════════════════
    private fun renderCurrentFrame() {
        val bmp = screenBitmap ?: return
        val p   = parser       ?: return
        if (currentFrameIdx !in frames.indices) return

        val frame = frames[currentFrameIdx]
        val data  = p.decodeScreenFrame(frame) ?: return
        p.applyFrameToBitmap(bmp, data)

        // تحديث الـ ImageView دون إعادة إنشاء bitmap
        imageViewScreen.invalidate()
        imageViewScreen.setImageBitmap(bmp)
    }

    // ══════════════════════════════════════════════════════════════════
    //  تحديث شريط التقدم والوقت
    // ══════════════════════════════════════════════════════════════════
    private fun updateTimeDisplay() {
        val p = parser ?: return
        val ts = if (currentFrameIdx in frames.indices)
            frames[currentFrameIdx].timestamp else 0L
        tvCurrentTime.text = formatTime(ts)

        val progress = if (frames.size > 1)
            ((currentFrameIdx.toFloat() / (frames.size - 1)) * 100).toInt()
        else 0
        seekBar.progress = progress
    }

    // ══════════════════════════════════════════════════════════════════
    //  مساعدات
    // ══════════════════════════════════════════════════════════════════
    private fun getFileFromUri(fileName: String): File? {
        // ابحث في المسارات الشائعة
        val paths = listOf(
            filesDir,
            getExternalFilesDir(null),
            cacheDir
        )
        for (dir in paths) {
            if (dir != null) {
                val f = File(dir, fileName)
                if (f.exists()) return f
            }
        }
        // محاولة كمسار مباشر
        val direct = File(fileName)
        if (direct.exists()) return direct
        return null
    }

    private fun setControlsEnabled(enabled: Boolean) {
        btnPlay.isEnabled  = enabled
        btnPause.isEnabled = enabled
        btnStop.isEnabled  = enabled
        seekBar.isEnabled  = enabled
    }

    private fun showError(msg: String) {
        layoutLoading.visibility = View.GONE
        progressBar.visibility   = View.GONE
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val h   = totalSec / 3600
        val m   = (totalSec % 3600) / 60
        val s   = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else               String.format("%02d:%02d", m, s)
    }
}
