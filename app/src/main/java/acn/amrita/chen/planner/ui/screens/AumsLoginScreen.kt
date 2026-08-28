package acn.amrita.chen.planner.ui.screens

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import acn.amrita.chen.planner.ui.MainViewModel
import acn.amrita.chen.planner.ui.theme.AcnRed

@Composable
fun AumsLoginScreen(viewModel: MainViewModel, onHtmlExtracted: (String) -> Unit) {
    val aumsUrl = "https://my.amrita.edu/index/login" // Chennai campus login
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Scaffold { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        webViewRef = this
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.allowContentAccess = true
                        settings.allowFileAccess = true
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                        webChromeClient = android.webkit.WebChromeClient()
                        
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun processHTML(html: String) {
                                onHtmlExtracted(html)
                            }
                        }, "HTMLOUT")

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                return false
                            }

                            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                                super.onReceivedError(view, request, error)
                                android.util.Log.e("WebViewError", "Error: ${error?.description}")
                            }

                            override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                                handler?.proceed()
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                // Inject an interval checker to capture table HTML even if rendered via AJAX
                                view.evaluateJavascript(
                                    "(function() { " +
                                    "   setInterval(function() {" +
                                    "       if (document.body && (document.body.innerText.includes('Total Classes') || document.body.innerText.includes('Course Code'))) {" +
                                    "           if (!window.aumsScraped) {" +
                                    "               window.aumsScraped = true;" +
                                    "               window.HTMLOUT.processHTML('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');" +
                                    "           }" +
                                    "       }" +
                                    "   }, 3000);" +
                                    "})();", null
                                )
                            }
                        }
                        loadUrl(aumsUrl)
                    }
                },
                update = { view ->
                    webViewRef = view
                }
            )

            FloatingActionButton(
                onClick = {
                    webViewRef?.evaluateJavascript(
                        "(function() { window.HTMLOUT.processHTML('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>'); })();",
                        null
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = AcnRed,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Sync, contentDescription = "Sync Attendance")
            }
        }
    }
}
