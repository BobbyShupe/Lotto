package com.example.minimal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// These keys must match exactly what is used in MainActivity.kt
private val WHITE_NUMBERS_KEY = stringSetPreferencesKey("white_numbers")
private val POWERBALL_KEY    = intPreferencesKey("powerball_number")
private val LAST_DRAW_DATE_KEY = stringPreferencesKey("last_known_draw_date")

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy")

class CheckWinningWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect("https://www.texaslottery.com/export/sites/lottery/Games/Powerball/Winning_Numbers/print.html")
                .userAgent("Mozilla/5.0")
                .timeout(15000)
                .get()

            val rows = doc.select("table tr")
            if (rows.size < 2) return@withContext Result.retry()

            val cells = rows[1].select("td")
            if (cells.size < 8) return@withContext Result.retry()

            val fetchedDateStr = cells[0].text().trim()  // e.g. "02/18/2026"

            val fetchedDate = try {
                LocalDate.parse(fetchedDateStr, DATE_FORMATTER)
            } catch (e: DateTimeParseException) {
                return@withContext Result.failure()
            }

            val prefs = applicationContext.dataStore.data.first()
            val lastKnownDateStr = prefs[LAST_DRAW_DATE_KEY]
            val lastKnownDate = lastKnownDateStr?.let {
                try {
                    LocalDate.parse(it, DATE_FORMATTER)
                } catch (_: Exception) {
                    null
                }
            }

            // Consider it a new draw if we have no previous date or fetched date is newer
            val isNewDraw = lastKnownDate == null || fetchedDate.isAfter(lastKnownDate)

            if (isNewDraw) {
                val whiteNumbers = listOfNotNull(
                    cells[1].text().trim().toIntOrNull(),
                    cells[2].text().trim().toIntOrNull(),
                    cells[3].text().trim().toIntOrNull(),
                    cells[4].text().trim().toIntOrNull(),
                    cells[5].text().trim().toIntOrNull()
                ).filter { it in 1..69 }.toSet()

                val powerball = cells[6].text().trim().toIntOrNull()?.takeIf { it in 1..26 }

                val userWhite = prefs[WHITE_NUMBERS_KEY]
                    ?.mapNotNull { it.toIntOrNull() }
                    ?.toSet() ?: emptySet()

                val userPowerball = prefs[POWERBALL_KEY]

                val whiteMatchCount = userWhite.intersect(whiteNumbers).size
                val powerballMatch = userPowerball == powerball

                // Only notify if there is at least one match
                if (whiteMatchCount > 0 || powerballMatch) {
                    showDrawNotification(fetchedDateStr, whiteMatchCount, powerballMatch)
                }

                // Always remember this draw date (so we don't notify again on same draw)
                applicationContext.dataStore.edit { settings ->
                    settings[LAST_DRAW_DATE_KEY] = fetchedDateStr
                }

                Result.success()
            } else {
                // Same draw → no action
                Result.success()
            }
        } catch (e: IOException) {
            Result.retry()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun showDrawNotification(drawDate: String, whiteMatchCount: Int, powerballMatch: Boolean) {
        val channelId = "powerball_new_draw"

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "New Powerball Draws",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a new Powerball draw is published"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val totalMatches = whiteMatchCount + if (powerballMatch) 1 else 0

        val message = buildString {
            append("New Powerball draw on $drawDate! ")
            append("You have ")
            if (whiteMatchCount > 0) append("$whiteMatchCount white ball${if (whiteMatchCount > 1) "s" else ""}")
            if (powerballMatch) {
                if (whiteMatchCount > 0) append(" + ")
                append("Powerball")
            }
            append(" match")
            if (totalMatches > 1) append("es")
            append("!")
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Powerball Match Alert")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1002, notification)
    }
}