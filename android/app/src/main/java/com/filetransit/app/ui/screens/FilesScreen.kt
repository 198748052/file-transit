package com.filetransit.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.filetransit.app.Graph
import com.filetransit.app.data.FileDto
import com.filetransit.app.data.FolderDto
import com.filetransit.app.ui.PreviewInbox
import com.filetransit.app.ui.PreviewRequest
import com.filetransit.app.ui.Routes
import com.filetransit.app.ui.components.ConfirmDialog
import com.filetransit.app.ui.components.EmptyState
import com.filetransit.app.ui.components.ErrorBanner
import com.filetransit.app.ui.components.ExpiryPasswordFields
import com.filetransit.app.ui.components.FolderPickerDialog
import com.filetransit.app.ui.components.LoadingBox
import com.filetransit.app.ui.components.QrDialog
import com.filetransit.app.ui.components.StatTile
import com.filetransit.app.util.apiMessage
import com.filetransit.app.util.copyToClipboard
import com.filetransit.app.util.enqueueDownload
import com.filetransit.app.util.expiryLabel
import com.filetransit.app.util.fileIcon
import com.filetransit.app.util.formatBytes
import com.filetransit.app.util.formatDateShort
import com.filetransit.app.util.statusLabel
import com.filetransit.app.vm.FileSort as SortOption
import com.filetransit.app.vm.FilesViewModel
import com.filetransit.app.vm.FilesUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    nav: NavHostController,
    outerPadding: PaddingValues,
    vm: FilesViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val uploads by Graph.uploads.tasks.collectAsStateWithLifecycle()

    var showSearch by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var fabMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    var showCreateFolder by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var fileSheet by remember { mutableStateOf<FileDto?>(null) }
    var folderSheet by remember { mutableStateOf<FolderDto?>(null) }
    var editFile by remember { mutableStateOf<FileDto?>(null) }
    var renameFolder by remember { mutableStateOf<FolderDto?>(null) }
    var deleteFile by remember { mutableStateOf<FileDto?>(null) }
    var deleteFolder by remember { mutableStateOf<FolderDto?>(null) }
    var showSelectionDelete by remember { mutableStateOf(false) }
    var showMoveFiles by remember { mutableStateOf(false) }
    var movingFolder by remember { mutableStateOf<FolderDto?>(null) }
    var collectionIds by remember { mutableStateOf<List<String>?>(null) }
    var shareFolder by remember { mutableStateOf<FolderDto?>(null) }
    var qr by remember { mutableStateOf<Pair<String, String>?>(null) }

    var pendingFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingFolder by remember { mutableStateOf<Uri?>(null) }
    var uploadOptions by remember { mutableStateOf(false) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            pendingFiles = uris
            pendingFolder = null
            uploadOptions = true
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pendingFolder = uri
            pendingFiles = emptyList()
            uploadOptions = true
        }
    }

    fun showMessage(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun openPreview(file: FileDto) {
        scope.launch {
            try {
                val info = Graph.repo.preview(file.id)
                PreviewInbox.request = PreviewRequest(
                    url = info.url,
                    name = info.name.ifBlank { file.name },
                    mime = info.mimeType.ifBlank { file.mimeType },
                )
                nav.navigate(Routes.PREVIEW)
            } catch (e: Exception) {
                showMessage(apiMessage(e))
            }
        }
    }

    fun download(file: FileDto) {
        scope.launch {
            try {
                val url = Graph.repo.downloadUrl(file.id)
                enqueueDownload(context, url, file.name)
            } catch (e: Exception) {
                showMessage(apiMessage(e))
            }
        }
    }

    Scaffold(
        modifier = Modifier.padding(outerPadding),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (ui.selected.isNotEmpty()) {
                TopAppBar(
                    title = { Text("已选 ${ui.selected.size} 项") },
                    navigationIcon = {
                        IconButton(onClick = vm::clearSelection) {
                            Icon(Icons.Outlined.Close, contentDescription = "取消选择")
                        }
                    },
                    actions = {
                        IconButton(onClick = vm::selectAll) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = "全选")
                        }
                        IconButton(onClick = { showMoveFiles = true }) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = "移动")
                        }
                        IconButton(onClick = { collectionIds = ui.selected.toList() }) {
                            Icon(Icons.Outlined.Link, contentDescription = "创建集合")
                        }
                        IconButton(onClick = { showSelectionDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("文件中转站") },
                    actions = {
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Outlined.Search, contentDescription = "搜索")
                        }
                        IconButton(onClick = vm::toggleGrid) {
                            Icon(
                                if (ui.grid) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                                contentDescription = if (ui.grid) "列表视图" else "网格视图",
                            )
                        }
                        Box {
                            IconButton(onClick = { moreMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("全选当前列表") },
                                    leadingIcon = { Icon(Icons.Outlined.SelectAll, contentDescription = null) },
                                    onClick = { moreMenu = false; vm.selectAll() },
                                )
                                DropdownMenuItem(
                                    text = { Text("刷新") },
                                    leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                                    onClick = { moreMenu = false; vm.refresh() },
                                )
                                DropdownMenuItem(
                                    text = { Text("跳转到文件夹") },
                                    leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                                    onClick = { moreMenu = false; showFolderPicker = true },
                                )
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (ui.selected.isEmpty()) {
                Box {
                    ExtendedFloatingActionButton(
                        onClick = { fabMenu = true },
                        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        text = { Text("上传") },
                    )
                    DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("上传文件") },
                            leadingIcon = { Icon(Icons.Outlined.Upload, contentDescription = null) },
                            onClick = { fabMenu = false; pickFiles.launch(arrayOf("*/*")) },
                        )
                        DropdownMenuItem(
                            text = { Text("上传文件夹") },
                            leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                            onClick = { fabMenu = false; pickFolder.launch(null) },
                        )
                        DropdownMenuItem(
                            text = { Text("新建文件夹") },
                            leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                            onClick = { fabMenu = false; showCreateFolder = true },
                        )
                        DropdownMenuItem(
                            text = { Text("新建文本") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                fabMenu = false
                                nav.navigate(Routes.TEXTS) { launchSingleTop = true }
                            },
                        )
                    }
                }
            }
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            if (ui.grid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    gridHeader(ui, uploads, showSearch, sortMenu, vm, snackbar,
                        onShowSearch = { showSearch = !showSearch },
                        onSortMenu = { sortMenu = it },
                        onOpenAll = { vm.openFolder("all") },
                        onOpenFolderPicker = { showFolderPicker = true })
                    gridItems(ui.visibleFolders, key = { "folder-${it.id}" }) { folder ->
                        FolderCard(folder, onOpen = { vm.openFolder(folder.id) }, onMore = { folderSheet = folder })
                    }
                    gridItems(ui.visibleFiles, key = { "file-${it.id}" }) { file ->
                        FileCard(
                            file = file,
                            selected = ui.selected.contains(file.id),
                            selectionMode = ui.selected.isNotEmpty(),
                            onClick = {
                                if (ui.selected.isNotEmpty()) vm.toggleSelection(file.id) else fileSheet = file
                            },
                            onLongClick = { vm.toggleSelection(file.id) },
                            onMore = { fileSheet = file },
                        )
                    }
                    if (ui.loading && ui.files.isEmpty() && ui.visibleFolders.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }
                        }
                    }
                    if (!ui.loading && ui.visibleFolders.isEmpty() && ui.visibleFiles.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(
                                icon = Icons.Outlined.FolderOpen,
                                title = if (ui.search.isNotBlank()) "没有匹配的文件" else "这里还没有文件",
                                subtitle = "点击右下角上传文件或新建文件夹",
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    listHeader(ui, uploads, showSearch, sortMenu, vm, snackbar,
                        onShowSearch = { showSearch = !showSearch },
                        onSortMenu = { sortMenu = it },
                        onOpenAll = { vm.openFolder("all") },
                        onOpenFolderPicker = { showFolderPicker = true })
                    items(ui.visibleFolders, key = { "folder-${it.id}" }) { folder ->
                        FolderRow(folder, onOpen = { vm.openFolder(folder.id) }, onMore = { folderSheet = folder })
                    }
                    items(ui.visibleFiles, key = { "file-${it.id}" }) { file ->
                        FileRow(
                            file = file,
                            selected = ui.selected.contains(file.id),
                            selectionMode = ui.selected.isNotEmpty(),
                            onClick = {
                                if (ui.selected.isNotEmpty()) vm.toggleSelection(file.id) else fileSheet = file
                            },
                            onLongClick = { vm.toggleSelection(file.id) },
                            onMore = { fileSheet = file },
                        )
                    }
                    if (ui.loading && ui.files.isEmpty() && ui.visibleFolders.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }
                        }
                    }
                    if (!ui.loading && ui.visibleFolders.isEmpty() && ui.visibleFiles.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Outlined.FolderOpen,
                                title = if (ui.search.isNotBlank()) "没有匹配的文件" else "这里还没有文件",
                                subtitle = "点击右下角上传文件或新建文件夹",
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateFolder) {
        NameDialog(
            title = "新建文件夹",
            label = "文件夹名称",
            initial = "",
            onDismiss = { showCreateFolder = false },
            onConfirm = { name ->
                vm.createFolder(name, { showCreateFolder = false }, { showMessage(it) })
            },
        )
    }

    if (showFolderPicker) {
        FolderPickerDialog(
            title = "跳转到文件夹",
            folders = ui.folders,
            onDismiss = { showFolderPicker = false },
            onPick = { id ->
                showFolderPicker = false
                vm.openFolder(id ?: "root")
            },
        )
    }

    if (showMoveFiles) {
        FolderPickerDialog(
            title = "移动 ${ui.selected.size} 个文件",
            folders = ui.folders,
            onDismiss = { showMoveFiles = false },
            onPick = { id ->
                showMoveFiles = false
                vm.moveSelected(id, { showMessage("已移动") }, { showMessage(it) })
            },
        )
    }

    movingFolder?.let { folder ->
        FolderPickerDialog(
            title = "移动文件夹「${folder.name}」",
            folders = ui.folders,
            excludeIds = subtreeIds(folder.id, ui.folders),
            onDismiss = { movingFolder = null },
            onPick = { id ->
                vm.moveFolder(folder.id, id, { movingFolder = null }, { showMessage(it) })
            },
        )
    }

    renameFolder?.let { folder ->
        NameDialog(
            title = "重命名文件夹",
            label = "文件夹名称",
            initial = folder.name,
            onDismiss = { renameFolder = null },
            onConfirm = { name ->
                vm.renameFolder(folder.id, name, { renameFolder = null }, { showMessage(it) })
            },
        )
    }

    deleteFile?.let { file ->
        ConfirmDialog(
            title = "删除文件",
            text = "确定删除「${file.name}」？该操作会同时删除 R2 上的对象，且不可恢复。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                vm.deleteFile(file, { deleteFile = null; showMessage("已删除") }, { showMessage(it) })
            },
            onDismiss = { deleteFile = null },
        )
    }

    deleteFolder?.let { folder ->
        ConfirmDialog(
            title = "删除文件夹",
            text = "确定删除「${folder.name}」及其全部子文件夹和文件？此操作不可恢复。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                vm.deleteFolder(folder.id, { deleteFolder = null; showMessage("已删除") }, { showMessage(it) })
            },
            onDismiss = { deleteFolder = null },
        )
    }

    if (showSelectionDelete) {
        ConfirmDialog(
            title = "批量删除",
            text = "确定删除选中的 ${ui.selected.size} 个文件？此操作不可恢复。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                showSelectionDelete = false
                vm.deleteSelected({ showMessage("已删除") }, { showMessage(it) })
            },
            onDismiss = { showSelectionDelete = false },
        )
    }

    editFile?.let { file ->
        EditFileDialog(
            file = file,
            onDismiss = { editFile = null },
            onSave = { name, days, password, clearPassword, keepExpiry ->
                vm.updateFile(
                    id = file.id,
                    name = name,
                    days = days,
                    keepExpiry = keepExpiry,
                    password = password,
                    clearPassword = clearPassword,
                    onSuccess = { editFile = null; showMessage("已保存") },
                    onError = { showMessage(it) },
                )
            },
        )
    }

    collectionIds?.let { ids ->
        FilesCreateCollectionDialog(
            count = ids.size,
            onDismiss = { collectionIds = null },
            onCreate = { name, days, password ->
                vm.createCollection(
                    fileIds = ids,
                    name = name,
                    expiresInDays = days.toIntOrNull(),
                    password = password.takeIf { it.isNotBlank() },
                    onSuccess = { url ->
                        collectionIds = null
                        qr = "集合已创建" to url
                    },
                    onError = { showMessage(it) },
                )
            },
        )
    }

    shareFolder?.let { folder ->
        FilesCreateFolderShareDialog(
            folder = folder,
            onDismiss = { shareFolder = null },
            onCreate = { name, days, password ->
                vm.createFolderShare(
                    folderId = folder.id,
                    name = name,
                    expiresInDays = days.toIntOrNull(),
                    password = password.takeIf { it.isNotBlank() },
                    onSuccess = { url ->
                        shareFolder = null
                        qr = "文件夹分享已创建" to url
                    },
                    onError = { showMessage(it) },
                )
            },
        )
    }

    qr?.let { (title, content) ->
        QrDialog(title = title, content = content, onDismiss = { qr = null })
    }

    if (uploadOptions) {
        UploadOptionsDialog(
            targetName = ui.folderPath,
            onDismiss = {
                uploadOptions = false
                pendingFiles = emptyList()
                pendingFolder = null
            },
            onConfirm = { days, password ->
                val expires = days.toIntOrNull()
                val pwd = password.takeIf { it.isNotBlank() }
                pendingFiles.forEach { Graph.uploads.enqueue(it, ui.targetFolderId, expires, pwd) }
                pendingFolder?.let { Graph.uploads.enqueueFolder(it, ui.targetFolderId, expires, pwd) }
                uploadOptions = false
                pendingFiles = emptyList()
                pendingFolder = null
            },
        )
    }

    fileSheet?.let { file ->
        ModalBottomSheet(onDismissRequest = { fileSheet = null }) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatBytes(file.size)} · ${statusLabel(file.status)} · 下载 ${file.downloadCount} 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                if (file.status == "ready") {
                    SheetAction(Icons.Outlined.Visibility, "预览") { fileSheet = null; openPreview(file) }
                    SheetAction(Icons.Outlined.Download, "下载") { fileSheet = null; download(file) }
                }
                if (file.status == "uploading") {
                    SheetAction(Icons.Outlined.Upload, "继续上传") {
                        fileSheet = null
                        if (uploads.any { it.fileId == file.id && !it.finished }) {
                            showMessage("该文件正在上传中")
                        } else {
                            vm.resumeUpload(file)
                            showMessage("已开始续传，若原文件权限失效请重新上传")
                        }
                    }
                    SheetAction(Icons.Outlined.Cancel, "取消上传") {
                        fileSheet = null
                        vm.abortUpload(file) { showMessage("已取消") }
                    }
                }
                if (file.status == "ready") {
                    SheetAction(Icons.Outlined.ContentCopy, "复制分享链接") {
                        fileSheet = null
                        copyToClipboard(context, "分享链接", file.shareUrl)
                    }
                    SheetAction(Icons.Outlined.QrCode2, "分享二维码") {
                        fileSheet = null
                        qr = "文件分享" to file.shareUrl
                    }
                    SheetAction(Icons.Outlined.Edit, "重命名 / 有效期 / 提取码") {
                        fileSheet = null
                        editFile = file
                    }
                    SheetAction(Icons.Outlined.FolderOpen, "移动") {
                        fileSheet = null
                        vm.selectOnly(file.id)
                        showMoveFiles = true
                    }
                    SheetAction(Icons.Outlined.Link, "加入集合") {
                        fileSheet = null
                        collectionIds = listOf(file.id)
                    }
                }
                SheetAction(Icons.Outlined.Delete, "删除", destructive = true) {
                    fileSheet = null
                    deleteFile = file
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    folderSheet?.let { folder ->
        ModalBottomSheet(onDismissRequest = { folderSheet = null }) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                Text(
                    folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Text(
                    "${folder.fileCount} 个文件 · ${formatBytes(folder.totalSize)} · ${folder.childCount} 个子文件夹",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                SheetAction(Icons.Outlined.FolderOpen, "打开") {
                    folderSheet = null
                    vm.openFolder(folder.id)
                }
                SheetAction(Icons.Outlined.Link, "分享文件夹") {
                    folderSheet = null
                    shareFolder = folder
                }
                SheetAction(Icons.Outlined.Edit, "重命名") {
                    folderSheet = null
                    renameFolder = folder
                }
                SheetAction(Icons.Outlined.FolderOpen, "移动") {
                    folderSheet = null
                    movingFolder = folder
                }
                SheetAction(Icons.Outlined.Delete, "删除", destructive = true) {
                    folderSheet = null
                    deleteFolder = folder
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.listHeader(
    ui: FilesUiState,
    uploads: List<com.filetransit.app.data.UploadTask>,
    showSearch: Boolean,
    sortMenu: Boolean,
    vm: FilesViewModel,
    snackbar: SnackbarHostState,
    onShowSearch: () -> Unit,
    onSortMenu: (Boolean) -> Unit,
    onOpenAll: () -> Unit,
    onOpenFolderPicker: () -> Unit,
) {
    item {
        HeaderSection(
            ui = ui,
            uploads = uploads,
            showSearch = showSearch,
            sortMenu = sortMenu,
            vm = vm,
            snackbar = snackbar,
            onShowSearch = onShowSearch,
            onSortMenu = onSortMenu,
            onOpenAll = onOpenAll,
            onOpenFolderPicker = onOpenFolderPicker,
        )
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.gridHeader(
    ui: FilesUiState,
    uploads: List<com.filetransit.app.data.UploadTask>,
    showSearch: Boolean,
    sortMenu: Boolean,
    vm: FilesViewModel,
    snackbar: SnackbarHostState,
    onShowSearch: () -> Unit,
    onSortMenu: (Boolean) -> Unit,
    onOpenAll: () -> Unit,
    onOpenFolderPicker: () -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        HeaderSection(
            ui = ui,
            uploads = uploads,
            showSearch = showSearch,
            sortMenu = sortMenu,
            vm = vm,
            snackbar = snackbar,
            onShowSearch = onShowSearch,
            onSortMenu = onSortMenu,
            onOpenAll = onOpenAll,
            onOpenFolderPicker = onOpenFolderPicker,
        )
    }
}

@Composable
private fun HeaderSection(
    ui: FilesUiState,
    uploads: List<com.filetransit.app.data.UploadTask>,
    showSearch: Boolean,
    sortMenu: Boolean,
    vm: FilesViewModel,
    snackbar: SnackbarHostState,
    onShowSearch: () -> Unit,
    onSortMenu: (Boolean) -> Unit,
    onOpenAll: () -> Unit,
    onOpenFolderPicker: () -> Unit,
) {
    Column {
        if (ui.error != null) {
            ErrorBanner(ui.error, onRetry = { vm.refresh() })
            Spacer(Modifier.height(8.dp))
        }
        StatsStrip(ui)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (ui.folderId != "all") {
                IconButton(onClick = {
                    val parent = if (ui.folderId == "root") {
                        "all"
                    } else {
                        ui.breadcrumb.lastOrNull()?.parentId ?: "root"
                    }
                    vm.openFolder(parent)
                }) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回上级")
                }
            }
            Text(
                ui.folderPath,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onOpenAll) { Text("全部") }
            IconButton(onClick = onOpenFolderPicker) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = "选择文件夹")
            }
        }

        if (showSearch || ui.search.isNotBlank()) {
            OutlinedTextField(
                value = ui.search,
                onValueChange = vm::setSearch,
                placeholder = { Text("搜索文件名") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (ui.search.isNotBlank()) {
                        IconButton(onClick = { vm.setSearch("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                "all" to "全部",
                "ready" to "可下载",
                "uploading" to "上传中",
                "expired" to "已过期",
            ).forEach { (value, label) ->
                FilterChip(
                    selected = ui.statusFilter == value,
                    onClick = { vm.setStatusFilter(value) },
                    label = { Text(label) },
                )
            }
            Box {
                AssistChip(
                    onClick = { onSortMenu(true) },
                    label = { Text(ui.sort.label) },
                )
                DropdownMenu(expanded = sortMenu, onDismissRequest = { onSortMenu(false) }) {
                    SortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = { vm.setSort(option); onSortMenu(false) },
                        )
                    }
                }
            }
        }

        if (uploads.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            UploadsPanel(uploads)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun StatsStrip(ui: FilesUiState) {
    val stats = ui.stats ?: return
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("文件", stats.fileCount.toString(), Modifier.weight(1f))
                StatTile("占用", formatBytes(stats.readySize), Modifier.weight(1f))
                StatTile("下载", stats.downloadTotal.toString(), Modifier.weight(1f))
                StatTile("集合", stats.collectionCount.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("可下载", stats.readyCount.toString(), Modifier.weight(1f))
                StatTile("上传中", stats.uploadingCount.toString(), Modifier.weight(1f))
                StatTile("已过期", stats.expiredCount.toString(), Modifier.weight(1f))
                StatTile("文件夹", stats.folderCount.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun UploadsPanel(uploads: List<com.filetransit.app.data.UploadTask>) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("上传队列", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { Graph.uploads.clearFinished() }) { Text("清除已完成") }
            }
            uploads.takeLast(6).forEach { task ->
                Column(modifier = Modifier.padding(vertical = 5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (task.finished) Icons.Outlined.CheckCircle else Icons.Outlined.Upload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (task.phase == com.filetransit.app.data.UploadPhase.FAILED)
                                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            task.name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!task.finished) {
                            IconButton(onClick = { Graph.uploads.cancel(task.localId) }) {
                                Icon(Icons.Outlined.Cancel, contentDescription = "取消", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    if (!task.finished) {
                        LinearProgressIndicator(
                            progress = { task.progress },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp),
                        )
                    }
                    Text(
                        task.message ?: when (task.phase) {
                            com.filetransit.app.data.UploadPhase.HASHING -> "计算 SHA-256…"
                            com.filetransit.app.data.UploadPhase.INITIALIZING -> "初始化…"
                            com.filetransit.app.data.UploadPhase.UPLOADING -> "${formatBytes(task.uploaded)} / ${formatBytes(task.size)}"
                            com.filetransit.app.data.UploadPhase.COMPLETING -> "完成中…"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 26.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: FileDto,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { onClick() })
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        fileIcon(file.mimeType, file.name),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (file.hasPassword) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.Lock, contentDescription = "有提取码", modifier = Modifier.size(13.dp))
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${formatBytes(file.size)} · ${formatDateShort(file.createdAt)} · ${file.downloadCount} 次下载",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(file.status)
                    if (file.expiresAt != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            expiryLabel(file.expiresAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(onClick = onMore) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "操作")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileCard(
    file: FileDto,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { onClick() })
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            fileIcon(file.mimeType, file.name),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMore) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "操作", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatBytes(file.size)} · ${file.downloadCount} 次",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(file.status)
                if (file.hasPassword) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun FolderRow(folder: FolderDto, onOpen: () -> Unit, onMore: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${folder.fileCount} 个文件 · ${formatBytes(folder.totalSize)} · ${folder.childCount} 个子文件夹",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onMore) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "文件夹操作")
            }
        }
    }
}

@Composable
private fun FolderCard(folder: FolderDto, onOpen: () -> Unit, onMore: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMore) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "文件夹操作", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(folder.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${folder.fileCount} 个文件 · ${formatBytes(folder.totalSize)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val color = when (status) {
        "ready" -> MaterialTheme.colorScheme.primary
        "uploading" -> MaterialTheme.colorScheme.tertiary
        "expired" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(
            statusLabel(status),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun EditFileDialog(
    file: FileDto,
    onDismiss: () -> Unit,
    onSave: (name: String, days: String, password: String, clearPassword: Boolean, keepExpiry: Boolean) -> Unit,
) {
    val initialDays = remember(file.id) {
        file.expiresAt?.let {
            ((it - System.currentTimeMillis()) / 86_400_000).coerceAtLeast(1).toString()
        } ?: ""
    }
    var name by remember(file.id) { mutableStateOf(file.name) }
    var days by remember(file.id) { mutableStateOf(initialDays) }
    var password by remember(file.id) { mutableStateOf("") }
    var clearPassword by remember(file.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑文件") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("文件名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                ExpiryPasswordFields(
                    days = days,
                    onDaysChange = { days = it },
                    password = password,
                    onPasswordChange = { password = it },
                    hasPassword = file.hasPassword,
                    clearPassword = clearPassword,
                    onClearPasswordChange = { clearPassword = it },
                    editing = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, days, password, clearPassword, days == initialDays) },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun FilesCreateCollectionDialog(
    count: Int,
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
                Text("将 $count 个文件组合成一个集合链接", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
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
        confirmButton = {
            TextButton(onClick = { onCreate(name, days, password) }) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun FilesCreateFolderShareDialog(
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
                Spacer(Modifier.height(10.dp))
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
        confirmButton = {
            TextButton(onClick = { onCreate(name, days, password) }) { Text("创建链接") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun UploadOptionsDialog(
    targetName: String,
    onDismiss: () -> Unit,
    onConfirm: (days: String, password: String) -> Unit,
) {
    var days by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("上传设置") },
        text = {
            Column {
                Text("上传到：$targetName", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
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
        confirmButton = { TextButton(onClick = { onConfirm(days, password) }) { Text("开始上传") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun subtreeIds(rootId: String, folders: List<FolderDto>): Set<String> {
    val result = mutableSetOf(rootId)
    var changed = true
    while (changed) {
        changed = false
        folders.forEach { folder ->
            val parent = folder.parentId
            if (parent != null && result.contains(parent) && result.add(folder.id)) {
                changed = true
            }
        }
    }
    return result
}
