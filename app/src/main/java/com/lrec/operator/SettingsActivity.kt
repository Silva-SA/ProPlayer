package com.lrec.operator

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("lrec_prefs", MODE_PRIVATE)

        applyTheme()

        setContentView(R.layout.activity_settings)

        val btnBackSettings  = findViewById<ImageButton>(R.id.btnBackSettings)
        val switchHidden     = findViewById<Switch>(R.id.switchShowHidden)
        val switchDarkMode   = findViewById<Switch>(R.id.switchDarkMode)
        val rgLanguage       = findViewById<RadioGroup>(R.id.rgLanguage)
        val rbArabic         = findViewById<RadioButton>(R.id.rbArabic)
        val rbEnglish        = findViewById<RadioButton>(R.id.rbEnglish)

        // تحميل القيم المحفوظة
        switchHidden.isChecked   = prefs.getBoolean("show_hidden", false)
        switchDarkMode.isChecked = prefs.getBoolean("dark_mode", true)
        val lang = prefs.getString("language", "ar")
        if (lang == "en") rbEnglish.isChecked = true else rbArabic.isChecked = true

        // زر الرجوع
        btnBackSettings.setOnClickListener { finish() }

        // إظهار الملفات المخفية
        switchHidden.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("show_hidden", checked).apply()
            Toast.makeText(
                this,
                if (checked) "سيتم عرض الملفات المخفية" else "لن تُعرض الملفات المخفية",
                Toast.LENGTH_SHORT
            ).show()
        }

        // الوضع الداكن / الفاتح
        switchDarkMode.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("dark_mode", checked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        // اللغة
        rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val selectedLang = if (checkedId == R.id.rbEnglish) "en" else "ar"
            prefs.edit().putString("language", selectedLang).apply()
            Toast.makeText(
                this,
                if (selectedLang == "en") "Language set to English" else "تم تعيين اللغة للعربية",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun applyTheme() {
        val isDark = prefs.getBoolean("dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
