package com.filetransit.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.filetransit.app.data.TextDto
import com.filetransit.app.ui.Routes
import com.filetransit.app.ui.ShareInbox
import com.filetransit.app.ui.components.ConfirmDialog
import com.filetransit.app.ui.components.EmptyState
import com.filetransit.app.ui.components.ErrorBanner
import com.filetransit.app.ui.components.ExpiryPasswordFields
import com.filetransit.app.ui.components.LoadingBox
import com.filetransit.app.ui.components.QrDialog
import com.filetransit.app.util.apiMessage
import com.filetransit.app.util.copyToClipboard
import com.filetransit.app.util.expiryLabel
import com.filetransit.app.util.formatDateShort
import com.filetransit.app.vm.TextsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextsScreen(
    nav: NavHostController,
    outerPadding: PaddingValues,
    vm: TextsViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var showSearch by remember { mutableStateOf(false) }
    var createWithContent by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<TextDto?>(null) }
    var deleting by remember { mutableStateOf<TextDto?>(null) }
    var qr by remember { mutableStateOf<Pair<String, String>?>(null) }
    val sharedText by ShareInbox.text.collectAsState()

    LaunchedEffect(sharedText) {
        sharedText?.let {
            createWithContent = it
            ShareInbox.text.value = null
        }
    }

    fun message(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    Scaffold(
        modifier = Modifier.padding(outerPadding),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("文本中转") },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Outlined.Search, contentDescription = "搜索")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { createWithContent = "" },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("新建文本") },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            if (showSearch || ui.search.isNotBlank()) {
                OutlinedTextField(
                    value = ui.search,
                    onValueChange = vm::setSearch,
                    placeholder = { Text("搜索标题或内容") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (ui.search.isNotBlank()) {
                            IconButton(onClick = { vm.setSearch("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = "清空")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (ui.error != null) {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    ErrorBanner(ui.error, onRetry = { vm.refresh() })
                }
            }
            if (ui.loading && ui.texts.isEmpty()) {
                LoadingBox()
            } else if (ui.texts.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Description,
                    title = if (ui.search.isNotBlank()) "没有匹配的文本" else "还没有文本",
                    subtitle = "保存剪贴板文字，生成可分享的链接",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(ui.texts, key = { it.id }, contentType = { "text" }) { text ->
                        TextRow(
                            text = text,
                            onOpen = { nav.navigate(Routes.text(text.code)) },
                            onCopy = { copyToClipboard(context, "文本内容", text.content) },
                            onCopyLink = { copyToClipboard(context, "分享链接", text.shareUrl) },
                            onQr = { qr = "文本分享" to text.shareUrl },
                            onEdit = { editing = text },
                            onDelete = { deleting = text },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    if (createWithContent != null) {
        TextEditorDialog(
            text = null,
            initialContent = createWithContent.orEmpty(),
            onDismiss = { createWithContent = null },
            onSave = { title, content, days, password, _, _ ->
                vm.create(
                    title = title,
                    content = content,
                    days = days,
                    password = password,
                    onSuccess = {
                        createWithContent = null
                        message("已保存")
                    },
                    onError = { message(it) },
                )
            },
        )
    }

    editing?.let { text ->
        TextEditorDialog(
            text = text,
            initialContent = text.content,
            onDismiss = { editing = null },
            onSave = { title, content, days, password, clearPassword, keepExpiry ->
                vm.update(
                    id = text.id,
                    title = title,
                    content = content,
                    days = days,
                    keepExpiry = keepExpiry,
                    password = password,
                    clearPassword = clearPassword,
                    onSuccess = {
                        editing = null
                        message("已保存")
                    },
                    onError = { message(it) },
                )
            },
        )
    }

    deleting?.let { text ->
        ConfirmDialog(
            title = "删除文本",
            text = "确定删除「${text.title}」？删除后分享链接立即失效。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                vm.delete(text.id, { deleting = null; message("已删除") }, { message(it) })
            },
            onDismiss = { deleting = null },
        )
    }

    qr?.let { (title, content) -> QrDialog(title, content) { qr = null } }
}

@Composable
private fun TextRow(
    text: TextDto,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onCopyLink: () -> Unit,
    onQr: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text.title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (text.hasPassword) {
                Icon(Icons.Outlined.Lock, contentDescription = "有提取码", modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text.content.replace('\n', ' ').take(120),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${text.content.length} 字 · ${formatDateShort(text.updatedAt)} · ${expiryLabel(text.expiresAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, contentDescription = "复制内容", modifier = Modifier.size(19.dp)) }
            IconButton(onClick = onCopyLink) { Icon(Icons.Outlined.Link, contentDescription = "复制链接", modifier = Modifier.size(19.dp)) }
            IconButton(onClick = onQr) { Icon(Icons.Outlined.QrCode2, contentDescription = "二维码", modifier = Modifier.size(19.dp)) }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "编辑", modifier = Modifier.size(19.dp)) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除", modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun TextEditorDialog(
    text: TextDto?,
    initialContent: String,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, days: String, password: String, clearPassword: Boolean, keepExpiry: Boolean) -> Unit,
) {
    val initialDays = remember(text?.id) {
        text?.expiresAt?.let {
            ((it - System.currentTimeMillis()) / 86_400_000).coerceAtLeast(1).toString()
        } ?: ""
    }
    var title by remember(text?.id) { mutableStateOf(text?.title.orEmpty()) }
    var content by remember(text?.id) { mutableStateOf(initialContent) }
    var days by remember(text?.id) { mutableStateOf(initialDays) }
    var password by remember(text?.id) { mutableStateOf("") }
    var clearPassword by remember(text?.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (text == null) "新建文本" else "编辑文本") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    minLines = 6,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    clipboard.getText()?.text?.let { content = it }
                }) { Text("从剪贴板粘贴") }
                Spacer(Modifier.height(8.dp))
                ExpiryPasswordFields(
                    days = days,
                    onDaysChange = { days = it },
                    password = password,
                    onPasswordChange = { password = it },
                    hasPassword = text?.hasPassword == true,
                    clearPassword = clearPassword,
                    onClearPasswordChange = { clearPassword = it },
                    editing = text != null,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, content, days, password, clearPassword, days == initialDays) },
                enabled = content.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
