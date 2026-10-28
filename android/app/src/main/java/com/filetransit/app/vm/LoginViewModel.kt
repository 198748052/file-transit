package com.filetransit.app.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filetransit.app.Graph
import com.filetransit.app.data.SessionStore
import com.filetransit.app.util.apiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class LoginViewModel : ViewModel() {
    private val _ui = MutableStateFlow(
        LoginUiState(
            server = Graph.session.server.value,
            username = Graph.session.username,
        ),
    )
    val ui = _ui.asStateFlow()

    fun setServer(value: String) = _ui.value.let { _ui.value = it.copy(server = value, error = null) }
    fun setUsername(value: String) = _ui.value.let { _ui.value = it.copy(username = value, error = null) }
    fun setPassword(value: String) = _ui.value.let { _ui.value = it.copy(password = value, error = null) }

    fun login(onSuccess: () -> Unit) {
        val state = _ui.value
        if (state.server.isBlank()) {
            _ui.value = state.copy(error = "请填写服务器地址")
            return
        }
        if (state.username.isBlank() || state.password.isBlank()) {
            _ui.value = state.copy(error = "请填写用户名和密码")
            return
        }
        _ui.value = state.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val server = SessionStore.normalizeServer(state.server)
                Graph.session.saveServer(server)
                val result = Graph.repo.login(state.username.trim(), state.password)
                Graph.session.saveToken(result.token)
                Graph.session.username = result.username.ifBlank { state.username.trim() }
                _ui.value = _ui.value.copy(loading = false, password = "")
                onSuccess()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = apiMessage(e))
            }
        }
    }

    fun saveServerOnly(value: String): Boolean {
        if (value.isBlank()) return false
        Graph.session.saveServer(value)
        _ui.value = _ui.value.copy(server = Graph.session.server.value)
        return true
    }
}
