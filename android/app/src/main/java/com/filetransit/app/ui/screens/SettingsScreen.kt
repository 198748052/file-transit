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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.filetransit.app.BuildConfig
import com.filetransit.app.Graph
import com.filetransit.app.data.BucketScanResult
import com.filetransit.app.data.KeyMigrationResult
import com.filetransit.app.data.R2UsageStatus
import com.filetransit.app.data.UpdateLog
import com.filetransit.app.ui.Routes
import com.filetransit.app.ui.components.ConfirmDialog
import com.filetransit.app.ui.components.ErrorBanner
import com.filetransit.app.ui.components.InfoRow
import com.filetransit.app.ui.components.LabeledField
import com.filetransit.app.ui.components.LoadingBox
import com.filetransit.app.ui.components.SecretField
import com.filetransit.app.ui.components.SectionCard
import com.filetransit.app.ui.components.StatTile
import com.filetransit.app.util.formatBytes
import com.filetransit.app.util.formatDate
import com.filetransit.app.util.formatDateShort
import com.filetransit.app.vm.SettingsViewModel
import kotlinx.coroutines.launch

private data class SettingEntry(
    val section: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
)

private val settingGroups = listOf(
    "账号与连接" to listOf(
        SettingEntry("account", Icons.Outlined.Person, "账号", "用户名、密码与凭据来源"),
        SettingEntry("server", Icons.Outlined.Link, "服务器连接", "切换或修改连接的服务器"),
    ),
    "存储" to listOf(
        SettingEntry("storage", Icons.Outlined.Cloud, "R2 存储", "凭据、桶与连接测试"),
        SettingEntry("usage", Icons.Outlined.CloudQueue, "账户用量", "R2 存储量与免费额度"),
        SettingEntry("bucket", Icons.Outlined.Folder, "存储桶体检", "扫描残留对象、迁移旧路径"),
    ),
    "分享与站点" to listOf(
        SettingEntry("site", Icons.Outlined.Link, "站点地址", "分享链接使用的公开地址"),
        SettingEntry("limit", Icons.Outlined.Shield, "公开分享限流", "限制下载频率与提取码错误次数"),
    ),
    "系统" to listOf(
        SettingEntry("update", Icons.Outlined.SystemUpdateAlt, "软件更新", "检查并更新服务器"),
        SettingEntry("about", Icons.Outlined.Info, "关于", "版本与服务端信息"),
    ),
)

private val settingSections = settingGroups.flatMap { it.second }.associateBy { it.section }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    nav: NavHostController,
    outerPadding: PaddingValues,
) {
    val serverUrl by Graph.session.server.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.padding(outerPadding),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("设置") }) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            settingGroups.forEach { (group, entries) ->
                item(key = "group-$group") {
                    Text(
                        group,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
                    )
                }
                item(key = "card-$group") {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            entries.forEachIndexed { index, entry ->
                                SettingsEntryRow(entry) {
                                    nav.navigate(Routes.settingsSection(entry.section))
                                }
                                if (index < entries.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 52.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "version", contentType = "footer") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "文件中转站 ${BuildConfig.VERSION_NAME} · $serverUrl",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsEntryRow(entry: SettingEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            entry.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(1.dp))
            Text(
                entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSectionScreen(
    nav: NavHostController,
    section: String,
    vm: SettingsViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val serverUrl by Graph.session.server.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var server by remember { mutableStateOf(Graph.session.server.value) }
    var r2Account by remember { mutableStateOf("") }
    var r2AccessKey by remember { mutableStateOf("") }
    var r2Secret by remember { mutableStateOf("") }
    var r2Bucket by remember { mutableStateOf("") }
    var r2Endpoint by remember { mutableStateOf("") }
    var usageToken by remember { mutableStateOf("") }
    var siteUrl by remember { mutableStateOf("") }
    var limitEnabled by remember { mutableStateOf(true) }
    var limitFailures by remember { mutableStateOf("10") }
    var limitWindow by remember { mutableStateOf("15") }
    var limitDownloads by remember { mutableStateOf("60") }

    var changeUsernameOpen by remember { mutableStateOf(false) }
    var changePasswordOpen by remember { mutableStateOf(false) }
    var resetUsernameOpen by remember { mutableStateOf(false) }
    var resetPasswordOpen by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<BucketScanResult?>(null) }
    var migrationResult by remember { mutableStateOf<KeyMigrationResult?>(null) }
    var updateLog by remember { mutableStateOf<UpdateLog?>(null) }
    var confirmUpdate by remember { mutableStateOf(false) }
    var logoutOpen by remember { mutableStateOf(false) }

    LaunchedEffect(ui.r2) {
        ui.r2?.let {
            r2Account = it.accountId
            r2AccessKey = it.accessKeyId
            r2Bucket = it.bucket
            r2Endpoint = it.endpoint
        }
    }
    LaunchedEffect(ui.site) {
        ui.site?.let { siteUrl = it.baseUrl }
    }
    LaunchedEffect(ui.shareLimit) {
        ui.shareLimit?.let {
            limitEnabled = it.enabled
            limitFailures = it.maxFailures.toString()
            limitWindow = it.windowMinutes.toString()
            limitDownloads = it.maxDownloads.toString()
        }
    }

    fun message(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(settingSections[section]?.title ?: "设置") },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = vm::load) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { inner ->
        if (ui.loading) {
            Box(Modifier.fillMaxSize().padding(inner)) { LoadingBox() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner).navigationBarsPadding().imePadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (ui.error != null) {
                    item { ErrorBanner(ui.error, onRetry = { vm.load() }) }
                }

                when (section) {
                    "account" -> item {
                        SectionCard("账号", "登录凭据与管理员信息") {
                            InfoRow("用户名", ui.username?.username ?: "-")
                            InfoRow("来源", if (ui.username?.storedIn == "db") "面板数据库" else ".env")
                            InfoRow(
                                "密码来源",
                                if (ui.password?.storedIn == "db") "面板数据库" else ".env",
                            )
                            InfoRow("改密时间", formatDate(ui.password?.changedAt))
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { changeUsernameOpen = true }) { Text("修改用户名") }
                                OutlinedButton(onClick = { changePasswordOpen = true }) { Text("修改密码") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { resetUsernameOpen = true }) { Text("恢复 .env 用户名") }
                                TextButton(onClick = { resetPasswordOpen = true }) { Text("恢复 .env 密码") }
                            }
                        }
                    }

                    "server" -> item {
                        SectionCard("服务器", "本应用连接的 file-transit 地址") {
                            LabeledField(
                                value = server,
                                onValueChange = { server = it },
                                label = "服务器地址",
                                keyboardType = KeyboardType.Uri,
                                placeholder = "https://files.example.com",
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val normalized = Graph.session.saveServer(server)
                                    server = normalized
                                    message("已保存服务器地址")
                                }) { Text("保存") }
                                OutlinedButton(onClick = { logoutOpen = true }) { Text("退出登录") }
                            }
                        }
                    }

                    "storage" -> item {
                        SectionCard(
                            "R2 存储",
                            "凭据保存在服务器数据库，优先于 .env；Secret 保存后不回显",
                        ) {
                            InfoRow("状态", if (ui.r2?.configured == true) "已配置" else "未配置")
                            InfoRow("来源", when (ui.r2?.storedIn) { "db" -> "面板数据库"; "env" -> ".env"; else -> "无" })
                            InfoRow("Secret", if (ui.r2?.hasSecret == true) "已保存" else "未保存")
                            Spacer(Modifier.height(8.dp))
                            LabeledField(r2Account, { r2Account = it }, "Account ID")
                            Spacer(Modifier.height(8.dp))
                            LabeledField(r2AccessKey, { r2AccessKey = it }, "Access Key ID")
                            Spacer(Modifier.height(8.dp))
                            SecretField(r2Secret, { r2Secret = it }, "Secret Access Key（留空保持不变）")
                            Spacer(Modifier.height(8.dp))
                            LabeledField(r2Bucket, { r2Bucket = it }, "桶名称")
                            Spacer(Modifier.height(8.dp))
                            LabeledField(r2Endpoint, { r2Endpoint = it }, "自定义 Endpoint（可选）", keyboardType = KeyboardType.Uri)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    enabled = !ui.busy,
                                    onClick = {
                                        vm.saveR2(
                                            r2Account, r2AccessKey, r2Secret, r2Bucket, r2Endpoint,
                                            onSuccess = { r2Secret = ""; message("R2 凭据已保存") },
                                            onError = { message(it) },
                                        )
                                    },
                                ) { Text("保存") }
                                OutlinedButton(
                                    enabled = !ui.busy,
                                    onClick = {
                                        vm.testR2(
                                            onSuccess = { message(it) },
                                            onError = { message(it) },
                                        )
                                    },
                                ) { Text("测试连接") }
                            }
                        }
                    }

                    "usage" -> item {
                        SectionCard("R2 账户用量", "需要只读 Cloudflare API Token；统计账号下全部桶") {
                            val usage = ui.usage
                            if (usage != null) {
                                UsageBlock(usage)
                            } else {
                                Text("用量数据加载中…", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(8.dp))
                            SecretField(usageToken, { usageToken = it }, "Cloudflare API Token（留空清除）")
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    enabled = !ui.busy,
                                    onClick = {
                                        vm.saveUsageToken(
                                            usageToken,
                                            onSuccess = { usageToken = ""; message("Token 已保存") },
                                            onError = { message(it) },
                                        )
                                    },
                                ) { Text("保存 Token") }
                                OutlinedButton(
                                    enabled = !ui.busy,
                                    onClick = { vm.refreshUsage { message(it) } },
                                ) { Text("刷新用量") }
                            }
                        }
                    }

                    "bucket" -> item {
                        SectionCard("存储桶体检", "扫描 R2 桶内对象，找出未被引用的残留文件") {
                            InfoRow("旧路径待迁移", "${ui.migration?.total ?: 0} 个文件")
                            if (!ui.migration?.samples.isNullOrEmpty()) {
                                ui.migration?.samples?.take(3)?.forEach { sample ->
                                    Text(
                                        "${sample.name} → ${sample.newKey}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    enabled = !ui.busy,
                                    onClick = {
                                        vm.scanBucket(
                                            onSuccess = { scanResult = it },
                                            onError = { message(it) },
                                        )
                                    },
                                ) { Text("扫描桶") }
                                OutlinedButton(
                                    enabled = !ui.busy,
                                    onClick = {
                                        vm.migrateKeys(
                                            100,
                                            onSuccess = { migrationResult = it },
                                            onError = { message(it) },
                                        )
                                    },
                                ) { Text("迁移旧路径") }
                            }
                        }
                    }

                    "site" -> item {
                        SectionCard("站点地址", "用于生成分享链接的公开地址；留空自动识别") {
                            LabeledField(
                                value = siteUrl,
                                onValueChange = { siteUrl = it },
                                label = "固定站点地址",
                                keyboardType = KeyboardType.Uri,
                                placeholder = ui.site?.autoDetected.orEmpty(),
                            )
                            Spacer(Modifier.height(6.dp))
                            InfoRow("当前模式", if (ui.site?.mode == "pinned") "固定" else "自动")
                            InfoRow("自动识别", ui.site?.autoDetected ?: "-")
                            Spacer(Modifier.height(8.dp))
                            Button(
                                enabled = !ui.busy,
                                onClick = {
                                    vm.saveSite(siteUrl, { message("站点地址已保存") }, { message(it) })
                                },
                            ) { Text("保存") }
                        }
                    }

                    "limit" -> item {
                        SectionCard("公开分享限流", "限制公开下载/预览频率与提取码错误次数") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("启用限流", modifier = Modifier.weight(1f))
                                Switch(checked = limitEnabled, onCheckedChange = { limitEnabled = it })
                            }
                            Spacer(Modifier.height(6.dp))
                            LabeledField(limitFailures, { limitFailures = it.filter(Char::isDigit) }, "窗口内最大失败次数", keyboardType = KeyboardType.Number)
                            Spacer(Modifier.height(8.dp))
                            LabeledField(limitWindow, { limitWindow = it.filter(Char::isDigit) }, "窗口分钟数", keyboardType = KeyboardType.Number)
                            Spacer(Modifier.height(8.dp))
                            LabeledField(limitDownloads, { limitDownloads = it.filter(Char::isDigit) }, "窗口内最大下载次数（0 表示不限）", keyboardType = KeyboardType.Number)
                            Spacer(Modifier.height(10.dp))
                            Button(
                                enabled = !ui.busy,
                                onClick = {
                                    vm.saveShareLimit(
                                        com.filetransit.app.data.ShareLimitSettings(
                                            enabled = limitEnabled,
                                            maxFailures = limitFailures.toIntOrNull() ?: 10,
                                            windowMinutes = limitWindow.toIntOrNull() ?: 15,
                                            maxDownloads = limitDownloads.toIntOrNull() ?: 0,
                                        ),
                                        { message("限流设置已保存") },
                                        { message(it) },
                                    )
                                },
                            ) { Text("保存") }
                        }
                    }

                    "update" -> item {
                        SectionCard("软件更新", "对比 GitHub 最新提交并一键更新服务器") {
                            val update = ui.update
                            InfoRow("状态", if (update?.enabled == true) "已启用" else "未启用")
                            InfoRow("分支", update?.branch ?: "-")
                            InfoRow(
                                "当前提交",
                                update?.current?.let { "${it.sha.take(8)} · ${it.message.take(40)}" } ?: "-",
                            )
                            InfoRow(
                                "最新提交",
                                update?.remote?.let { "${it.sha.take(8)} · ${it.message.take(40)}" } ?: "-",
                            )
                            InfoRow("落后提交", (update?.behind ?: 0).toString())
                            InfoRow("上次检查", formatDate(update?.lastCheckedAt))
                            InfoRow("上次运行", update?.lastRun ?: "-")
                            InfoRow("运行中", if (update?.running == true) "是" else "否")
                            update?.error?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    enabled = !ui.busy,
                                    onClick = {
                                        vm.checkUpdate(
                                            onSuccess = { message(if (it.hasUpdate) "发现新版本" else "已是最新版本") },
                                            onError = { message(it) },
                                        )
                                    },
                                ) { Text("检查更新") }
                                OutlinedButton(
                                    enabled = !ui.busy,
                                    onClick = { confirmUpdate = true },
                                ) { Text("立即更新") }
                                TextButton(
                                    enabled = !ui.busy,
                                    onClick = {
                                        vm.loadUpdateLog(
                                            onSuccess = { updateLog = it },
                                            onError = { message(it) },
                                        )
                                    },
                                ) { Text("查看日志") }
                            }
                        }
                    }

                    "about" -> item {
                        SectionCard("关于") {
                            InfoRow("应用", "文件中转站 Android 客户端")
                            InfoRow("版本", BuildConfig.VERSION_NAME)
                            InfoRow("服务端", serverUrl)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "文件字节直传 Cloudflare R2，服务器只负责认证与签名。所有功能与 Web 面板保持一致。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (changeUsernameOpen) {
        ChangeUsernameDialog(
            onDismiss = { changeUsernameOpen = false },
            onSubmit = { current, newName ->
                vm.changeUsername(
                    current, newName,
                    onSuccess = { changeUsernameOpen = false; message("用户名已更新") },
                    onError = { message(it) },
                )
            },
        )
    }

    if (changePasswordOpen) {
        ChangePasswordDialog(
            onDismiss = { changePasswordOpen = false },
            onSubmit = { current, newPassword ->
                vm.changePassword(
                    current, newPassword,
                    onSuccess = {
                        changePasswordOpen = false
                        nav.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onError = { message(it) },
                )
            },
        )
    }

    if (resetUsernameOpen) {
        ConfirmDialog(
            title = "恢复 .env 用户名",
            text = "清除面板中保存的用户名，改用 .env 中的 ADMIN_USERNAME。",
            onConfirm = {
                resetUsernameOpen = false
                vm.clearUsername({ message("已恢复") }, { message(it) })
            },
            onDismiss = { resetUsernameOpen = false },
        )
    }

    if (resetPasswordOpen) {
        ConfirmDialog(
            title = "恢复 .env 密码",
            text = "清除面板中保存的密码，改用 .env 中的 ADMIN_PASSWORD。",
            onConfirm = {
                resetPasswordOpen = false
                vm.clearPassword({ message("已恢复") }, { message(it) })
            },
            onDismiss = { resetPasswordOpen = false },
        )
    }

    if (logoutOpen) {
        ConfirmDialog(
            title = "退出登录",
            text = "确定退出当前账号？",
            confirmText = "退出",
            onConfirm = {
                logoutOpen = false
                Graph.session.clearToken()
                nav.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onDismiss = { logoutOpen = false },
        )
    }

    if (confirmUpdate) {
        ConfirmDialog(
            title = "立即更新",
            text = "服务器将执行 git reset → 构建 → 重启。更新期间面板会短暂不可用。",
            confirmText = "开始更新",
            onConfirm = {
                confirmUpdate = false
                vm.runUpdate({ message("更新已开始") }, { message(it) })
            },
            onDismiss = { confirmUpdate = false },
        )
    }

    scanResult?.let { result ->
        BucketScanDialog(
            result = result,
            busy = ui.busy,
            onDismiss = { scanResult = null },
            onDeleteOrphans = {
                vm.deleteOrphans(
                    result.orphans.map { it.key },
                    onSuccess = {
                        scanResult = null
                        message("已删除 ${it.deleted.size} 个残留对象")
                    },
                    onError = { message(it) },
                )
            },
        )
    }

    migrationResult?.let { result ->
        AlertDialog(
            onDismissRequest = { migrationResult = null },
            title = { Text("迁移完成") },
            text = {
                Column {
                    Text("成功 ${result.migrated} 个，跳过 ${result.skipped} 个，失败 ${result.failed.size} 个")
                    Text("剩余 ${result.remaining} / ${result.total}", style = MaterialTheme.typography.bodySmall)
                    result.failed.take(5).forEach {
                        Text("${it.key}: ${it.reason}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { migrationResult = null }) { Text("关闭") } },
        )
    }

    updateLog?.let { log ->
        AlertDialog(
            onDismissRequest = { updateLog = null },
            title = { Text("更新日志（${log.lastRun}）") },
            text = {
                Column(modifier = Modifier.height(360.dp)) {
                    Text(
                        log.log.ifBlank { "暂无日志" },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { updateLog = null }) { Text("关闭") } },
        )
    }
}

@Composable
private fun UsageBlock(usage: R2UsageStatus) {
    if (!usage.configured) {
        Text(
            if (usage.hasToken) "已保存 Token，但尚未配置 R2 Account ID" else "尚未配置只读 API Token",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val data = usage.usage
    if (data == null) {
        Text(
            usage.error ?: "暂无用量数据",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    val percent = if (usage.freeTierBytes > 0) data.totalBytes.toFloat() / usage.freeTierBytes else 0f
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("存储总量", formatBytes(data.totalBytes), Modifier.weight(1f))
        StatTile("对象数", data.objectCount.toString(), Modifier.weight(1f))
        StatTile("免费额度占比", "${(percent * 100).toInt()}%", Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { percent.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "已发布 ${formatBytes(data.publishedBytes)} · 待清理 ${formatBytes(data.pendingBytes)} · 更新于 ${formatDateShort(data.fetchedAt)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ChangeUsernameDialog(
    onDismiss: () -> Unit,
    onSubmit: (currentPassword: String, newUsername: String) -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改用户名") },
        text = {
            Column {
                SecretField(current, { current = it }, "当前密码")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(current, newName) },
                enabled = current.isNotBlank() && newName.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (currentPassword: String, newPassword: String) -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.isNotBlank() && confirm != newPassword
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码") },
        text = {
            Column {
                SecretField(current, { current = it }, "当前密码")
                Spacer(Modifier.height(8.dp))
                SecretField(newPassword, { newPassword = it }, "新密码（至少 8 位）")
                Spacer(Modifier.height(8.dp))
                SecretField(confirm, { confirm = it }, "确认新密码")
                if (mismatch) {
                    Spacer(Modifier.height(4.dp))
                    Text("两次输入的新密码不一致", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "修改成功后所有已签发的登录态会失效，需要重新登录。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(current, newPassword) },
                enabled = current.isNotBlank() && newPassword.length >= 8 && confirm == newPassword,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun BucketScanDialog(
    result: BucketScanResult,
    busy: Boolean,
    onDismiss: () -> Unit,
    onDeleteOrphans: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("存储桶体检") },
        text = {
            Column(modifier = Modifier.height(420.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("对象", result.objectCount.toString(), Modifier.weight(1f))
                    StatTile("残留", result.orphanCount.toString(), Modifier.weight(1f))
                    StatTile("缺失", result.missingCount.toString(), Modifier.weight(1f))
                }
                Text(
                    "总大小 ${formatBytes(result.totalSize)} · 残留占用 ${formatBytes(result.orphanBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (result.truncated) {
                    Text("扫描结果已截断（对象过多）", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(6.dp))
                HorizontalDivider()
                if (result.orphans.isEmpty() && result.missing.isEmpty()) {
                    Text("没有发现残留或缺失对象", modifier = Modifier.padding(vertical = 12.dp))
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (result.orphans.isNotEmpty()) {
                        item {
                            Text("未引用的残留对象", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 6.dp))
                        }
                        items(result.orphans, key = { "o-${it.key}" }) { obj ->
                            Column(modifier = Modifier.padding(vertical = 3.dp)) {
                                Text(obj.key, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatBytes(obj.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (result.missing.isNotEmpty()) {
                        item {
                            Text("数据库有记录但桶内缺失", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 6.dp))
                        }
                        items(result.missing, key = { "m-${it.id}" }) { record ->
                            Column(modifier = Modifier.padding(vertical = 3.dp)) {
                                Text(record.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(record.key, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (result.orphans.isNotEmpty()) {
                TextButton(onClick = onDeleteOrphans, enabled = !busy) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("清理全部残留")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
