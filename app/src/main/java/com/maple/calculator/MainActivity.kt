package com.maple.calculator

import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.MathContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        setContent {
            CalculatorTheme {
                CalculatorScreen()
            }
        }
    }
}

@Composable
fun CalculatorTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) {
        darkColorScheme(
            primary = Color(0xFFB1D2BD),
            onPrimary = Color(0xFF1C372A),
            primaryContainer = Color(0xFF354F40),
            onPrimaryContainer = Color(0xFFCDE9D5),
            secondaryContainer = Color(0xFF3B4B41),
            onSecondaryContainer = Color(0xFFDCE8DE),
            tertiaryContainer = Color(0xFF5A4039),
            onTertiaryContainer = Color(0xFFFFDAD0)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF496356),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFCBE9D5),
            onPrimaryContainer = Color(0xFF082113),
            secondaryContainer = Color(0xFFDDE9DF),
            onSecondaryContainer = Color(0xFF17251B),
            tertiaryContainer = Color(0xFFFFDAD0),
            onTertiaryContainer = Color(0xFF311008),
            surface = Color(0xFFF8FAF6),
            surfaceVariant = Color(0xFFE8EDE7)
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun CalculatorScreen() {
    var tokens by remember { mutableStateOf(emptyList<String>()) }
    var entry by remember { mutableStateOf("0") }
    var expectingOperand by remember { mutableStateOf(false) }
    var justEvaluated by remember { mutableStateOf(false) }
    var expressionOverride by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }

    fun resetForNewEntry() {
        tokens = emptyList()
        entry = "0"
        expressionOverride = null
        error = false
        expectingOperand = false
        justEvaluated = false
    }

    fun inputDigit(digit: String) {
        if (justEvaluated || error) resetForNewEntry()
        expressionOverride = null
        error = false
        entry = when {
            expectingOperand -> digit
            entry == "0" -> digit
            entry == "-0" -> "-$digit"
            entry.length < 18 -> entry + digit
            else -> entry
        }
        expectingOperand = false
    }

    fun inputDecimal() {
        if (justEvaluated || error) resetForNewEntry()
        expressionOverride = null
        if (expectingOperand) {
            entry = "0."
            expectingOperand = false
        } else if (!entry.contains('.')) {
            entry += "."
        }
    }

    fun inputOperator(operator: String) {
        if (error) return
        expressionOverride = null
        if (justEvaluated) {
            tokens = listOf(entry)
            justEvaluated = false
            expectingOperand = true
            tokens = tokens + operator
            return
        }
        if (expectingOperand) {
            if (tokens.lastOrNull()?.let { it in listOf("+", "−", "×", "÷") } == true) {
                tokens = tokens.dropLast(1) + operator
            }
        } else {
            tokens = tokens + entry + operator
            expectingOperand = true
        }
    }

    fun clear() {
        tokens = emptyList()
        entry = "0"
        expectingOperand = false
        justEvaluated = false
        expressionOverride = null
        error = false
    }

    fun backspace() {
        if (error || justEvaluated) {
            clear()
            return
        }
        expressionOverride = null
        if (expectingOperand && tokens.lastOrNull()?.let { it in listOf("+", "−", "×", "÷") } == true) {
            tokens = tokens.dropLast(1)
            entry = tokens.lastOrNull() ?: "0"
            if (tokens.isNotEmpty()) tokens = tokens.dropLast(1)
            expectingOperand = false
        } else if (!expectingOperand) {
            entry = when {
                entry.length > 1 -> entry.dropLast(1).let { if (it == "-" || it == "") "0" else it }
                else -> "0"
            }
        }
    }

    fun toggleSign() {
        if (error || expectingOperand) return
        expressionOverride = null
        entry = if (entry.startsWith('-')) entry.drop(1) else "-$entry"
        if (entry == "-0") entry = "0"
    }

    fun percent() {
        if (error || expectingOperand) return
        val value = entry.toBigDecimalOrNull() ?: return
        entry = formatNumber(value.divide(BigDecimal("100"), MathContext.DECIMAL64))
        expressionOverride = null
        justEvaluated = false
    }

    fun equals() {
        if (error || expectingOperand || tokens.isEmpty()) return
        val fullTokens = tokens + entry
        val expression = fullTokens.joinToString(" ") + " ="
        val result = runCatching { ExpressionParser(fullTokens).parse() }.getOrNull()
        if (result == null) {
            error = true
            entry = "错误"
            expressionOverride = expression
        } else {
            entry = formatNumber(result)
            expressionOverride = expression
            tokens = emptyList()
            justEvaluated = true
        }
        expectingOperand = false
    }

    val expression = expressionOverride ?: buildString {
        append(tokens.joinToString(" "))
        if (!expectingOperand) {
            if (isNotEmpty()) append(' ')
            append(entry)
        }
    }
    val displayText = if (error) "错误" else entry
    val displayFontSize = when (displayText.length) {
        in 0..7 -> 58.sp
        in 8..10 -> 48.sp
        in 11..13 -> 38.sp
        else -> 30.sp
    }
    val expressionScrollState = rememberScrollState()
    val scrollState = rememberScrollState()
    LaunchedEffect(expression) { expressionScrollState.scrollTo(expressionScrollState.maxValue) }
    LaunchedEffect(displayText) { scrollState.scrollTo(scrollState.maxValue) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { insets ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            val keyHeight = ((maxHeight - 230.dp) / 5).coerceIn(48.dp, 72.dp)
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = expression,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(expressionScrollState),
                        minLines = 1,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = displayText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = displayFontSize,
                        lineHeight = displayFontSize,
                        fontWeight = FontWeight.Light
                    )
                }

                val rows = listOf(
                    listOf("AC", "⌫", "%", "÷"),
                    listOf("7", "8", "9", "×"),
                    listOf("4", "5", "6", "−"),
                    listOf("1", "2", "3", "+"),
                    listOf("±", "0", ".", "=")
                )
                rows.forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { label ->
                            CalculatorButton(
                                text = label,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(keyHeight),
                                onClick = {
                                    when (label) {
                                        "AC" -> clear()
                                        "⌫" -> backspace()
                                        "%" -> percent()
                                        "±" -> toggleSign()
                                        "." -> inputDecimal()
                                        "=" -> equals()
                                        "+", "−", "×", "÷" -> inputOperator(label)
                                        else -> inputDigit(label)
                                    }
                                },
                                emphasized = label in listOf("+", "−", "×", "÷", "="),
                                utility = rowIndex == 0 || label == "±"
                            )
                        }
                    }
                    if (rowIndex < rows.lastIndex) Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun CalculatorButton(
    text: String,
    modifier: Modifier,
    emphasized: Boolean,
    utility: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessMedium),
        label = "keyPressScale"
    )
    val view = LocalView.current
    val colors = MaterialTheme.colorScheme
    val container = when {
        text == "=" -> colors.primary
        emphasized -> colors.primaryContainer
        utility -> colors.tertiaryContainer
        else -> colors.secondaryContainer
    }
    val content = when {
        text == "=" -> colors.onPrimary
        emphasized -> colors.onPrimaryContainer
        utility -> colors.onTertiaryContainer
        else -> colors.onSecondaryContainer
    }

    FilledTonalButton(
        onClick = {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    HapticFeedbackConstants.KEYBOARD_TAP
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }
            )
            onClick()
        },
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(22.dp),
        interactionSource = interactionSource,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = container,
            contentColor = content
        ),
        elevation = ButtonDefaults.filledTonalButtonElevation(
            defaultElevation = if (emphasized) 1.dp else 0.dp,
            pressedElevation = 0.dp
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            fontSize = if (text == "AC" || text == "⌫") 20.sp else 25.sp,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            color = content
        )
    }
}

private fun formatNumber(value: BigDecimal): String {
    val normalized = value.stripTrailingZeros()
    if (normalized.precision() > 16 || normalized.scale() < -8) {
        return normalized.round(MathContext(12)).stripTrailingZeros().toPlainString()
    }
    return normalized.toPlainString().ifEmpty { "0" }
}

private class ExpressionParser(private val tokens: List<String>) {
    private var position = 0

    fun parse(): BigDecimal {
        val value = parseExpression(0)
        if (position != tokens.size) error("Unexpected token")
        return value
    }

    private fun parseExpression(minPrecedence: Int): BigDecimal {
        var left = parseValue()
        while (position < tokens.size) {
            val operator = tokens[position]
            val precedence = when (operator) {
                "+", "−" -> 1
                "×", "÷" -> 2
                else -> break
            }
            if (precedence < minPrecedence) break
            position++
            val right = parseExpression(precedence + 1)
            left = when (operator) {
                "+" -> left.add(right, MathContext.DECIMAL64)
                "−" -> left.subtract(right, MathContext.DECIMAL64)
                "×" -> left.multiply(right, MathContext.DECIMAL64)
                "÷" -> left.divide(right, MathContext.DECIMAL64)
                else -> error("Unknown operator")
            }
        }
        return left
    }

    private fun parseValue(): BigDecimal {
        val token = tokens.getOrNull(position++) ?: error("Missing value")
        return token.toBigDecimalOrNull() ?: error("Invalid number")
    }
}
