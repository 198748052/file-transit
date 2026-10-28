package com.filetransit.app.util

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.filetransit.app.data.ApiException

fun copyToClipboard(context: Context, label: String, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

fun enqueueDownload(context: Context, url: String, name: String) {
    val safeName = name.ifBlank { "download-${System.currentTimeMillis()}" }
        .replace('/', '_')
        .replace('\u0000', '_')
    runCatching {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(safeName)
            .setDescription("文件中转站下载")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
    }.onSuccess {
        Toast.makeText(context, "已加入下载队列", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "下载失败：${it.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
    }
}

fun apiMessage(error: Throwable?): String {
    if (error == null) return "未知错误"
    if (error !is ApiException) return error.message ?: "未知错误"
    return when (error.code) {
        "network_error" -> "网络连接失败，请检查服务器地址与网络"
        "invalid_credentials" -> "用户名或密码不正确"
        "too_many_attempts" -> "尝试次数过多，请稍后再试"
        "too_many_requests" -> "访问过于频繁，请稍后再试"
        "password_required" -> "请输入提取码"
        "wrong_password" -> "提取码不正确"
        "expired" -> "分享已过期"
        "not_found" -> "分享不存在或已被删除"
        "not_ready" -> "文件尚未准备好"
        "not_uploading" -> "文件不在上传中"
        "not_a_resumable_upload" -> "该文件不支持断点续传"
        "file_not_found" -> "文件不存在"
        "folder_not_found" -> "文件夹不存在"
        "folder_name_taken" -> "同级目录下已存在同名文件夹"
        "folder_cycle" -> "不能把文件夹移动到自身内部"
        "folder_nesting_too_deep" -> "文件夹层级过深"
        "file_not_editable" -> "文件已过期，无法编辑"
        "collection_not_editable" -> "集合已过期，无法编辑"
        "folder_share_not_editable" -> "文件夹分享已过期，无法编辑"
        "no_ready_files" -> "所选文件尚未准备好"
        "secret_required" -> "请填写 R2 Secret Access Key"
        "r2_not_configured" -> "尚未配置 R2 存储"
        "invalid_token" -> "Token 格式不正确"
        "same_password" -> "新密码不能与当前密码相同"
        "same_username" -> "新用户名不能与当前用户名相同"
        "file_too_large" -> "文件超过服务器允许的大小"
        "update_disabled" -> "服务器未启用远程更新"
        "not_git_repo" -> "服务器不是 Git 仓库，无法更新"
        "bucket_scan_failed" -> "存储桶扫描失败"
        "already_finalized" -> "该上传已经完成"
        else -> when {
            error.code.startsWith("r2_check_failed") -> "R2 连接失败：${error.code.substringAfter(':', "").trim()}"
            error.code.startsWith("folder_delete_partial") -> "部分文件删除失败，请稍后重试"
            error.status == 0 -> "请求失败：${error.code}"
            else -> "请求失败（${error.status}）：${error.code}"
        }
    }
}
