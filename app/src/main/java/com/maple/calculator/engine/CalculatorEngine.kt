package com.maple.calculator.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * 计算引擎是纯函数状态机：只依赖当前不可变状态与事件，不接触 Android/Compose。
 * 数值统一使用 BigDecimal，全程不出现 Float/Double。
 */
enum class Operator(val symbol: String) {
    ADD("+"),
    SUBTRACT("−"),
    MULTIPLY("×"),
    DIVIDE("÷")
}

sealed interface CalculatorEvent {
    data class Digit(val value: Char) : CalculatorEvent
    data object DecimalPoint : CalculatorEvent
    data class OperatorPressed(val operator: Operator) : CalculatorEvent
    data object Equals : CalculatorEvent
    data object Clear : CalculatorEvent
    data object Backspace : CalculatorEvent
    data object ToggleSign : CalculatorEvent
    data object Percent : CalculatorEvent
}

/**
 * 单一不可变状态源。entry 只保存输入的数字字符串，带千分位/科学计数法的展示
 * 由 formatNumber 在输出时生成，绝不回写到运算状态。
 */
data class CalculatorState(
    val accumulator: BigDecimal? = null,
    val pendingOperator: Operator? = null,
    val entry: String = "0",
    val isEntering: Boolean = false,
    val justEvaluated: Boolean = false,
    val lastOperator: Operator? = null,
    val lastOperand: BigDecimal? = null,
    val lastExpression: String? = null,
    val error: String? = null
) {
    val display: String get() = error ?: entry

    val history: String
        get() = lastExpression ?: when {
            pendingOperator != null && accumulator != null ->
                "${formatNumber(accumulator)} ${pendingOperator.symbol}" + if (isEntering) " $entry" else ""
            isEntering -> entry
            else -> ""
        }
}

object CalculatorEngine {
    const val MAX_INPUT_DIGITS = 15
    private val MC = MathContext(16, RoundingMode.HALF_UP)
    private const val MAX_ADJUSTED_EXPONENT = 99

    fun reduce(state: CalculatorState, event: CalculatorEvent): CalculatorState = when (event) {
        is CalculatorEvent.Digit -> onDigit(state, event.value)
        CalculatorEvent.DecimalPoint -> onDecimal(state)
        is CalculatorEvent.OperatorPressed -> onOperator(state, event.operator)
        CalculatorEvent.Equals -> onEquals(state)
        CalculatorEvent.Clear -> onClear(state)
        CalculatorEvent.Backspace -> onBackspace(state)
        CalculatorEvent.ToggleSign -> onToggleSign(state)
        CalculatorEvent.Percent -> onPercent(state)
    }

    private fun onDigit(state: CalculatorState, digit: Char): CalculatorState {
        if (digit !in '0'..'9') return state
        if (state.error != null || state.justEvaluated) {
            return CalculatorState(entry = digit.toString(), isEntering = true)
        }
        if (state.isEntering && countDigits(state.entry) >= MAX_INPUT_DIGITS) return state
        val newEntry = when {
            !state.isEntering -> digit.toString()
            state.entry == "0" -> digit.toString()
            state.entry == "-0" -> "-$digit"
            else -> state.entry + digit
        }
        return state.copy(entry = newEntry, isEntering = true, lastExpression = null)
    }

    private fun onDecimal(state: CalculatorState): CalculatorState {
        if (state.error != null || state.justEvaluated) {
            return CalculatorState(entry = "0.", isEntering = true)
        }
        if (!state.isEntering) {
            return state.copy(entry = "0.", isEntering = true, lastExpression = null)
        }
        if (state.entry.contains('.')) return state
        return state.copy(entry = state.entry + ".", lastExpression = null)
    }

    private fun onOperator(state: CalculatorState, operator: Operator): CalculatorState {
        // 错误态下按运算符静默拦截，等待数字键或 AC 自愈。
        if (state.error != null) return state
        if (state.justEvaluated) {
            val result = state.accumulator ?: parseEntry(state.entry)
            return state.copy(
                accumulator = result,
                pendingOperator = operator,
                isEntering = false,
                justEvaluated = false,
                lastExpression = null,
                lastOperator = null,
                lastOperand = null
            )
        }
        val pending = state.pendingOperator
        val accumulator = state.accumulator
        if (pending != null && accumulator != null && state.isEntering) {
            // 链式运算：先结算上一步，再把结果作为新的左操作数。
            val right = parseEntry(state.entry)
            return when (val computed = compute(accumulator, pending, right)) {
                is ComputeResult.Failure -> state.copy(
                    error = computed.message,
                    lastExpression = expressionOf(accumulator, pending, right)
                )
                is ComputeResult.Value -> state.copy(
                    accumulator = computed.value,
                    pendingOperator = operator,
                    entry = formatNumber(computed.value),
                    isEntering = false,
                    lastExpression = expressionOf(accumulator, pending, right)
                )
            }
        }
        if (pending != null && accumulator != null) {
            // 尚未输入右操作数时直接替换运算符。
            return state.copy(pendingOperator = operator, entry = formatNumber(accumulator), lastExpression = null)
        }
        return state.copy(
            accumulator = parseEntry(state.entry),
            pendingOperator = operator,
            isEntering = false,
            lastExpression = null
        )
    }

    private fun onEquals(state: CalculatorState): CalculatorState {
        if (state.error != null) return state
        val pending = state.pendingOperator
        val accumulator = state.accumulator
        if (pending != null && accumulator != null) {
            // 缺省右操作数时复制左操作数，例如 5 + = 得到 10。
            val right = if (state.isEntering) parseEntry(state.entry) else accumulator
            return when (val computed = compute(accumulator, pending, right)) {
                is ComputeResult.Failure -> state.copy(
                    error = computed.message,
                    lastExpression = expressionOf(accumulator, pending, right)
                )
                is ComputeResult.Value -> state.copy(
                    accumulator = computed.value,
                    pendingOperator = null,
                    entry = formatNumber(computed.value),
                    isEntering = false,
                    justEvaluated = true,
                    lastOperator = pending,
                    lastOperand = right,
                    lastExpression = expressionOf(accumulator, pending, right)
                )
            }
        }
        val lastOperator = state.lastOperator
        val lastOperand = state.lastOperand
        if (state.justEvaluated && lastOperator != null && lastOperand != null) {
            val left = state.accumulator ?: parseEntry(state.entry)
            return when (val computed = compute(left, lastOperator, lastOperand)) {
                is ComputeResult.Failure -> state.copy(error = computed.message)
                is ComputeResult.Value -> state.copy(
                    accumulator = computed.value,
                    entry = formatNumber(computed.value),
                    justEvaluated = true,
                    lastExpression = expressionOf(left, lastOperator, lastOperand)
                )
            }
        }
        return state
    }

    private fun onClear(state: CalculatorState): CalculatorState {
        // 正在输入时只清除当前输入（C），其余情况重置整个状态机（AC）。
        if (state.error == null && !state.justEvaluated && state.isEntering) {
            return state.copy(entry = "0", isEntering = false, lastExpression = null)
        }
        return CalculatorState()
    }

    private fun onBackspace(state: CalculatorState): CalculatorState {
        // 结果态与错误态不允许直接改字符；等待右操作数时退格不生效。
        if (state.error != null || state.justEvaluated || !state.isEntering) return state
        val trimmed = state.entry.dropLast(1)
        val newEntry = when {
            trimmed.isEmpty() || trimmed == "-" -> "0"
            else -> trimmed
        }
        return state.copy(entry = newEntry, lastExpression = null)
    }

    private fun onToggleSign(state: CalculatorState): CalculatorState {
        if (state.error != null) return state
        if (state.isEntering) {
            val toggled = if (state.entry.startsWith('-')) state.entry.drop(1) else "-${state.entry}"
            val normalized = when (toggled) {
                "", "-", "-0" -> "0"
                else -> toggled
            }
            return state.copy(entry = normalized, lastExpression = null)
        }
        if (state.justEvaluated) {
            val value = (state.accumulator ?: parseEntry(state.entry)).negate()
            return state.copy(
                accumulator = value,
                entry = formatNumber(value),
                lastExpression = null
            )
        }
        // 等待右操作数时直接开始输入负数。
        return state.copy(entry = "-0", isEntering = true, lastExpression = null)
    }

    private fun onPercent(state: CalculatorState): CalculatorState {
        if (state.error != null) return state
        if (state.isEntering) {
            val value = parseEntry(state.entry).divide(BigDecimal("100"), MC)
            return state.copy(entry = formatNumber(value), lastExpression = null)
        }
        if (state.justEvaluated) {
            val value = (state.accumulator ?: parseEntry(state.entry)).divide(BigDecimal("100"), MC)
            return state.copy(
                accumulator = value,
                entry = formatNumber(value),
                lastExpression = null
            )
        }
        return state
    }

    private sealed interface ComputeResult {
        data class Value(val value: BigDecimal) : ComputeResult
        data class Failure(val message: String) : ComputeResult
    }

    private fun compute(left: BigDecimal, operator: Operator, right: BigDecimal): ComputeResult {
        val raw = when (operator) {
            Operator.ADD -> left.add(right, MC)
            Operator.SUBTRACT -> left.subtract(right, MC)
            Operator.MULTIPLY -> left.multiply(right, MC)
            Operator.DIVIDE -> {
                // 数值判断统一用 compareTo，避免 BigDecimal.equals 的 scale 陷阱。
                if (right.compareTo(BigDecimal.ZERO) == 0) {
                    return ComputeResult.Failure("不能除以 0")
                }
                left.divide(right, MC)
            }
        }
        if (!isInRange(raw)) return ComputeResult.Failure("数值超出范围")
        return ComputeResult.Value(raw)
    }

    private fun isInRange(value: BigDecimal): Boolean {
        if (value.compareTo(BigDecimal.ZERO) == 0) return true
        val adjustedExponent = value.precision() - value.scale() - 1
        return adjustedExponent in -MAX_ADJUSTED_EXPONENT..MAX_ADJUSTED_EXPONENT
    }

    private fun expressionOf(left: BigDecimal, operator: Operator, right: BigDecimal): String =
        "${formatNumber(left)} ${operator.symbol} ${formatNumber(right)} ="

    private fun parseEntry(entry: String): BigDecimal {
        val cleaned = entry.removeSuffix(".").ifEmpty { "0" }
        return cleaned.toBigDecimalOrNull() ?: BigDecimal.ZERO
    }

    private fun countDigits(entry: String): Int = entry.count { it in '0'..'9' }
}

/**
 * 展示层格式化：去尾零、普通范围用 toPlainString、超大/超小值用科学计数法。
 * 只在 UI 渲染和表达式展示时调用，不会把格式化字符串喂回运算。
 */
fun formatNumber(value: BigDecimal): String {
    if (value.compareTo(BigDecimal.ZERO) == 0) return "0"
    val stripped = value.stripTrailingZeros()
    val adjustedExponent = stripped.precision() - stripped.scale() - 1
    return when {
        adjustedExponent >= 15 || adjustedExponent <= -11 -> toScientific(stripped)
        else -> stripped.toPlainString()
    }
}

private fun toScientific(value: BigDecimal): String {
    val rounded = value.round(MathContext(10, RoundingMode.HALF_UP)).stripTrailingZeros()
    val exponent = rounded.precision() - rounded.scale() - 1
    val mantissa = rounded.movePointLeft(exponent).stripTrailingZeros().toPlainString()
    return "${mantissa}E$exponent"
}
