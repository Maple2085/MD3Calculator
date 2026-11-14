package com.maple.calculator.engine

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorEngineTest {

    private fun run(vararg events: CalculatorEvent): CalculatorState =
        events.fold(CalculatorState()) { state, event -> CalculatorEngine.reduce(state, event) }

    private fun digits(text: String): Array<CalculatorEvent> =
        text.map { CalculatorEvent.Digit(it) }.toTypedArray()

    private fun op(value: Operator): CalculatorEvent = CalculatorEvent.OperatorPressed(value)

    @Test
    fun `basic multiplication`() {
        val state = run(*digits("5"), op(Operator.MULTIPLY), *digits("2"), CalculatorEvent.Equals)
        assertEquals("10", state.entry)
        assertEquals("5 × 2 =", state.history)
        assertNull(state.error)
    }

    @Test
    fun `decimal addition keeps exact scale`() {
        val state = run(
            *digits("0"), CalculatorEvent.DecimalPoint, *digits("1"),
            op(Operator.ADD),
            *digits("0"), CalculatorEvent.DecimalPoint, *digits("2"),
            CalculatorEvent.Equals
        )
        assertEquals("0.3", state.entry)
    }

    @Test
    fun `division by zero enters error state`() {
        val state = run(*digits("5"), op(Operator.DIVIDE), *digits("0"), CalculatorEvent.Equals)
        assertEquals("不能除以 0", state.error)
        assertEquals("不能除以 0", state.display)
    }

    @Test
    fun `error state heals on digit input`() {
        val state = run(
            *digits("5"), op(Operator.DIVIDE), *digits("0"), CalculatorEvent.Equals,
            *digits("7")
        )
        assertNull(state.error)
        assertEquals("7", state.entry)
    }

    @Test
    fun `chained operator evaluates previous step`() {
        val state = run(*digits("2"), op(Operator.ADD), *digits("3"), op(Operator.SUBTRACT))
        assertEquals("5", state.entry)
        assertEquals(Operator.SUBTRACT, state.pendingOperator)
        assertEquals("2 + 3 =", state.history)
    }

    @Test
    fun `repeated equals repeats last operation`() {
        val first = run(*digits("2"), op(Operator.ADD), *digits("3"), CalculatorEvent.Equals)
        val second = CalculatorEngine.reduce(first, CalculatorEvent.Equals)
        val third = CalculatorEngine.reduce(second, CalculatorEvent.Equals)
        assertEquals("5", first.entry)
        assertEquals("8", second.entry)
        assertEquals("11", third.entry)
    }

    @Test
    fun `equals without right operand copies left operand`() {
        val state = run(*digits("5"), op(Operator.ADD), CalculatorEvent.Equals)
        assertEquals("10", state.entry)
    }

    @Test
    fun `operator replacement before right operand`() {
        val state = run(*digits("5"), op(Operator.ADD), op(Operator.MULTIPLY))
        assertEquals(Operator.MULTIPLY, state.pendingOperator)
        assertEquals("5", state.entry)
    }

    @Test
    fun `negative entry allowed while waiting for operand`() {
        val state = run(
            *digits("5"), op(Operator.ADD),
            CalculatorEvent.ToggleSign, *digits("3"),
            CalculatorEvent.Equals
        )
        assertEquals("2", state.entry)
    }

    @Test
    fun `toggle sign on zero never leaves negative zero`() {
        val state = run(*digits("0"), CalculatorEvent.ToggleSign)
        assertEquals("0", state.entry)
    }

    @Test
    fun `input is limited to fifteen significant digits`() {
        val state = run(*digits("1234567890123456789"))
        assertEquals("123456789012345", state.entry)
    }

    @Test
    fun `leading zeros collapse`() {
        val state = run(*digits("0005"))
        assertEquals("5", state.entry)
    }

    @Test
    fun `repeated decimal point is ignored`() {
        val state = run(
            *digits("1"), CalculatorEvent.DecimalPoint,
            CalculatorEvent.DecimalPoint, *digits("5")
        )
        assertEquals("1.5", state.entry)
    }

    @Test
    fun `decimal point starts new operand as zero dot`() {
        val state = run(*digits("5"), op(Operator.ADD), CalculatorEvent.DecimalPoint)
        assertEquals("0.", state.entry)
    }

    @Test
    fun `backspace removes last digit`() {
        val state = run(*digits("123"), CalculatorEvent.Backspace)
        assertEquals("12", state.entry)
    }

    @Test
    fun `backspace reduces single digit to zero`() {
        val state = run(*digits("5"), CalculatorEvent.Backspace)
        assertEquals("0", state.entry)
    }

    @Test
    fun `backspace cannot edit final result`() {
        val state = run(*digits("2"), op(Operator.ADD), *digits("3"), CalculatorEvent.Equals, CalculatorEvent.Backspace)
        assertEquals("5", state.entry)
        assertTrue(state.justEvaluated)
    }

    @Test
    fun `clear while entering only clears entry`() {
        val state = run(*digits("5"), op(Operator.ADD), *digits("3"), CalculatorEvent.Clear)
        assertEquals("0", state.entry)
        assertEquals(Operator.ADD, state.pendingOperator)
        assertEquals(BigDecimal("5"), state.accumulator)
    }

    @Test
    fun `clear after entry cleared resets everything`() {
        val state = run(
            *digits("5"), op(Operator.ADD), *digits("3"),
            CalculatorEvent.Clear, CalculatorEvent.Clear
        )
        assertEquals(CalculatorState(), state)
    }

    @Test
    fun `percent converts entry to hundredths`() {
        val state = run(*digits("50"), CalculatorEvent.Percent)
        assertEquals("0.5", state.entry)
    }

    @Test
    fun `result continues as left operand for new operator`() {
        val state = run(
            *digits("2"), op(Operator.ADD), *digits("3"), CalculatorEvent.Equals,
            op(Operator.MULTIPLY), *digits("4"), CalculatorEvent.Equals
        )
        assertEquals("20", state.entry)
    }

    @Test
    fun `digit after result starts new calculation`() {
        val state = run(
            *digits("2"), op(Operator.ADD), *digits("3"), CalculatorEvent.Equals,
            *digits("7")
        )
        assertEquals("7", state.entry)
        assertNull(state.accumulator)
        assertEquals("7", state.history)
    }

    @Test
    fun `third of one divides with bounded precision`() {
        val state = run(*digits("1"), op(Operator.DIVIDE), *digits("3"), CalculatorEvent.Equals)
        assertEquals("0.3333333333333333", state.entry)
    }

    @Test
    fun `trailing zeros are stripped from results`() {
        val state = run(
            *digits("2"), CalculatorEvent.DecimalPoint, *digits("00"),
            op(Operator.ADD),
            *digits("3"), CalculatorEvent.DecimalPoint, *digits("00"),
            CalculatorEvent.Equals
        )
        assertEquals("5", state.entry)
    }

    @Test
    fun `large result uses scientific notation`() {
        val state = run(
            *digits("10000000"), op(Operator.MULTIPLY), *digits("100000000"),
            CalculatorEvent.Equals
        )
        assertEquals("1E15", state.entry)
    }

    @Test
    fun `formatNumber handles thresholds and trailing zeros`() {
        assertEquals("5", formatNumber(BigDecimal("5.00")))
        assertEquals("0", formatNumber(BigDecimal("-0.00")))
        assertEquals("999999999999999", formatNumber(BigDecimal("999999999999999")))
        assertEquals("1E15", formatNumber(BigDecimal("1000000000000000")))
        assertEquals("0.0000000001", formatNumber(BigDecimal("0.0000000001")))
        assertEquals("1E-11", formatNumber(BigDecimal("0.00000000001")))
    }

    @Test
    fun `out of range result reports error`() {
        fun apply(state: CalculatorState, events: List<CalculatorEvent>): CalculatorState =
            events.fold(state) { current, event -> CalculatorEngine.reduce(current, event) }

        val multiply = listOf(op(Operator.MULTIPLY)) + digits("999999999999999").toList() +
            CalculatorEvent.Equals
        var state = apply(CalculatorState(), digits("999999999999999").toList() + multiply)
        var guard = 0
        while (state.error == null && guard < 20) {
            state = apply(state, multiply)
            guard++
        }
        assertEquals("数值超出范围", state.error)
    }
}
