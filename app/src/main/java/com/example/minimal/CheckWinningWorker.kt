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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private fun notifyKey(categoryId: String) = booleanPreferencesKey("notify_$categoryId")

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy")
private val json = Json { ignoreUnknownKeys = true }

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
            val lastKnownDateStr = prefs[DataStoreKeys.LAST_DRAW_DATE_KEY]
            val lastKnownDate = lastKnownDateStr?.let {
                try { LocalDate.parse(it, DATE_FORMATTER) } catch (_: Exception) { null }
            }

            val isNewDraw = lastKnownDate == null || fetchedDate.isAfter(lastKnownDate)

            if (!isNewDraw) {
                return@withContext Result.success()
            }

            // Parse winning numbers
            val winningWhite = listOfNotNull(
                cells[1].text().trim().toIntOrNull(),
                cells[2].text().trim().toIntOrNull(),
                cells[3].text().trim().toIntOrNull(),
                cells[4].text().trim().toIntOrNull(),
                cells[5].text().trim().toIntOrNull()
            ).filter { it in 1..69 }.toSet()

            val winningPB = cells[6].text().trim().toIntOrNull()?.takeIf { it in 1..26 }

            // ────────────────────────────────────────────────
            // Store to history (unchanged)
            // ────────────────────────────────────────────────
            val historyJson = prefs[DataStoreKeys.HISTORY_KEY] ?: "[]"
            val historyList = json.decodeFromString<MutableList<HistoryEntry>>(historyJson)

            if (historyList.none { it.drawDate == fetchedDateStr }) {
                val userWhiteAtTime = prefs[DataStoreKeys.WHITE_NUMBERS_KEY]
                    ?.mapNotNull { it.toIntOrNull() }
                    ?.toSet() ?: emptySet()

                val userPBAtTime = prefs[DataStoreKeys.POWERBALL_KEY]

                historyList.add(
                    HistoryEntry(
                        drawDate = fetchedDateStr,
                        winningWhite = winningWhite,
                        winningPowerball = winningPB,
                        userWhite = userWhiteAtTime,
                        userPowerball = userPBAtTime,
                        note = ""
                    )
                )

                applicationContext.dataStore.edit { settings ->
                    settings[DataStoreKeys.HISTORY_KEY] = json.encodeToString(historyList)
                }
            }

            // User's current selection
            val userWhite = prefs[DataStoreKeys.WHITE_NUMBERS_KEY]
                ?.mapNotNull { it.toIntOrNull() }
                ?.toSet() ?: emptySet()

            val userPB = prefs[DataStoreKeys.POWERBALL_KEY]

            // ─── Compute matches ────────────────────────────────
            val matchedWhite = userWhite.intersect(winningWhite)
            val whiteMatchesCount = matchedWhite.size
            val pbMatch = userPB == winningPB

            val category = NotificationChannels.findCategory(whiteMatchesCount, pbMatch)

            if (category != null) {
                val notifyPrefs = applicationContext.settingsDataStore.data.first()
                val shouldNotify = notifyPrefs[notifyKey(category.id)] ?: true

                if (shouldNotify) {
                    showMatchNotification(
                        drawDate = fetchedDateStr,
                        category = category,
                        matchedWhite = matchedWhite.sorted(),           // ← sorted for readability
                        pbMatch = pbMatch,
                        winningPB = winningPB,
                        whiteMatchesCount = whiteMatchesCount   // ← add this line
                    )
                }
            }

            // Remember this draw
            applicationContext.dataStore.edit { settings ->
                settings[DataStoreKeys.LAST_DRAW_DATE_KEY] = fetchedDateStr
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
        matchedWhite: List<Int>,           // ← changed to List<Int>
        pbMatch: Boolean,
        winningPB: Int?,
        whiteMatchesCount: Int
    ) {
        val context = applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val title = when {
            matchedWhite.size == 5 && pbMatch -> "JACKPOT WINNER!"
            matchedWhite.size == 5 -> "5 White Balls – Major Prize!"
            matchedWhite.size >= 4 -> "Strong Match – Prize Likely!"
            matchedWhite.isNotEmpty() || pbMatch -> "You Matched!"
            else -> "Powerball Result"
        }

        val message = buildString {
            append("Draw $drawDate: ")

            when {
                matchedWhite.size == 5 && pbMatch -> {
                    append("JACKPOT! All 5 white + Powerball matched.")
                }
                matchedWhite.size == 5 -> {
                    append("5 white balls matched: ${matchedWhite.joinToString(", ")}")
                }
                matchedWhite.isNotEmpty() || pbMatch -> {
                    if (matchedWhite.isNotEmpty()) {
                        append("${matchedWhite.size} white: ${matchedWhite.joinToString(", ")}")
                    }
                    if (pbMatch && winningPB != null) {
                        if (matchedWhite.isNotEmpty()) append(" • ")
                        append("Powerball: $winningPB")
                    }
                    append(" matched")
                }
                else -> {
                    append("No matches this time.")
                }
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))  // ← helps when text is longer
            .setPriority(priority)
            .setAutoCancel(true)
            .build()

        val notificationId = category.id.hashCode()
        notificationManager.notify(notificationId, notification)
    }
}