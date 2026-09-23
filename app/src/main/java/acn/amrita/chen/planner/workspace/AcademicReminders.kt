package acn.amrita.chen.planner.workspace

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** WorkManager provides durable, best-effort reminders; it does not promise exact alarms. */
object AcademicReminders {
    fun schedule(context:Context,owner:String,record:AcademicRecord,minutes:Int?) {
        val name="academic-reminder-$owner-${record.id}"
        if(minutes==null||record.deleted){WorkManager.getInstance(context).cancelUniqueWork(name);return}
        val j=record.json();val date=j.optString("date")
        if(date.isBlank()||date=="null"){WorkManager.getInstance(context).cancelUniqueWork(name);return}
        val at=LocalDate.parse(date).atTime(j.optString("time").takeUnless{it.isBlank()||it=="null"}?.let{LocalTime.parse(it)}?:LocalTime.of(9,0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()-minutes*60000L
        if(at<=System.currentTimeMillis()){WorkManager.getInstance(context).cancelUniqueWork(name);return}
        val request=OneTimeWorkRequestBuilder<AcademicReminderWorker>().setInitialDelay(at-System.currentTimeMillis(),TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("owner" to owner,"recordId" to record.id)).addTag("reminders-$owner").build()
        WorkManager.getInstance(context).enqueueUniqueWork(name,ExistingWorkPolicy.REPLACE,request)
    }
}
class AcademicReminderWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val owner=inputData.getString("owner")?:return Result.failure()
        val user=com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.takeUnless{it.isAnonymous}?.uid?:"legacy"
        if(owner!=user)return Result.success()
        val record=acn.amrita.chen.planner.data.AppDatabase.getDatabase(applicationContext).workspaceDao().get(owner,inputData.getString("recordId")?:"")?:return Result.success()
        if(record.deleted)return Result.success()
        if(android.os.Build.VERSION.SDK_INT>=33&&applicationContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return Result.success()
        val manager=applicationContext.getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(android.app.NotificationChannel("academic_reminders","Academic reminders",android.app.NotificationManager.IMPORTANCE_DEFAULT))
        val intent=android.content.Intent(applicationContext,acn.amrita.chen.planner.MainActivity::class.java)
        val pending=android.app.PendingIntent.getActivity(applicationContext,record.id.hashCode(),intent,android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(record.id.hashCode(),androidx.core.app.NotificationCompat.Builder(applicationContext,"academic_reminders").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(record.title).setContentText(dateLabel(record.json())).setContentIntent(pending).setAutoCancel(true).build())
        return Result.success()
    }
}

/** Date-specific entries override their weekly template; a cancellation hides that occurrence. */
object ScheduleEngine {
    fun forDate(records:List<AcademicRecord>,date:LocalDate):List<AcademicRecord> {
        if(records.any{it.kind=="NOTICE"&&it.json().optBoolean("isHoliday")&&it.json().optString("date")==date.toString()})return emptyList()
        val sessions=records.filter{it.kind=="TIMETABLE"&&!it.deleted}
        val dated=sessions.filter{it.json().optString("date")==date.toString()}
        val replaced=dated.map{it.json().optString("templateId")}.filter{it.isNotBlank()}.toSet()
        return (sessions.filter{val j=it.json();it.id !in replaced&&j.optString("date").let{d->d.isBlank()||d=="null"}&&j.optInt("day")==date.dayOfWeek.value}+dated)
            .filter{!it.json().optBoolean("cancelled")}.sortedBy{it.json().optString("time")}
    }
}
