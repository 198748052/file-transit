package com.filetransit.app.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filetransit.app.Graph
import com.filetransit.app.data.BucketDeleteResult
import com.filetransit.app.data.BucketScanResult
import com.filetransit.app.data.KeyMigrationPlan
import com.filetransit.app.data.KeyMigrationResult
import com.filetransit.app.data.PasswordStatus
import com.filetransit.app.data.R2SaveRequest
import com.filetransit.app.data.R2Status
import com.filetransit.app.data.R2UsageStatus
import com.filetransit.app.data.ShareLimitSettings
import com.filetransit.app.data.SiteStatus
import com.filetransit.app.data.UpdateLog
import com.filetransit.app.data.UpdateStatus
import com.filetransit.app.data.UsernameStatus
import com.filetransit.app.util.apiMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val username: UsernameStatus? = null,
    val password: PasswordStatus? = null,
    val r2: R2Status? = null,
    val usage: R2UsageStatus? = null,
    val shareLimit: ShareLimitSettings? = null,
    val site: SiteStatus? = null,
    val migration: KeyMigrationPlan? = null,
    val update: UpdateStatus? = null,
    val scan: BucketScanResult? = null,
    val updateLog: UpdateLog? = null,
    val error: String? = null,
)

class SettingsViewModel : ViewModel() {
    private val _ui = MutableStateFlow(SettingsUiState())
    val ui = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        _ui.value = _ui.value.copy(loading = _ui.value.r2 == null, error = null)
        viewModelScope.launch {
            try {
                coroutineScope {
                    val username = async { Graph.repo.usernameStatus() }
                    val password = async { Graph.repo.passwordStatus() }
                    val r2 = async { Graph.repo.r2Status() }
                    val usage = async { Graph.repo.r2Usage(false) }
                    val limit = async { Graph.repo.shareLimit() }
                    val site = async { Graph.repo.site() }
                    val migration = async { Graph.repo.keyMigrationStatus() }
                    val update = async { Graph.repo.updateStatus() }
                    _ui.value = _ui.value.copy(
                        loading = false,
                        username = username.await(),
                        password = password.await(),
                        r2 = r2.await(),
                        usage = usage.await(),
                        shareLimit = limit.await(),
                        site = site.await(),
                        migration = migration.await(),
                        update = update.await(),
                        error = null,
                    )
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = apiMessage(e))
            }
        }
    }

    fun changeUsername(currentPassword: String, newUsername: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        action(onError) {
            Graph.repo.changeUsername(currentPassword, newUsername)
            Graph.session.username = newUsername
            _ui.value = _ui.value.copy(username = Graph.repo.usernameStatus())
            onSuccess()
        }
    }

    fun clearUsername(onSuccess: () -> Unit, onError: (String) -> Unit) {
        action(onError) {
            Graph.repo.clearUsername()
            _ui.value = _ui.value.copy(username = Graph.repo.usernameStatus())
            onSuccess()
        }
    }

    fun changePassword(currentPassword: String, newPassword: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        action(onError) {
            Graph.repo.changePassword(currentPassword, newPassword)
            Graph.session.clearToken()
            onSuccess()
        }
    }

    fun clearPassword(onSuccess: () -> Unit, onError: (String) -> Unit) {
        action(onError) {
            Graph.repo.clearPassword()
            _ui.value = _ui.value.copy(password = Graph.repo.passwordStatus())
            onSuccess()
        }
    }

    fun saveR2(
        accountId: String,
        accessKeyId: String,
        secret: String,
        bucket: String,
        endpoint: String,
        onSuccess: (R2Status) -> Unit,
        onError: (String) -> Unit,
    ) {
        action(onError) {
            val status = Graph.repo.saveR2(
                R2SaveRequest(
                    accountId = accountId.trim(),
                    accessKeyId = accessKeyId.trim(),
                    bucket = bucket.trim(),
                    secretAccessKey = secret.takeIf { it.isNotBlank() },
                    endpoint = endpoint.trim().takeIf { it.isNotBlank() },
                ),
            )
            _ui.value = _ui.value.copy(r2 = status)
            onSuccess(status)
        }
    }

    fun testR2(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        action(onError) {
            val result = Graph.repo.testR2()
            onSuccess("连接成功：${result.bucket}")
        }
    }

    fun saveUsageToken(token: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        action(onError) {
            Graph.repo.saveUsageToken(token.trim())
            _ui.value = _ui.value.copy(usage = Graph.repo.r2Usage(false), r2 = Graph.repo.r2Status())
            onSuccess()
        }
    }

    fun refreshUsage(onError: (String) -> Unit) {
        action(onError) {
            _ui.value = _ui.value.copy(usage = Graph.repo.r2Usage(true))
        }
    }

    fun saveShareLimit(value: ShareLimitSettings, onSuccess: () -> Unit, onError: (String) -> Unit) {
        action(onError) {
            val saved = Graph.repo.saveShareLimit(value)
            _ui.value = _ui.value.copy(shareLimit = saved)
            onSuccess()
        }
    }

    fun saveSite(baseUrl: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        action(onError) {
            val saved = Graph.repo.saveSite(baseUrl.trim())
            _ui.value = _ui.value.copy(site = saved)
            onSuccess()
        }
    }

    fun scanBucket(onSuccess: (BucketScanResult) -> Unit, onError: (String) -> Unit) {
        action(onError) {
            val result = Graph.repo.scanBucket()
            _ui.value = _ui.value.copy(scan = result)
            onSuccess(result)
        }
    }

    fun deleteOrphans(keys: List<String>, onSuccess: (BucketDeleteResult) -> Unit, onError: (String) -> Unit) {
        action(onError) {
            val result = Graph.repo.deleteOrphans(keys)
            onSuccess(result)
            _ui.value = _ui.value.copy(scan = Graph.repo.scanBucket())
        }
    }

    fun migrateKeys(limit: Int, onSuccess: (KeyMigrationResult) -> Unit, onError: (String) -> Unit) {
        action(onError) {
            val result = Graph.repo.migrateKeys(limit)
            onSuccess(result)
            _ui.value = _ui.value.copy(
                migration = Graph.repo.keyMigrationStatus(),
                scan = null,
            )
        }
    }

    fun checkUpdate(onSuccess: (UpdateStatus) -> Unit, onError: (String) -> Unit) {
        action(onError) {
            val status = Graph.repo.checkUpdate()
            _ui.value = _ui.value.copy(update = status)
            onSuccess(status)
        }
    }

    fun runUpdate(onSuccess: () -> Unit, onError: (String) -> Unit) {
        action(onError) {
            Graph.repo.runUpdate()
            onSuccess()
            _ui.value = _ui.value.copy(update = Graph.repo.updateStatus())
        }
    }

    fun loadUpdateLog(onSuccess: (UpdateLog) -> Unit, onError: (String) -> Unit) {
        action(onError) {
            val log = Graph.repo.updateLog()
            _ui.value = _ui.value.copy(updateLog = log)
            onSuccess(log)
        }
    }

    private fun action(onError: (String) -> Unit, block: suspend () -> Unit) {
        _ui.value = _ui.value.copy(busy = true)
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                onError(apiMessage(e))
            } finally {
                _ui.value = _ui.value.copy(busy = false)
            }
        }
    }
}
