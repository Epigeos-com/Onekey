package com.epigeos.onekey

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class PreferenceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_layout)
        supportFragmentManager.beginTransaction().replace(R.id.settings_layout, PreferenceFragment()).commit()
    }
}