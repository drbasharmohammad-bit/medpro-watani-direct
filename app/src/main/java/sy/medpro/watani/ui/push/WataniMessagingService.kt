package sy.medpro.watani.ui.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sy.medpro.watani.MainActivity
import sy.medpro.watani.data.WorkerActivationApi

/**
 * External push notifications (FCM).
 *
 * Shows a system notification for every incoming message (title/body,
 * tap opens the app). This is separate from the in-app notification
 * list, which keeps fetching /v1/notifications unchanged.
 *
 * onNewToken uploads the fresh token best-effort; the same upload also
 * runs at activation and at sign-in (deduped inside uploadFcmToken),
 * so a token refreshed while signed out still lands on next login.
 */
class WataniMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "watani_general"
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Med Pro - وطني"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: return
        showSystemNotification(title, body)
    }

    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WorkerActivationApi.uploadFcmToken(applicationContext)
            } catch (_: Exception) {
                // Best-effort: the next sign-in/activation upload retries.
            }
        }
    }

    private fun showSystemNotification(title: String, body: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "إشعارات وطني",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(sy.medpro.watani.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        // Stable-ish id per message so consecutive pushes don't clobber
        // each other into a single slot.
        manager.notify((title + body).hashCode(), notification)
    }
}
