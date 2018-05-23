package math

fun assertEquals(expected: Double, actual: Double, delta: Double, message: String? = null) {
    if (Math.abs(expected - actual) > delta)
        kotlin.test.assertEquals(expected, actual, message)
}