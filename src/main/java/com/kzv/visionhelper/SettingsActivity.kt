package com.kzv.visionhelper

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
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
        val currentLang = prefs.getString("language", "en") ?: "en"
        setLocalizedTexts(currentLang)

        //  model
        val modelList = listOf("HQ (F32)", "LQ (F16)")
        val modelCodes = listOf("float32", "float16")
        val modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modelList)
        binding.modelSpinner.adapter = modelAdapter



        // Restore saved state
        val soundEnabled = prefs.getBoolean("sound", true)
        val vibrationEnabled = prefs.getBoolean("vibration", true)
        val languageCode = prefs.getString("language", "en") ?: "en"

        binding.switchSound.isChecked = soundEnabled
        binding.switchVibration.isChecked = vibrationEnabled

        // Language Spinner Setup
        val languageList = listOf("English", "Русский")
        val languageCodes = listOf("en", "ru")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languageList)
        binding.languageSpinner.adapter = spinnerAdapter
        binding.languageSpinner.setSelection(languageCodes.indexOf(languageCode))

        // Save Sound toggle
        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("sound", isChecked).apply()
            //Toast.makeText(this, "Sound ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        // Save Vibration toggle
        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("vibration", isChecked).apply()
            //Toast.makeText(this, "Vibration ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        // Save Language selection
        binding.languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedLang = languageCodes[position]
                prefs.edit().putString("language", selectedLang).apply()
                if (selectedLang != currentLang) {
                    prefs.edit().putString("language", selectedLang).apply()
                    setLocalizedTexts(selectedLang)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }


        // Back button
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun setLocalizedTexts(selectedLang: String) {
        if (selectedLang == "ru") {
            binding.settingsTitle.text = "Настройки"
            binding.switchSound.text = "Звуковое оповещение"
            binding.switchVibration.text = "Виброотклик"
            binding.languageLabel.text = "Выбор языка"
            binding.modelLabel.text = "Выбор качесвтва модели"

        } else {
            binding.settingsTitle.text = "Settings"
            binding.switchSound.text = "Enable sound feedback"
            binding.switchVibration.text = "Enable vibration feedback"
            binding.languageLabel.text = "Select language"
            binding.modelLabel.text = "Model quality"
        }
    }
}
