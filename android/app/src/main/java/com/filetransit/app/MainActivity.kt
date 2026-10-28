package com.filetransit.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import com.filetransit.app.ui.App
import com.filetransit.app.ui.Routes
import com.filetransit.app.ui.ShareInbox
import com.filetransit.app.ui.theme.FileTransitTheme
import kotlinx.coroutines.flow.MutableStateFlow

data class DeepLinkEvent(val route: String, val stamp: Long = System.nanoTime())

class MainActivity : ComponentActivity() {
    private val deepLink = MutableStateFlow<DeepLinkEvent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.init(applicationContext)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestLegacyStoragePermission()
        handleIntent(intent)
        setContent {
            FileTransitTheme {
                val event by deepLink.collectAsState()
                App(deepLink = event)
            }
        }
    }

    private fun requestLegacyStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            ShareInbox.text.value = intent.getStringExtra(Intent.EXTRA_TEXT)
            deepLink.value = DeepLinkEvent(Routes.TEXTS)
            return
        }
        val data = intent.data ?: return
        val route = parseDeepLink(data.toString()) ?: return
        deepLink.value = DeepLinkEvent(route)
    }

    private fun parseDeepLink(raw: String): String? {
        val uri = runCatching { android.net.Uri.parse(raw) }.getOrNull() ?: return null
        val segments = uri.pathSegments
        val type: String?
        val code: String?
        if (uri.scheme == "filetransit") {
            type = uri.host ?: segments.getOrNull(0)
            code = if (uri.host != null) segments.getOrNull(0) else segments.getOrNull(1)
        } else {
            val marker = segments.indexOfFirst { it == "s" || it == "c" || it == "f" || it == "t" }
            type = segments.getOrNull(marker)
            code = segments.getOrNull(marker + 1)
            if ((uri.scheme == "http" || uri.scheme == "https") && !uri.authority.isNullOrBlank() && marker >= 0) {
                val prefix = if (marker > 0) segments.take(marker).joinToString("/") else ""
                val base = "${uri.scheme}://${uri.authority}" + if (prefix.isEmpty()) "" else "/$prefix"
                Graph.session.saveServer(base)
            }
        }
        if (code.isNullOrBlank()) return null
        return when (type) {
            "s" -> "share/$code"
            "c" -> "collection/$code"
            "f" -> "folderShare/$code"
            "t" -> "text/$code"
            else -> null
        }
    }
}

