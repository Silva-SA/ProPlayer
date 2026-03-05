package com.lrec.operator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class VideoItem(
    val id: Long,
    val title: String,
    val duration: Long,
    val size: Long,
    val uri: Uri,
    val thumbnailUri: Uri?
)

class VideoLibraryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var tvVideoCount: TextView

    private val videoList = mutableListOf<VideoItem>()
    private lateinit var adapter: VideoAdapter

    companion object {
        const val REQUEST_PERMISSION = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // وضع ملء الشاشة
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        setContentView(R.layout.activity_library)
        initViews()
        checkPermissionAndLoad()
    }

    private fun initViews() {
        recyclerView  = findViewById(R.id.recyclerView)
        tvEmpty       = findViewById(R.id.tvEmpty)
        progressBar   = findViewById(R.id.progressBar)
        btnRefresh    = findViewById(R.id.btnRefresh)
        tvVideoCount  = findViewById(R.id.tvVideoCount)

        // إعداد الـ RecyclerView بشبكة عمودين
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        adapter = VideoAdapter(videoList) { video -> openVideo(video) }
        recyclerView.adapter = adapter

        btnRefresh.setOnClickListener {
            animateRotate(it)
            checkPermissionAndLoad()
        }
    }

    private fun animateRotate(v: View) {
        v.animate().rotationBy(360f).setDuration(500).start()
    }

    // ─── الأذونات ──────────────────────────────────────────────────
    private fun checkPermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_VIDEO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadVideos()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadVideos()
        } else {
            tvEmpty.text = "يجب منح إذن الوصول للملفات\nاضغط تحديث للمحاولة مجدداً"
            tvEmpty.visibility = View.VISIBLE
        }
    }

    // ─── تحميل الفيديوهات ─────────────────────────────────────────
    private fun loadVideos() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        recyclerView.visibility = View.GONE

        Thread {
            val videos = queryVideos()
            runOnUiThread {
                progressBar.visibility = View.GONE
                videoList.clear()
                videoList.addAll(videos)
                adapter.notifyDataSetChanged()

                if (videoList.isEmpty()) {
                    tvEmpty.text = "لا توجد فيديوهات في الجهاز"
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    tvVideoCount.text = "${videoList.size} فيديو"
                }
            }
        }.start()
    }

    private fun queryVideos(): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATA
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val cursor: Cursor? = contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )

        cursor?.use {
            val idCol       = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleCol    = it.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol     = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            while (it.moveToNext()) {
                val id       = it.getLong(idCol)
                val title    = it.getString(titleCol) ?: "فيديو"
                val duration = it.getLong(durationCol)
                val size     = it.getLong(sizeCol)
                val uri      = Uri.withAppendedPath(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString()
                )
                list.add(VideoItem(id, title, duration, size, uri, null))
            }
        }
        return list
    }

    // ─── فتح المشغل ───────────────────────────────────────────────
    private fun openVideo(video: VideoItem) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = video.uri
            putExtra("VIDEO_TITLE", video.title)
        }
        startActivity(intent)
    }
}

// ─── Adapter قائمة الفيديوهات ─────────────────────────────────────
class VideoAdapter(
    private val items: List<VideoItem>,
    private val onClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView    = view.findViewById(R.id.tvVideoTitle)
        val tvDuration: TextView = view.findViewById(R.id.tvVideoDuration)
        val tvSize: TextView     = view.findViewById(R.id.tvVideoSize)
        val ivThumb: ImageView   = view.findViewById(R.id.ivThumbnail)
        val card: View           = view.findViewById(R.id.cardRoot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val video = items[position]
        holder.tvTitle.text = video.title
        holder.tvDuration.text = formatDuration(video.duration)
        holder.tvSize.text = formatSize(video.size)

        holder.card.setOnClickListener {
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                onClick(video)
            }.start()
        }
    }

    override fun getItemCount() = items.size

    private fun formatDuration(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec)
        else String.format("%02d:%02d", m, sec)
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
            else -> String.format("%.0f KB", bytes / 1024.0)
        }
    }
}
