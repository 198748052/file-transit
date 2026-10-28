package com.filetransit.app.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filetransit.app.Graph
import com.filetransit.app.data.FileDto
import com.filetransit.app.data.FolderDto
import com.filetransit.app.data.Stats
import com.filetransit.app.ui.components.flattenFolders
import com.filetransit.app.util.apiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FileSort(val label: String) {
    DATE_DESC("最新优先"),
    NAME_ASC("名称 A-Z"),
    SIZE_DESC("大文件优先"),
    DOWNLOAD_DESC("下载最多"),
}

data class FilesUiState(
    val loading: Boolean = false,
    val files: List<FileDto> = emptyList(),
    val folders: List<FolderDto> = emptyList(),
    val stats: Stats? = null,
    val folderId: String = "all",
    val search: String = "",
    val statusFilter: String = "all",
    val sort: FileSort = FileSort.DATE_DESC,
    val grid: Boolean = false,
    val selected: Set<String> = emptySet(),
    val error: String? = null,
) {
    val targetFolderId: String?
        get() = when (folderId) {
            "all", "root" -> null
            else -> folderId
        }

    val visibleFolders: List<FolderDto>
        get() {
            if (folderId == "all") return emptyList()
            val parent = if (folderId == "root") null else folderId
            val base = folders.filter { it.parentId == parent }
            return if (search.isBlank()) base.sortedBy { it.name.lowercase() }
            else base.filter { it.name.contains(search, ignoreCase = true) }
                .sortedBy { it.name.lowercase() }
        }

    val visibleFiles: List<FileDto>
        get() {
            var list = files
            if (statusFilter != "all") list = list.filter { it.status == statusFilter }
            if (search.isNotBlank()) list = list.filter { it.name.contains(search, ignoreCase = true) }
            return when (sort) {
                FileSort.DATE_DESC -> list.sortedByDescending { it.createdAt }
                FileSort.NAME_ASC -> list.sortedBy { it.name.lowercase() }
                FileSort.SIZE_DESC -> list.sortedByDescending { it.size }
                FileSort.DOWNLOAD_DESC -> list.sortedByDescending { it.downloadCount }
            }
        }

    val breadcrumb: List<FolderDto>
        get() {
            if (folderId == "all" || folderId == "root") return emptyList()
            val byId = folders.associateBy { it.id }
            val path = mutableListOf<FolderDto>()
            var cursor = byId[folderId]
            val guard = mutableSetOf<String>()
            while (cursor != null && guard.add(cursor.id)) {
                path.add(0, cursor)
                cursor = cursor.parentId?.let { byId[it] }
            }
            return path
        }

    val folderPath: String
        get() = when (folderId) {
            "all" -> "全部文件"
            "root" -> "根目录"
            else -> breadcrumb.joinToString(" / ") { it.name }.ifBlank { "根目录" }
        }

    val allFolderNodes get() = flattenFolders(folders)
}

class FilesViewModel : ViewModel() {
    private val _ui = MutableStateFlow(FilesUiState(loading = true))
    val ui = _ui.asStateFlow()
    private var loadJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            Graph.uploads.completed.collect { refresh(silent = true) }
        }
    }

    fun refresh(silent: Boolean = false) {
        loadJob?.cancel()
        val state = _ui.value
        if (!silent) _ui.value = state.copy(loading = state.files.isEmpty(), error = null)
        loadJob = viewModelScope.launch {
            try {
                coroutineScope {
                    val filesDeferred = async { Graph.repo.listFiles(state.folderId) }
                    val foldersDeferred = async { Graph.repo.listFolders() }
                    val statsDeferred = async { Graph.repo.stats() }
                    val files = filesDeferred.await()
                    val folders = foldersDeferred.await()
                    val stats = statsDeferred.await()
                    _ui.value = _ui.value.copy(
                        loading = false,
                        files = files,
                        folders = folders,
                        stats = stats,
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = apiMessage(e))
            }
        }
    }

    fun openFolder(folderId: String) {
        _ui.value = _ui.value.copy(folderId = folderId, selected = emptySet(), search = "")
        refresh()
    }

    fun setSearch(value: String) {
        _ui.value = _ui.value.copy(search = value)
    }

    fun setStatusFilter(value: String) {
        _ui.value = _ui.value.copy(statusFilter = value)
    }

    fun setSort(value: FileSort) {
        _ui.value = _ui.value.copy(sort = value)
    }

    fun toggleGrid() {
        _ui.value = _ui.value.copy(grid = !_ui.value.grid)
    }

    fun toggleSelection(id: String) {
        val selected = _ui.value.selected
        _ui.value = _ui.value.copy(selected = if (selected.contains(id)) selected - id else selected + id)
    }

    fun selectOnly(id: String) {
        _ui.value = _ui.value.copy(selected = setOf(id))
    }

    fun selectAll() {
        val ids = _ui.value.visibleFiles.filter { it.status != "deleted" }.map { it.id }.toSet()
        _ui.value = _ui.value.copy(selected = ids)
    }

    fun clearSelection() {
        _ui.value = _ui.value.copy(selected = emptySet())
    }

    fun createFolder(name: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (name.isBlank()) {
            onError("请输入文件夹名称")
            return
        }
        viewModelScope.launch {
            try {
                Graph.repo.createFolder(name.trim(), _ui.value.targetFolderId)
                onSuccess()
                refresh(silent = true)
            } catch (e: Exception) {
                onError(apiMessage(e))
            }
        }
    }

    fun renameFolder(id: String, name: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (name.isBlank()) {
            onError("请输入文件夹名称")
            return
        }
        viewModelScope.launch {
            try {
                Graph.repo.renameFolder(id, name.trim())
                onSuccess()
                refresh(silent = true)
            } catch (e: Exception) {
                onError(apiMessage(e))
            }
        }
    }

    fun moveFolder(id: String, parentId: String?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                Graph.repo.moveFolder(id, parentId)
                onSuccess()
                refresh(silent = true)
            } catch (e: Exception) {
                onError(apiMessage(e))
            }
        }
    }

    fun deleteFolder(id: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                Graph.repo.deleteFolder(id)
                onSuccess()
                refresh(silent = true)
            } catch (e: Exception) {
                onError(apiMessage(e))
            }
        }
    }

    fun moveSelected(targetFolderId: String?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val ids = _ui.value.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                Graph.repo.moveFiles(ids, targetFolderId)
                _ui.value = _ui.value.copy(selected = emptySet())
                onSuccess()
                refresh(silent = true)
            } catch (e: Exception) {
                onError(apiMessage(e))
            }
        }
    }

    fun deleteSelected(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val ids = _ui.value.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                ids.forEach { Graph.uploads.cancelLocalByFileId(it) }
                val result = Graph.repo.bulkDeleteFiles(ids)
                _ui.value = _ui.value.copy(selected = emptySet())
                onSuccess()
                if (result.failed.isNotEmpty()) onError("${result.failed.size} 个文件删除失败")
                refresh(silent = true)
            } catch (e: Exception) {
                onError(apiMessage(e))
            }
        }
    }

    fun createCollection(
        fileIds: List<String>,
        name: String,
        expiresInDays: Int?,
        password: String?,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (fileIds.isEmpty()) {
            onError("请先选择文件")
            return
        }
        viewModelScope.launch {
            try {
                val collection = Graph.repo.createCollection(
                    name = name.ifBlank { "文件集合" },
                    fileIds = fileIds,
                    expiresInDays = expiresInDays,
                    password = password,
                )
                _ui.value = _ui.value.copy(selected = emptySet())
                onSuccess(collection.shareUrl)
                refresh(silent = true)
            } catch (e: Exception) {
                onError(apiMessage(e))
            }
        }
    }

    fun createFolderShare(
        folderId: String,
        name: String,
        expiresInDays: Int?,
        password: String?,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val share = Graph.repo.createFolderShare(
                    folderId = folderId,
                    name = name.takeIf { it.isNotBlank() },
                    expiresInDays = expiresInDays,
                    password = password?.takeIf { it.isNotBlank() },
                )
                onSuccess(share.shareUrl)
                refresh(silent = true)
            } catch (e: Exception) {
                onError(apiMessage(e))
            }
        }
    }

    fun updateFile(
        id: String,
        name: String,
        days: String,
        keepExpiry: Boolean,
        password: String,
        clearPassword: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                Graph.repo.patchFile(
                    id = id,
                    name = name,
                    expiresInDays = if (keepExpiry) null else days.toIntOrNull(),
                    clearExpiry = if (keepExpiry) false else days.isBlank(),
                    password = password,
                    clearPassword = clearPassword,
                )
                onSuccess()
                refresh(silent = true)
            } catch (e: Exception) {
                onError(apiMessage(e))
            }
        }
    }

    fun deleteFile(file: FileDto, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                if (file.status == "uploading") {
                    Graph.uploads.cancelLocalByFileId(file.id)
                    runCatching { Graph.repo.abortUpload(file.id) }
                } else {
                    Graph.repo.deleteFile(file.id)
                }
                onSuccess()
                refresh(silent = true)
            } catch (e: Exception) {
                onError(apiMessage(e))
            }
        }
    }

    fun abortUpload(file: FileDto, onDone: () -> Unit) {
        Graph.uploads.cancelByFileId(file.id)
        viewModelScope.launch {
            runCatching { Graph.repo.abortUpload(file.id) }
            Graph.session.clearUploadUri(file.id)
            onDone()
            refresh(silent = true)
        }
    }

    fun resumeUpload(file: FileDto) {
        Graph.uploads.resume(file)
    }
}
