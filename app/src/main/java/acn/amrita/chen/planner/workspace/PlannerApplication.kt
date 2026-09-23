package acn.amrita.chen.planner.workspace

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class PlannerApplication:Application() {
    override fun onCreate() {
        super.onCreate()
        val debug=(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0
        val factory=if(debug)runCatching {
            Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory").getMethod("getInstance").invoke(null) as AppCheckProviderFactory
        }.getOrElse{PlayIntegrityAppCheckProviderFactory.getInstance()} else PlayIntegrityAppCheckProviderFactory.getInstance()
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)
        val sync=androidx.work.PeriodicWorkRequestBuilder<WorkspaceSyncWorker>(15,java.util.concurrent.TimeUnit.MINUTES)
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()).build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork("academic-outbox",androidx.work.ExistingPeriodicWorkPolicy.KEEP,sync)
    }
}
