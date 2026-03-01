package com.example.minimal

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import androidx.core.app.NotificationCompat
import android.widget.LinearLayout
import android.widget.Toast

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val contentContainer = findViewById<LinearLayout>(R.id.settings_content)

        // Add explanation text (already in XML, but you can add more here if needed)

        // Add the prime button
        val primeButton = MaterialButton(this).apply {
            text = "Prime Channels & Unlock Custom Sounds"
            setOnClickListener {
                primeAllChannelsWithTestNotifications()
                Toast.makeText(
                    this@SettingsActivity,
                    "Test notifications sent — custom sounds should now be selectable in system settings",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // Optional: style it a bit
        primeButton.setPadding(32, 16, 32, 16)

        contentContainer.addView(primeButton)
    }

    private fun primeAllChannelsWithTestNotifications() {
        // 1. Make sure channels exist (safe to call multiple times)
        NotificationChannels.createAll(this)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        NotificationChannels.MATCH_CATEGORIES.forEachIndexed { index, category ->
            val testBuilder = NotificationCompat.Builder(this, category.id)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Channel Test: ${category.label}")
                .setContentText("This silent test unlocks custom sound selection for this category.")
                .setPriority(NotificationCompat.PRIORITY_MIN)          // As silent as possible
                .setSound(null)                                         // No sound for the test
                .setVibrate(null)                                       // No vibration
                .setAutoCancel(true)

            // Unique ID per channel so they don't overwrite each other
            val testId = 8000 + index

            notificationManager.notify(testId, testBuilder.build())
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}