package acn.amrita.chen.planner.workspace

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import acn.amrita.chen.planner.data.AumsScraper
import acn.amrita.chen.planner.ui.components.*
import acn.amrita.chen.planner.ui.theme.AcnBrandCrimson
import org.json.JSONArray

private fun isAllowedPortalUrl(url: String?): Boolean = runCatching {
    if (url.isNullOrBlank()) return false
    val u = Uri.parse(url)
    val scheme = u.scheme?.lowercase()
    if (scheme != "https" && scheme != "http") return false
    val host = u.host?.lowercase() ?: return false
    host == "my.amrita.edu" ||
        host.endsWith(".amrita.edu") ||
        host == "login.microsoftonline.com" ||
        host.endsWith(".microsoftonline.com") ||
        host == "login.live.com" ||
        host.endsWith(".live.com") ||
        host == "login.microsoft.com" ||
        host.endsWith(".microsoft.com") ||
        host.endsWith(".msftauth.net") ||
        host.endsWith(".msauth.net") ||
        host.endsWith(".windowsazure.com")
}.getOrDefault(false)

private fun isAttendancePage(url: String?): Boolean = runCatching {
    if (url.isNullOrBlank()) return false
    val u = Uri.parse(url)
    val host = u.host?.lowercase() ?: return false
    host == "my.amrita.edu" || host.endsWith(".amrita.edu")
}.getOrDefault(false)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalScreen(vm: WorkspaceViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var preview by remember { mutableStateOf<AumsScraper.ParsedAumsData?>(null) }
    var currentUrl by remember { mutableStateOf("https://my.amrita.edu/index/login") }
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = true) {
        if (web?.canGoBack() == true) {
            web?.goBack()
        } else {
            onClose()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            web?.stopLoading()
            web?.destroy()
            web = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "My Amrita Portal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            currentUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close portal")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        loadError = null
                        web?.reload()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl.ifBlank { "https://my.amrita.edu/index/login" }))
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in browser")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    AcnButton(
                        text = "Preview Attendance Import",
                        onClick = {
                            if (isAttendancePage(web?.url)) {
                                web?.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                                    runCatching {
                                        JSONArray("[$raw]").getString(0)
                                    }.onSuccess {
                                        preview = AumsScraper.parseAttendanceHtml(it)
                                    }.onFailure {
                                        vm.message.value = "Unable to read the attendance page"
                                    }
                                }
                            } else {
                                vm.message.value = "Please navigate to my.amrita.edu attendance page first"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading && progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = AcnBrandCrimson
                )
            }

            AcnNoticeBanner(
                message = "Sign in on My Amrita, open your attendance page, then preview the import. Your session data and credentials never leave this device.",
                icon = Icons.Default.Lock,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )

            if (loadError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            loadError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            loadError = null
                            web?.loadUrl("https://my.amrita.edu/index/login")
                        }) {
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                factory = { context ->
                    WebView(context).apply {
                        web = this
                        isClickable = true
                        isFocusable = true
                        isFocusableInTouchMode = true
                        WebView.setWebContentsDebuggingEnabled(true)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)
                            javaScriptCanOpenWindowsAutomatically = true
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        }

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                                if (newProgress >= 100) {
                                    isLoading = false
                                }
                            }

                            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                android.util.Log.d("PortalJS", "${consoleMessage?.message()} [line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}]")
                                return super.onConsoleMessage(consoleMessage)
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString()
                                return if (isAllowedPortalUrl(url)) {
                                    false
                                } else {
                                    true
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                currentUrl = url ?: "https://my.amrita.edu/index/login"
                                loadError = null
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                currentUrl = url ?: "https://my.amrita.edu/index/login"
                                view?.evaluateJavascript(
                                    "(function() { " +
                                    "  var s = document.getElementById('submitBtn'); " +
                                    "  if (s) { " +
                                    "    document.querySelectorAll('[onclick*=\"submitBtn\"]').forEach(function(el) { " +
                                    "      el.addEventListener('click', function() { s.click(); }); " +
                                    "    }); " +
                                    "  } " +
                                    "})();",
                                    null
                                )
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    isLoading = false
                                    loadError = "Connection error: ${error?.description ?: "Failed to load page"}"
                                }
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                handler?.cancel()
                                isLoading = false
                                loadError = "Portal certificate could not be verified. Login stopped."
                            }
                        }

                        loadUrl("https://my.amrita.edu/index/login")
                    }
                }
            )
        }
    }

    preview?.let { p ->
        var targetSemester by remember(p) { mutableIntStateOf(p.detectedSemester ?: vm.semester.value) }
        var showSemPicker by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { preview = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Semester $targetSemester Import",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    AcnBadge(
                        text = "${p.attendanceList.size} Courses",
                        style = if (p.attendanceList.isNotEmpty()) AcnBadgeStyle.PRIMARY else AcnBadgeStyle.CAUTION
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!p.academicTerm.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Academic Term: ${p.academicTerm}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (p.detectedSemester != null) {
                                    Text(
                                        text = "Mapped to Semester ${p.detectedSemester}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Target Semester:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Box {
                            OutlinedButton(
                                onClick = { showSemPicker = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Semester $targetSemester ▼", style = MaterialTheme.typography.labelMedium)
                            }
                            DropdownMenu(
                                expanded = showSemPicker,
                                onDismissRequest = { showSemPicker = false }
                            ) {
                                (1..12).forEach { sem ->
                                    DropdownMenuItem(
                                        text = { Text("Semester $sem") },
                                        onClick = {
                                            targetSemester = sem
                                            showSemPicker = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    p.attendanceList.forEach { record ->
                        AcnCard {
                            Text(
                                record.subjectName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    record.subjectCode,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${record.attendedClasses} / ${record.totalClasses} classes (${if (record.totalClasses > 0) record.attendedClasses * 100 / record.totalClasses else 0}%)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    if (p.warnings.isNotEmpty()) {
                        p.warnings.forEach { warn ->
                            AcnNoticeBanner(
                                message = warn,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    Text(
                        "Existing records are preserved if this import fails or is cancelled.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                AcnButton(
                    text = "Confirm Import",
                    enabled = p.attendanceList.isNotEmpty(),
                    onClick = {
                        vm.importAttendance(p, targetSemester)
                        preview = null
                        onClose()
                    }
                )
            },
            dismissButton = {
                AcnOutlinedButton(
                    text = "Cancel",
                    onClick = { preview = null }
                )
            }
        )
    }
}

