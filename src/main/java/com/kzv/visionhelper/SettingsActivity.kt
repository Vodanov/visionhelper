package com.kzv.visionhelper

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.kzv.visionhelper.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs by lazy { getSharedPreferences("vision_settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Language
        var currentLang = prefs.getString("language", "en") ?: "en"
        LocalizationManager.load(this, currentLang)
        setLocalizedTexts(LocalizationManager.settingsNames)

        // Model spinner
        val modelList = listOf("HQ (F32)", "LQ (F16)")
        val modelCodes = listOf("float32", "float16")
        val modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modelList)
        binding.modelSpinner.setAdapter(modelAdapter)

        val savedModelType = prefs.getString("model_type", "float32") ?: "float32"
        val index = modelCodes.indexOf(savedModelType)
        if (index != -1) {
            binding.modelSpinner.setText(modelList[index], false)
        }

        binding.modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                prefs.edit().putString("model_type", modelCodes[position]).apply()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Restore saved values
        val currentFps = prefs.getInt("fps_limit", 2)
        val currentCooldown = prefs.getInt("feedback_cooldown", 500)

        binding.seekFps.value = currentFps.toFloat()
        binding.seekCooldown.value = currentCooldown.toFloat()

        binding.seekFps.addOnChangeListener { _, value, _ ->
            prefs.edit().putInt("fps_limit", value.toInt()).apply()
        }

        binding.seekCooldown.addOnChangeListener { _, value, _ ->
            prefs.edit().putInt("feedback_cooldown", value.toInt()).apply()
        }

        // Sound & Vibration
        val soundEnabled = prefs.getBoolean("sound", true)
        val vibrationEnabled = prefs.getBoolean("vibration", true)
        binding.switchSound.isChecked = soundEnabled
        binding.switchVibration.isChecked = vibrationEnabled

        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("sound", isChecked).apply()
        }

        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("vibration", isChecked).apply()
        }

        // Language Spinner setup
        val languageList = LocalizationManager.getLanguageListWithFlags(this)
        val languageCodes = LocalizationManager.availableLanguages
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languageList)

        // Set adapter for language spinner
        binding.languageSpinner.setAdapter(spinnerAdapter)

        // Set initial selection for language spinner
        currentLang = prefs.getString("language", "en") ?: "en"
        val currentLangPosition = languageCodes.indexOf(currentLang)

        if (currentLangPosition != -1) {
            binding.languageSpinner.setText(languageList[currentLangPosition], false)
        }

        // Add item selected listener for the spinner
        binding.languageSpinner.setOnItemClickListener { parent, view, position, id ->
            val selectedLang = languageCodes[position]
            prefs.edit().putString("language", selectedLang).apply()

            if (selectedLang != currentLang) {
                LocalizationManager.load(this, selectedLang)
                setLocalizedTexts(LocalizationManager.settingsNames)
            }
        }

        // Back button
        binding.buttonBack.setOnClickListener {
            finish()
        }

        // Apply (just close settings for now)
        binding.buttonApply.setOnClickListener {
            finish()
        }
    }

    private fun setLocalizedTexts(localized: List<String>) {
        binding.settingsTitle.text = localized[0]
        binding.switchSound.text = localized[1]
        binding.switchVibration.text = localized[2]
        binding.languageLabel.text = localized[3]
        binding.modelLabel.text = localized[4]
        binding.fpsLabel.text = localized[5]
        binding.cooldownLabel.text = localized[6]
        binding.buttonApply.text = localized[7]
    }
}
