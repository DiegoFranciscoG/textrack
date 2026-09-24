package io.github.diegofranciscog.textrack.scanner.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.diegofranciscog.textrack.scanner.TextrackApp
import java.io.IOException
import java.util.concurrent.TimeUnit
import retrofit2.HttpException

/**
 * Sube las lecturas pendientes cuando hay red. WorkManager persiste el trabajo aunque la app se cierre o el
 * dispositivo se reinicie, y reintenta con espera exponencial si falla la conexión.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val locator = (applicationContext as TextrackApp).locator
        if (!locator.tokens.hasSession()) {
            return Result.failure()
        }
        return try {
            locator.readings.syncPending()
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: HttpException) {
            when {
                e.code() == 401 || e.code() == 403 -> Result.failure()
                e.code() >= 500 || e.code() == 429 -> Result.retry()
                else -> Result.failure()
            }
        }
    }

    companion object {
        private const val ONE_TIME = "sync-readings"
        private const val PERIODIC = "sync-readings-periodic"

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Encola una sincronización; si ya hay una en curso, se encadena detrás (no se pierde ninguna lectura). */
        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
