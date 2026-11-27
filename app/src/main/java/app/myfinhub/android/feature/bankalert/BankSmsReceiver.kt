package app.myfinhub.android.feature.bankalert

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.content.ContextCompat
import app.myfinhub.android.BuildConfig
import app.myfinhub.android.MainActivity
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal const val BANK_INBOX_ACTION = "app.myfinhub.android.action.BANK_INBOX"

internal fun hasBankSmsPermission(context: Context): Boolean =
    BuildConfig.BANK_SMS_CAPTURE_ENABLED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

/** Only the system can invoke the exported receiver (BROADCAST_SMS signature permission). */
class BankSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION || !hasBankSmsPermission(context)) return
        val parts = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull() ?: return
        if (parts.size !in 1..10) return
        val sender = parts.first().originatingAddress
        if (!sender.orEmpty().trim().equals("Piraeus", ignoreCase = true) ||
            parts.any { !it.originatingAddress.orEmpty().trim().equals("Piraeus", ignoreCase = true) }) return
        val body = buildString {
            parts.forEach { append(it.messageBody.orEmpty()) }
        }
        if (body.length !in 1..1500) return
        val timestamp = parts.first().timestampMillis
        val app = context.applicationContext
        val pending = goAsync()
        try { executor.execute {
            try {
                if (hasBankSmsPermission(app) && BankSmsInboxStore(app).receive(sender, timestamp, body)) {
                    notifyBankSms(app)
                }
            } catch (_: Exception) {
                // Never log the message or derived financial details.
            } finally { pending.finish() }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { pending.finish() }
    }

    private companion object {
        val executor = ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS, ArrayBlockingQueue(16))
    }
}

private const val BANK_CHANNEL = "bank_sms_review_v1"
private const val BANK_NOTIFICATION_ID = 4107

internal fun clearBankSmsNotification(context: Context) {
    context.getSystemService(NotificationManager::class.java)?.cancel(BANK_NOTIFICATION_ID)
}

private fun notifyBankSms(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    if (!manager.areNotificationsEnabled()) return
    manager.createNotificationChannel(NotificationChannel(
        BANK_CHANNEL, "Τραπεζικά SMS προς έλεγχο", NotificationManager.IMPORTANCE_HIGH,
    ).apply { lockscreenVisibility = Notification.VISIBILITY_SECRET })
    val open = PendingIntent.getActivity(context, BANK_NOTIFICATION_ID,
        Intent(context, MainActivity::class.java).setAction(BANK_INBOX_ACTION)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    manager.notify(BANK_NOTIFICATION_ID, Notification.Builder(context, BANK_CHANNEL)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("MyFinHub")
        .setContentText("Νέα τραπεζική κίνηση προς έλεγχο")
        .setVisibility(Notification.VISIBILITY_SECRET)
        .setContentIntent(open)
        .setAutoCancel(true)
        .build())
}
