package com.example.minimal

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

object DataStoreKeys {
    val WHITE_NUMBERS_KEY          = stringSetPreferencesKey("white_numbers")
    val POWERBALL_KEY              = intPreferencesKey("powerball_number")
    val HIGHLIGHT_ENABLED_KEY      = booleanPreferencesKey("highlight_enabled")
    val NOTIFICATIONS_ENABLED_KEY  = booleanPreferencesKey("notifications_enabled")
    val LAST_DRAW_DATE_KEY         = stringPreferencesKey("last_known_draw_date")
    val HISTORY_KEY                = stringPreferencesKey("winning_history_json")
}