package com.epigeos.onekey

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat

class PreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val wpm = preferenceManager.findPreference<EditTextPreference>("wpm")
        val swipeDistance = preferenceManager.findPreference<EditTextPreference>("swipe_distance")
        val acceptableError = preferenceManager.findPreference<EditTextPreference>("acceptable_error")

        val numericInputs = arrayOf(wpm, swipeDistance, acceptableError)
        numericInputs.forEach { input ->
            input?.summary = input?.text
            input?.setOnPreferenceChangeListener { preference, newValue ->
                if (newValue.toString().toFloatOrNull() == null){
                    false
                }else{
                    preference.summary = newValue.toString()
                    true
                }
            }
            input?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER + if (input == swipeDistance) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
            }
        }

        val tryIt = preferenceManager.findPreference<EditTextPreference>("try_it")
        tryIt?.setOnPreferenceChangeListener { preference, newValue -> false }
    }
}