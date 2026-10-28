package com.filetransit.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class UploadPhase { HASHING, INITIALIZING, UPLOADING, COMPLETING, DONE, DUPLICATE, FAILED, CANCELLED }

data class UploadTask(
    val localId: String,
    val name: String,
    val size: Long = 0,
    val uploaded: Long = 0,
    val phase: UploadPhase = UploadPhase.HASHING,
    val fileId: String? = null,
    val message: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
) {
    val progress: Float
        get() = if (size <= 0) 0f else (uploaded.toFloat() / size.toFloat()).coerceIn(0f, 1f)
    val finished: Boolean
        get() = phase == UploadPhase.DONE || phase == UploadPhase.DUPLICATE ||
            phase == UploadPhase.FAILED || phase == UploadPhase.CANCELLED
}

class UploadManager(
    private val context: Context,
    private val repo: Repository,
    private val session: SessionStore,
    private val scope: CoroutineScope,
) {
    private val resolver = context.contentResolver

    private val _tasks = MutableStateFlow<List<UploadTask>>(emptyList())
    val tasks: StateFlow<List<UploadTask>> = _tasks.asStateFlow()

    private val _completed = MutableSharedFlow<FileDto>(extraBufferCapacity = 32)
    val completed = _completed.asSharedFlow()

    private val jobs = ConcurrentHashMap<String, Job>()

    private val r2Client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.MINUTES)
        .readTimeout(15, TimeUnit.MINUTES)
        .build()

    fun enqueue(
        uri: Uri,
        folderId: String?,
        expiresInDays: Int?,
        password: String?,
    ): String {
        val localId = UUID.randomUUID().toString()
        addTask(UploadTask(localId = localId, name = "准备上传…", phase = UploadPhase.INITIALIZING))
        jobs[localId] = scope.launch {
            runUpload(localId, uri, folderId, expiresInDays, password)
        }
        return localId
    }

    fun enqueueFolder(
        treeUri: Uri,
        targetFolderId: String?,
        expiresInDays: Int?,
        password: String?,
    ): String {
        val localId = UUID.randomUUID().toString()
        addTask(UploadTask(localId = localId, name = "扫描文件夹…", phase = UploadPhase.INITIALIZING))
        jobs[localId] = scope.launch {
            runFolderUpload(localId, treeUri, targetFolderId, expiresInDays, password)
        }
        return localId
    }

    fun resume(file: FileDto) {
        val uriString = session.uploadUri(file.id)
        val localId = UUID.randomUUID().toString()
        if (uriString.isNullOrBlank()) {
            addTask(
                UploadTask(
                    localId = localId,
                    name = file.name,
                    size = file.size,
                    phase = UploadPhase.FAILED,
                    fileId = file.id,
                    message = "找不到原始文件，请重新选择文件上传",
                ),
            )
            return
        }
        addTask(
            UploadTask(
                localId = localId,
                name = file.name,
                size = file.size,
                phase = UploadPhase.UPLOADING,
                fileId = file.id,
            ),
        )
        jobs[localId] = scope.launch {
            runResume(localId, file, Uri.parse(uriString))
        }
    }

    fun cancel(localId: String) {
        jobs.remove(localId)?.cancel()
        val task = _tasks.value.find { it.localId == localId } ?: return
        updateTask(localId) { it.copy(phase = UploadPhase.CANCELLED, message = "已取消") }
        val fileId = task.fileId
        if (fileId != null) {
            scope.launch {
                runCatching { repo.abortUpload(fileId) }
                session.clearUploadUri(fileId)
            }
        }
    }

    fun cancelByFileId(fileId: String) {
        val task = _tasks.value.firstOrNull { it.fileId == fileId } ?: return
        cancel(task.localId)
    }

    /** Stop the local transfer without aborting the server record (used before bulk delete). */
    fun cancelLocalByFileId(fileId: String) {
        val task = _tasks.value.firstOrNull { it.fileId == fileId } ?: return
        jobs.remove(task.localId)?.cancel()
        updateTask(task.localId) { it.copy(phase = UploadPhase.CANCELLED, message = "已取消") }
        session.clearUploadUri(fileId)
    }

    fun clearFinished() {
        _tasks.value = _tasks.value.filterNot { it.finished }
        jobs.keys.filter { key -> _tasks.value.none { it.localId == key } }.forEach { jobs.remove(it) }
    }

    // ─────────────────────────── new upload ───────────────────────────

    private suspend fun runUpload(
        localId: String,
        uri: Uri,
        folderId: String?,
        expiresInDays: Int?,
        password: String?,
    ) {
        try {
            val meta = readMeta(uri)
            if (meta.size <= 0) throw IOException("无法读取文件大小")
            updateTask(localId) { it.copy(name = meta.name, size = meta.size, phase = UploadPhase.HASHING) }

            val hash = runCatching {
                hashUri(uri, meta.size) { done ->
                    updateTask(localId) { it.copy(uploaded = done) }
                }
            }.getOrNull()

            updateTask(localId) {
                it.copy(phase = UploadPhase.INITIALIZING, uploaded = 0, message = "初始化上传…")
            }
            val init = repo.initUpload(
                name = meta.name,
                size = meta.size,
                mimeType = meta.mime,
                expiresInDays = expiresInDays,
                password = password,
                folderId = folderId,
                hash = hash,
            )
            val fileId = init.file.id
            updateTask(localId) { it.copy(fileId = fileId) }
            session.persistUploadUri(fileId, uri.toString())

            if (init.mode == "duplicate") {
                updateTask(localId) {
                    it.copy(
                        phase = UploadPhase.DUPLICATE,
                        uploaded = meta.size,
                        message = "秒传完成，节省 ${formatBytes(init.savedBytes ?: meta.size)}",
                    )
                }
                session.clearUploadUri(fileId)
                _completed.tryEmit(init.file)
                return
            }

            val upload = init.upload ?: throw IOException("服务器未返回上传地址")

            if (init.mode == "single") {
                updateTask(localId) { it.copy(phase = UploadPhase.UPLOADING, message = null) }
                putSingle(
                    url = upload.url ?: throw IOException("缺少上传地址"),
                    contentType = upload.headers["Content-Type"] ?: meta.mime,
                    body = UriRequestBody(uri, meta.size) { done ->
                        updateTask(localId) { it.copy(uploaded = done) }
                    },
                    onProgress = { done -> updateTask(localId) { it.copy(uploaded = done) } },
                )
                updateTask(localId) { it.copy(phase = UploadPhase.COMPLETING, uploaded = meta.size) }
                val done = repo.completeUpload(fileId)
                finish(localId, done)
                return
            }

            updateTask(localId) { it.copy(phase = UploadPhase.UPLOADING, message = null) }
            val partSize = upload.partSize ?: throw IOException("缺少分片大小")
            val urls = upload.urls.associateBy { it.partNumber }
            val parts = resolver.openInputStream(uri)?.use { input ->
                uploadAllParts(localId, fileId, input, meta.size, partSize, urls, emptySet(), emptyList())
            } ?: throw IOException("无法打开文件")

            updateTask(localId) { it.copy(phase = UploadPhase.COMPLETING, uploaded = meta.size) }
            val done = repo.completeUpload(fileId, parts)
            finish(localId, done)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateTask(localId) {
                it.copy(phase = UploadPhase.FAILED, message = friendlyMessage(e))
            }
        }
    }

    // ─────────────────────────── resume ───────────────────────────

    private suspend fun runResume(localId: String, file: FileDto, uri: Uri) {
        try {
            val info = repo.getParts(file.id)
            val uploaded = info.uploaded.toSet()
            val doneBytes = info.uploaded.sumOf { partBytes(it.partNumber, info.partSize, file.size) }
            updateTask(localId) {
                it.copy(
                    phase = UploadPhase.UPLOADING,
                    uploaded = doneBytes,
                    message = "继续上传 ${info.uploaded.size}/${info.partCount} 片",
                )
            }
            val urls = info.urls.associateBy { it.partNumber }
            val parts = resolver.openInputStream(uri)?.use { input ->
                uploadAllParts(
                    localId = localId,
                    fileId = file.id,
                    input = input,
                    totalSize = file.size,
                    partSize = info.partSize,
                    urls = urls,
                    skip = uploaded.map { it.partNumber }.toSet(),
                    initialParts = info.uploaded,
                    initialBytes = doneBytes,
                )
            } ?: throw IOException("无法打开原始文件")

            updateTask(localId) { it.copy(phase = UploadPhase.COMPLETING, uploaded = file.size) }
            val done = repo.completeUpload(file.id, parts)
            finish(localId, done)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateTask(localId) { it.copy(phase = UploadPhase.FAILED, message = friendlyMessage(e)) }
        }
    }

    private suspend fun uploadAllParts(
        localId: String,
        fileId: String,
        input: InputStream,
        totalSize: Long,
        partSize: Long,
        urls: Map<Int, UploadPartUrl>,
        skip: Set<Int>,
        initialParts: List<UploadedPart>,
        initialBytes: Long = 0,
    ): List<UploadedPart> {
        val partCount = ((totalSize + partSize - 1) / partSize).toInt()
        val result = initialParts.toMutableList()
        var completedBytes = initialBytes
        var partNumber = 1

        while (partNumber <= partCount) {
            val expected = partBytes(partNumber, partSize, totalSize)
            val bytes = readChunk(input, expected.toInt())
            if (bytes.isEmpty() && expected > 0) throw IOException("文件读取中断")
            if (partNumber in skip) {
                partNumber++
                continue
            }
            val initial = urls[partNumber]?.url ?: repo.partUrl(fileId, partNumber)
            val etag = putPartWithRetry(initial, bytes) { repo.partUrl(fileId, partNumber) }
            result += UploadedPart(partNumber, etag)
            completedBytes += expected
            updateTask(localId) { it.copy(uploaded = completedBytes) }
            partNumber++
        }
        return result
    }

    // ─────────────────────────── folder upload ───────────────────────────

    private suspend fun runFolderUpload(
        localId: String,
        treeUri: Uri,
        targetFolderId: String?,
        expiresInDays: Int?,
        password: String?,
    ) {
        try {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: throw IOException("无法读取所选文件夹")
            updateTask(localId) { it.copy(name = root.name ?: "文件夹", phase = UploadPhase.INITIALIZING) }

            var fileCount = 0
            var folderCount = 0
            val known = repo.listFolders().toMutableList()

            suspend fun ensureFolder(parentId: String?, name: String): String {
                known.firstOrNull { it.parentId == parentId && it.name.equals(name, ignoreCase = true) }
                    ?.let { return it.id }
                return try {
                    val created = repo.createFolder(name, parentId)
                    known += created
                    folderCount++
                    created.id
                } catch (e: ApiException) {
                    if (e.code == "folder_name_taken") {
                        val fresh = repo.listFolders()
                        known.clear()
                        known.addAll(fresh)
                        known.firstOrNull { it.parentId == parentId && it.name.equals(name, ignoreCase = true) }
                            ?.id ?: throw e
                    } else {
                        throw e
                    }
                }
            }

            suspend fun walk(dir: DocumentFile, parentId: String?) {
                for (child in dir.listFiles()) {
                    val name = child.name ?: continue
                    if (child.isDirectory) {
                        val childId = ensureFolder(parentId, name)
                        walk(child, childId)
                    } else if (child.isFile) {
                        enqueue(child.uri, parentId, expiresInDays, password)
                        fileCount++
                    }
                }
            }

            walk(root, targetFolderId)
            updateTask(localId) {
                it.copy(
                    phase = UploadPhase.DONE,
                    message = "已加入 $fileCount 个文件、$folderCount 个文件夹",
                    uploaded = 1,
                    size = 1,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateTask(localId) { it.copy(phase = UploadPhase.FAILED, message = friendlyMessage(e)) }
        }
    }

    // ─────────────────────────── helpers ───────────────────────────

    private fun finish(localId: String, file: FileDto) {
        updateTask(localId) {
            it.copy(
                phase = UploadPhase.DONE,
                fileId = file.id,
                uploaded = file.size,
                size = file.size,
                message = "上传完成",
            )
        }
        session.clearUploadUri(file.id)
        _completed.tryEmit(file)
    }

    private fun addTask(task: UploadTask) {
        _tasks.value = _tasks.value + task
    }

    private fun updateTask(localId: String, transform: (UploadTask) -> UploadTask) {
        _tasks.value = _tasks.value.map { if (it.localId == localId) transform(it) else it }
    }

    private fun putSingle(
        url: String,
        contentType: String?,
        body: RequestBody,
        onProgress: (Long) -> Unit,
    ) {
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .put(body)
                    .apply { if (!contentType.isNullOrBlank()) header("Content-Type", contentType) }
                    .build()
                r2Client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("上传失败 HTTP ${response.code}")
                }
                return
            } catch (e: Exception) {
                last = e
                if (e is CancellationException) throw e
                Thread.sleep(400L * (attempt + 1))
            }
        }
        throw last ?: IOException("上传失败")
    }

    private suspend fun putPartWithRetry(
        initialUrl: String,
        bytes: ByteArray,
        refresh: suspend () -> String,
    ): String {
        var last: Exception? = null
        var url = initialUrl
        repeat(3) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .put(bytes.toRequestBody(null))
                    .build()
                r2Client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("分片上传失败 HTTP ${response.code}")
                    return response.header("ETag") ?: throw IOException("分片响应缺少 ETag")
                }
            } catch (e: Exception) {
                last = e
                if (e is CancellationException) throw e
                url = runCatching { refresh() }.getOrDefault(url)
                Thread.sleep(400L * (attempt + 1))
            }
        }
        throw last ?: IOException("分片上传失败")
    }

    private fun readMeta(uri: Uri): FileMeta {
        var name = uri.lastPathSegment ?: "file"
        var size = -1L
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        if (size < 0) {
            size = runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            }.getOrNull() ?: 0L
        }
        val mime = resolver.getType(uri) ?: guessMime(name)
        return FileMeta(name, size, mime)
    }

    private fun hashUri(uri: Uri, size: Long, onProgress: (Long) -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = resolver.openInputStream(uri) ?: throw IOException("无法打开文件")
        input.use { stream ->
            val buffer = ByteArray(128 * 1024)
            var done = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
                done += read
                onProgress(done.coerceAtMost(size))
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun readChunk(input: InputStream, max: Int): ByteArray {
        val buffer = ByteArray(max)
        var total = 0
        while (total < max) {
            val read = input.read(buffer, total, max - total)
            if (read < 0) break
            total += read
        }
        return if (total == max) buffer else buffer.copyOf(total)
    }

    private fun partBytes(partNumber: Int, partSize: Long, total: Long): Long {
        val start = (partNumber - 1) * partSize
        return minOf(partSize, total - start)
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun friendlyMessage(e: Exception): String = when (e) {
        is ApiException -> when (e.code) {
            "network_error" -> "网络连接失败"
            "file_too_large" -> "文件过大"
            "r2_not_configured" -> "服务器尚未配置 R2 存储"
            else -> e.code
        }
        else -> e.message ?: "上传失败"
    }

    private inner class UriRequestBody(
        private val uri: Uri,
        private val length: Long,
        private val onProgress: (Long) -> Unit,
    ) : RequestBody() {
        override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
        override fun contentLength(): Long = length
        override fun writeTo(sink: BufferedSink) {
            val stream = resolver.openInputStream(uri)
                ?: throw IOException("无法打开文件")
            stream.use { input ->
                val buffer = ByteArray(128 * 1024)
                var done = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    sink.write(buffer, 0, read)
                    done += read
                    onProgress(done)
                }
            }
        }
    }

    private data class FileMeta(val name: String, val size: Long, val mime: String)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var index = 0
    while (value >= 1024 && index < units.size - 1) {
        value /= 1024
        index++
    }
    return String.format(Locale.US, "%.1f %s", value, units[index])
}

