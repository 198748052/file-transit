package com.filetransit.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("file_transit_session", Context.MODE_PRIVATE)

    private val _token = MutableStateFlow(prefs.getString(KEY_TOKEN, null))
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _server = MutableStateFlow(prefs.getString(KEY_SERVER, "").orEmpty())
    val server: StateFlow<String> = _server.asStateFlow()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    fun saveServer(raw: String): String {
        val normalized = normalizeServer(raw)
        prefs.edit().putString(KEY_SERVER, normalized).apply()
        _server.value = normalized
        return normalized
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
        _token.value = token
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
        _token.value = null
    }

    fun persistUploadUri(fileId: String, uri: String) {
        prefs.edit().putString("upload_uri_$fileId", uri).apply()
    }

    fun uploadUri(fileId: String): String? = prefs.getString("upload_uri_$fileId", null)

    fun clearUploadUri(fileId: String) {
        prefs.edit().remove("upload_uri_$fileId").apply()
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_SERVER = "server"
        private const val KEY_USERNAME = "username"

        fun normalizeServer(raw: String): String {
            var value = raw.trim()
            if (value.isEmpty()) return ""
            val lower = value.lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                value = "https://$value"
            }
            return value.trimEnd('/')
        }
    }
}
