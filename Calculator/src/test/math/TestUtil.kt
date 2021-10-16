package math

import kotlin.math.abs

fun assertEquals(expected: Double, actual: Double, delta: Double, message: String? = null) {
    if (abs(expected - actual) > delta)
        kotlin.test.assertEquals(expected, actual, message)
}