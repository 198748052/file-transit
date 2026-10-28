package com.filetransit.app.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filetransit.app.Graph
import com.filetransit.app.data.CollectionDto
import com.filetransit.app.data.FileDto
import com.filetransit.app.data.FolderDto
import com.filetransit.app.data.FolderShareDto
import com.filetransit.app.util.apiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SharesUiState(
    val loading: Boolean = true,
    val collections: List<CollectionDto> = emptyList(),
    val folderShares: List<FolderShareDto> = emptyList(),
    val folders: List<FolderDto> = emptyList(),
    val readyFiles: List<FileDto> = emptyList(),
    val error: String? = null,
)

class SharesViewModel : ViewModel() {
    private val _ui = MutableStateFlow(SharesUiState())
    val ui = _ui.asStateFlow()
    private var loadJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            Graph.uploads.completed.collect { refresh() }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        _ui.value = _ui.value.copy(loading = _ui.value.collections.isEmpty() && _ui.value.folderShares.isEmpty(), error = null)
        loadJob = viewModelScope.launch {
            try {
                coroutineScope {
                    val collections = async { Graph.repo.listCollections() }
                    val folderShares = async { Graph.repo.listFolderShares() }
                    val folders = async { Graph.repo.listFolders() }
                    val files = async { Graph.repo.listFiles("all").filter { it.status == "ready" } }
                    _ui.value = _ui.value.copy(
                        loading = false,
                        collections = collections.await(),
                        folderShares = folderShares.await(),
                        folders = folders.await(),
                        readyFiles = files.await(),
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

    fun createCollection(
        name: String,
        fileIds: List<String>,
        days: String,
        password: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (fileIds.isEmpty()) {
            onError("请选择至少一个文件")
            return
        }
        viewModelScope.launch {
            try {
                val collection = Graph.repo.createCollection(
                    name = name.ifBlank { "文件集合" },
                    fileIds = fileIds,
                    expiresInDays = days.toIntOrNull(),
                    password = password.takeIf { it.isNotBlank() },
                )
                onSuccess(collection.shareUrl)
                refresh()
            } catch (e: Exception) {
                onError(apiMessage(e))
            }
        }
    }

    fun updateCollection(
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
                Graph.repo.updateCollection(
                    id = id,
                    name = name,
                    expiresInDays = if (keepExpiry) null else days.toIntOrNull(),
                    clearExpiry = if (keepExpiry) false else days.isBlank(),
                    password = password,
                    clearPassword = clearPassword,
                )
                onSuccess()
                refresh()
            } catch (e: Exception) {
                onError(apiMessage(e))
            }
        }
    }

    fun deleteCollection(id: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                Graph.repo.deleteCollection(id)
                onSuccess()
                refresh()
            } catch (e: Exception) {
                onError(apiMessage(e))
            }
        }
    }

    fun createFolderShare(
        folderId: String,
        name: String,
        days: String,
        password: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val share = Graph.repo.createFolderShare(
                    folderId = folderId,
                    name = name.takeIf { it.isNotBlank() },
                    expiresInDays = days.toIntOrNull(),
                    password = password.takeIf { it.isNotBlank() },
                )
                onSuccess(share.shareUrl)
                refresh()
            } catch (e: Exception) {
                onError(apiMessage(e))
            }
        }
    }

    fun updateFolderShare(
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
                Graph.repo.updateFolderShare(
                    id = id,
                    name = name,
                    expiresInDays = if (keepExpiry) null else days.toIntOrNull(),
                    clearExpiry = if (keepExpiry) false else days.isBlank(),
                    password = password,
                    clearPassword = clearPassword,
                )
                onSuccess()
                refresh()
            } catch (e: Exception) {
                onError(apiMessage(e))
            }
        }
    }

    fun deleteFolderShare(id: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                Graph.repo.deleteFolderShare(id)
                onSuccess()
                refresh()
            } catch (e: Exception) {
                onError(apiMessage(e))
            }
        }
    }
}
