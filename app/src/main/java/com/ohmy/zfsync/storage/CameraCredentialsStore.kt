package com.ohmy.zfsync.storage

import android.content.Context

data class SavedCameraCredentials(val ssid: String, val password: String)

/**
 * Remembers the last Wi-Fi SSID/password that connected successfully, so the Connect screen can
 * pre-fill them next time instead of making the user read both off the camera's screen again.
 * The camera generates this password once and keeps it stable across Wi-Fi toggles, so caching
 * it locally (private app storage, never sent anywhere) is safe to do without prompting.
 */
class CameraCredentialsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): SavedCameraCredentials? {
        val ssid = prefs.getString(KEY_SSID, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return SavedCameraCredentials(ssid, password)
    }

    fun save(ssid: String, password: String) {
        prefs.edit().putString(KEY_SSID, ssid).putString(KEY_PASSWORD, password).apply()
    }

    private companion object {
        const val PREFS_NAME = "camera_credentials"
        const val KEY_SSID = "ssid"
        const val KEY_PASSWORD = "password"
    }
}
