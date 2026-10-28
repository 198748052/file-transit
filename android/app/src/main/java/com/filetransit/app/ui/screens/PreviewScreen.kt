package com.filetransit.app.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.filetransit.app.ui.PreviewInbox
import com.filetransit.app.util.PreviewKind
import com.filetransit.app.util.enqueueDownload
import com.filetransit.app.util.previewKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(nav: NavHostController) {
    val request = remember { PreviewInbox.request }
    val context = LocalContext.current
    val kind = remember(request) { request?.let { previewKind(it.mime, it.name) } ?: PreviewKind.NONE }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        request?.name ?: "预览",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateUp() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    request?.let {
                        IconButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(it.url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }) {
                            Icon(Icons.Outlined.OpenInNew, contentDescription = "用其他应用打开")
                        }
                        IconButton(onClick = { enqueueDownload(context, it.url, it.name) }) {
                            Icon(Icons.Outlined.Download, contentDescription = "下载")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (request == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("预览信息已失效，请返回重试")
            }
            return@Scaffold
        }
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val req = request ?: return@Box
            when (kind) {
                PreviewKind.IMAGE -> ImagePreview(req.url)
                PreviewKind.VIDEO -> MediaPreview(req.url, req.mime)
                PreviewKind.AUDIO -> MediaPreview(req.url, req.mime)
                PreviewKind.PDF -> PdfPreview(req.url)
                PreviewKind.TEXT -> TextPreview(req.url)
                PreviewKind.NONE -> UnsupportedPreview(req.name, req.mime, req.url)
            }
        }
    }
}

@Composable
private fun ImagePreview(url: String) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = "图片预览",
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().background(Color.Black),
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        },
        error = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("图片加载失败", color = Color.White)
                }
            }
        },
        success = { SubcomposeAsyncImageContent() },
    )
}

@Composable
private fun MediaPreview(url: String, mime: String) {
    val context = LocalContext.current
    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (mime.startsWith("audio/")) {
            Text(
                "音频播放中",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

private data class PdfHolder(
    val renderer: PdfRenderer,
    val descriptor: ParcelFileDescriptor,
    val pageCount: Int,
    val file: File,
)

@Composable
private fun PdfPreview(url: String) {
    val context = LocalContext.current
    var holder by remember { mutableStateOf<PdfHolder?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val mutex = remember { Mutex() }

    LaunchedEffect(url) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(context.cacheDir, "preview-${url.hashCode()}.pdf")
                if (!file.exists() || file.length() == 0L) {
                    OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        val body = response.body ?: throw IOException("empty body")
                        body.byteStream().use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(descriptor)
                withContext(Dispatchers.Main) {
                    holder = PdfHolder(renderer, descriptor, renderer.pageCount, file)
                    loading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = e.message ?: "PDF 加载失败"
                    loading = false
                }
            }
        }
    }

    DisposableEffect(holder) {
        onDispose {
            holder?.renderer?.close()
            holder?.descriptor?.close()
        }
    }

    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        error != null || holder == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "PDF 加载失败")
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text("用其他应用打开") }
            }
        }
        else -> {
            val pdf = holder!!
            val pagerState = rememberPagerState(pageCount = { pdf.pageCount })
            Column(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) { page ->
                    PdfPage(pdf, page, mutex)
                }
                Text(
                    "第 ${pagerState.currentPage + 1} / ${pdf.pageCount} 页",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun PdfPage(pdf: PdfHolder, index: Int, mutex: Mutex) {
    var bitmap by remember(pdf, index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pdf, index) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                mutex.withLock {
                    pdf.renderer.openPage(index).use { page ->
                        val width = 1200
                        val height = (width * page.height / page.width.toFloat()).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(AndroidColor.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF222222)).verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        val image = bitmap
        if (image == null) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 48.dp), color = Color.White)
        } else {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "第 ${index + 1} 页",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TextPreview(url: String) {
    var text by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        withContext(Dispatchers.IO) {
            try {
                OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val stream = response.body?.byteStream() ?: throw IOException("empty body")
                    val buffer = CharArray(64 * 1024)
                    val builder = StringBuilder()
                    var total = 0
                    stream.bufferedReader(Charsets.UTF_8).use { reader ->
                        while (total < 200_000) {
                            val read = reader.read(buffer)
                            if (read <= 0) break
                            builder.append(buffer, 0, read)
                            total += read
                        }
                    }
                    withContext(Dispatchers.Main) { text = builder.toString() }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { error = e.message ?: "文本加载失败" }
            }
        }
    }

    when {
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error!!) }
        text == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize(),
        ) {
            SelectionContainer {
                Text(
                    text!!,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun UnsupportedPreview(name: String, mime: String, url: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text("暂不支持在线预览", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("$name（$mime）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }) { Text("用其他应用打开") }
            Button(onClick = { enqueueDownload(context, url, name) }) { Text("下载文件") }
        }
    }
}
