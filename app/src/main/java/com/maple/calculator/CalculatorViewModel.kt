package com.maple.calculator

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.maple.calculator.engine.CalculatorEngine
import com.maple.calculator.engine.CalculatorEvent
import com.maple.calculator.engine.CalculatorState
import com.maple.calculator.engine.Operator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 运行态由 ViewModel 接管；SavedStateHandle 负责进程被回收后的恢复。
 * 事件处理保持同步，状态通过单一 StateFlow 发布。
 */
class CalculatorViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    private val _uiState = MutableStateFlow(restore())
    val uiState: StateFlow<CalculatorState> = _uiState.asStateFlow()

    fun onEvent(event: CalculatorEvent) {
        val next = CalculatorEngine.reduce(_uiState.value, event)
        if (next == _uiState.value) return
        _uiState.value = next
        persist(next)
    }

    private fun restore(): CalculatorState = CalculatorState(
        accumulator = savedStateHandle.get<String>(KEY_ACCUMULATOR)?.toBigDecimalOrNull(),
        pendingOperator = savedStateHandle.get<String>(KEY_PENDING)?.toOperator(),
        entry = savedStateHandle.get<String>(KEY_ENTRY) ?: "0",
        isEntering = savedStateHandle.get<Boolean>(KEY_ENTERING) ?: false,
        justEvaluated = savedStateHandle.get<Boolean>(KEY_JUST_EVALUATED) ?: false,
        lastOperator = savedStateHandle.get<String>(KEY_LAST_OPERATOR)?.toOperator(),
        lastOperand = savedStateHandle.get<String>(KEY_LAST_OPERAND)?.toBigDecimalOrNull(),
        lastExpression = savedStateHandle.get<String>(KEY_LAST_EXPRESSION),
        error = savedStateHandle.get<String>(KEY_ERROR)
    )

    private fun persist(state: CalculatorState) {
        savedStateHandle[KEY_ACCUMULATOR] = state.accumulator?.toPlainString()
        savedStateHandle[KEY_PENDING] = state.pendingOperator?.name
        savedStateHandle[KEY_ENTRY] = state.entry
        savedStateHandle[KEY_ENTERING] = state.isEntering
        savedStateHandle[KEY_JUST_EVALUATED] = state.justEvaluated
        savedStateHandle[KEY_LAST_OPERATOR] = state.lastOperator?.name
        savedStateHandle[KEY_LAST_OPERAND] = state.lastOperand?.toPlainString()
        savedStateHandle[KEY_LAST_EXPRESSION] = state.lastExpression
        savedStateHandle[KEY_ERROR] = state.error
    }

    private fun String.toOperator(): Operator? =
        runCatching { Operator.valueOf(this) }.getOrNull()

    private companion object {
        const val KEY_ACCUMULATOR = "accumulator"
        const val KEY_PENDING = "pendingOperator"
        const val KEY_ENTRY = "entry"
        const val KEY_ENTERING = "isEntering"
        const val KEY_JUST_EVALUATED = "justEvaluated"
        const val KEY_LAST_OPERATOR = "lastOperator"
        const val KEY_LAST_OPERAND = "lastOperand"
        const val KEY_LAST_EXPRESSION = "lastExpression"
        const val KEY_ERROR = "error"
    }
}
