package com.example.minimal

import androidx.lifecycle.lifecycleScope
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.util.Log

// DataStore delegate
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "powerball_selection")

class MainActivity : AppCompatActivity() {

    private lateinit var selectedNumbersText: MaterialTextView
    private lateinit var latestWinningText: MaterialTextView

    // Powerball Rules
    private val maxWhite = 69
    private val maxPowerball = 26
    private val whiteLimit = 5
    private val numbersPerRow = 10

    private val selectedWhite = mutableSetOf<Int>()
    private var selectedPB: Int? = null

    private val whiteButtons = mutableMapOf<Int, MaterialButton>()
    private val pbButtons = mutableMapOf<Int, MaterialButton>()

    // Winning numbers from latest draw
    private val winningWhite = mutableSetOf<Int>()
    private var winningPB: Int? = null

    // Toggle for winning highlights
    private var showWinningHighlights = true

    // Colors
    private val COLOR_WINNING = Color.parseColor("#9C27B0")     // purple
    private val COLOR_GOLD     = Color.parseColor("#FFD700")     // gold
    private val COLOR_SELECTED_WHITE = Color.parseColor("#616161")
    private val COLOR_SELECTED_PB    = Color.parseColor("#D32F2F")
    private val COLOR_UNSELECTED     = Color.LTGRAY

    // DataStore keys
    private val WHITE_NUMBERS_KEY = stringSetPreferencesKey("white_numbers")
    private val POWERBALL_KEY = intPreferencesKey("powerball_number")
    private val HIGHLIGHT_ENABLED_KEY = booleanPreferencesKey("highlight_enabled")

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scheduleBackgroundWinningCheck()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val container = findViewById<LinearLayout>(R.id.contentContainer)
        container.setBackgroundColor(Color.parseColor("#F5F5F5"))

        // Title
        container.addView(MaterialTextView(this).apply {
            text = "Powerball Selector"
            textSize = 28f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })

        // Latest draw
        latestWinningText = MaterialTextView(this).apply {
            text = "Latest Draw: Loading..."
            textSize = 11f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        container.addView(latestWinningText)

        // Selected numbers display
        selectedNumbersText = MaterialTextView(this).apply {
            text = "Select your numbers"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        container.addView(selectedNumbersText)

        // White balls section
        container.addView(createSectionTitle("White Balls (Pick 5)"))
        container.addView(createGrid(maxWhite, false))

        // Powerball section
        container.addView(createSectionTitle("Powerball (Pick 1)"))
        container.addView(createGrid(maxPowerball, true))

        // Notification permission & scheduling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                scheduleBackgroundWinningCheck()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            scheduleBackgroundWinningCheck()
        }

        // Load saved selection and fetch latest draw
        lifecycleScope.launch { loadSavedSelectionAndHighlightState() }
        lifecycleScope.launch { fetchLatestWinningNumbersForUI() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.menu_toggle_highlights)?.isChecked = showWinningHighlights
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_toggle_highlights -> {
                showWinningHighlights = !showWinningHighlights
                item.isChecked = showWinningHighlights
                lifecycleScope.launch {
                    dataStore.edit { prefs ->
                        prefs[HIGHLIGHT_ENABLED_KEY] = showWinningHighlights
                    }
                }
                applyHighlightState()
                true
            }
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun scheduleBackgroundWinningCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<CheckWinningWorker>(
            12, TimeUnit.HOURS,
            1, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "powerball_winning_check",
                ExistingPeriodicWorkPolicy.REPLACE,
                periodicRequest
            )

        Log.i("WorkerSetup", "Powerball check scheduled (12h ±1min)")
    }

    private suspend fun fetchLatestWinningNumbersForUI() {
        withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect("https://www.texaslottery.com/export/sites/lottery/Games/Powerball/Winning_Numbers/print.html")
                    .userAgent("Mozilla/5.0")
                    .timeout(15000)
                    .get()

                val rows = doc.select("table tr")
                if (rows.size < 2) throw IOException("No data rows found")

                val cells = rows[1].select("td")
                if (cells.size < 8) throw IOException("Incomplete row data")

                val date = cells[0].text().trim()
                val nums = cells.subList(1, 6).mapNotNull { it.text().trim().toIntOrNull() }
                val pb = cells[6].text().trim().toIntOrNull()

                if (nums.size == 5 && pb != null && pb in 1..26) {
                    winningWhite.clear()
                    winningWhite.addAll(nums)

                    winningPB = pb

                    val display = "Latest ($date): ${winningWhite.sorted().joinToString(" ")} PB $pb"

                    withContext(Dispatchers.Main) {
                        latestWinningText.text = display
                        latestWinningText.setTextColor(Color.BLACK)
                        if (showWinningHighlights) highlightWinningNumbers()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    latestWinningText.text = "Latest Draw: Unable to load"
                    latestWinningText.setTextColor(Color.RED)
                }
            }
        }
    }

    private fun highlightWinningNumbers() {
        if (!showWinningHighlights) return

        winningWhite.forEach { num ->
            whiteButtons[num]?.let { btn ->
                btn.backgroundTintList = null
                btn.setBackgroundColor(
                    if (selectedWhite.contains(num)) COLOR_GOLD else COLOR_WINNING
                )
                btn.setTextColor(
                    if (selectedWhite.contains(num)) Color.BLACK else Color.WHITE
                )
            }
        }

        winningPB?.let { pbNum ->
            pbButtons[pbNum]?.let { btn ->
                btn.backgroundTintList = null
                btn.setBackgroundColor(
                    if (selectedPB == pbNum) COLOR_GOLD else COLOR_WINNING
                )
                btn.setTextColor(
                    if (selectedPB == pbNum) Color.BLACK else Color.WHITE
                )
            }
        }
    }

    private fun clearAllHighlights() {
        winningWhite.forEach { num ->
            whiteButtons[num]?.let { btn ->
                btn.backgroundTintList = null
                btn.setBackgroundColor(
                    if (selectedWhite.contains(num)) COLOR_SELECTED_WHITE else COLOR_UNSELECTED
                )
                btn.setTextColor(
                    if (selectedWhite.contains(num)) Color.WHITE else Color.BLACK
                )
            }
        }

        winningPB?.let { pbNum ->
            pbButtons[pbNum]?.let { btn ->
                btn.backgroundTintList = null
                btn.setBackgroundColor(
                    if (selectedPB == pbNum) COLOR_SELECTED_PB else COLOR_UNSELECTED
                )
                btn.setTextColor(
                    if (selectedPB == pbNum) Color.WHITE else Color.BLACK
                )
            }
        }
    }

    private fun applyHighlightState() {
        if (showWinningHighlights) {
            highlightWinningNumbers()
        } else {
            clearAllHighlights()
        }
    }

    private suspend fun loadSavedSelectionAndHighlightState() {
        val prefs = dataStore.data.first()

        // ── your existing number loading code ──
        prefs[WHITE_NUMBERS_KEY]?.let { savedSet ->
            val validWhite = savedSet.mapNotNull { it.toIntOrNull() }
                .filter { it in 1..maxWhite }
                .take(whiteLimit)
                .toSet()

            selectedWhite.clear()
            selectedWhite.addAll(validWhite)

            validWhite.forEach { num ->
                whiteButtons[num]?.let { btn ->
                    btn.backgroundTintList = null
                    btn.setBackgroundColor(COLOR_SELECTED_WHITE)
                    btn.setTextColor(Color.WHITE)
                }
            }
        }

        prefs[POWERBALL_KEY]?.let { savedPB ->
            if (savedPB in 1..maxPowerball) {
                selectedPB = savedPB
                pbButtons[savedPB]?.let { btn ->
                    btn.backgroundTintList = null
                    btn.setBackgroundColor(COLOR_SELECTED_PB)
                    btn.setTextColor(Color.WHITE)
                }
            }
        }

        // ── NEW: load the toggle state ──
        showWinningHighlights = prefs[HIGHLIGHT_ENABLED_KEY] ?: true   // default true if never saved

        // Apply to UI
        updateDisplay()
        applyHighlightState()           // or highlightWinningNumbers() if you don't have apply function yet

        // Make sure menu checkbox is correct
        invalidateOptionsMenu()         // ← important
    }

    private fun toggleWhite(num: Int) {
        val btn = whiteButtons[num] ?: return

        if (selectedWhite.contains(num)) {
            selectedWhite.remove(num)
            btn.backgroundTintList = null
            btn.setBackgroundColor(
                if (showWinningHighlights && winningWhite.contains(num)) COLOR_WINNING
                else COLOR_UNSELECTED
            )
            btn.setTextColor(
                if (showWinningHighlights && winningWhite.contains(num)) Color.WHITE
                else Color.BLACK
            )
        } else if (selectedWhite.size < whiteLimit) {
            selectedWhite.add(num)
            btn.backgroundTintList = null
            btn.setBackgroundColor(
                if (showWinningHighlights && winningWhite.contains(num)) COLOR_GOLD
                else COLOR_SELECTED_WHITE
            )
            btn.setTextColor(
                if (showWinningHighlights && winningWhite.contains(num)) Color.BLACK
                else Color.WHITE
            )
        }

        updateDisplay()
    }

    private fun togglePowerball(num: Int) {
        val btn = pbButtons[num] ?: return

        // Reset previous selection if any
        selectedPB?.let { prev ->
            pbButtons[prev]?.let { prevBtn ->
                prevBtn.backgroundTintList = null
                prevBtn.setBackgroundColor(
                    if (showWinningHighlights && winningPB == prev) COLOR_WINNING
                    else COLOR_UNSELECTED
                )
                prevBtn.setTextColor(
                    if (showWinningHighlights && winningPB == prev) Color.WHITE
                    else Color.BLACK
                )
            }
        }

        // Toggle
        if (selectedPB == num) {
            selectedPB = null
        } else {
            selectedPB = num
            btn.backgroundTintList = null
            btn.setBackgroundColor(
                if (showWinningHighlights && winningPB == num) COLOR_GOLD
                else COLOR_SELECTED_PB
            )
            btn.setTextColor(
                if (showWinningHighlights && winningPB == num) Color.BLACK
                else Color.WHITE
            )
        }

        updateDisplay()
    }

    private fun updateDisplay() {
        val whiteText = if (selectedWhite.isEmpty()) "None" else selectedWhite.sorted().joinToString(" ")
        val pbText = selectedPB?.toString() ?: "None"
        selectedNumbersText.text = "WB $whiteText PB $pbText"
    }

    private fun saveSelection() {
        lifecycleScope.launch {
            dataStore.edit { prefs ->
                prefs[WHITE_NUMBERS_KEY] = selectedWhite.map { it.toString() }.toSet()
                if (selectedPB != null) {
                    prefs[POWERBALL_KEY] = selectedPB!!
                } else {
                    prefs.remove(POWERBALL_KEY)
                }
            }
        }
    }

    private fun createSectionTitle(title: String) = MaterialTextView(this).apply {
        text = title
        textSize = 16f
        setPadding(0, 32, 0, 16)
        setTextColor(Color.BLACK)
    }

    private fun createGrid(max: Int, isPowerball: Boolean): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = 12f.dpToPx()
            cardElevation = 2f.dpToPx()
        }

        val grid = GridLayout(this).apply {
            columnCount = numbersPerRow
            setPadding(8.dpToPx().toInt(),8.dpToPx().toInt(),8.dpToPx().toInt(),8.dpToPx().toInt())
        }

        for (i in 1..max) {
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle).apply {
                text = i.toString()
                textSize = 10f
                setPadding(0,0,0,0)
                insetTop = 0
                insetBottom = 0
                minWidth = 0
                minHeight = 0
                minimumWidth = 0
                minimumHeight = 40.dpToPx().toInt()
                cornerRadius = 9999.dpToPx().toInt()

                backgroundTintList = null
                setBackgroundColor(COLOR_UNSELECTED)
                setTextColor(Color.BLACK)

                if (isPowerball) pbButtons[i] = this else whiteButtons[i] = this

                setOnClickListener {
                    if (isPowerball) togglePowerball(i) else toggleWhite(i)
                    saveSelection()
                }
            }

            // ────────────────────────────────────────────────
            //   Fixed LayoutParams creation
            // ────────────────────────────────────────────────
            val params = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),   // row
                GridLayout.spec(GridLayout.UNDEFINED, 1f)    // column
            ).apply {
                width = 0
                setMargins(1, 1, 1, 1)   // nicer spacing – feel free to use 2,2,2,2 or 1,1,1,1
            }

            grid.addView(btn, params)
        }

        card.addView(grid)
        return card
    }

    private fun Float.dpToPx(): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics)

    private fun Int.dpToPx(): Float = toFloat().dpToPx()
}