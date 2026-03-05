package com.example.minimal

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textview.MaterialTextView
import androidx.core.app.NotificationCompat
import android.widget.LinearLayout
import android.widget.Toast
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val Context.settingsDataStore by preferencesDataStore(name = "notification_preferences")

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val contentContainer = findViewById<LinearLayout>(R.id.settings_content)

        contentContainer.setPadding(
            contentContainer.paddingLeft,
            contentContainer.paddingTop,
            contentContainer.paddingRight,
            (24 * resources.displayMetrics.density).toInt() + 80   // 24dp + extra ~80px buffer for nav bar
        )

        contentContainer.addView(MaterialTextView(this).apply {
            text = "Select which results should notify you"
            textSize = 16f
            setPadding(0, 0, 0, 12)
        })

        val scope = kotlinx.coroutines.MainScope()

        scope.launch {
            val prefs = applicationContext.settingsDataStore.data.first()

            NotificationChannels.MATCH_CATEGORIES.forEach { category ->

                val key = booleanPreferencesKey("notify_${category.id}")

                // Default: on for matches, off for "No matches"
                val defaultChecked = category.wb > 0 || category.pb
                val initialChecked = prefs[key] ?: defaultChecked



                val prizeHint = when {
                    category.wb == 5 && category.pb -> " – Jackpot tier"
                    category.wb == 5                -> " – Major prize"
                    category.wb == 4                -> " – Significant prize"
                    category.wb == 3 && category.pb -> " – Good prize"
                    category.wb == 3                -> " – Prize tier"
                    else                            -> ""
                }

                val checkBox = MaterialCheckBox(this@SettingsActivity).apply {
                    text = category.label + prizeHint
                    isChecked = initialChecked

                    // Critical fixes
                    minimumHeight = 0                  // ← removes forced touch target height
                    minHeight = 0                      // both needed in some versions
                    setPadding(0, 2, 0, 2)             // tiny internal padding to prevent clipping

                    // Force tight layout
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        0f  // no weight
                    ).apply {
                        topMargin    = 0
                        bottomMargin = 8
                    }
                }

                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    scope.launch {
                        applicationContext.settingsDataStore.edit { settings ->
                            settings[key] = isChecked
                        }
                    }
                }

                contentContainer.addView(checkBox)
            }

            // Small spacer before prime button
            contentContainer.addView(View(this@SettingsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    16   // height in pixels (~8-12dp depending on density)
                )
            })

            val primeButton = MaterialButton(this@SettingsActivity).apply {
                text = "Prime Channels & Unlock Custom Sounds"
                setOnClickListener {
                    primeAllChannelsWithTestNotifications()
                    Toast.makeText(
                        this@SettingsActivity,
                        "Test notifications sent — custom sounds should now be selectable",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            primeButton.setPadding(32, 16, 32, 16)
            contentContainer.addView(primeButton)
        }
    }

    private fun primeAllChannelsWithTestNotifications() {
        NotificationChannels.createAll(this)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        NotificationChannels.MATCH_CATEGORIES.forEachIndexed { index, category ->
            val builder = NotificationCompat.Builder(this, category.id)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Test: ${category.label}")
                .setContentText("Silent test for this category")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSound(null)
                .setVibrate(null)
                .setAutoCancel(true)

            manager.notify(8000 + index, builder.build())
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}