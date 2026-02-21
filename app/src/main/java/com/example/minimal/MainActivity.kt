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

    private lateinit var resultText: MaterialTextView
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
            textSize = 18f
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

        val checkButton = MaterialButton(this).apply {
            text = "Check Selection"
            setOnClickListener { checkSelection() }
            cornerRadius = 28.dpToPx().toInt()
        }
        container.addView(checkButton)

        resultText = MaterialTextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 64)
        }
        container.addView(resultText)

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

                // Select all rows and grab the second one (index 1) to skip the header
                val rows = doc.select("table tr")
                if (rows.size < 2) throw IOException("Table not found")

                val cells = rows[1].select("td")

                // The working version expects at least 8 columns:
                // 0: Date, 1-5: White Balls, 6: PB, 7: Power Play
                if (cells.size >= 8) {
                    val date = cells[0].text().trim()
                    val white1 = cells[1].text().trim()
                    val white2 = cells[2].text().trim()
                    val white3 = cells[3].text().trim()
                    val white4 = cells[4].text().trim()
                    val white5 = cells[5].text().trim()
                    val powerball = cells[6].text().trim()
                    val powerPlay = cells[7].text().trim()

                    val display = "Latest ($date): $white1 • $white2 • $white3 • $white4 • $white5 • PB $powerball (x$powerPlay)"

                    withContext(Dispatchers.Main) {
                        latestWinningText.text = display
                        latestWinningText.setTextColor(Color.BLACK)
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
                whiteButtons[num]?.setBackgroundColor(Color.DKGRAY)
            }
        }

        prefs[POWERBALL_KEY]?.let { savedPB ->
            if (savedPB in 1..maxPowerball) {
                selectedPB = savedPB
                pbButtons[savedPB]?.setBackgroundColor(Color.RED)
            }
        }

        updateDisplay()
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
        if (selectedWhite.contains(num)) {
            selectedWhite.remove(num)
            whiteButtons[num]?.setBackgroundColor(Color.LTGRAY)
        } else if (selectedWhite.size < whiteLimit) {
            selectedWhite.add(num)
            whiteButtons[num]?.setBackgroundColor(Color.DKGRAY)
        }
        updateDisplay()
    }

    private fun togglePowerball(num: Int) {
        selectedPB?.let { pbButtons[it]?.setBackgroundColor(Color.LTGRAY) }
        if (selectedPB == num) {
            selectedPB = null
        } else {
            selectedPB = num
            pbButtons[num]?.setBackgroundColor(Color.RED)
        }
        updateDisplay()
    }

    private fun updateDisplay() {
        val whitePart = if (selectedWhite.isEmpty()) "None" else selectedWhite.sorted().joinToString(", ")
        val pbPart = selectedPB ?: "None"
        selectedNumbersText.text = "Whites: $whitePart | PB: $pbPart"
    }

    private fun checkSelection() {
        if (selectedWhite.size < whiteLimit || selectedPB == null) {
            resultText.text = "Selection Incomplete"
            resultText.setTextColor(Color.RED)
        } else {
            resultText.text = "Selection Complete!"
            resultText.setTextColor(Color.GREEN)
        }
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