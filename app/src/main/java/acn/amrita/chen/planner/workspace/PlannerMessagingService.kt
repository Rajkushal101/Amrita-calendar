package acn.amrita.chen.planner.workspace

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

fun registerDevice(context:android.content.Context) {
    val user=FirebaseAuth.getInstance().currentUser?.takeIf{it.isEmailVerified}?:return
    val prefs=context.getSharedPreferences("device_identity",0)
    val id=prefs.getString("id",null)?:java.util.UUID.randomUUID().toString().also{prefs.edit().putString("id",it).apply()}
    com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener {token->
        if(FirebaseAuth.getInstance().currentUser?.uid==user.uid)FirebaseFirestore.getInstance().document("users/${user.uid}/devices/$id").set(mapOf("token" to token))
    }
}

class PlannerMessagingService:FirebaseMessagingService() {
    override fun onNewToken(token:String) {
        val user=FirebaseAuth.getInstance().currentUser ?: return
        if(user.isEmailVerified)FirebaseFirestore.getInstance().document("users/${user.uid}/devices/${deviceId()}").set(mapOf("token" to token))
    }
    override fun onMessageReceived(message:RemoteMessage) {
        // FCM only supplies a generic alert. Academic records always come from authorized sync.
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return
        val manager=getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(android.app.NotificationChannel("class_updates","Class updates",android.app.NotificationManager.IMPORTANCE_DEFAULT))
        val intent=android.content.Intent(this,acn.amrita.chen.planner.MainActivity::class.java)
        val pending=android.app.PendingIntent.getActivity(this,0,intent,android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(message.messageId?.hashCode()?:0,androidx.core.app.NotificationCompat.Builder(this,"class_updates").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Class update").setContentText("Open ACN Planner to see the update.").setContentIntent(pending).setAutoCancel(true).build())
    }
    private fun deviceId():String {
        val p=getSharedPreferences("device_identity",0)
        return p.getString("id",null) ?: java.util.UUID.randomUUID().toString().also {p.edit().putString("id",it).apply()}
    }
}
