package com.filetransit.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filetransit.app.data.FolderDto
import com.filetransit.app.util.copyToClipboard
import com.filetransit.app.util.qrBitmap

@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun LabeledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    enabled: Boolean = true,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
    )
}

@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "隐藏" else "显示",
                )
            }
        },
    )
}

@Composable
fun ErrorBanner(message: String?, onRetry: (() -> Unit)? = null) {
    if (message.isNullOrBlank()) return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重试")
                }
            }
        }
    }
}

@Composable
fun LoadingBox(text: String = "加载中…") {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            action()
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "确定",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun QrDialog(title: String, content: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(content) { runCatching { qrBitmap(content) }.getOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (bitmap != null) {
                    Surface(color = androidx.compose.ui.graphics.Color.White, shape = RoundedCornerShape(12.dp)) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "二维码",
                            modifier = Modifier.size(240.dp).padding(10.dp),
                        )
                    }
                } else {
                    Text("二维码生成失败")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    content,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { copyToClipboard(context, "分享链接", content) }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("复制链接")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
fun ExpiryPasswordFields(
    days: String,
    onDaysChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    hasPassword: Boolean,
    clearPassword: Boolean,
    onClearPasswordChange: (Boolean) -> Unit,
    editing: Boolean,
) {
    LabeledField(
        value = days,
        onValueChange = { value -> onDaysChange(value.filter { it.isDigit() }.take(4)) },
        label = "有效期（天）",
        placeholder = if (editing) "留空表示永久有效" else "留空表示永久有效",
        keyboardType = KeyboardType.Number,
    )
    Spacer(Modifier.height(8.dp))
    LabeledField(
        value = password,
        onValueChange = onPasswordChange,
        label = if (editing && hasPassword) "新提取码（留空不修改）" else "提取码（可选）",
        singleLine = true,
    )
    if (editing && hasPassword) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onClearPasswordChange(!clearPassword) }) {
                Icon(
                    if (clearPassword) Icons.Outlined.Check else Icons.Outlined.Close,
                    contentDescription = null,
                    tint = if (clearPassword) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
            Text("清除已有提取码", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PasswordPromptDialog(
    title: String,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("该分享需要提取码", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                SecretField(value = password, onValueChange = { password = it }, label = "提取码")
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(password) }, enabled = password.isNotBlank() && !loading) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("确定")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

data class FolderNode(val folder: FolderDto, val depth: Int, val path: String)

fun flattenFolders(folders: List<FolderDto>): List<FolderNode> {
    val byParent = folders.groupBy { it.parentId }
    val result = mutableListOf<FolderNode>()
    val visited = mutableSetOf<String>()

    fun walk(parentId: String?, depth: Int, prefix: String) {
        byParent[parentId].orEmpty().sortedBy { it.name.lowercase() }.forEach { folder ->
            if (!visited.add(folder.id)) return@forEach
            val path = if (prefix.isEmpty()) folder.name else "$prefix / ${folder.name}"
            result += FolderNode(folder, depth, path)
            walk(folder.id, depth + 1, path)
        }
    }
    walk(null, 0, "")
    folders.filterNot { visited.contains(it.id) }.forEach { folder ->
        result += FolderNode(folder, 0, folder.name)
    }
    return result
}

@Composable
fun FolderPickerDialog(
    title: String,
    folders: List<FolderDto>,
    excludeIds: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    val nodes = remember(folders) { flattenFolders(folders) }
    var selected by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selected == null) MaterialTheme.colorScheme.primaryContainer
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("根目录", modifier = Modifier.weight(1f))
                            TextButton(onClick = { selected = null }) {
                                if (selected == null) Text("已选") else Text("选择")
                            }
                        }
                    }
                    items(nodes.filterNot { excludeIds.contains(it.folder.id) }) { node ->
                        val isSelected = selected == node.folder.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(start = (10 + node.depth * 16).dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                node.folder.name,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { selected = node.folder.id }) {
                                if (isSelected) Text("已选") else Text("选择")
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text(
                    "移动到：" + (nodes.firstOrNull { it.folder.id == selected }?.path ?: "根目录"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(selected) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
fun ShareLinkRow(shareUrl: String, onQr: () -> Unit) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                shareUrl,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { copyToClipboard(context, "分享链接", shareUrl) }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "复制链接")
            }
            IconButton(onClick = onQr) {
                Icon(Icons.Outlined.QrCode2, contentDescription = "二维码")
            }
        }
    }
}
