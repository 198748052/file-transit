package com.filetransit.app.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filetransit.app.Graph
import com.filetransit.app.data.TextDto
import com.filetransit.app.util.apiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TextsUiState(
    val loading: Boolean = true,
    val texts: List<TextDto> = emptyList(),
    val search: String = "",
    val error: String? = null,
)

class TextsViewModel : ViewModel() {
    private val _ui = MutableStateFlow(TextsUiState())
    val ui = _ui.asStateFlow()
    private var searchJob: Job? = null
    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        _ui.value = _ui.value.copy(loading = _ui.value.texts.isEmpty(), error = null)
        loadJob = viewModelScope.launch {
            try {
                val texts = Graph.repo.listTexts(_ui.value.search)
                _ui.value = _ui.value.copy(loading = false, texts = texts, error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = apiMessage(e))
            }
        }
    }

    fun setSearch(value: String) {
        _ui.value = _ui.value.copy(search = value)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            refresh()
        }
    }

    fun create(
        title: String,
        content: String,
        days: String,
        password: String,
        onSuccess: (TextDto) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (content.isBlank()) {
            onError("请输入文本内容")
            return
        }
        viewModelScope.launch {
            try {
                val text = Graph.repo.createText(
                    title = title,
                    content = content,
                    expiresInDays = days.toIntOrNull(),
                    password = password.takeIf { it.isNotBlank() },
                )
                onSuccess(text)
                refresh()
            } catch (e: Exception) {
                onError(apiMessage(e))
            }
        }
    }

    fun update(
        id: String,
        title: String,
        content: String,
        days: String,
        keepExpiry: Boolean,
        password: String,
        clearPassword: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (content.isBlank()) {
            onError("请输入文本内容")
            return
        }
        viewModelScope.launch {
            try {
                Graph.repo.updateText(
                    id = id,
                    title = title,
                    content = content,
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

    fun delete(id: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                Graph.repo.deleteText(id)
                onSuccess()
                refresh()
            } catch (e: Exception) {
                onError(apiMessage(e))
            }
        }
    }
}
