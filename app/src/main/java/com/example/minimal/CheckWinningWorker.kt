package com.example.minimal

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
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

// DataStore keys
private val WHITE_NUMBERS_KEY       = stringSetPreferencesKey("white_numbers")
private val POWERBALL_KEY           = intPreferencesKey("powerball_number")
private val LAST_DRAW_DATE_KEY      = stringPreferencesKey("last_known_draw_date")

private fun notifyKey(categoryId: String) = booleanPreferencesKey("notify_$categoryId")

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

            val fetchedDateStr = cells[0].text().trim()

            val fetchedDate = try {
                LocalDate.parse(fetchedDateStr, DATE_FORMATTER)
            } catch (e: DateTimeParseException) {
                return@withContext Result.failure()
            }

            val prefs = applicationContext.dataStore.data.first()
            val lastKnownDateStr = prefs[LAST_DRAW_DATE_KEY]
            val lastKnownDate = lastKnownDateStr?.let {
                try { LocalDate.parse(it, DATE_FORMATTER) } catch (_: Exception) { null }
            }

            val isNewDraw = lastKnownDate == null || fetchedDate.isAfter(lastKnownDate)

            if (!isNewDraw) {
                return@withContext Result.success()
            }

            val winningWhite = listOfNotNull(
                cells[1].text().trim().toIntOrNull(),
                cells[2].text().trim().toIntOrNull(),
                cells[3].text().trim().toIntOrNull(),
                cells[4].text().trim().toIntOrNull(),
                cells[5].text().trim().toIntOrNull()
            ).filter { it in 1..69 }.toSet()

            val winningPB = cells[6].text().trim().toIntOrNull()?.takeIf { it in 1..26 }

            val userWhite = prefs[WHITE_NUMBERS_KEY]
                ?.mapNotNull { it.toIntOrNull() }
                ?.toSet() ?: emptySet()

            val userPB = prefs[POWERBALL_KEY]

            val whiteMatches = userWhite.intersect(winningWhite).size
            val pbMatch = userPB == winningPB

            val category = when {
                whiteMatches == 0 && !pbMatch ->
                    NotificationChannels.MATCH_CATEGORIES.firstOrNull { it.id == "no_match" }
                else ->
                    NotificationChannels.findCategory(whiteMatches, pbMatch)
            }

            if (category != null) {
                val notifyPrefs = applicationContext.settingsDataStore.data.first()
                val shouldNotify = notifyPrefs[notifyKey(category.id)] ?: true

                if (shouldNotify) {
                    showMatchNotification(
                        drawDate = fetchedDateStr,
                        category = category,
                        whiteMatches = whiteMatches,
                        pbMatch = pbMatch
                    )
                }
            }

            applicationContext.dataStore.edit { settings ->
                settings[LAST_DRAW_DATE_KEY] = fetchedDateStr
            }

            Result.success()

        } catch (e: IOException) {
            Result.retry()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun showMatchNotification(
        drawDate: String,
        category: MatchCategory,
        whiteMatches: Int,
        pbMatch: Boolean
    ) {
        val context = applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val title = when {
            whiteMatches == 5 && pbMatch -> "JACKPOT WINNER!"
            whiteMatches == 5 -> "5 White Balls – Major Prize!"
            whiteMatches >= 4 -> "Strong Match – Prize!"
            whiteMatches >= 1 || pbMatch -> "You Matched!"
            else -> "Draw Result"
        }

        val message = buildString {
            append("Draw on $drawDate: ")
            when {
                whiteMatches == 5 && pbMatch -> append("5 white + Powerball – Jackpot!")
                whiteMatches == 5 -> append("5 white balls matched")
                whiteMatches > 0 || pbMatch -> {
                    if (whiteMatches > 0) {
                        append("$whiteMatches white ${if (whiteMatches > 1) "balls" else "ball"}")
                    }
                    if (pbMatch) {
                        if (whiteMatches > 0) append(" + ")
                        append("Powerball")
                    }
                    append(" matched.")
                }
                else -> append("No matches this time.")
            }
        }

        val channelImportance = NotificationChannels.getImportance(category)

        val priority = when (channelImportance) {
            NotificationManager.IMPORTANCE_HIGH    -> NotificationCompat.PRIORITY_HIGH
            NotificationManager.IMPORTANCE_DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
            else                                   -> NotificationCompat.PRIORITY_LOW
        }

        val notification = NotificationCompat.Builder(context, category.id)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(priority)
            .setAutoCancel(true)
            .build()

        val notificationId = category.id.hashCode()
        notificationManager.notify(notificationId, notification)
    }
}