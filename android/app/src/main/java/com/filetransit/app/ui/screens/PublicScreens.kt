package com.filetransit.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.navigation.NavHostController
import com.filetransit.app.Graph
import com.filetransit.app.data.CollectionShareInfo
import com.filetransit.app.data.FolderShareInfo
import com.filetransit.app.data.ShareInfo
import com.filetransit.app.data.TextShareInfo
import com.filetransit.app.ui.PreviewInbox
import com.filetransit.app.ui.PreviewRequest
import com.filetransit.app.ui.Routes
import com.filetransit.app.ui.components.ErrorBanner
import com.filetransit.app.ui.components.InfoRow
import com.filetransit.app.ui.components.LoadingBox
import com.filetransit.app.ui.components.QrDialog
import com.filetransit.app.ui.components.SecretField
import com.filetransit.app.util.apiMessage
import com.filetransit.app.util.copyToClipboard
import com.filetransit.app.util.enqueueDownload
import com.filetransit.app.util.expiryLabel
import com.filetransit.app.util.fileIcon
import com.filetransit.app.util.formatBytes
import com.filetransit.app.util.formatDate
import com.filetransit.app.util.formatDateShort
import com.filetransit.app.util.statusLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublicScaffold(
    title: String,
    nav: NavHostController,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateUp() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        content = content,
    )
}

@Composable
fun PublicFileShareScreen(nav: NavHostController, code: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<ShareInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var qrOpen by remember { mutableStateOf(false) }

    LaunchedEffect(code) {
        loading = true
        try {
            info = Graph.repo.shareInfo(code)
        } catch (e: Exception) {
            error = apiMessage(e)
        } finally {
            loading = false
        }
    }

    fun download() {
        busy = true
        actionError = null
        scope.launch {
            try {
                val url = Graph.repo.shareDownload(code, password.takeIf { it.isNotBlank() })
                enqueueDownload(context, url, info?.name ?: "download")
            } catch (e: Exception) {
                actionError = apiMessage(e)
            } finally {
                busy = false
            }
        }
    }

    fun preview() {
        busy = true
        actionError = null
        scope.launch {
            try {
                val preview = Graph.repo.sharePreview(code, password.takeIf { it.isNotBlank() })
                PreviewInbox.request = PreviewRequest(preview.url, preview.name, preview.mimeType)
                nav.navigate(Routes.PREVIEW)
            } catch (e: Exception) {
                actionError = apiMessage(e)
            } finally {
                busy = false
            }
        }
    }

    PublicScaffold("文件分享", nav) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding)) { LoadingBox() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                if (error != null) {
                    ErrorBanner(error)
                    return@Column
                }
                val data = info ?: return@Column
                if (!data.found) {
                    ErrorBanner("分享不存在或已被删除")
                    return@Column
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(64.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    fileIcon(data.mimeType, data.name),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            data.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${formatBytes(data.size)} · ${statusLabel(data.status)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (data.expired) {
                    ErrorBanner("该分享已过期")
                }
                if (data.requiresPassword) {
                    SecretField(password, { password = it }, "提取码")
                    Spacer(Modifier.height(8.dp))
                }
                if (actionError != null) {
                    ErrorBanner(actionError)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = ::download,
                        enabled = !busy && !data.expired,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else {
                            Icon(Icons.Outlined.Download, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("下载")
                        }
                    }
                    OutlinedButton(
                        onClick = ::preview,
                        enabled = !busy && !data.expired,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Visibility, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("预览")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        InfoRow("文件大小", formatBytes(data.size))
                        InfoRow("上传时间", formatDate(data.createdAt))
                        InfoRow("有效期", expiryLabel(data.expiresAt))
                        InfoRow("下载次数", data.downloadCount.toString())
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = {
                                copyToClipboard(
                                    context,
                                    "分享链接",
                                    Graph.session.server.value.trimEnd('/') + "/s/$code",
                                )
                            }) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("复制当前链接")
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = { qrOpen = true }) {
                                Icon(Icons.Outlined.QrCode2, contentDescription = "二维码")
                            }
                        }
                    }
                }
            }
        }
    }

    if (qrOpen) {
        val url = info?.let { Graph.session.server.value.trimEnd('/') + "/s/$code" }.orEmpty()
        QrDialog("文件分享", url) { qrOpen = false }
    }
}

@Composable
fun CollectionShareScreen(nav: NavHostController, code: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<CollectionShareInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(code) {
        try {
            info = Graph.repo.collectionShareInfo(code)
        } catch (e: Exception) {
            error = apiMessage(e)
        } finally {
            loading = false
        }
    }

    fun downloadItem(fileId: String, name: String) {
        busy = true
        actionError = null
        scope.launch {
            try {
                val url = Graph.repo.collectionItemDownload(code, fileId, password.takeIf { it.isNotBlank() })
                enqueueDownload(context, url, name)
            } catch (e: Exception) {
                actionError = apiMessage(e)
            } finally {
                busy = false
            }
        }
    }

    fun previewItem(fileId: String) {
        busy = true
        actionError = null
        scope.launch {
            try {
                val preview = Graph.repo.collectionItemPreview(code, fileId, password.takeIf { it.isNotBlank() })
                PreviewInbox.request = PreviewRequest(preview.url, preview.name, preview.mimeType)
                nav.navigate(Routes.PREVIEW)
            } catch (e: Exception) {
                actionError = apiMessage(e)
            } finally {
                busy = false
            }
        }
    }

    fun downloadAll() {
        busy = true
        actionError = null
        scope.launch {
            try {
                val downloads = Graph.repo.collectionDownloadAll(code, password.takeIf { it.isNotBlank() })
                downloads.forEach { enqueueDownload(context, it.url, it.name) }
            } catch (e: Exception) {
                actionError = apiMessage(e)
            } finally {
                busy = false
            }
        }
    }

    PublicScaffold("集合分享", nav) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding)) { LoadingBox() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (error != null) {
                    item { ErrorBanner(error) }
                } else {
                    val data = info
                    if (data == null || !data.found) {
                        item { ErrorBanner("分享不存在或已被删除") }
                    } else {
                        item {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text(data.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${data.items.size} 个文件 · ${formatBytes(data.items.sumOf { it.size })} · 下载 ${data.downloadCount} 次",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "创建于 ${formatDateShort(data.createdAt)} · ${expiryLabel(data.expiresAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (data.expired) {
                            item { ErrorBanner("该分享已过期") }
                        }
                        if (data.requiresPassword) {
                            item { SecretField(password, { password = it }, "提取码") }
                        }
                        if (actionError != null) {
                            item { ErrorBanner(actionError) }
                        }
                        item {
                            Button(
                                onClick = ::downloadAll,
                                enabled = !busy && !data.expired && data.items.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("一键下载全部")
                            }
                        }
                        items(data.items, key = { it.fileId }) { item ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        fileIcon(item.mimeType, item.name),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "${formatBytes(item.size)} · 下载 ${item.downloadCount} 次",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { previewItem(item.fileId) }, enabled = !busy) {
                                        Icon(Icons.Outlined.Visibility, contentDescription = "预览")
                                    }
                                    IconButton(onClick = { downloadItem(item.fileId, item.name) }, enabled = !busy) {
                                        Icon(Icons.Outlined.Download, contentDescription = "下载")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderShareScreen(nav: NavHostController, code: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<FolderShareInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(code) {
        try {
            info = Graph.repo.folderShareInfo(code)
        } catch (e: Exception) {
            error = apiMessage(e)
        } finally {
            loading = false
        }
    }

    fun downloadItem(fileId: String, name: String) {
        busy = true
        actionError = null
        scope.launch {
            try {
                val url = Graph.repo.folderShareItemDownload(code, fileId, password.takeIf { it.isNotBlank() })
                enqueueDownload(context, url, name)
            } catch (e: Exception) {
                actionError = apiMessage(e)
            } finally {
                busy = false
            }
        }
    }

    fun previewItem(fileId: String) {
        busy = true
        actionError = null
        scope.launch {
            try {
                val preview = Graph.repo.folderShareItemPreview(code, fileId, password.takeIf { it.isNotBlank() })
                PreviewInbox.request = PreviewRequest(preview.url, preview.name, preview.mimeType)
                nav.navigate(Routes.PREVIEW)
            } catch (e: Exception) {
                actionError = apiMessage(e)
            } finally {
                busy = false
            }
        }
    }

    fun downloadAll() {
        busy = true
        actionError = null
        scope.launch {
            try {
                val downloads = Graph.repo.folderShareDownloadAll(code, password.takeIf { it.isNotBlank() })
                downloads.forEach { enqueueDownload(context, it.url, it.name) }
            } catch (e: Exception) {
                actionError = apiMessage(e)
            } finally {
                busy = false
            }
        }
    }

    PublicScaffold("文件夹分享", nav) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding)) { LoadingBox() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (error != null) {
                    item { ErrorBanner(error) }
                } else {
                    val data = info
                    if (data == null || !data.found) {
                        item { ErrorBanner("分享不存在或已被删除") }
                    } else {
                        item {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(data.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        data.folderPath.ifBlank { data.name },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "${data.items.size} 个文件 · ${formatBytes(data.items.sumOf { it.size })} · 下载 ${data.downloadCount} 次",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "创建于 ${formatDateShort(data.createdAt)} · ${expiryLabel(data.expiresAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (data.expired) {
                            item { ErrorBanner("该分享已过期") }
                        }
                        if (data.requiresPassword) {
                            item { SecretField(password, { password = it }, "提取码") }
                        }
                        if (actionError != null) {
                            item { ErrorBanner(actionError) }
                        }
                        item {
                            Button(
                                onClick = ::downloadAll,
                                enabled = !busy && !data.expired && data.items.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("一键下载全部")
                            }
                        }
                        val groups = data.items.groupBy { it.path }
                        groups.forEach { (path, files) ->
                            item(key = "header-$path") {
                                Text(
                                    if (path.isBlank()) "根目录" else path,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            items(files, key = { it.fileId }) { item ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 1.dp,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            fileIcon(item.mimeType, item.name),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                "${formatBytes(item.size)} · 下载 ${item.downloadCount} 次",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(onClick = { previewItem(item.fileId) }, enabled = !busy) {
                                            Icon(Icons.Outlined.Visibility, contentDescription = "预览")
                                        }
                                        IconButton(onClick = { downloadItem(item.fileId, item.name) }, enabled = !busy) {
                                            Icon(Icons.Outlined.Download, contentDescription = "下载")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TextShareScreen(nav: NavHostController, code: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<TextShareInfo?>(null) }
    var content by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var revealing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }

    fun reveal(withPassword: String?) {
        revealing = true
        error = null
        scope.launch {
            try {
                content = Graph.repo.revealText(code, withPassword?.takeIf { it.isNotBlank() })
            } catch (e: Exception) {
                error = apiMessage(e)
            } finally {
                revealing = false
            }
        }
    }

    LaunchedEffect(code) {
        try {
            val data = Graph.repo.textShareInfo(code)
            info = data
            if (!data.requiresPassword && !data.expired) {
                reveal(null)
            }
        } catch (e: Exception) {
            error = apiMessage(e)
        } finally {
            loading = false
        }
    }

    PublicScaffold("文本分享", nav) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding)) { LoadingBox() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                val data = info
                if (data == null || !data.found) {
                    ErrorBanner(error ?: "分享不存在或已被删除")
                    return@Column
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(data.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${data.charCount} 字 · ${formatDateShort(data.updatedAt)} · ${expiryLabel(data.expiresAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (data.expired) {
                    ErrorBanner("该分享已过期")
                    return@Column
                }
                if (data.requiresPassword && content == null) {
                    SecretField(password, { password = it }, "提取码")
                    Spacer(Modifier.height(8.dp))
                }
                if (error != null) {
                    ErrorBanner(error)
                    Spacer(Modifier.height(8.dp))
                }
                if (content == null) {
                    Button(
                        onClick = { reveal(password) },
                        enabled = !revealing && (!data.requiresPassword || password.isNotBlank()),
                    ) {
                        if (revealing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else {
                            Icon(Icons.Outlined.Lock, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("查看内容")
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row {
                                Text("文本内容", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = { copyToClipboard(context, "文本内容", content.orEmpty()) }) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("复制")
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            SelectionContainer {
                                Text(
                                    content.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
