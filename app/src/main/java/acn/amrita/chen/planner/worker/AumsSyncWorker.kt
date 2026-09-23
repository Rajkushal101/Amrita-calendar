package acn.amrita.chen.planner.worker
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
/** Retained so upgrades can resolve previously scheduled work. No unattended portal access. */
class AumsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork() = Result.failure()
}
