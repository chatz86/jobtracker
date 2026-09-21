package com.example.jobtracker

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

class KeyboardActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prompt = intent.getStringExtra("PROMPT") ?: "Enter text"

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        val label = TextView(this).apply {
            text = prompt
            setTextColor(Color.parseColor("#64B5F6"))
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = 24
            }
        }

        val input = EditText(this).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            hint = "Type here..."
            textSize = 18f
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = 16
            }
        }

        val confirmBtn = Button(this).apply {
            text = "Done"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2E7D32"))
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = 8
            }
            setOnClickListener {
                val resultIntent = android.content.Intent().apply {
                    putExtra("INPUT_TEXT", input.text.toString())
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }

        val cancelBtn = Button(this).apply {
            text = "Cancel"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#C62828"))
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            setOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }
        }

        layout.addView(label)
        layout.addView(input)
        layout.addView(confirmBtn)
        layout.addView(cancelBtn)

        setContentView(layout)

        input.requestFocus()
        input.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            // 0 == InputMethodManager.SHOW_IMPLICIT, which is deprecated as a constant.
            imm.showSoftInput(input, 0)
        }, 300)
    }
}
