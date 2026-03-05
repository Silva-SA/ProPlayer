package com.lrec.operator

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun attachBaseContext(newBase: android.content.Context) {
        val p    = newBase.getSharedPreferences("lrec_prefs", MODE_PRIVATE)
        val lang = p.getString("language", "ar") ?: "ar"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("lrec_prefs", MODE_PRIVATE)
        applyTheme()
        setContentView(R.layout.activity_settings)

        val btnBack      = findViewById<ImageButton>(R.id.btnBackSettings)
        val switchHidden = findViewById<Switch>(R.id.switchShowHidden)
        val switchDark   = findViewById<Switch>(R.id.switchDarkMode)
        val rgLanguage   = findViewById<RadioGroup>(R.id.rgLanguage)
        val rbArabic     = findViewById<RadioButton>(R.id.rbArabic)
        val rbEnglish    = findViewById<RadioButton>(R.id.rbEnglish)

        switchHidden.isChecked = prefs.getBoolean("show_hidden", false)
        switchDark.isChecked   = prefs.getBoolean("dark_mode", true)
        if (prefs.getString("language", "ar") == "en") rbEnglish.isChecked = true
        else rbArabic.isChecked = true

        btnBack.setOnClickListener { finish() }

        switchHidden.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("show_hidden", checked).apply()
            Toast.makeText(this,
                if (checked) "سيتم عرض الملفات المخفية" else "لن تُعرض الملفات المخفية",
                Toast.LENGTH_SHORT).show()
        }

        // تطبيق الثيم فوراً مع إعادة رسم الشاشة
        switchDark.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("dark_mode", checked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES
                else         AppCompatDelegate.MODE_NIGHT_NO
            )
            recreate()
        }

        // تغيير اللغة يُعيد تشغيل التطبيق بالكامل
        rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val newLang = if (checkedId == R.id.rbEnglish) "en" else "ar"
            if (newLang == prefs.getString("language", "ar")) return@setOnCheckedChangeListener
            prefs.edit().putString("language", newLang).apply()
            Toast.makeText(this,
                if (newLang == "en") "Language set to English" else "تم تعيين اللغة للعربية",
                Toast.LENGTH_SHORT).show()
            val intent = Intent(this, VideoLibraryActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finishAffinity()
        }
    }

    private fun applyTheme() {
        AppCompatDelegate.setDefaultNightMode(
            if (prefs.getBoolean("dark_mode", true)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
