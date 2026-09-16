package de.stefan.nasbackup.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {

    const val PERIODIC_WORK = "nas_periodic_sync"
    const val MANUAL_WORK = "nas_manual_sync"

    /**
     * UNMETERED bedeutet praktisch WLAN. Damit laeuft nie ein
     * Urlaubsvideo ueber das Mobilfunkvolumen.
     */
    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresBatteryNotLow(true)
        .build()

    fun schedulePeriodic(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<UploadWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .addTag(PERIODIC_WORK)
            .build()

        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Der Knopf "Jetzt sichern". Ignoriert den 6-Stunden-Takt. */
    fun runNow(ctx: Context) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .addTag(MANUAL_WORK)
            .build()

        WorkManager.getInstance(ctx).enqueueUniqueWork(
            MANUAL_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancelAll(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC_WORK)
        WorkManager.getInstance(ctx).cancelUniqueWork(MANUAL_WORK)
    }

    /**
     * Der Knopf "Abbrechen". Bricht einen laufenden Sync sofort ab (egal ob
     * per 6-Stunden-Takt oder "Jetzt sichern" gestartet), plant den
     * 6-Stunden-Takt danach aber sofort neu -- anders als cancelAll() also
     * kein Abmelden, der Zeitplan bleibt aktiv.
     */
    fun cancelRunning(ctx: Context) {
        cancelAll(ctx)
        schedulePeriodic(ctx)
    }
}
