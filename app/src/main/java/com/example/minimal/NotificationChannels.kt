package com.example.minimal

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build

data class MatchCategory(
    val id: String,
    val label: String,
    val wb: Int,
    val pb: Boolean
)

object NotificationChannels {

    val MATCH_CATEGORIES = listOf(
        MatchCategory("pb_only",    "Powerball Only",                  0, true),
        MatchCategory("wb1",        "1 White Ball",                    1, false),
        MatchCategory("wb1_pb",     "1 White Ball + Powerball",        1, true),
        MatchCategory("wb2",        "2 White Balls",                   2, false),
        MatchCategory("wb2_pb",     "2 White Balls + Powerball",       2, true),
        MatchCategory("wb3",        "3 White Balls",                   3, false),
        MatchCategory("wb3_pb",     "3 White Balls + Powerball",       3, true),
        MatchCategory("wb4",        "4 White Balls",                   4, false),
        MatchCategory("wb4_pb",     "4 White Balls + Powerball",       4, true),
        MatchCategory("wb5",        "5 White Balls (no PB)",           5, false),
        MatchCategory("wb5_pb",     "5 White Balls + Powerball (Jackpot!)", 5, true)
    )

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Optional: group channels in system settings (Android 8.1+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val group = NotificationChannelGroup("powerball_matches", "Powerball Match Alerts")
            manager.createNotificationChannelGroup(group)
        }

        MATCH_CATEGORIES.forEach { category ->
            val importance = getImportance(category)

            val channel = NotificationChannel(
                category.id,
                category.label,
                importance
            ).apply {
                description = "Alerts for ${category.label.lowercase()}"
                enableVibration(true)
                // enableLights(true)           // optional
                // lightColor = Color.GREEN     // optional
                group = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) "powerball_matches" else null
            }

            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Returns the recommended importance level for a match category.
     * This is used both when creating channels and when building notifications.
     */
    fun getImportance(category: MatchCategory): Int = when {
        category.wb == 5 && category.pb -> NotificationManager.IMPORTANCE_HIGH     // Jackpot – urgent
        category.wb == 5                -> NotificationManager.IMPORTANCE_HIGH     // $1M prize tier
        category.wb >= 4                -> NotificationManager.IMPORTANCE_HIGH     // Significant prizes
        category.wb >= 3                -> NotificationManager.IMPORTANCE_DEFAULT  // Decent prize
        else                            -> NotificationManager.IMPORTANCE_LOW       // Small prizes
    }

    fun findCategory(wb: Int, pb: Boolean): MatchCategory? =
        MATCH_CATEGORIES.firstOrNull { it.wb == wb && it.pb == pb }
}