package com.filetransit.app.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // ── auth ──
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("api/auth/me")
    suspend fun me(): UsernameStatus

    @GET("api/auth/username")
    suspend fun usernameStatus(): UsernameStatus

    @POST("api/auth/username")
    suspend fun changeUsername(@Body body: ChangeUsernameRequest): OkResponse

    @DELETE("api/auth/username")
    suspend fun clearUsername(): OkResponse

    @GET("api/auth/password")
    suspend fun passwordStatus(): PasswordStatus

    @POST("api/auth/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): OkResponse

    @DELETE("api/auth/password")
    suspend fun clearPassword(): OkResponse

    // ── files ──
    @GET("api/files")
    suspend fun listFiles(@Query("folderId") folderId: String? = null): FilesResponse

    @POST("api/files/move")
    suspend fun moveFiles(@Body body: MoveFilesRequest): MoveResponse

    @POST("api/files/init")
    suspend fun initUpload(@Body body: InitUploadRequest): InitUploadResponse

    @GET("api/files/{id}/parts")
    suspend fun getParts(@Path("id") id: String): PartsResponse

    @GET("api/files/{id}/parts/{part}")
    suspend fun partUrl(@Path("id") id: String, @Path("part") part: Int): PartUrlResponse

    @POST("api/files/{id}/complete")
    suspend fun completeUpload(@Path("id") id: String, @Body body: CompleteUploadRequest): FileResponse

    @POST("api/files/{id}/abort")
    suspend fun abortUpload(@Path("id") id: String): OkResponse

    @PATCH("api/files/{id}")
    suspend fun patchFile(@Path("id") id: String, @Body body: FilePatchRequest): FileResponse

    @DELETE("api/files/{id}")
    suspend fun deleteFile(@Path("id") id: String): OkResponse

    @POST("api/files/bulk-delete")
    suspend fun bulkDeleteFiles(@Body body: BulkDeleteRequest): BulkDeleteResponse

    @GET("api/files/{id}/download")
    suspend fun downloadFile(@Path("id") id: String): DownloadUrlResponse

    @GET("api/files/{id}/preview")
    suspend fun previewFile(@Path("id") id: String): PreviewInfo

    @GET("api/files/stats")
    suspend fun stats(): Stats

    // ── folders ──
    @GET("api/folders")
    suspend fun listFolders(): FoldersResponse

    @POST("api/folders")
    suspend fun createFolder(@Body body: FolderCreateRequest): FolderResponse

    @PATCH("api/folders/{id}")
    suspend fun patchFolder(@Path("id") id: String, @Body body: FolderPatchRequest): FolderResponse

    @DELETE("api/folders/{id}")
    suspend fun deleteFolder(@Path("id") id: String): DeleteFolderResponse

    // ── collections ──
    @GET("api/collections")
    suspend fun listCollections(): CollectionsResponse

    @POST("api/collections")
    suspend fun createCollection(@Body body: CollectionCreateRequest): CollectionResponse

    @PATCH("api/collections/{id}")
    suspend fun patchCollection(@Path("id") id: String, @Body body: CollectionPatchRequest): CollectionResponse

    @DELETE("api/collections/{id}")
    suspend fun deleteCollection(@Path("id") id: String): OkResponse

    @GET("api/collections/share/{code}")
    suspend fun collectionShareInfo(@Path("code") code: String): CollectionShareInfo

    @POST("api/collections/share/{code}/item/{fileId}/download")
    suspend fun collectionItemDownload(
        @Path("code") code: String,
        @Path("fileId") fileId: String,
        @Body body: PasswordBody,
    ): DownloadUrlResponse

    @POST("api/collections/share/{code}/item/{fileId}/preview")
    suspend fun collectionItemPreview(
        @Path("code") code: String,
        @Path("fileId") fileId: String,
        @Body body: PasswordBody,
    ): PreviewInfo

    @POST("api/collections/share/{code}/download-all")
    suspend fun collectionDownloadAll(
        @Path("code") code: String,
        @Body body: PasswordBody,
    ): DownloadAllResponse

    // ── texts ──
    @GET("api/texts")
    suspend fun listTexts(@Query("q") q: String? = null): TextsResponse

    @POST("api/texts")
    suspend fun createText(@Body body: TextCreateRequest): TextResponse

    @PATCH("api/texts/{id}")
    suspend fun patchText(@Path("id") id: String, @Body body: TextPatchRequest): TextResponse

    @DELETE("api/texts/{id}")
    suspend fun deleteText(@Path("id") id: String): OkResponse

    @GET("api/texts/share/{code}")
    suspend fun textShareInfo(@Path("code") code: String): TextShareInfo

    @POST("api/texts/share/{code}")
    suspend fun revealText(@Path("code") code: String, @Body body: PasswordBody): TextRevealResponse

    // ── folder shares ──
    @GET("api/folder-shares")
    suspend fun listFolderShares(): FolderSharesResponse

    @POST("api/folder-shares")
    suspend fun createFolderShare(@Body body: FolderShareCreateRequest): FolderShareResponse

    @PATCH("api/folder-shares/{id}")
    suspend fun patchFolderShare(@Path("id") id: String, @Body body: FolderSharePatchRequest): FolderShareResponse

    @DELETE("api/folder-shares/{id}")
    suspend fun deleteFolderShare(@Path("id") id: String): OkResponse

    @GET("api/folder-shares/share/{code}")
    suspend fun folderShareInfo(@Path("code") code: String): FolderShareInfo

    @POST("api/folder-shares/share/{code}/file/{fileId}/download")
    suspend fun folderShareItemDownload(
        @Path("code") code: String,
        @Path("fileId") fileId: String,
        @Body body: PasswordBody,
    ): DownloadUrlResponse

    @POST("api/folder-shares/share/{code}/file/{fileId}/preview")
    suspend fun folderShareItemPreview(
        @Path("code") code: String,
        @Path("fileId") fileId: String,
        @Body body: PasswordBody,
    ): PreviewInfo

    @POST("api/folder-shares/share/{code}/download-all")
    suspend fun folderShareDownloadAll(
        @Path("code") code: String,
        @Body body: PasswordBody,
    ): DownloadAllResponse

    // ── public file share ──
    @GET("api/share/{code}")
    suspend fun shareInfo(@Path("code") code: String): ShareInfo

    @POST("api/share/{code}/download")
    suspend fun shareDownload(@Path("code") code: String, @Body body: PasswordBody): DownloadUrlResponse

    @POST("api/share/{code}/preview")
    suspend fun sharePreview(@Path("code") code: String, @Body body: PasswordBody): PreviewInfo

    // ── settings ──
    @GET("api/settings/r2")
    suspend fun r2Status(): R2Status

    @PUT("api/settings/r2")
    suspend fun saveR2(@Body body: R2SaveRequest): R2Status

    @POST("api/settings/r2/test")
    suspend fun testR2(): R2TestResponse

    @GET("api/settings/r2/usage")
    suspend fun r2Usage(@Query("refresh") refresh: String? = null): R2UsageStatus

    @PUT("api/settings/r2/usage-token")
    suspend fun saveUsageToken(@Body body: UsageTokenRequest): UsageTokenResponse

    @GET("api/settings/share-limit")
    suspend fun shareLimit(): ShareLimitSettings

    @PUT("api/settings/share-limit")
    suspend fun saveShareLimit(@Body body: ShareLimitSettings): ShareLimitSettings

    @GET("api/settings/site")
    suspend fun site(): SiteStatus

    @PUT("api/settings/site")
    suspend fun saveSite(@Body body: SiteSaveRequest): SiteStatus

    // ── bucket ──
    @GET("api/bucket/scan")
    suspend fun scanBucket(): BucketScanResult

    @POST("api/bucket/delete-orphans")
    suspend fun deleteOrphans(@Body body: DeleteOrphansRequest): BucketDeleteResult

    @GET("api/bucket/key-migration")
    suspend fun keyMigrationStatus(): KeyMigrationPlan

    @POST("api/bucket/migrate-keys")
    suspend fun migrateKeys(@Body body: MigrateKeysRequest): KeyMigrationResult

    // ── logs ──
    @GET("api/logs")
    suspend fun logs(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 30,
        @Query("action") action: String? = null,
        @Query("result") result: String? = null,
        @Query("visitor") visitor: String? = null,
        @Query("q") q: String? = null,
    ): LogsResponse

    @GET("api/logs/stats")
    suspend fun logStats(): LogsStats

    @DELETE("api/logs")
    suspend fun clearLogs(): ClearResponse

    // ── update ──
    @GET("api/update/status")
    suspend fun updateStatus(): UpdateStatus

    @POST("api/update/check")
    suspend fun checkUpdate(): UpdateStatus

    @POST("api/update/run")
    suspend fun runUpdate(): StartedResponse

    @GET("api/update/log")
    suspend fun updateLog(): UpdateLog
}
