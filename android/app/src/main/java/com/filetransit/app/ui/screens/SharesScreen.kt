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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.filetransit.app.data.CollectionDto
import com.filetransit.app.data.FileDto
import com.filetransit.app.data.FolderDto
import com.filetransit.app.data.FolderShareDto
import com.filetransit.app.ui.Routes
import com.filetransit.app.ui.components.ConfirmDialog
import com.filetransit.app.ui.components.EmptyState
import com.filetransit.app.ui.components.ErrorBanner
import com.filetransit.app.ui.components.ExpiryPasswordFields
import com.filetransit.app.ui.components.FolderPickerDialog
import com.filetransit.app.ui.components.LoadingBox
import com.filetransit.app.ui.components.QrDialog
import com.filetransit.app.ui.components.flattenFolders
import com.filetransit.app.util.apiMessage
import com.filetransit.app.util.copyToClipboard
import com.filetransit.app.util.expiryLabel
import com.filetransit.app.util.formatBytes
import com.filetransit.app.util.formatDateShort
import com.filetransit.app.vm.SharesViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharesScreen(
    nav: NavHostController,
    outerPadding: PaddingValues,
    vm: SharesViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var tab by remember { mutableStateOf(0) }
    var pickFilesOpen by remember { mutableStateOf(false) }
    var collectionFiles by remember { mutableStateOf<List<FileDto>?>(null) }
    var pickFolderOpen by remember { mutableStateOf(false) }
    var folderShareFolder by remember { mutableStateOf<FolderDto?>(null) }
    var editingCollection by remember { mutableStateOf<CollectionDto?>(null) }
    var editingFolderShare by remember { mutableStateOf<FolderShareDto?>(null) }
    var deletingCollection by remember { mutableStateOf<CollectionDto?>(null) }
    var deletingFolderShare by remember { mutableStateOf<FolderShareDto?>(null) }
    var qr by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun message(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    Scaffold(
        modifier = Modifier.padding(outerPadding),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("分享管理") },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (tab == 0) pickFilesOpen = true else pickFolderOpen = true
                },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(if (tab == 0) "新建集合" else "分享文件夹") },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("集合链接") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("文件夹分享") })
            }
            if (ui.error != null) {
                Box(Modifier.padding(12.dp)) { ErrorBanner(ui.error, onRetry = { vm.refresh() }) }
            }
            if (ui.loading) {
                LoadingBox()
            } else if (tab == 0) {
                if (ui.collections.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Link,
                        title = "还没有集合",
                        subtitle = "把多个文件组合成一个分享链接",
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(ui.collections, key = { it.id }) { collection ->
                            CollectionCard(
                                collection = collection,
                                onOpen = { nav.navigate(Routes.collection(collection.code)) },
                                onCopy = { copyToClipboard(context, "集合链接", collection.shareUrl) },
                                onQr = { qr = "集合分享" to collection.shareUrl },
                                onEdit = { editingCollection = collection },
                                onDelete = { deletingCollection = collection },
                            )
                        }
                    }
                }
            } else {
                if (ui.folderShares.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Folder,
                        title = "还没有文件夹分享",
                        subtitle = "把整个文件夹（含子文件夹）生成一个链接",
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(ui.folderShares, key = { it.id }) { share ->
                            FolderShareCard(
                                share = share,
                                onOpen = { nav.navigate(Routes.folderShare(share.code)) },
                                onCopy = { copyToClipboard(context, "文件夹分享链接", share.shareUrl) },
                                onQr = { qr = "文件夹分享" to share.shareUrl },
                                onEdit = { editingFolderShare = share },
                                onDelete = { deletingFolderShare = share },
                            )
                        }
                    }
                }
            }
        }
    }

    if (pickFilesOpen) {
        FilePickerDialog(
            files = ui.readyFiles,
            onDismiss = { pickFilesOpen = false },
            onConfirm = { selected ->
                pickFilesOpen = false
                collectionFiles = selected
            },
        )
    }

    collectionFiles?.let { files ->
        SharesCreateCollectionDialog(
            fileCount = files.size,
            onDismiss = { collectionFiles = null },
            onCreate = { name, days, password ->
                vm.createCollection(
                    name = name,
                    fileIds = files.map { it.id },
                    days = days,
                    password = password,
                    onSuccess = { url ->
                        collectionFiles = null
                        qr = "集合已创建" to url
                    },
                    onError = { message(it) },
                )
            },
        )
    }

    if (pickFolderOpen) {
        FolderPickerDialog(
            title = "选择要分享的文件夹",
            folders = ui.folders,
            onDismiss = { pickFolderOpen = false },
            onPick = { id ->
                pickFolderOpen = false
                val folder = ui.folders.find { it.id == id }
                if (folder == null) message("请选择根目录以外的文件夹")
                else folderShareFolder = folder
            },
        )
    }

    folderShareFolder?.let { folder ->
        SharesCreateFolderShareDialog(
            folder = folder,
            onDismiss = { folderShareFolder = null },
            onCreate = { name, days, password ->
                vm.createFolderShare(
                    folderId = folder.id,
                    name = name,
                    days = days,
                    password = password,
                    onSuccess = { url ->
                        folderShareFolder = null
                        qr = "文件夹分享已创建" to url
                    },
                    onError = { message(it) },
                )
            },
        )
    }

    editingCollection?.let { collection ->
        EditShareDialog(
            id = collection.id,
            title = "编辑集合",
            name = collection.name,
            expiresAt = collection.expiresAt,
            hasPassword = collection.hasPassword,
            onDismiss = { editingCollection = null },
            onSave = { name, days, password, clear, keepExpiry ->
                vm.updateCollection(
                    id = collection.id,
                    name = name,
                    days = days,
                    keepExpiry = keepExpiry,
                    password = password,
                    clearPassword = clear,
                    onSuccess = { editingCollection = null; message("已保存") },
                    onError = { message(it) },
                )
            },
        )
    }

    editingFolderShare?.let { share ->
        EditShareDialog(
            id = share.id,
            title = "编辑文件夹分享",
            name = share.name,
            expiresAt = share.expiresAt,
            hasPassword = share.hasPassword,
            onDismiss = { editingFolderShare = null },
            onSave = { name, days, password, clear, keepExpiry ->
                vm.updateFolderShare(
                    id = share.id,
                    name = name,
                    days = days,
                    keepExpiry = keepExpiry,
                    password = password,
                    clearPassword = clear,
                    onSuccess = { editingFolderShare = null; message("已保存") },
                    onError = { message(it) },
                )
            },
        )
    }

    deletingCollection?.let { collection ->
        ConfirmDialog(
            title = "删除集合",
            text = "确定删除集合「${collection.name}」？分享链接会立即失效，文件本身不会被删除。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                vm.deleteCollection(collection.id, { deletingCollection = null; message("已删除") }, { message(it) })
            },
            onDismiss = { deletingCollection = null },
        )
    }

    deletingFolderShare?.let { share ->
        ConfirmDialog(
            title = "删除文件夹分享",
            text = "确定删除「${share.name}」的分享链接？文件本身不会被删除。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                vm.deleteFolderShare(share.id, { deletingFolderShare = null; message("已删除") }, { message(it) })
            },
            onDismiss = { deletingFolderShare = null },
        )
    }

    qr?.let { (title, content) -> QrDialog(title, content) { qr = null } }
}

@Composable
private fun CollectionCard(
    collection: CollectionDto,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onQr: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    collection.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (collection.hasPassword) {
                    Icon(Icons.Outlined.Lock, contentDescription = "有提取码", modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${collection.itemCount} 个文件 · ${formatBytes(collection.totalSize)} · 下载 ${collection.downloadCount} 次",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${formatDateShort(collection.createdAt)} 创建 · ${expiryLabel(collection.expiresAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                collection.shareUrl,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (collection.items.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    collection.items.take(3).joinToString("、") { it.name } +
                        if (collection.items.size > 3) " 等 ${collection.items.size} 个" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onOpen) { Icon(Icons.Outlined.Share, contentDescription = "查看", modifier = Modifier.size(19.dp)) }
                IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, contentDescription = "复制链接", modifier = Modifier.size(19.dp)) }
                IconButton(onClick = onQr) { Icon(Icons.Outlined.QrCode2, contentDescription = "二维码", modifier = Modifier.size(19.dp)) }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "编辑", modifier = Modifier.size(19.dp)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除", modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun FolderShareCard(
    share: FolderShareDto,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onQr: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text(
                    share.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (share.hasPassword) {
                    Icon(Icons.Outlined.Lock, contentDescription = "有提取码", modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                share.folderPath.ifBlank { share.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${share.itemCount} 个文件 · ${formatBytes(share.totalSize)} · 下载 ${share.downloadCount} 次 · ${expiryLabel(share.expiresAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                share.shareUrl,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onOpen) { Icon(Icons.Outlined.Share, contentDescription = "查看", modifier = Modifier.size(19.dp)) }
                IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, contentDescription = "复制链接", modifier = Modifier.size(19.dp)) }
                IconButton(onClick = onQr) { Icon(Icons.Outlined.QrCode2, contentDescription = "二维码", modifier = Modifier.size(19.dp)) }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "编辑", modifier = Modifier.size(19.dp)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除", modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun FilePickerDialog(
    files: List<FileDto>,
    onDismiss: () -> Unit,
    onConfirm: (List<FileDto>) -> Unit,
) {
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择文件（${selected.size}）") },
        text = {
            Column {
                Row {
                    TextButton(onClick = { selected = files.map { it.id }.toSet() }) { Text("全选") }
                    TextButton(onClick = { selected = emptySet() }) { Text("清空") }
                }
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(files, key = { it.id }) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (selected.contains(file.id)) selected - file.id else selected + file.id
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selected.contains(file.id),
                                onCheckedChange = {
                                    selected = if (selected.contains(file.id)) selected - file.id else selected + file.id
                                },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                Text(formatBytes(file.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(files.filter { selected.contains(it.id) }) },
                enabled = selected.isNotEmpty(),
            ) { Text("下一步") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SharesCreateCollectionDialog(
    fileCount: Int,
    onDismiss: () -> Unit,
    onCreate: (name: String, days: String, password: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建集合分享") },
        text = {
            Column {
                Text("已选择 $fileCount 个文件", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("集合名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                ExpiryPasswordFields(
                    days = days,
                    onDaysChange = { days = it },
                    password = password,
                    onPasswordChange = { password = it },
                    hasPassword = false,
                    clearPassword = false,
                    onClearPasswordChange = {},
                    editing = false,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, days, password) }) { Text("创建") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SharesCreateFolderShareDialog(
    folder: FolderDto,
    onDismiss: () -> Unit,
    onCreate: (name: String, days: String, password: String) -> Unit,
) {
    var name by remember { mutableStateOf(folder.name) }
    var days by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分享文件夹") },
        text = {
            Column {
                Text("分享「${folder.name}」及其全部子文件夹", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分享名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                ExpiryPasswordFields(
                    days = days,
                    onDaysChange = { days = it },
                    password = password,
                    onPasswordChange = { password = it },
                    hasPassword = false,
                    clearPassword = false,
                    onClearPasswordChange = {},
                    editing = false,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, days, password) }) { Text("创建链接") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun EditShareDialog(
    id: String,
    title: String,
    name: String,
    expiresAt: Long?,
    hasPassword: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, days: String, password: String, clearPassword: Boolean, keepExpiry: Boolean) -> Unit,
) {
    val initialDays = remember(id) {
        expiresAt?.let { ((it - System.currentTimeMillis()) / 86_400_000).coerceAtLeast(1).toString() } ?: ""
    }
    var value by remember(id) { mutableStateOf(name) }
    var days by remember(id) { mutableStateOf(initialDays) }
    var password by remember(id) { mutableStateOf("") }
    var clearPassword by remember(id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                ExpiryPasswordFields(
                    days = days,
                    onDaysChange = { days = it },
                    password = password,
                    onPasswordChange = { password = it },
                    hasPassword = hasPassword,
                    clearPassword = clearPassword,
                    onClearPasswordChange = { clearPassword = it },
                    editing = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value, days, password, clearPassword, days == initialDays) }, enabled = value.isNotBlank()) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
