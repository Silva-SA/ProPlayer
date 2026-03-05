package com.lrec.operator

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class SplashActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("lrec_prefs", MODE_PRIVATE)

        // تطبيق الثيم المحفوظ
        val isDark = prefs.getBoolean("dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        // ملء الشاشة
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        setContentView(R.layout.activity_splash)

        val tvBrand   = findViewById<TextView>(R.id.tvBrand)
        val tvMessage = findViewById<TextView>(R.id.tvMessage)
        val btnEnter  = findViewById<Button>(R.id.btnEnter)

        // أنيميشن ظهور
        val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 900 }
        val scaleIn = ScaleAnimation(
            0.85f, 1f, 0.85f, 1f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        ).apply { duration = 900 }

        val anim = AnimationSet(true).apply {
            addAnimation(fadeIn)
            addAnimation(scaleIn)
        }

        tvBrand.startAnimation(anim)

        Handler(Looper.getMainLooper()).postDelayed({
            tvMessage.visibility = View.VISIBLE
            tvMessage.startAnimation(fadeIn)
            btnEnter.visibility = View.VISIBLE
            btnEnter.startAnimation(fadeIn)
        }, 600)

        btnEnter.setOnClickListener {
            startActivity(Intent(this, VideoLibraryActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }
}
