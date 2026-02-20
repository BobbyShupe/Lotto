package com.example.minimal

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import androidx.core.view.setPadding

class MainActivity : AppCompatActivity() {

    private lateinit var resultText: MaterialTextView
    private lateinit var selectedNumbersText: MaterialTextView

    // Powerball Rules: 5 White Balls (1-69) and 1 Powerball (1-26)
    private val maxWhite = 69
    private val maxPowerball = 26
    private val whiteLimit = 5
    private val numbersPerRow = 7

    private val selectedWhite = mutableSetOf<Int>()
    private var selectedPB: Int? = null

    private val whiteButtons = mutableMapOf<Int, MaterialButton>()
    private val pbButtons = mutableMapOf<Int, MaterialButton>()

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
            setPadding(0, 0, 0, 32)
        })

        selectedNumbersText = MaterialTextView(this).apply {
            text = "Select your numbers"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        container.addView(selectedNumbersText)

        // Section 1: White Balls
        container.addView(createSectionTitle("White Balls (Pick 5)"))
        container.addView(createGrid(maxWhite, false))

        // Section 2: Powerball
        container.addView(createSectionTitle("Powerball (Pick 1)"))
        container.addView(createGrid(maxPowerball, true))

        val checkButton = MaterialButton(this).apply {
            text = "Check Selection"
            setOnClickListener { checkSelection() }
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
            // FIXED: Corrected style reference
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle).apply {
                text = i.toString()
                textSize = 10f
                setPadding(0)
                insetTop = 0
                insetBottom = 0
                minWidth = 0
                minHeight = 0
                minimumWidth = 0
                // FIXED: Changed to handle Int conversion
                minimumHeight = 40.dpToPx().toInt()

                if (isPowerball) pbButtons[i] = this else whiteButtons[i] = this
                setOnClickListener { if (isPowerball) togglePowerball(i) else toggleWhite(i) }
            }

            val params = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                setMargins(2, 2, 2, 2)
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

    // FIXED: Added both Float and Int extension helpers to avoid receiver mismatch
    private fun Float.dpToPx(): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics)

    private fun Int.dpToPx(): Float =
        this.toFloat().dpToPx()
}