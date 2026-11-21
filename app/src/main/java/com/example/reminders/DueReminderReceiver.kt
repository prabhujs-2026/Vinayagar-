package com.example.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.util.DateAndReportUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DueReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences(DueReminderScheduler.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(DueReminderScheduler.KEY_ENABLED, true)) return

        val dueDay = prefs.getInt(DueReminderScheduler.KEY_DUE_DAY, 10).toLong()
        val daysBefore = prefs.getInt(DueReminderScheduler.KEY_DAYS_BEFORE, 5).toLong()
        val festivalName = prefs.getString(DueReminderScheduler.KEY_FESTIVAL_NAME, "Vinayagar Fund") ?: "Vinayagar Fund"
        val isAdmin = prefs.getBoolean(DueReminderScheduler.KEY_IS_ADMIN, false)
        val pendingCount = prefs.getInt(DueReminderScheduler.KEY_PENDING_COUNT, 0)

        val currentMonth = DateAndReportUtils.currentBillingMonth()
        val remaining = DateAndReportUtils.daysUntilDue(currentMonth, dueDay)
        if (remaining > daysBefore.toInt()) return

        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (prefs.getString(DueReminderScheduler.KEY_LAST_NOTIFIED_DATE, "") == todayKey) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        DueReminderScheduler.ensureChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", if (isAdmin) "reminders" else "dues")
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            5106,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dueDateLabel = DateAndReportUtils.formatDueDate(currentMonth, dueDay)
        val title = if (isAdmin) {
            "5-Day Due Reminder Window • $festivalName"
        } else {
            "Monthly Contribution Due Soon • $festivalName"
        }
        val body = if (isAdmin) {
            if (pendingCount > 0) {
                "$pendingCount members still pending before $dueDateLabel. Tap to send gentle WhatsApp reminders."
            } else {
                "Due date is $dueDateLabel (${DateAndReportUtils.deadlineStatusLabel(currentMonth, dueDay)}). Tap to review pending WhatsApp reminders."
            }
        } else {
            "Gentle reminder: Your monthly contribution is due by $dueDateLabel. Tap to pay seamlessly via UPI."
        }

        val notification = NotificationCompat.Builder(context, DueReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(5107, notification)
        prefs.edit().putString(DueReminderScheduler.KEY_LAST_NOTIFIED_DATE, todayKey).apply()
    }
}
