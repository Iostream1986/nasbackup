package de.stefan.nasbackup.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.stefan.nasbackup.NasBackupApp
import de.stefan.nasbackup.data.CredentialStore
import de.stefan.nasbackup.data.UploadLog
import de.stefan.nasbackup.media.MediaScanner
import de.stefan.nasbackup.net.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Der eigentliche Motor. Laeuft ueber WorkManager, ueberlebt damit
 * App-Schliessen und Geraete-Neustart.
 */
class UploadWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    companion object {
        const val PROGRESS_DONE = "done"
        const val PROGRESS_TOTAL = "total"
        const val RESULT_UPLOADED = "uploaded"
        const val RESULT_FAILED = "failed"
        const val RESULT_ERROR = "error"
        private const val NOTIFICATION_ID = 4711
    }

    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.GERMANY)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext

        val creds = CredentialStore.load(ctx)
            ?: return@withContext Result.failure(
                workDataOf(RESULT_ERROR to "Nicht angemeldet")
            )

        val client = WebDavClient(creds.baseUrl, creds.user, creds.password)

        client.checkLogin().onFailure {
            // Netzwerkprobleme sind temporaer, WorkManager wartet selbst ab.
            return@withContext Result.retry()
        }

        val pending = try {
            MediaScanner.scan(ctx).filter { !UploadLog.isDone(ctx, it.key) }
        } catch (e: SecurityException) {
            return@withContext Result.failure(
                workDataOf(RESULT_ERROR to "Zugriff auf Medien nicht erlaubt")
            )
        }

        if (pending.isEmpty()) {
            return@withContext Result.success(
                workDataOf(RESULT_UPLOADED to 0, RESULT_FAILED to 0)
            )
        }

        setForeground(buildForegroundInfo(0, pending.size))

        val knownDirs = mutableSetOf<String>()
        var uploaded = 0
        var failed = 0

        pending.forEachIndexed { index, item ->
            if (isStopped) return@withContext Result.retry()

            val folder = "${creds.user}/DCIM/" + monthFormat.format(Date(item.dateAddedSeconds * 1000L))
            val remotePath = "$folder/${item.displayName}"

            val ok = runCatching {
                if (knownDirs.add(folder)) {
                    client.makeDirs(folder).getOrThrow()
                }

                if (client.exists(remotePath)) {
                    // Liegt schon oben. Als erledigt verbuchen statt erneut senden.
                    return@runCatching
                }

                client.upload(
                    path = remotePath,
                    length = item.sizeBytes,
                    mimeType = item.mimeType
                ) {
                    ctx.contentResolver.openInputStream(item.uri)
                        ?: throw Exception("Datei nicht lesbar: ${item.displayName}")
                }.getOrThrow()
            }.isSuccess

            if (ok) {
                UploadLog.markDone(ctx, item.key)
                uploaded++
            } else {
                // Kein Abbruch: eine kaputte Datei darf nicht den Lauf stoppen.
                failed++
            }

            setProgress(
                workDataOf(
                    PROGRESS_DONE to index + 1,
                    PROGRESS_TOTAL to pending.size
                )
            )
            setForeground(buildForegroundInfo(index + 1, pending.size))
        }

        Result.success(
            workDataOf(RESULT_UPLOADED to uploaded, RESULT_FAILED to failed)
        )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        buildForegroundInfo(0, 0)

    private fun buildForegroundInfo(done: Int, total: Int): ForegroundInfo {
        val text = if (total > 0) "$done von $total" else "wird vorbereitet"
        val notification = NotificationCompat.Builder(
            applicationContext,
            NasBackupApp.CHANNEL_ID
        )
            .setContentTitle("Backup auf NAS")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setProgress(total, done, total == 0)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
