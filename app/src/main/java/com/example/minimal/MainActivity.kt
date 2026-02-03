package com.example.minimal

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textview.MaterialTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = ConstraintLayout(this).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }

        val text = MaterialTextView(this).apply {
            text = "Hello, minimal Material 3 world!"
            textSize = 24f
            id = android.R.id.text1

            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topToTop = LayoutParams.PARENT_ID
                bottomToBottom = LayoutParams.PARENT_ID
                startToStart = LayoutParams.PARENT_ID
                endToEnd = LayoutParams.PARENT_ID
            }
        }

        root.addView(text)
        setContentView(root)
    }
}