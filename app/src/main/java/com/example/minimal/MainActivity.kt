package com.example.minimal

import androidx.lifecycle.lifecycleScope
import android.content.Intent
import android.Manifest
import android.R.attr.insetLeft
import android.R.attr.insetRight
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
import android.view.ViewOutlineProvider
import androidx.core.graphics.toColorInt

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "powerball_selection")

class MainActivity : AppCompatActivity() {

    private lateinit var selectedNumbersText: MaterialTextView
    private lateinit var latestWinningText: MaterialTextView

    private val maxWhite = 69
    private val maxPowerball = 26
    private val whiteLimit = 5
    private val numbersPerRow = 10

    private val selectedWhite = mutableSetOf<Int>()
    private var selectedPB: Int? = null

    private val whiteButtons = mutableMapOf<Int, MaterialButton>()
    private val pbButtons = mutableMapOf<Int, MaterialButton>()

    private val winningWhite = mutableSetOf<Int>()
    private var winningPB: Int? = null

    private var showWinningHighlights = true
    private var notificationsEnabled = true

    private val COLOR_WINNING = Color.parseColor("#9C27B0")
    private val COLOR_GOLD     = Color.parseColor("#FFD700")
    private val COLOR_SELECTED_WHITE = Color.parseColor("#616161")
    private val COLOR_SELECTED_PB    = Color.parseColor("#D32F2F")
    private val COLOR_UNSELECTED     = Color.LTGRAY

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && notificationsEnabled) {
            scheduleBackgroundWinningCheck()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationChannels.createAll(this)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val container = findViewById<LinearLayout>(R.id.contentContainer)
        container.setBackgroundColor(Color.parseColor("#000000"))

        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK

        container.addView(MaterialTextView(this).apply {
            text = "Powerball"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })

        latestWinningText = MaterialTextView(this).apply {
            text = "Latest Draw: Loading..."
            textSize = 11f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        container.addView(latestWinningText)

        selectedNumbersText = MaterialTextView(this).apply {
            text = "Select your numbers"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        container.addView(selectedNumbersText)

        container.addView(createSectionTitle("White Balls"))
        container.addView(createGrid(maxWhite, false))

        container.addView(createSectionTitle("Powerball"))
        container.addView(createGrid(maxPowerball, true))

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

        lifecycleScope.launch { loadSavedPreferences() }
        lifecycleScope.launch { fetchLatestWinningNumbersForUI() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.menu_toggle_highlights)?.isChecked = showWinningHighlights
        menu.findItem(R.id.menu_toggle_notifications)?.isChecked = notificationsEnabled
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_toggle_highlights -> {
                showWinningHighlights = !showWinningHighlights
                item.isChecked = showWinningHighlights
                lifecycleScope.launch {
                    dataStore.edit { prefs ->
                        prefs[DataStoreKeys.HIGHLIGHT_ENABLED_KEY] = showWinningHighlights
                    }
                }
                applyHighlightState()
                true
            }

            R.id.menu_toggle_notifications -> {
                notificationsEnabled = !notificationsEnabled
                item.isChecked = notificationsEnabled

                lifecycleScope.launch {
                    dataStore.edit { prefs ->
                        prefs[DataStoreKeys.NOTIFICATIONS_ENABLED_KEY] = notificationsEnabled
                    }

                    if (notificationsEnabled) {
                        scheduleBackgroundWinningCheck()
                    } else {
                        cancelBackgroundWinningCheck()
                    }
                }
                true
            }

            R.id.menu_history -> {
                startActivity(Intent(this, HistoryActivity::class.java))
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
        if (!notificationsEnabled) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<CheckWinningWorker>(
            repeatInterval = 12, TimeUnit.HOURS,
            flexTimeInterval = 1, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "powerball_winning_check",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
    }

    private fun cancelBackgroundWinningCheck() {
        WorkManager.getInstance(this)
            .cancelUniqueWork("powerball_winning_check")
    }

    private suspend fun loadSavedPreferences() {
        val prefs = dataStore.data.first()

        prefs[DataStoreKeys.WHITE_NUMBERS_KEY]?.let { savedSet ->
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

        prefs[DataStoreKeys.POWERBALL_KEY]?.let { savedPB ->
            if (savedPB in 1..maxPowerball) {
                selectedPB = savedPB
                pbButtons[savedPB]?.let { btn ->
                    btn.backgroundTintList = null
                    btn.setBackgroundColor(COLOR_SELECTED_PB)
                    btn.setTextColor(Color.WHITE)
                }
            }
        }

        showWinningHighlights = prefs[DataStoreKeys.HIGHLIGHT_ENABLED_KEY] ?: true
        notificationsEnabled = prefs[DataStoreKeys.NOTIFICATIONS_ENABLED_KEY] ?: true

        updateDisplay()
        applyHighlightState()
        invalidateOptionsMenu()

        if (notificationsEnabled) {
            scheduleBackgroundWinningCheck()
        }
    }

    private fun toggleWhite(num: Int) {
        val btn = whiteButtons[num] ?: return

        if (selectedWhite.contains(num)) {
            // Deselect
            selectedWhite.remove(num)
            btn.backgroundTintList = null
            btn.setBackgroundColor(
                if (showWinningHighlights && winningWhite.contains(num)) COLOR_GOLD
                else Color.parseColor("#f0f0f0")  // light gray-white unselected
            )
            btn.setTextColor(
                if (showWinningHighlights && winningWhite.contains(num)) Color.BLACK
                else Color.BLACK  // dark text on light bg
            )
        } else if (selectedWhite.size < whiteLimit) {
            // Select
            selectedWhite.add(num)
            btn.backgroundTintList = null
            btn.setBackgroundColor(
                if (showWinningHighlights && winningWhite.contains(num)) COLOR_GOLD
                else Color.parseColor("#9e9e9e")  // medium gray when selected
            )
            btn.setTextColor(
                if (showWinningHighlights && winningWhite.contains(num)) Color.BLACK
                else Color.WHITE  // white text on medium gray
            )
        }

        updateDisplay()
        saveSelection()
    }

    private fun togglePowerball(num: Int) {
        val btn = pbButtons[num] ?: return

        // First, reset previous selection (if any)
        selectedPB?.let { prev ->
            pbButtons[prev]?.let { prevBtn ->
                prevBtn.backgroundTintList = null
                prevBtn.setBackgroundColor(
                    if (showWinningHighlights && winningPB == prev) COLOR_GOLD
                    else Color.parseColor("#f0f0f0")  // light unselected
                )
                prevBtn.setTextColor(
                    if (showWinningHighlights && winningPB == prev) Color.BLACK
                    else Color.BLACK
                )
            }
        }

        if (selectedPB == num) {
            // Deselect (toggle off)
            selectedPB = null
        } else {
            // Select new one
            selectedPB = num
            btn.backgroundTintList = null
            btn.setBackgroundColor(
                if (showWinningHighlights && winningPB == num) COLOR_GOLD
                else Color.parseColor("#9e9e9e")  // medium gray when selected
            )
            btn.setTextColor(
                if (showWinningHighlights && winningPB == num) Color.BLACK
                else Color.WHITE  // white on selected gray
            )
        }

        updateDisplay()
        saveSelection()
    }

    private fun updateDisplay() {
        val whiteText = if (selectedWhite.isEmpty()) "None" else selectedWhite.sorted().joinToString(" ")
        val pbText = selectedPB?.toString() ?: "None"
        selectedNumbersText.text = "WB $whiteText PB $pbText"
    }

    private fun saveSelection() {
        lifecycleScope.launch {
            dataStore.edit { prefs ->
                prefs[DataStoreKeys.WHITE_NUMBERS_KEY] = selectedWhite.map { it.toString() }.toSet()
                if (selectedPB != null) {
                    prefs[DataStoreKeys.POWERBALL_KEY] = selectedPB!!
                } else {
                    prefs.remove(DataStoreKeys.POWERBALL_KEY)
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
            radius = 4f.dpToPx()           // card itself has nice rounding
            cardElevation = 6f.dpToPx()
            setCardBackgroundColor(Color.parseColor("#111111"))  // very dark base

            // Optional: subtle mesh/perforated feel (requires drawable)
            // background = ContextCompat.getDrawable(context, R.drawable.mesh_background)
        }

        val grid = GridLayout(this).apply {
            columnCount = numbersPerRow
            setPadding(12.dpToPx().toInt(), 12.dpToPx().toInt(), 12.dpToPx().toInt(), 12.dpToPx().toInt())
            // minimal padding around whole grid
        }

        for (i in 1..max) {
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle).apply {
                text = i.toString()
                textSize = 9f                  // larger text like on machine
                setTypeface(null, Typeface.BOLD) // bold numbers
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                insetTop = 0
                insetBottom = 0
                minimumWidth = 0
                minimumHeight = 0

                // Stadium / pill shape – high but finite corner radius
                cornerRadius = 2.dpToPx().toInt()   // 24–32 dp gives nice rounded rect

                // Base look: vivid red + white text + thin border
                backgroundTintList = null
                setBackgroundColor(Color.parseColor("#ffffff"))   // machine-like red
                setTextColor(Color.BLACK)
                //setStrokeColor(ColorStateList.valueOf(Color.parseColor("#b71c1c"))) // darker red border
                //strokeWidth = 2.dpToPx().toInt()   // thin metallic-like outline

                // Slight 3D push-button feel
                elevation = 2f
                translationZ = 2f   // tiny lift
                outlineProvider = ViewOutlineProvider.BACKGROUND
                clipToOutline = true

                if (isPowerball) pbButtons[i] = this else whiteButtons[i] = this

                setOnClickListener {
                    if (isPowerball) togglePowerball(i) else toggleWhite(i)
                }
            }

            val params = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 15.dpToPx().toInt()
                height = 20.dpToPx().toInt()
                    setMargins(15, 15, 15, 15)   // very tight – almost no gap
            }

            grid.addView(btn, params)
        }

        card.addView(grid)
        return card
    }

    private fun Float.dpToPx(): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics)

    private fun Int.dpToPx(): Float = toFloat().dpToPx()

    private suspend fun fetchLatestWinningNumbersForUI() {
        withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect("https://www.texaslottery.com/export/sites/lottery/Games/Powerball/Winning_Numbers/print.html")
                    .userAgent("Mozilla/5.0")
                    .timeout(15000)
                    .get()

                val cells = doc.select("table tr")[1].select("td")
                if (cells.size >= 7) {
                    val white = listOfNotNull(
                        cells[1].text().trim().toIntOrNull(),
                        cells[2].text().trim().toIntOrNull(),
                        cells[3].text().trim().toIntOrNull(),
                        cells[4].text().trim().toIntOrNull(),
                        cells[5].text().trim().toIntOrNull()
                    ).toSet()

                    val pb = cells[6].text().trim().toIntOrNull()

                    withContext(Dispatchers.Main) {
                        winningWhite.clear()
                        winningWhite.addAll(white)
                        winningPB = pb
                        latestWinningText.text = "Latest Draw: ${white.sorted().joinToString(" ")} PB $pb"
                        applyHighlightState()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    latestWinningText.text = "Latest Draw: (offline)"
                }
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

    private fun highlightWinningNumbers() {
        winningWhite.forEach { num ->
            whiteButtons[num]?.let { btn ->
                if (selectedWhite.contains(num)) {
                    btn.setBackgroundColor(COLOR_GOLD)
                    btn.setTextColor(Color.BLACK)
                } else {
                    btn.setBackgroundColor(COLOR_WINNING)
                    btn.setTextColor(Color.WHITE)
                }
            }
        }

        winningPB?.let { pb ->
            pbButtons[pb]?.let { btn ->
                if (selectedPB == pb) {
                    btn.setBackgroundColor(COLOR_GOLD)
                    btn.setTextColor(Color.BLACK)
                } else {
                    btn.setBackgroundColor(COLOR_WINNING)
                    btn.setTextColor(Color.WHITE)
                }
            }
        }
    }

    private fun clearAllHighlights() {
        selectedWhite.forEach { num ->
            whiteButtons[num]?.let { btn ->
                btn.setBackgroundColor(COLOR_SELECTED_WHITE)
                btn.setTextColor(Color.WHITE)
            }
        }

        selectedPB?.let { pb ->
            pbButtons[pb]?.let { btn ->
                btn.setBackgroundColor(COLOR_SELECTED_PB)
                btn.setTextColor(Color.WHITE)
            }
        }

        winningWhite.forEach { num ->
            if (!selectedWhite.contains(num)) {
                whiteButtons[num]?.let { btn ->
                    btn.setBackgroundColor(COLOR_UNSELECTED)
                    btn.setTextColor(Color.BLACK)
                }
            }
        }

        winningPB?.let { pb ->
            if (selectedPB != pb) {
                pbButtons[pb]?.let { btn ->
                    btn.setBackgroundColor(COLOR_UNSELECTED)
                    btn.setTextColor(Color.BLACK)
                }
            }
        }
    }
}