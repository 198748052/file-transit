package com.filetransit.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filetransit.app.data.ActivityLog
import com.filetransit.app.ui.components.ConfirmDialog
import com.filetransit.app.ui.components.EmptyState
import com.filetransit.app.ui.components.ErrorBanner
import com.filetransit.app.ui.components.LoadingBox
import com.filetransit.app.ui.components.StatTile
import com.filetransit.app.util.formatDate
import com.filetransit.app.vm.LogsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    outerPadding: PaddingValues,
    vm: LogsViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var actionMenu by remember { mutableStateOf(false) }
    var resultMenu by remember { mutableStateOf(false) }
    var visitorMenu by remember { mutableStateOf(false) }
    var clearOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.padding(outerPadding),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("操作日志") },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { clearOpen = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "清空日志")
                    }
                },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            ui.stats?.let { stats ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatTile("总记录", stats.total.toString(), Modifier.weight(1f))
                        StatTile("近 24 小时", stats.last24h.toString(), Modifier.weight(1f))
                        StatTile("失败", stats.failures.toString(), Modifier.weight(1f))
                        StatTile("当前筛选", ui.total.toString(), Modifier.weight(1f))
                    }
                }
            }
            if (ui.error != null) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    ErrorBanner(ui.error, onRetry = { vm.refresh() })
                }
            }
            OutlinedTextField(
                value = ui.search,
                onValueChange = vm::setSearch,
                placeholder = { Text("搜索目标、操作者、IP、UA") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    AssistChip(
                        onClick = { resultMenu = true },
                        label = { Text(when (ui.resultFilter) { "ok" -> "成功"; "fail" -> "失败"; else -> "结果：全部" }) },
                    )
                    DropdownMenu(expanded = resultMenu, onDismissRequest = { resultMenu = false }) {
                        listOf(null to "全部", "ok" to "成功", "fail" to "失败").forEach { (value, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { vm.setResult(value); resultMenu = false })
                        }
                    }
                }
                Box {
                    AssistChip(
                        onClick = { visitorMenu = true },
                        label = { Text(when (ui.visitorFilter) { "public" -> "公开访问"; "admin" -> "管理员"; else -> "访问者：全部" }) },
                    )
                    DropdownMenu(expanded = visitorMenu, onDismissRequest = { visitorMenu = false }) {
                        listOf(null to "全部", "public" to "公开访问", "admin" to "管理员").forEach { (value, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { vm.setVisitor(value); visitorMenu = false })
                        }
                    }
                }
                Box {
                    AssistChip(
                        onClick = { actionMenu = true },
                        label = { Text(ui.actionFilter ?: "操作类型：全部") },
                    )
                    DropdownMenu(expanded = actionMenu, onDismissRequest = { actionMenu = false }) {
                        DropdownMenuItem(text = { Text("全部操作") }, onClick = { vm.setAction(null); actionMenu = false })
                        ui.actions.forEach { action ->
                            DropdownMenuItem(
                                text = { Text("${action.action} (${action.count})") },
                                onClick = { vm.setAction(action.action); actionMenu = false },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            if (ui.loading && ui.logs.isEmpty()) {
                LoadingBox()
            } else if (ui.logs.isEmpty()) {
                EmptyState(icon = Icons.Outlined.Search, title = "没有日志", subtitle = "上传、下载、分享等操作会自动记录在这里")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(ui.logs, key = { it.id }, contentType = { "log" }) { log ->
                        LogCard(log)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            if (ui.hasMore) {
                                if (ui.loadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    TextButton(onClick = vm::loadMore) { Text("加载更多（${ui.logs.size}/${ui.total}）") }
                                }
                            } else {
                                Text(
                                    "已加载全部 ${ui.total} 条",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (clearOpen) {
        ConfirmDialog(
            title = "清空日志",
            text = "确定清空全部操作日志？该操作不可恢复。",
            confirmText = "清空",
            destructive = true,
            onConfirm = {
                clearOpen = false
                vm.clear(
                    { scope.launch { snackbar.showSnackbar("日志已清空") } },
                    { scope.launch { snackbar.showSnackbar(it) } },
                )
            },
            onDismiss = { clearOpen = false },
        )
    }
}

@Composable
private fun LogCard(log: ActivityLog) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 10.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    log.action,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (log.result == "fail") MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        if (log.result == "fail") "失败" else "成功",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (log.result == "fail") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                (log.targetName ?: log.targetType ?: "-") + (log.actor?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatDate(log.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                DetailLine("IP", log.ip)
                DetailLine("User-Agent", log.userAgent)
                DetailLine("Referer", log.referer)
                DetailLine("目标 ID", log.targetId)
                if (log.detail != null) {
                    DetailLine("详情", log.detail.toString())
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(78.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
        )
    }
}
