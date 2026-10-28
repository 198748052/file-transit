package com.filetransit.app.ui

import kotlinx.coroutines.flow.MutableStateFlow

object Routes {
    const val LOGIN = "login"
    const val FILES = "files"
    const val TEXTS = "texts"
    const val SHARES = "shares"
    const val LOGS = "logs"
    const val SETTINGS = "settings"
    const val PREVIEW = "preview"

    const val SHARE = "share/{code}"
    const val COLLECTION_SHARE = "collection/{code}"
    const val FOLDER_SHARE = "folderShare/{code}"
    const val TEXT_SHARE = "text/{code}"

    fun share(code: String) = "share/$code"
    fun collection(code: String) = "collection/$code"
    fun folderShare(code: String) = "folderShare/$code"
    fun text(code: String) = "text/$code"
}

data class PreviewRequest(val url: String, val name: String, val mime: String)

object PreviewInbox {
    var request: PreviewRequest? = null
}

object ShareInbox {
    val text = MutableStateFlow<String?>(null)
}
