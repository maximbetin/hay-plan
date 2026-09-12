package com.mbk.hayplan.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mbk.hayplan.MainActivity
import com.mbk.hayplan.R
import com.mbk.hayplan.data.ForecastCache
import com.mbk.hayplan.data.ForecastIssue
import com.mbk.hayplan.data.ForecastRepository
import com.mbk.hayplan.data.LocationCatalog
import com.mbk.hayplan.data.OpenMeteoClient
import com.mbk.hayplan.domain.ActivityType
import com.mbk.hayplan.domain.DailyRecommendations
import com.mbk.hayplan.domain.DailyRecommendationPlanner
import com.mbk.hayplan.domain.DayPlanner
import com.mbk.hayplan.domain.PlaceRecommendation
import com.mbk.hayplan.domain.RecommendedActivity
import com.mbk.hayplan.ui.AppLanguage
import com.mbk.hayplan.ui.DailyNotificationFormatter
import kotlinx.coroutines.CancellationException
import java.io.File
import java.time.Instant
import java.time.LocalDateTime

class DailyPlanWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val settings = DailyNotificationPreferences.read(applicationContext)
        if (!settings.enabled) return Result.success()
        DailyPlanScheduler.enqueueNext(applicationContext, settings)

        val nowInstant = Instant.now()
        val now = LocalDateTime.ofInstant(nowInstant, LocationCatalog.zone)
        val language = selectedLanguage()
        val notificationLocations = LocationCatalog.locations.filter { it.id == "gijon" || it.id == "oviedo" }
        val repository = ForecastRepository(OpenMeteoClient(
            ForecastCache(File(applicationContext.noBackupFilesDir, "forecasts"))), notificationLocations)
        val forecasts = try {
            repository.load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return retryOrNotifyUnavailable(language)
        }
        val gijon = forecasts.firstOrNull { it.location.id == "gijon" }
        val oviedo = forecasts.firstOrNull { it.location.id == "oviedo" }
        if (gijon == null || oviedo == null) return retryOrNotifyUnavailable(language)

        val gijonCoastalWeatherAvailable = gijon.beach.hours.isNotEmpty() &&
            ForecastIssue.BEACH_WEATHER_FALLBACK !in gijon.beach.errors
        val essentialWeatherAvailable = gijon.weather.hours.isNotEmpty() &&
            gijonCoastalWeatherAvailable && oviedo.weather.hours.isNotEmpty()
        if (DailyNotificationRetryPolicy.shouldRetry(runAttemptCount, essentialWeatherAvailable)) {
            return Result.retry()
        }

        val date = now.toLocalDate()
        val gijonBeach = DayPlanner.forDate(gijon.beach.hours, date, now, ActivityType.BEACH)
        val gijonHiking = DayPlanner.forDate(gijon.weather.hours, date, now, ActivityType.HIKING)
        val oviedoHiking = DayPlanner.forDate(oviedo.weather.hours, date, now, ActivityType.HIKING)
        val plan = DailyRecommendationPlanner.create(
            gijonBeach,
            gijonHiking,
            oviedoHiking,
            gijonCoastalWeatherAvailable = gijonCoastalWeatherAvailable,
        )
        val gijonSources = if (plan.gijon.activity == RecommendedActivity.BEACH)
            gijon.beach.sources else gijon.weather.sources
        val sources = gijonSources + oviedo.weather.sources
        val saved = sources.any { it.refreshFailed || !ForecastCache.isFresh(it.fetchedAt, nowInstant) }
        val text = DailyNotificationFormatter.format(plan, language, saved)
        showNotification(text.title, text.body, text.subText, language)
        return Result.success()
    }

    private fun retryOrNotifyUnavailable(language: AppLanguage): Result {
        if (DailyNotificationRetryPolicy.shouldRetry(runAttemptCount, essentialForecastAvailable = false)) {
            return Result.retry()
        }
        val unavailable = PlaceRecommendation(RecommendedActivity.UNAVAILABLE)
        val text = DailyNotificationFormatter.format(
            DailyRecommendations(unavailable, unavailable), language, savedForecast = false)
        showNotification(text.title, text.body, text.subText, language)
        return Result.success()
    }

    private fun selectedLanguage(): AppLanguage {
        val preferences = applicationContext.getSharedPreferences("settings", 0)
        return if (preferences.contains("language"))
            AppLanguage.fromCode(preferences.getString("language", null)) else AppLanguage.fromSystem()
    }

    private fun showNotification(title: String, body: String, subText: String?, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        val channelName = if (language == AppLanguage.SPANISH) "Plan diario" else "Daily plan"
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText(subText)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "daily-outing-plan"
        private const val NOTIFICATION_ID = 1001
    }
}

object DailyNotificationRetryPolicy {
    const val MAX_RETRIES = 3

    fun shouldRetry(runAttemptCount: Int, essentialForecastAvailable: Boolean): Boolean =
        !essentialForecastAvailable && runAttemptCount < MAX_RETRIES
}
