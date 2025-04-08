package com.kzv.visionhelper

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kzv.visionhelper.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            Toast.makeText(this, "Sound ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            Toast.makeText(this, "Vibration ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        binding.buttonBack.setOnClickListener {
            finish()
        }
    }
}
