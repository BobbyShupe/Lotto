package com.example.minimal

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException

// DataStore delegate
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "powerball_selection")

class MainActivity : AppCompatActivity() {

    private lateinit var selectedNumbersText: MaterialTextView
    private lateinit var latestWinningText: MaterialTextView

    // Powerball Rules
    private val maxWhite = 69
    private val maxPowerball = 26
    private val whiteLimit = 5
    private val numbersPerRow = 7

    private val selectedWhite = mutableSetOf<Int>()
    private var selectedPB: Int? = null

    private val whiteButtons = mutableMapOf<Int, MaterialButton>()
    private val pbButtons = mutableMapOf<Int, MaterialButton>()

    // Winning numbers from latest draw
    private val winningWhite = mutableSetOf<Int>()
    private var winningPB: Int? = null

    // Colors
    private val COLOR_WINNING = Color.parseColor("#9C27B0")     // purple
    private val COLOR_GOLD     = Color.parseColor("#FFD700")     // gold
    private val COLOR_SELECTED_WHITE = Color.parseColor("#616161")
    private val COLOR_SELECTED_PB    = Color.parseColor("#D32F2F")
    private val COLOR_UNSELECTED     = Color.LTGRAY

    // DataStore keys
    private val WHITE_NUMBERS_KEY = stringSetPreferencesKey("white_numbers")
    private val POWERBALL_KEY = intPreferencesKey("powerball_number")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32)
        }

        container.addView(MaterialTextView(this).apply {
            text = "Powerball Selector"
            textSize = 28f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })

        latestWinningText = MaterialTextView(this).apply {
            text = "Latest Draw: Loading..."
            textSize = 11f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        container.addView(latestWinningText)

        selectedNumbersText = MaterialTextView(this).apply {
            text = "Select your numbers"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        container.addView(selectedNumbersText)

        container.addView(createSectionTitle("White Balls (Pick 5)"))
        container.addView(createGrid(maxWhite, false))

        container.addView(createSectionTitle("Powerball (Pick 1)"))
        container.addView(createGrid(maxPowerball, true))

        scrollView.addView(container)
        setContentView(scrollView)

        lifecycleScope.launch {
            loadSavedSelection()
        }

        lifecycleScope.launch {
            fetchLatestWinningNumbers()
        }
    }

    private suspend fun fetchLatestWinningNumbers() {
        withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect("https://www.texaslottery.com/export/sites/lottery/Games/Powerball/Winning_Numbers/print.html")
                    .userAgent("Mozilla/5.0")
                    .timeout(15000)
                    .get()

                val rows = doc.select("table tr")
                if (rows.size < 2) throw IOException("Table not found")

                val cells = rows[1].select("td")

                if (cells.size >= 8) {
                    val date = cells[0].text().trim()
                    val w1 = cells[1].text().trim().toIntOrNull() ?: 0
                    val w2 = cells[2].text().trim().toIntOrNull() ?: 0
                    val w3 = cells[3].text().trim().toIntOrNull() ?: 0
                    val w4 = cells[4].text().trim().toIntOrNull() ?: 0
                    val w5 = cells[5].text().trim().toIntOrNull() ?: 0
                    val pb = cells[6].text().trim().toIntOrNull() ?: 0
                    val powerPlay = cells[7].text().trim()

                    winningWhite.clear()
                    winningWhite.addAll(listOf(w1, w2, w3, w4, w5).filter { it in 1..maxWhite })

                    winningPB = if (pb in 1..maxPowerball) pb else null

                    val whiteStr = winningWhite.sorted().joinToString(" ")
                    val pbStr = winningPB?.toString() ?: "?"

                    val display = "Latest ($date): $whiteStr PB $pbStr (x$powerPlay)"

                    withContext(Dispatchers.Main) {
                        latestWinningText.text = display
                        latestWinningText.setTextColor(Color.BLACK)

                        highlightWinningNumbers()
                    }
                } else {
                    throw IOException("Data format unexpected")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    latestWinningText.text = "Latest Draw: Unable to load – check internet"
                    latestWinningText.setTextColor(Color.RED)
                }
            }
        }
    }

    private fun highlightWinningNumbers() {
        winningWhite.forEach { num ->
            whiteButtons[num]?.let { btn ->
                btn.backgroundTintList = null
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
                btn.backgroundTintList = null
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

    private suspend fun loadSavedSelection() {
        val prefs = dataStore.data.first()

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
                this.selectedPB = savedPB
                pbButtons[savedPB]?.let { btn ->
                    btn.backgroundTintList = null
                    btn.setBackgroundColor(COLOR_SELECTED_PB)
                    btn.setTextColor(Color.WHITE)
                }
            }
        }

        updateDisplay()
        highlightWinningNumbers()  // apply winning highlights after selections loaded
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
            setPadding(8.dpToPx().toInt())
        }

        for (i in 1..max) {
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle).apply {
                text = i.toString()
                textSize = 10f
                setPadding(0)
                insetTop = 0
                insetBottom = 0
                minWidth = 0
                minHeight = 0
                minimumWidth = 0
                minimumHeight = 40.dpToPx().toInt()
                cornerRadius = 9999.dpToPx().toInt()

                backgroundTintList = null
                setBackgroundColor(Color.LTGRAY)
                setTextColor(Color.BLACK)

                if (isPowerball) pbButtons[i] = this else whiteButtons[i] = this
                setOnClickListener {
                    if (isPowerball) togglePowerball(i) else toggleWhite(i)
                    saveSelection()
                }
            }

            val params = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                setMargins(1, 1, 1, 1)
            }
            grid.addView(btn, params)
        }

        card.addView(grid)
        return card
    }

    private fun toggleWhite(num: Int) {
        val btn = whiteButtons[num] ?: return

        if (selectedWhite.contains(num)) {
            selectedWhite.remove(num)
            btn.backgroundTintList = null
            if (winningWhite.contains(num)) {
                // was golden → now just winning (purple)
                btn.setBackgroundColor(COLOR_WINNING)
                btn.setTextColor(Color.WHITE)
            } else {
                // normal deselection
                btn.setBackgroundColor(Color.LTGRAY)
                btn.setTextColor(Color.BLACK)
            }
        } else if (selectedWhite.size < whiteLimit) {
            selectedWhite.add(num)
            btn.backgroundTintList = null
            if (winningWhite.contains(num)) {
                // selected + winning → gold
                btn.setBackgroundColor(COLOR_GOLD)
                btn.setTextColor(Color.BLACK)
            } else {
                // normal selection
                btn.setBackgroundColor(COLOR_SELECTED_WHITE)
                btn.setTextColor(Color.WHITE)
            }
        }
        updateDisplay()
    }

    private fun togglePowerball(num: Int) {
        val btn = pbButtons[num] ?: return

        // Reset previous if changing
        selectedPB?.let { prevNum ->
            val prevBtn = pbButtons[prevNum]
            prevBtn?.backgroundTintList = null
            if (winningPB == prevNum) {
                prevBtn?.setBackgroundColor(COLOR_WINNING)
                prevBtn?.setTextColor(Color.WHITE)
            } else {
                prevBtn?.setBackgroundColor(Color.LTGRAY)
                prevBtn?.setTextColor(Color.BLACK)
            }
        }

        if (selectedPB == num) {
            selectedPB = null
        } else {
            selectedPB = num
            btn.backgroundTintList = null
            if (winningPB == num) {
                // selected + winning → gold
                btn.setBackgroundColor(COLOR_GOLD)
                btn.setTextColor(Color.BLACK)
            } else {
                // normal selection
                btn.setBackgroundColor(COLOR_SELECTED_PB)
                btn.setTextColor(Color.WHITE)
            }
        }
        updateDisplay()
    }

    private fun updateDisplay() {
        val whitePart = if (selectedWhite.isEmpty()) "None" else selectedWhite.sorted().joinToString(" ")
        val pbPart = selectedPB ?: "None"
        selectedNumbersText.text = "WB $whitePart PB $pbPart"
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

    private fun Float.dpToPx(): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics)

    private fun Int.dpToPx(): Float =
        this.toFloat().dpToPx()
}