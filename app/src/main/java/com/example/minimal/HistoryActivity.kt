package com.example.minimal

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import androidx.datastore.preferences.core.edit

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import com.example.minimal.DataStoreKeys   // ← make sure this import exists

class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HistoryAdapter

    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.recycler_history)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )

        adapter = HistoryAdapter { entry, newNote ->
            updateNoteInDataStore(entry, newNote)
        }

        recyclerView.adapter = adapter

        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val prefs = dataStore.data.first()
            val historyJson = prefs[DataStoreKeys.HISTORY_KEY] ?: "[]"
            val history = json.decodeFromString<List<HistoryEntry>>(historyJson)
                .sortedByDescending { it.drawDate }

            adapter.submitList(history)
        }
    }

    private fun updateNoteInDataStore(entry: HistoryEntry, newNote: String) {
        lifecycleScope.launch {
            dataStore.edit { prefs ->                           // ← correct: edit takes a lambda with Preferences.MutablePreferences
                val currentJson = prefs[DataStoreKeys.HISTORY_KEY] ?: "[]"
                val list = json.decodeFromString<MutableList<HistoryEntry>>(currentJson)

                val index = list.indexOfFirst { it.drawDate == entry.drawDate }
                if (index != -1) {
                    list[index] = list[index].copy(note = newNote)
                    prefs[DataStoreKeys.HISTORY_KEY] = json.encodeToString(list)   // ← correct usage
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}