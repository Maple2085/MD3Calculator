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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maple.calculator.engine.CalculatorEvent
import com.maple.calculator.engine.CalculatorState
import com.maple.calculator.engine.Operator
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            val calculatorViewModel: CalculatorViewModel = viewModel()
            val state by calculatorViewModel.uiState.collectAsState()
            val onEvent = remember(calculatorViewModel) {
                { event: CalculatorEvent -> calculatorViewModel.onEvent(event) }
            }
            CalculatorTheme {
                CalculatorScreen(state = state, onEvent = onEvent)
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
fun CalculatorScreen(state: CalculatorState, onEvent: (CalculatorEvent) -> Unit) {
    val density = LocalDensity.current
    // 限制系统字体缩放的放大倍率，避免 200% 字号撑破按键网格。
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = min(density.fontScale, 1.2f)
        )
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { insets ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                val landscape = maxWidth > maxHeight
                if (landscape) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DisplayPanel(state, Modifier.weight(0.9f).fillMaxHeight())
                        Keypad(state, onEvent, Modifier.weight(1.1f).fillMaxHeight())
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DisplayPanel(state, Modifier.weight(1f).fillMaxWidth())
                        Keypad(state, onEvent, Modifier.weight(2.6f).fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayPanel(state: CalculatorState, modifier: Modifier) {
    val historyScroll = rememberScrollState()
    LaunchedEffect(state.history) { historyScroll.scrollTo(historyScroll.maxValue) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = state.history,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(historyScroll),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.End,
            color = if (state.error != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        AdaptiveNumberText(
            text = state.display,
            isError = state.error != null,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AdaptiveNumberText(text: String, isError: Boolean, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val baseStyle = TextStyle(
        fontWeight = FontWeight.Light,
        fontFeatureSettings = "tnum",
        color = color
    )
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth
        val candidates = if (isError) {
            listOf(32, 28, 24, 20)
        } else {
            listOf(64, 56, 48, 40, 34, 28, 24, 20)
        }
        val fontSize = remember(text, widthPx, isError) {
            candidates.firstOrNull { size ->
                measurer.measure(text, baseStyle.copy(fontSize = size.sp)).size.width <= widthPx
            } ?: candidates.last()
        }
        val scrollState = rememberScrollState()
        LaunchedEffect(text) { scrollState.scrollTo(scrollState.maxValue) }
        Text(
            text = text,
            style = baseStyle.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.1f).sp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.End
        )
    }
}

private enum class KeyKind { Number, Operator, Result, Utility }

private data class KeySpec(
    val label: String,
    val event: CalculatorEvent,
    val kind: KeyKind
)

@Composable
private fun Keypad(state: CalculatorState, onEvent: (CalculatorEvent) -> Unit, modifier: Modifier) {
    val clearLabel = if (state.error == null && !state.justEvaluated && state.isEntering) "C" else "AC"
    val rows = remember(clearLabel) {
        listOf(
            listOf(
                KeySpec(clearLabel, CalculatorEvent.Clear, KeyKind.Utility),
                KeySpec("⌫", CalculatorEvent.Backspace, KeyKind.Utility),
                KeySpec("%", CalculatorEvent.Percent, KeyKind.Utility),
                KeySpec("÷", CalculatorEvent.OperatorPressed(Operator.DIVIDE), KeyKind.Operator)
            ),
            listOf(
                KeySpec("7", CalculatorEvent.Digit('7'), KeyKind.Number),
                KeySpec("8", CalculatorEvent.Digit('8'), KeyKind.Number),
                KeySpec("9", CalculatorEvent.Digit('9'), KeyKind.Number),
                KeySpec("×", CalculatorEvent.OperatorPressed(Operator.MULTIPLY), KeyKind.Operator)
            ),
            listOf(
                KeySpec("4", CalculatorEvent.Digit('4'), KeyKind.Number),
                KeySpec("5", CalculatorEvent.Digit('5'), KeyKind.Number),
                KeySpec("6", CalculatorEvent.Digit('6'), KeyKind.Number),
                KeySpec("−", CalculatorEvent.OperatorPressed(Operator.SUBTRACT), KeyKind.Operator)
            ),
            listOf(
                KeySpec("1", CalculatorEvent.Digit('1'), KeyKind.Number),
                KeySpec("2", CalculatorEvent.Digit('2'), KeyKind.Number),
                KeySpec("3", CalculatorEvent.Digit('3'), KeyKind.Number),
                KeySpec("+", CalculatorEvent.OperatorPressed(Operator.ADD), KeyKind.Operator)
            ),
            listOf(
                KeySpec("±", CalculatorEvent.ToggleSign, KeyKind.Utility),
                KeySpec("0", CalculatorEvent.Digit('0'), KeyKind.Number),
                KeySpec(".", CalculatorEvent.DecimalPoint, KeyKind.Number),
                KeySpec("=", CalculatorEvent.Equals, KeyKind.Result)
            )
        )
    }
    Column(modifier = modifier) {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { spec ->
                    CalculatorKey(
                        spec = spec,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onEvent = onEvent
                    )
                }
            }
            if (index < rows.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CalculatorKey(spec: KeySpec, modifier: Modifier, onEvent: (CalculatorEvent) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessMedium),
        label = "keyPressScale"
    )
    val view = LocalView.current
    val colors = MaterialTheme.colorScheme
    val container = when (spec.kind) {
        KeyKind.Result -> colors.primary
        KeyKind.Operator -> colors.primaryContainer
        KeyKind.Utility -> colors.tertiaryContainer
        KeyKind.Number -> colors.secondaryContainer
    }
    val content = when (spec.kind) {
        KeyKind.Result -> colors.onPrimary
        KeyKind.Operator -> colors.onPrimaryContainer
        KeyKind.Utility -> colors.onTertiaryContainer
        KeyKind.Number -> colors.onSecondaryContainer
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
            onEvent(spec.event)
        },
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(22.dp),
        interactionSource = interactionSource,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = container,
            contentColor = content
        ),
        elevation = ButtonDefaults.filledTonalButtonElevation(
            defaultElevation = if (spec.kind == KeyKind.Operator || spec.kind == KeyKind.Result) 1.dp else 0.dp,
            pressedElevation = 0.dp
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = spec.label,
            fontSize = if (spec.label == "AC" || spec.label == "C") 18.sp else if (spec.label == "⌫") 20.sp else 24.sp,
            fontWeight = if (spec.kind == KeyKind.Operator || spec.kind == KeyKind.Result) {
                FontWeight.SemiBold
            } else {
                FontWeight.Medium
            },
            fontFamily = FontFamily.Default,
            maxLines = 1,
            color = content
        )
    }
}
