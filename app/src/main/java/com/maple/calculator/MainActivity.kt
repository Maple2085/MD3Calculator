package com.maple.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalculatorTheme {
                CalculatorScreen()
            }
        }
    }
}

@Composable
fun CalculatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = dynamicLightColorScheme(androidx.compose.ui.platform.LocalContext.current),
        content = content
    )
}

@Composable
fun CalculatorScreen() {
    var display by remember { mutableStateOf("0") }
    var currentInput by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf<String?>(null) }
    var previousValue by remember { mutableStateOf(0.0) }
    var shouldResetDisplay by remember { mutableStateOf(false) }

    fun onNumberClick(number: String) {
        if (shouldResetDisplay) {
            display = number
            currentInput = number
            shouldResetDisplay = false
        } else {
            if (display == "0") {
                display = number
                currentInput = number
            } else {
                display += number
                currentInput += number
            }
        }
    }

    fun onOperatorClick(op: String) {
        if (currentInput.isNotEmpty()) {
            previousValue = currentInput.toDoubleOrNull() ?: 0.0
            currentInput = ""
        }
        operator = op
        shouldResetDisplay = true
    }

    fun onEqualsClick() {
        val current = currentInput.toDoubleOrNull() ?: 0.0
        val result = when (operator) {
            "+" -> previousValue + current
            "-" -> previousValue - current
            "×" -> previousValue * current
            "÷" -> if (current != 0.0) previousValue / current else Double.NaN
            else -> current
        }
        display = if (result.isNaN()) "错误" else {
            if (result % 1.0 == 0.0) result.toInt().toString()
            else String.format("%.8f", result).trimEnd('0').trimEnd('.')
        }
        currentInput = display
        operator = null
        shouldResetDisplay = true
    }

    fun onClearClick() {
        display = "0"
        currentInput = ""
        operator = null
        previousValue = 0.0
        shouldResetDisplay = false
    }

    fun onBackspaceClick() {
        if (display.length > 1) {
            display = display.dropLast(1)
            currentInput = display
        } else {
            display = "0"
            currentInput = ""
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Display
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = display,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.End,
                    maxLines = 2
                )
            }

            // Buttons
            val buttons = listOf(
                listOf("C", "⌫", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("0", ".", "=")
            )

            buttons.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { label ->
                        CalculatorButton(
                            text = label,
                            modifier = Modifier.weight(if (label == "0") 2f else 1f),
                            onClick = {
                                when (label) {
                                    "C" -> onClearClick()
                                    "⌫" -> onBackspaceClick()
                                    "=" -> onEqualsClick()
                                    "+", "-", "×", "÷" -> onOperatorClick(label)
                                    "." -> {
                                        if (!currentInput.contains(".")) {
                                            onNumberClick(".")
                                        }
                                    }
                                    else -> onNumberClick(label)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CalculatorButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isOperator = text in listOf("+", "-", "×", "÷", "=")
    val isSpecial = text in listOf("C", "⌫")

    Button(
        onClick = onClick,
        modifier = modifier
            .height(72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                isOperator -> MaterialTheme.colorScheme.primaryContainer
                isSpecial -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Text(
            text = text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = when {
                isOperator -> MaterialTheme.colorScheme.onPrimaryContainer
                isSpecial -> MaterialTheme.colorScheme.onErrorContainer
                else -> MaterialTheme.colorScheme.onSecondaryContainer
            }
        )
    }
}
