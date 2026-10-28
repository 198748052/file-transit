package com.filetransit.app.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filetransit.app.Graph
import com.filetransit.app.data.ActivityLog
import com.filetransit.app.data.LogAction
import com.filetransit.app.data.LogsStats
import com.filetransit.app.util.apiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LogsUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val logs: List<ActivityLog> = emptyList(),
    val stats: LogsStats? = null,
    val actions: List<LogAction> = emptyList(),
    val page: Int = 1,
    val total: Int = 0,
    val hasMore: Boolean = false,
    val actionFilter: String? = null,
    val resultFilter: String? = null,
    val visitorFilter: String? = null,
    val search: String = "",
    val error: String? = null,
)

class LogsViewModel : ViewModel() {
    private val _ui = MutableStateFlow(LogsUiState())
    val ui = _ui.asStateFlow()
    private var searchJob: Job? = null
    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        _ui.value = _ui.value.copy(loading = true, loadingMore = false, error = null, page = 1)
        loadJob = viewModelScope.launch {
            try {
                val state = _ui.value
                val response = Graph.repo.logs(
                    page = 1,
                    pageSize = PAGE_SIZE,
                    action = state.actionFilter,
                    result = state.resultFilter,
                    visitor = state.visitorFilter,
                    q = state.search,
                )
                val stats = Graph.repo.logStats()
                _ui.value = _ui.value.copy(
                    loading = false,
                    logs = response.logs,
                    actions = response.actions,
                    total = response.total,
                    page = 1,
                    hasMore = response.logs.size < response.total,
                    stats = stats,
                    error = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = apiMessage(e))
            }
        }
    }

    fun loadMore() {
        val state = _ui.value
        if (state.loading || state.loadingMore || !state.hasMore) return
        _ui.value = state.copy(loadingMore = true)
        loadJob = viewModelScope.launch {
            try {
                val next = state.page + 1
                val response = Graph.repo.logs(
                    page = next,
                    pageSize = PAGE_SIZE,
                    action = state.actionFilter,
                    result = state.resultFilter,
                    visitor = state.visitorFilter,
                    q = state.search,
                )
                val merged = state.logs + response.logs
                _ui.value = _ui.value.copy(
                    loadingMore = false,
                    logs = merged,
                    page = next,
                    total = response.total,
                    hasMore = merged.size < response.total,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loadingMore = false, error = apiMessage(e))
            }
        }
    }

    fun setAction(value: String?) {
        _ui.value = _ui.value.copy(actionFilter = value)
        refresh()
    }

    fun setResult(value: String?) {
        _ui.value = _ui.value.copy(resultFilter = value)
        refresh()
    }

    fun setVisitor(value: String?) {
        _ui.value = _ui.value.copy(visitorFilter = value)
        refresh()
    }

    fun setSearch(value: String) {
        _ui.value = _ui.value.copy(search = value)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            refresh()
        }
    }

    fun clear(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                Graph.repo.clearLogs()
                onSuccess()
                refresh()
            } catch (e: Exception) {
                onError(apiMessage(e))
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 30
    }
}
