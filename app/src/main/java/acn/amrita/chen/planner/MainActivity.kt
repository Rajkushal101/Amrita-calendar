package acn.amrita.chen.planner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import acn.amrita.chen.planner.workspace.SharedImports
import acn.amrita.chen.planner.workspace.WorkspaceApp

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        androidx.work.WorkManager.getInstance(this).cancelUniqueWork("AumsSyncWorker")
        androidx.work.WorkManager.getInstance(this).cancelUniqueWork("DailyBriefing")
        acceptShare(intent)
        setContent { WorkspaceApp() }
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);acceptShare(intent)}
    @Suppress("DEPRECATION") private fun acceptShare(intent:Intent) {
        if(intent.action==Intent.ACTION_SEND || intent.action==Intent.ACTION_SEND_MULTIPLE) {
            SharedImports.text.value=intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            SharedImports.uris.value=if(intent.action==Intent.ACTION_SEND_MULTIPLE)intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.toList().orEmpty().take(5)
                else listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        }
    }
}
