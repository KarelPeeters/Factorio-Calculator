package math

import java.math.BigInteger

/**
 * Immutable representation of a rational number. Guarantees:
 * - gcd(numerator, denominator) == 1
 * - denominator > 0
 */
class Frac : Comparable<Frac> {
    private val n: BigInteger
    private val d: BigInteger

    val numerator get() = n
    val denominator get() = d

    constructor(numerator: BigInteger, denominator: BigInteger) {
        if (denominator == BigInteger.ZERO)
            throw DivideByZeroException()

        val divider = numerator.gcd(denominator)

        this.n = numerator / divider * denominator.signum().toBigInteger()
        this.d = denominator.abs() / divider
    }

    constructor(value: BigInteger) : this(value, BigInteger.ONE)
    constructor(numerator: Int, denominator: Int) : this(numerator.toBigInteger(), denominator.toBigInteger())
    constructor(value: Int) : this(value.toBigInteger(), BigInteger.ONE)

    val signum get() = n.signum()
    val abs get() = Frac(n.abs(), d)

    operator fun unaryPlus() = this
    operator fun unaryMinus() = Frac(-n, d)
    operator fun not() = Frac(d, n)

    operator fun inc() = Frac(n + d, d)
    operator fun dec() = Frac(n - d, d)

    operator fun plus(other: Frac) = Frac(n * other.d + d * other.n, d * other.d)
    operator fun minus(other: Frac) = Frac(n * other.d - d * other.n, d * other.d)
    operator fun times(other: Frac) = Frac(n * other.n, d * other.d)
    operator fun div(other: Frac) = Frac(n * other.d, d * other.n)

    companion object {
        val ZERO = Frac(0)
        val ONE = Frac(1)
    }

    override operator fun compareTo(other: Frac) = (n * other.d).compareTo(d * other.n)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Frac) return false

        return (n == other.n) && (d == other.d)
    }

    override fun hashCode() = n.hashCode() * 31 + d.hashCode()

    override fun toString() = if (d == BigInteger.ONE) "$n" else "$n/$d"

    fun toDouble() = n.toDouble() / d.toDouble()
    fun toBigDecimal() = n.toBigDecimal() / d.toBigDecimal()
}

class DivideByZeroException : Throwable("/0")

//Frac <-> Int

operator fun Frac.plus(other: Int) = this + Frac(other)
operator fun Int.plus(other: Frac) = Frac(this) + other
operator fun Frac.minus(other: Int) = this - Frac(other)
operator fun Int.minus(other: Frac) = Frac(this) - other
operator fun Frac.times(other: Int) = this * Frac(other)
operator fun Int.times(other: Frac) = Frac(this) * other
operator fun Frac.div(other: Int) = this / Frac(other)
operator fun Int.div(other: Frac) = Frac(this) / other
operator fun Frac.compareTo(other: Int) = this.compareTo(Frac(other))
operator fun Int.compareTo(other: Frac) = Frac(this).compareTo(other)

val BigInteger.frac get() = Frac(this)
val Int.frac get() = Frac(this)

//Collections
inline fun <T> Iterable<T>.sumByFrac(selector: (T) -> Frac): Frac {
    var sum = Frac.ZERO
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

fun Iterable<Frac>.sum() = fold(Frac.ZERO, Frac::plus)