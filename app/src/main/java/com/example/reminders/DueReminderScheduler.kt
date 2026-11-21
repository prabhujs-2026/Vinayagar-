package com.example.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.CommitteeSettings
import java.util.Calendar

object DueReminderScheduler {

    const val CHANNEL_ID = "vinayagar_due_reminders"
    const val PREFS_NAME = "vinayagar_reminder_prefs"
    const val KEY_ENABLED = "auto_reminder_enabled"
    const val KEY_DUE_DAY = "due_day"
    const val KEY_DAYS_BEFORE = "reminder_days_before"
    const val KEY_FESTIVAL_NAME = "festival_name"
    const val KEY_IS_ADMIN = "is_admin"
    const val KEY_PENDING_COUNT = "pending_count"
    const val KEY_LAST_NOTIFIED_DATE = "last_notified_date"

    private const val REQUEST_CODE = 5105

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Vinayagar Monthly Due Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifies 5 days before the monthly contribution deadline for WhatsApp reminders and UPI payments."
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun syncSchedule(
        context: Context,
        settings: CommitteeSettings,
        isAdmin: Boolean,
        pendingCount: Int
    ) {
        ensureChannel(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.autoReminderEnabled)
            .putInt(KEY_DUE_DAY, settings.dueDayOfMonth.toInt())
            .putInt(KEY_DAYS_BEFORE, settings.reminderDaysBefore.toInt())
            .putString(KEY_FESTIVAL_NAME, settings.festivalName)
            .putBoolean(KEY_IS_ADMIN, isAdmin)
            .putInt(KEY_PENDING_COUNT, pendingCount)
            .apply()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DueReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!settings.autoReminderEnabled) {
            alarmManager.cancel(pendingIntent)
            return
        }

        // Daily check around 9:00 AM using battery-friendly inexact repeating alarm (zero special permissions)
        val triggerAt = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAt.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }
}
