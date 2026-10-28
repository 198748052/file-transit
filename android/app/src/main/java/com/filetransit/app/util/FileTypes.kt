package com.filetransit.app.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.ui.graphics.vector.ImageVector

enum class PreviewKind { IMAGE, VIDEO, AUDIO, PDF, TEXT, NONE }

fun previewKind(mime: String, name: String): PreviewKind {
    val lowerMime = mime.lowercase()
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        ext == "svg" -> PreviewKind.TEXT
        lowerMime.startsWith("image/") -> PreviewKind.IMAGE
        lowerMime.startsWith("video/") -> PreviewKind.VIDEO
        lowerMime.startsWith("audio/") -> PreviewKind.AUDIO
        lowerMime == "application/pdf" || ext == "pdf" -> PreviewKind.PDF
        lowerMime.startsWith("text/") -> PreviewKind.TEXT
        lowerMime in setOf(
            "application/json",
            "application/xml",
            "application/javascript",
            "application/x-javascript",
            "application/x-sh",
        ) -> PreviewKind.TEXT
        ext in setOf(
            "txt", "md", "markdown", "log", "json", "xml", "yaml", "yml", "toml", "ini", "conf",
            "csv", "tsv", "js", "ts", "jsx", "tsx", "css", "scss", "html", "htm", "vue", "svelte",
            "java", "kt", "kts", "py", "rb", "go", "rs", "c", "h", "cpp", "hpp", "cs", "php",
            "sh", "bash", "zsh", "sql", "gradle", "properties", "gitignore", "env", "svg",
        ) -> PreviewKind.TEXT
        else -> PreviewKind.NONE
    }
}

fun fileIcon(mime: String, name: String): ImageVector {
    val lower = mime.lowercase()
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        ext == "svg" -> Icons.Outlined.Code
        lower.startsWith("image/") -> Icons.Outlined.Image
        lower.startsWith("video/") -> Icons.Outlined.Movie
        lower.startsWith("audio/") -> Icons.Outlined.Audiotrack
        lower == "application/pdf" || ext == "pdf" -> Icons.Outlined.PictureAsPdf
        previewKind(mime, name) == PreviewKind.TEXT -> Icons.Outlined.Code
        ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz") -> Icons.Outlined.Archive
        lower.startsWith("text/") -> Icons.Outlined.Description
        else -> Icons.Outlined.InsertDriveFile
    }
}
