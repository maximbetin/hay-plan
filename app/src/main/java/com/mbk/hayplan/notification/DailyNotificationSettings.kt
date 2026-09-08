package com.mbk.hayplan.notification

import android.content.Context
import androidx.core.content.edit
import java.time.LocalTime

data class DailyNotificationSettings(
    val enabled: Boolean = false,
    val time: LocalTime = LocalTime.of(9, 0),
)

object DailyNotificationPreferences {
    private const val PREFERENCES = "settings"
    private const val ENABLED = "daily_notification_enabled"
    private const val HOUR = "daily_notification_hour"
    private const val MINUTE = "daily_notification_minute"

    fun read(context: Context): DailyNotificationSettings {
        val preferences = context.getSharedPreferences(PREFERENCES, 0)
        val hour = preferences.getInt(HOUR, 9).coerceIn(0, 23)
        val minute = preferences.getInt(MINUTE, 0).coerceIn(0, 59)
        return DailyNotificationSettings(preferences.getBoolean(ENABLED, false), LocalTime.of(hour, minute))
    }

    fun setEnabled(context: Context, enabled: Boolean): DailyNotificationSettings {
        context.getSharedPreferences(PREFERENCES, 0).edit { putBoolean(ENABLED, enabled) }
        return read(context)
    }

    fun setTime(context: Context, time: LocalTime): DailyNotificationSettings {
        context.getSharedPreferences(PREFERENCES, 0).edit {
            putInt(HOUR, time.hour)
            putInt(MINUTE, time.minute)
        }
        return read(context)
    }
}
