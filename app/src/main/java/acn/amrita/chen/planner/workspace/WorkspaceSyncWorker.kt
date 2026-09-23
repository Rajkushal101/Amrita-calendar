package acn.amrita.chen.planner.workspace

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.coroutineScope

/** Delivers only previously saved or reviewed operations. Never initiates AI work. */
class WorkspaceSyncWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result=coroutineScope {
        val repo=WorkspaceRepository(applicationContext,this,realtime=false)
        try {
            repo.retryPending()
            Result.success() // Conflicts wait for Inbox review; the next periodic run retries connectivity failures.
        }finally{repo.close()}
    }
}
