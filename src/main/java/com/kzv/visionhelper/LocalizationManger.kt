package com.kzv.visionhelper

import android.content.Context
import org.json.JSONObject

object LocalizationManager {
    lateinit var labels: List<String>
    lateinit var settingsNames: List<String>
    lateinit var availableLanguages: List<String>
    fun load(context: Context, languageCode: String) {
        val jsonStr = context.assets.open("labels.json")
            .bufferedReader()
            .use { it.readText() }

        val jsonObject = JSONObject(jsonStr)
        val localized = jsonObject.getJSONObject(languageCode)

        val labelList = mutableListOf<String>()
        for (i in 0..5) {
            labelList.add(localized.getString(i.toString()))
        }

        val settingsList = listOf(
            localized.getString("settingsTitle"),
            localized.getString("switchSound"),
            localized.getString("switchVibration"),
            localized.getString("languageLabel"),
            localized.getString("modelLabel"),
            localized.getString("fpsLabel"),
            localized.getString("cooldownLabel"),
            localized.getString("buttonApply")
        )

        labels = labelList
        settingsNames = settingsList
    }
    fun getLanguageListWithFlags(context: Context): List<String> {
        val jsonStr = context.assets.open("labels.json")
            .bufferedReader()
            .use { it.readText() }

        val jsonObject = JSONObject(jsonStr)

        val langList = mutableListOf<String>()
        val langCodes = mutableListOf<String>()

        for (key in jsonObject.keys()) {
            val langObject = jsonObject.getJSONObject(key)
            val flag = langObject.optString("Flag", "")
            langList.add("$flag $key")
            langCodes.add(key)
        }

        availableLanguages = langCodes
        return langList
    }
}
