package com.filetransit.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException

class ApiException(val status: Int, val code: String) : Exception(code)

class Repository(
    private val api: ApiService,
    private val session: SessionStore,
) {
    private suspend fun <T> call(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: HttpException) {
            throw ApiException(e.code(), parseError(e))
        } catch (e: IOException) {
            throw ApiException(0, "network_error")
        } catch (e: ApiException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(0, e.message ?: "unknown_error")
        }
    }

    private fun parseError(e: HttpException): String {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        return runCatching {
            Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: "http_${e.code()}"
    }

    // ── auth ──
    suspend fun login(username: String, password: String): LoginResponse =
        call { api.login(LoginRequest(username, password)) }

    suspend fun me(): UsernameStatus = call { api.me() }
    suspend fun usernameStatus(): UsernameStatus = call { api.usernameStatus() }
    suspend fun changeUsername(currentPassword: String, newUsername: String): OkResponse =
        call { api.changeUsername(ChangeUsernameRequest(currentPassword, newUsername)) }

    suspend fun clearUsername(): OkResponse = call { api.clearUsername() }
    suspend fun passwordStatus(): PasswordStatus = call { api.passwordStatus() }
    suspend fun changePassword(currentPassword: String, newPassword: String): OkResponse =
        call { api.changePassword(ChangePasswordRequest(currentPassword, newPassword)) }

    suspend fun clearPassword(): OkResponse = call { api.clearPassword() }

    // ── files ──
    suspend fun listFiles(folderId: String): List<FileDto> = call {
        val query = when (folderId) {
            "all" -> null
            "root" -> "root"
            else -> folderId
        }
        api.listFiles(query).files
    }

    suspend fun moveFiles(ids: List<String>, folderId: String?): MoveResponse =
        call { api.moveFiles(MoveFilesRequest(ids, folderId)) }

    suspend fun initUpload(
        name: String,
        size: Long,
        mimeType: String,
        expiresInDays: Int?,
        password: String?,
        folderId: String?,
        hash: String?,
    ): InitUploadResponse = call {
        api.initUpload(
            InitUploadRequest(
                name = name,
                size = size,
                mimeType = mimeType,
                expiresInDays = expiresInDays,
                password = password,
                folderId = folderId,
                hash = hash,
            ),
        )
    }

    suspend fun getParts(id: String): PartsResponse = call { api.getParts(id) }
    suspend fun partUrl(id: String, partNumber: Int): String = call { api.partUrl(id, partNumber).url }

    suspend fun completeUpload(id: String, parts: List<UploadedPart>? = null): FileDto =
        call { api.completeUpload(id, CompleteUploadRequest(parts)).file }

    suspend fun abortUpload(id: String): OkResponse = call { api.abortUpload(id) }

    suspend fun patchFile(
        id: String,
        name: String? = null,
        expiresInDays: Int? = null,
        clearExpiry: Boolean = false,
        password: String? = null,
        clearPassword: Boolean = false,
    ): FileDto = call {
        api.patchFile(
            id,
            FilePatchRequest(
                name = name?.takeIf { it.isNotBlank() },
                expiresInDays = expiryJson(expiresInDays, clearExpiry),
                password = password?.takeIf { it.isNotBlank() },
                clearPassword = if (clearPassword) true else null,
            ),
        ).file
    }

    suspend fun deleteFile(id: String): OkResponse = call { api.deleteFile(id) }
    suspend fun bulkDeleteFiles(ids: List<String>): BulkDeleteResponse = call { api.bulkDeleteFiles(BulkDeleteRequest(ids)) }
    suspend fun downloadUrl(id: String): String = call { api.downloadFile(id).url }
    suspend fun preview(id: String): PreviewInfo = call { api.previewFile(id) }
    suspend fun stats(): Stats = call { api.stats() }

    // ── folders ──
    suspend fun listFolders(): List<FolderDto> = call { api.listFolders().folders }
    suspend fun createFolder(name: String, parentId: String?): FolderDto =
        call { api.createFolder(FolderCreateRequest(name, parentId)).folder }

    suspend fun renameFolder(id: String, name: String): FolderDto =
        call { api.patchFolder(id, FolderPatchRequest(name = name)).folder }

    suspend fun moveFolder(id: String, parentId: String?): FolderDto =
        call { api.patchFolder(id, FolderPatchRequest(parentId = parentId?.let { JsonPrimitive(it) } ?: JsonNull)).folder }

    suspend fun deleteFolder(id: String): DeleteFolderResponse = call { api.deleteFolder(id) }

    // ── collections ──
    suspend fun listCollections(): List<CollectionDto> = call { api.listCollections().collections }

    suspend fun createCollection(
        name: String,
        fileIds: List<String>,
        expiresInDays: Int?,
        password: String?,
    ): CollectionDto = call {
        api.createCollection(
            CollectionCreateRequest(
                name = name,
                fileIds = fileIds,
                expiresInDays = expiresInDays,
                password = password?.takeIf { it.isNotBlank() },
            ),
        ).collection
    }

    suspend fun updateCollection(
        id: String,
        name: String? = null,
        expiresInDays: Int? = null,
        clearExpiry: Boolean = false,
        password: String? = null,
        clearPassword: Boolean = false,
    ): CollectionDto = call {
        api.patchCollection(
            id,
            CollectionPatchRequest(
                name = name?.takeIf { it.isNotBlank() },
                expiresInDays = expiryJson(expiresInDays, clearExpiry),
                password = password?.takeIf { it.isNotBlank() },
                clearPassword = if (clearPassword) true else null,
            ),
        ).collection
    }

    suspend fun deleteCollection(id: String): OkResponse = call { api.deleteCollection(id) }

    suspend fun collectionShareInfo(code: String): CollectionShareInfo = call { api.collectionShareInfo(code) }

    suspend fun collectionItemDownload(code: String, fileId: String, password: String?): String =
        call { api.collectionItemDownload(code, fileId, PasswordBody(password)).url }

    suspend fun collectionItemPreview(code: String, fileId: String, password: String?): PreviewInfo =
        call { api.collectionItemPreview(code, fileId, PasswordBody(password)) }

    suspend fun collectionDownloadAll(code: String, password: String?): List<DownloadEntry> =
        call { api.collectionDownloadAll(code, PasswordBody(password)).downloads }

    // ── texts ──
    suspend fun listTexts(q: String? = null): List<TextDto> =
        call { api.listTexts(q?.takeIf { it.isNotBlank() }).texts }

    suspend fun createText(
        title: String?,
        content: String,
        expiresInDays: Int?,
        password: String?,
    ): TextDto = call {
        api.createText(
            TextCreateRequest(
                title = title?.takeIf { it.isNotBlank() },
                content = content,
                expiresInDays = expiresInDays,
                password = password?.takeIf { it.isNotBlank() },
            ),
        ).text
    }

    suspend fun updateText(
        id: String,
        title: String? = null,
        content: String? = null,
        expiresInDays: Int? = null,
        clearExpiry: Boolean = false,
        password: String? = null,
        clearPassword: Boolean = false,
    ): TextDto = call {
        api.patchText(
            id,
            TextPatchRequest(
                title = title,
                content = content,
                expiresInDays = expiryJson(expiresInDays, clearExpiry),
                password = password?.takeIf { it.isNotBlank() },
                clearPassword = if (clearPassword) true else null,
            ),
        ).text
    }

    suspend fun deleteText(id: String): OkResponse = call { api.deleteText(id) }
    suspend fun textShareInfo(code: String): TextShareInfo = call { api.textShareInfo(code) }
    suspend fun revealText(code: String, password: String?): String =
        call { api.revealText(code, PasswordBody(password)).content }

    // ── folder shares ──
    suspend fun listFolderShares(): List<FolderShareDto> = call { api.listFolderShares().shares }

    suspend fun createFolderShare(
        folderId: String,
        name: String?,
        expiresInDays: Int?,
        password: String?,
    ): FolderShareDto = call {
        api.createFolderShare(
            FolderShareCreateRequest(
                folderId = folderId,
                name = name?.takeIf { it.isNotBlank() },
                expiresInDays = expiresInDays,
                password = password?.takeIf { it.isNotBlank() },
            ),
        ).share
    }

    suspend fun updateFolderShare(
        id: String,
        name: String? = null,
        expiresInDays: Int? = null,
        clearExpiry: Boolean = false,
        password: String? = null,
        clearPassword: Boolean = false,
    ): FolderShareDto = call {
        api.patchFolderShare(
            id,
            FolderSharePatchRequest(
                name = name?.takeIf { it.isNotBlank() },
                expiresInDays = expiryJson(expiresInDays, clearExpiry),
                password = password?.takeIf { it.isNotBlank() },
                clearPassword = if (clearPassword) true else null,
            ),
        ).share
    }

    suspend fun deleteFolderShare(id: String): OkResponse = call { api.deleteFolderShare(id) }
    suspend fun folderShareInfo(code: String): FolderShareInfo = call { api.folderShareInfo(code) }

    suspend fun folderShareItemDownload(code: String, fileId: String, password: String?): String =
        call { api.folderShareItemDownload(code, fileId, PasswordBody(password)).url }

    suspend fun folderShareItemPreview(code: String, fileId: String, password: String?): PreviewInfo =
        call { api.folderShareItemPreview(code, fileId, PasswordBody(password)) }

    suspend fun folderShareDownloadAll(code: String, password: String?): List<DownloadEntry> =
        call { api.folderShareDownloadAll(code, PasswordBody(password)).downloads }

    // ── public file share ──
    suspend fun shareInfo(code: String): ShareInfo = call { api.shareInfo(code) }
    suspend fun shareDownload(code: String, password: String?): String =
        call { api.shareDownload(code, PasswordBody(password)).url }

    suspend fun sharePreview(code: String, password: String?): PreviewInfo =
        call { api.sharePreview(code, PasswordBody(password)) }

    // ── settings ──
    suspend fun r2Status(): R2Status = call { api.r2Status() }
    suspend fun saveR2(body: R2SaveRequest): R2Status = call { api.saveR2(body) }
    suspend fun testR2(): R2TestResponse = call { api.testR2() }
    suspend fun r2Usage(refresh: Boolean): R2UsageStatus = call { api.r2Usage(if (refresh) "1" else null) }
    suspend fun saveUsageToken(token: String): UsageTokenResponse = call { api.saveUsageToken(UsageTokenRequest(token)) }
    suspend fun shareLimit(): ShareLimitSettings = call { api.shareLimit() }
    suspend fun saveShareLimit(body: ShareLimitSettings): ShareLimitSettings = call { api.saveShareLimit(body) }
    suspend fun site(): SiteStatus = call { api.site() }
    suspend fun saveSite(baseUrl: String): SiteStatus = call { api.saveSite(SiteSaveRequest(baseUrl)) }

    // ── bucket ──
    suspend fun scanBucket(): BucketScanResult = call { api.scanBucket() }
    suspend fun deleteOrphans(keys: List<String>): BucketDeleteResult = call { api.deleteOrphans(DeleteOrphansRequest(keys)) }
    suspend fun keyMigrationStatus(): KeyMigrationPlan = call { api.keyMigrationStatus() }
    suspend fun migrateKeys(limit: Int): KeyMigrationResult = call { api.migrateKeys(MigrateKeysRequest(limit)) }

    // ── logs ──
    suspend fun logs(
        page: Int,
        pageSize: Int,
        action: String?,
        result: String?,
        visitor: String?,
        q: String?,
    ): LogsResponse = call {
        api.logs(
            page = page,
            pageSize = pageSize,
            action = action?.takeIf { it.isNotBlank() },
            result = result?.takeIf { it.isNotBlank() },
            visitor = visitor?.takeIf { it.isNotBlank() },
            q = q?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun logStats(): LogsStats = call { api.logStats() }
    suspend fun clearLogs(): ClearResponse = call { api.clearLogs() }

    // ── update ──
    suspend fun updateStatus(): UpdateStatus = call { api.updateStatus() }
    suspend fun checkUpdate(): UpdateStatus = call { api.checkUpdate() }
    suspend fun runUpdate(): StartedResponse = call { api.runUpdate() }
    suspend fun updateLog(): UpdateLog = call { api.updateLog() }

    fun serverUrl(): String = session.server.value

    private fun expiryJson(days: Int?, clear: Boolean): kotlinx.serialization.json.JsonElement? = when {
        clear -> JsonNull
        days != null -> JsonPrimitive(days)
        else -> null
    }
}
