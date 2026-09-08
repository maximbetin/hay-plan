package com.mbk.hayplan.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mbk.hayplan.data.LocationCatalog
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object DailyPlanScheduler {
    private const val WORK_TAG = "daily-outing-plan"

    fun replace(context: Context, settings: DailyNotificationSettings) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        if (settings.enabled) enqueueNext(context, settings)
    }

    fun ensure(context: Context, settings: DailyNotificationSettings) {
        if (settings.enabled) enqueueNext(context, settings)
        else WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }

    fun enqueueNext(
        context: Context,
        settings: DailyNotificationSettings,
        now: ZonedDateTime = ZonedDateTime.now(LocationCatalog.zone),
    ) {
        if (!settings.enabled) return
        val next = nextRun(now, settings.time)
        val delayMillis = Duration.between(now, next).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<DailyPlanWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$WORK_TAG-${next.toLocalDate()}", ExistingWorkPolicy.KEEP, request)
    }

    fun nextRun(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
        val localNow = now.withZoneSameInstant(LocationCatalog.zone)
        val today = localNow.toLocalDate().atTime(time).atZone(LocationCatalog.zone)
        return if (today.isAfter(localNow)) today else today.plusDays(1)
    }
}
