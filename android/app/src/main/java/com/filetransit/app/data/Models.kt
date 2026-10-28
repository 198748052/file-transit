package com.filetransit.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class FileDto(
    val id: String,
    val shareCode: String = "",
    val name: String = "",
    val size: Long = 0,
    val mimeType: String = "application/octet-stream",
    val status: String = "ready",
    val createdAt: Long = 0,
    val expiresAt: Long? = null,
    val hasPassword: Boolean = false,
    val downloadCount: Int = 0,
    val lastDownloadAt: Long? = null,
    val folderId: String? = null,
    val shareUrl: String = "",
)

@Serializable
data class FolderDto(
    val id: String,
    val name: String = "",
    val parentId: String? = null,
    val createdAt: Long = 0,
    val fileCount: Int = 0,
    val totalSize: Long = 0,
    val childCount: Int = 0,
)

@Serializable
data class Stats(
    val fileCount: Int = 0,
    val readyCount: Int = 0,
    val uploadingCount: Int = 0,
    val expiredCount: Int = 0,
    val totalSize: Long = 0,
    val readySize: Long = 0,
    val expiredSize: Long = 0,
    val uploadSize: Long = 0,
    val downloadTotal: Int = 0,
    val collectionCount: Int = 0,
    val folderCount: Int = 0,
)

@Serializable
data class UploadPartUrl(val partNumber: Int, val url: String)

@Serializable
data class UploadInfo(
    val url: String? = null,
    val method: String? = null,
    val key: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val uploadId: String? = null,
    val partSize: Long? = null,
    val partCount: Int? = null,
    val urls: List<UploadPartUrl> = emptyList(),
)

@Serializable
data class InitUploadResponse(
    val mode: String,
    val file: FileDto,
    val upload: UploadInfo? = null,
    val duplicateOf: String? = null,
    val savedBytes: Long? = null,
)

@Serializable
data class UploadedPart(val partNumber: Int, val etag: String)

@Serializable
data class PartsResponse(
    val fileId: String,
    val uploadId: String,
    val partSize: Long,
    val partCount: Int,
    val uploaded: List<UploadedPart> = emptyList(),
    val urls: List<UploadPartUrl> = emptyList(),
)

@Serializable
data class PreviewInfo(
    val url: String,
    val name: String = "",
    val size: Long = 0,
    val mimeType: String = "application/octet-stream",
)

@Serializable
data class ShareInfo(
    val found: Boolean = false,
    val expired: Boolean = false,
    val requiresPassword: Boolean = false,
    val name: String = "",
    val size: Long = 0,
    val mimeType: String = "application/octet-stream",
    val createdAt: Long = 0,
    val expiresAt: Long? = null,
    val downloadCount: Int = 0,
    val status: String = "",
)

@Serializable
data class CollectionItemDto(
    val fileId: String,
    val name: String = "",
    val size: Long = 0,
    val mimeType: String = "application/octet-stream",
    val status: String = "",
    val downloadCount: Int = 0,
)

@Serializable
data class CollectionDto(
    val id: String,
    val code: String = "",
    val name: String = "",
    val status: String = "",
    val createdAt: Long = 0,
    val expiresAt: Long? = null,
    val hasPassword: Boolean = false,
    val downloadCount: Int = 0,
    val shareUrl: String = "",
    val itemCount: Int = 0,
    val totalSize: Long = 0,
    val items: List<CollectionItemDto> = emptyList(),
)

@Serializable
data class CollectionShareInfo(
    val found: Boolean = false,
    val expired: Boolean = false,
    val requiresPassword: Boolean = false,
    val name: String = "",
    val createdAt: Long = 0,
    val expiresAt: Long? = null,
    val downloadCount: Int = 0,
    val status: String = "",
    val items: List<CollectionItemDto> = emptyList(),
)

@Serializable
data class FolderShareItemDto(
    val fileId: String,
    val name: String = "",
    val size: Long = 0,
    val mimeType: String = "application/octet-stream",
    val path: String = "",
    val downloadCount: Int = 0,
    val status: String = "",
)

@Serializable
data class FolderShareDto(
    val id: String,
    val code: String = "",
    val folderId: String = "",
    val folderPath: String = "",
    val name: String = "",
    val status: String = "",
    val createdAt: Long = 0,
    val expiresAt: Long? = null,
    val hasPassword: Boolean = false,
    val downloadCount: Int = 0,
    val shareUrl: String = "",
    val itemCount: Int = 0,
    val totalSize: Long = 0,
)

@Serializable
data class FolderShareInfo(
    val found: Boolean = false,
    val expired: Boolean = false,
    val requiresPassword: Boolean = false,
    val name: String = "",
    val folderPath: String = "",
    val createdAt: Long = 0,
    val expiresAt: Long? = null,
    val downloadCount: Int = 0,
    val status: String = "",
    val items: List<FolderShareItemDto> = emptyList(),
)

@Serializable
data class TextDto(
    val id: String,
    val code: String = "",
    val title: String = "",
    val content: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val expiresAt: Long? = null,
    val hasPassword: Boolean = false,
    val shareUrl: String = "",
)

@Serializable
data class TextShareInfo(
    val found: Boolean = false,
    val expired: Boolean = false,
    val requiresPassword: Boolean = false,
    val title: String = "",
    val charCount: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val expiresAt: Long? = null,
)

@Serializable
data class ActivityLog(
    val id: Long = 0,
    val action: String = "",
    val actor: String? = null,
    val targetType: String? = null,
    val targetId: String? = null,
    val targetName: String? = null,
    val detail: JsonElement? = null,
    val result: String = "ok",
    val ip: String? = null,
    val userAgent: String? = null,
    val referer: String? = null,
    val createdAt: Long = 0,
)

@Serializable
data class LogsResponse(
    val logs: List<ActivityLog> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 30,
    val actions: List<LogAction> = emptyList(),
)

@Serializable
data class LogAction(val action: String, val count: Int)

@Serializable
data class LogsStats(
    val total: Int = 0,
    val failures: Int = 0,
    val last24h: Int = 0,
    val oldestAt: Long? = null,
)

@Serializable
data class R2Status(
    val configured: Boolean = false,
    val accountId: String = "",
    val accessKeyId: String = "",
    val bucket: String = "",
    val endpoint: String = "",
    val hasSecret: Boolean = false,
    val hasUsageToken: Boolean = false,
    val storedIn: String = "none",
)

@Serializable
data class R2Usage(
    val totalBytes: Long = 0,
    val publishedBytes: Long = 0,
    val pendingBytes: Long = 0,
    val standardBytes: Long = 0,
    val infrequentAccessBytes: Long = 0,
    val objectCount: Long = 0,
    val fetchedAt: Long = 0,
)

@Serializable
data class R2UsageStatus(
    val configured: Boolean = false,
    val hasToken: Boolean = false,
    val freeTierBytes: Long = 0,
    val usage: R2Usage? = null,
    val error: String? = null,
)

@Serializable
data class ShareLimitSettings(
    val enabled: Boolean = true,
    val maxFailures: Int = 10,
    val windowMinutes: Int = 15,
    val maxDownloads: Int = 60,
)

@Serializable
data class SiteStatus(
    val baseUrl: String = "",
    val mode: String = "auto",
    val autoDetected: String = "",
)

@Serializable
data class PasswordStatus(val storedIn: String = "env", val changedAt: Long? = null)

@Serializable
data class UsernameStatus(val username: String = "", val storedIn: String = "env")

@Serializable
data class BucketScanObject(
    val key: String,
    val name: String? = null,
    val size: Long = 0,
    val lastModified: Long? = null,
    val tracked: Boolean = false,
    val foreign: Boolean = false,
)

@Serializable
data class BucketMissingRecord(
    val id: String,
    val name: String = "",
    val key: String = "",
    val status: String = "",
    val size: Long = 0,
    val createdAt: Long = 0,
)

@Serializable
data class BucketScanResult(
    val scannedAt: Long = 0,
    val truncated: Boolean = false,
    val objectCount: Int = 0,
    val totalSize: Long = 0,
    val trackedCount: Int = 0,
    val orphanCount: Int = 0,
    val orphanBytes: Long = 0,
    val foreignCount: Int = 0,
    val missingCount: Int = 0,
    val objects: List<BucketScanObject> = emptyList(),
    val orphans: List<BucketScanObject> = emptyList(),
    val missing: List<BucketMissingRecord> = emptyList(),
)

@Serializable
data class KeyMigrationCandidate(
    val id: String,
    val name: String = "",
    val size: Long = 0,
    val oldKey: String = "",
    val newKey: String = "",
)

@Serializable
data class KeyMigrationPlan(val total: Int = 0, val samples: List<KeyMigrationCandidate> = emptyList())

@Serializable
data class KeyMigrationFailure(val id: String, val key: String, val reason: String)

@Serializable
data class KeyMigrationResult(
    val migrated: Int = 0,
    val skipped: Int = 0,
    val failed: List<KeyMigrationFailure> = emptyList(),
    val remaining: Int = 0,
    val total: Int = 0,
)

@Serializable
data class BucketDeleteResult(
    val deleted: List<String> = emptyList(),
    val failed: List<BucketDeleteFailure> = emptyList(),
    val skipped: List<String> = emptyList(),
)

@Serializable
data class BucketDeleteFailure(val key: String, val message: String)

@Serializable
data class CommitInfo(val sha: String = "", val date: Long = 0, val message: String = "")

@Serializable
data class UpdateStatus(
    val enabled: Boolean = false,
    val branch: String = "",
    val isGit: Boolean = false,
    val current: CommitInfo? = null,
    val remote: CommitInfo? = null,
    val behind: Int = 0,
    val hasUpdate: Boolean = false,
    val lastCheckedAt: Long? = null,
    val running: Boolean = false,
    val lastRun: String = "none",
    val error: String? = null,
)

@Serializable
data class UpdateLog(
    val log: String = "",
    val lastRun: String = "none",
    val running: Boolean = false,
)

// ─────────────────────────── request bodies ───────────────────────────

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(val token: String, val username: String = "")

@Serializable
data class ChangeUsernameRequest(val currentPassword: String, val newUsername: String)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class InitUploadRequest(
    val name: String,
    val size: Long,
    val mimeType: String,
    val expiresInDays: Int? = null,
    val password: String? = null,
    val folderId: String? = null,
    val hash: String? = null,
)

/**
 * `expiresInDays` uses JsonElement so that JsonNull ("never expires") and an absent
 * field ("keep current value") stay distinguishable.
 */
@Serializable
data class FilePatchRequest(
    val name: String? = null,
    val expiresInDays: JsonElement? = null,
    val password: String? = null,
    val clearPassword: Boolean? = null,
)

@Serializable
data class MoveFilesRequest(val ids: List<String>, val folderId: String? = null)

@Serializable
data class BulkDeleteRequest(val ids: List<String>)

@Serializable
data class CompleteUploadRequest(val parts: List<UploadedPart>? = null)

@Serializable
data class FolderCreateRequest(val name: String, val parentId: String? = null)

@Serializable
data class FolderPatchRequest(val name: String? = null, val parentId: JsonElement? = null)

@Serializable
data class CollectionCreateRequest(
    val name: String,
    val fileIds: List<String>,
    val expiresInDays: Int? = null,
    val password: String? = null,
)

@Serializable
data class CollectionPatchRequest(
    val name: String? = null,
    val expiresInDays: JsonElement? = null,
    val password: String? = null,
    val clearPassword: Boolean? = null,
)

@Serializable
data class FolderShareCreateRequest(
    val folderId: String,
    val name: String? = null,
    val expiresInDays: Int? = null,
    val password: String? = null,
)

@Serializable
data class FolderSharePatchRequest(
    val name: String? = null,
    val expiresInDays: JsonElement? = null,
    val password: String? = null,
    val clearPassword: Boolean? = null,
)

@Serializable
data class TextCreateRequest(
    val title: String? = null,
    val content: String,
    val expiresInDays: Int? = null,
    val password: String? = null,
)

@Serializable
data class TextPatchRequest(
    val title: String? = null,
    val content: String? = null,
    val expiresInDays: JsonElement? = null,
    val password: String? = null,
    val clearPassword: Boolean? = null,
)

@Serializable
data class PasswordBody(val password: String? = null)

@Serializable
data class R2SaveRequest(
    val accountId: String,
    val accessKeyId: String,
    val bucket: String,
    val secretAccessKey: String? = null,
    val endpoint: String? = null,
)

@Serializable
data class UsageTokenRequest(val token: String)

@Serializable
data class SiteSaveRequest(val baseUrl: String)

@Serializable
data class DeleteOrphansRequest(val keys: List<String>)

@Serializable
data class MigrateKeysRequest(val limit: Int = 100)

@Serializable
data class OkResponse(val ok: Boolean = true)

@Serializable
data class TokenResponse(val token: String = "")

// ─────────────────────────── response wrappers ───────────────────────────

@Serializable data class FilesResponse(val files: List<FileDto> = emptyList())
@Serializable data class FoldersResponse(val folders: List<FolderDto> = emptyList())
@Serializable data class FileResponse(val file: FileDto)
@Serializable data class FolderResponse(val folder: FolderDto)
@Serializable data class CollectionsResponse(val collections: List<CollectionDto> = emptyList())
@Serializable data class CollectionResponse(val collection: CollectionDto)
@Serializable data class FolderSharesResponse(val shares: List<FolderShareDto> = emptyList())
@Serializable data class FolderShareResponse(val share: FolderShareDto)
@Serializable data class TextsResponse(val texts: List<TextDto> = emptyList())
@Serializable data class TextResponse(val text: TextDto)
@Serializable data class TextRevealResponse(val content: String = "")
@Serializable data class DownloadUrlResponse(val url: String)
@Serializable data class DownloadAllResponse(val downloads: List<DownloadEntry> = emptyList())
@Serializable data class DownloadEntry(val name: String, val url: String)
@Serializable data class PartUrlResponse(val partNumber: Int, val url: String)
@Serializable data class MoveResponse(val moved: Int = 0, val folderId: String? = null)
@Serializable data class BulkDeleteResponse(
    val deleted: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
)
@Serializable data class DeleteFolderResponse(
    val ok: Boolean = true,
    val deletedFolders: Int = 0,
    val deletedFiles: Int = 0,
)
@Serializable data class R2TestResponse(val ok: Boolean = false, val bucket: String = "", val endpoint: String = "")
@Serializable data class UsageTokenResponse(val hasToken: Boolean = false, val configured: Boolean = false)
@Serializable data class ClearResponse(val ok: Boolean = true, val cleared: Int = 0)
@Serializable data class StartedResponse(val started: Boolean = true)
