package math

class Frac : Comparable<Frac> {
    private val n: Long
    private val d: Long

    val numerator: Long   //gcd(numerator, denominator) == 1
    val denominator: Long //denominator > 0

    constructor(numerator: Long, denominator: Long) {
        if (denominator == 0L)
            throw DivideByZeroException()

        val divider = gcd(numerator, denominator)

        this.n = numerator / divider * denominator.sign
        this.d = denominator.abs / divider

        this.numerator = n
        this.denominator = d
    }

    constructor(numerator: Int, denominator: Int) : this(numerator.toLong(), denominator.toLong())

    constructor(value: Long) : this(value, 1L)

    constructor(value: Int) : this(value.toLong(), 1L)

    val sign get() = n.sign

    operator fun unaryPlus() = this
    operator fun unaryMinus() = Frac(-n, d)
    operator fun not() = Frac(d, n)

    operator fun inc() = Frac(n + d, d)
    operator fun dec() = Frac(n - d, d)

    operator fun plus(other: Frac) = Frac(n * other.d + d * other.n, d * other.d)
    operator fun minus(other: Frac) = Frac(n * other.d - d * other.n, d * other.d)
    operator fun times(other: Frac) = if (other.n == 0L || this.n == 0L) ZERO else Frac(n * other.n, d * other.d)
    operator fun div(other: Frac) = Frac(n * other.d, d * other.n)

    override operator fun compareTo(other: Frac) = (n * other.d).compareTo(d * other.n)

    companion object {
        val ZERO = Frac(0)
        val ONE = Frac(1)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Frac) return false

        return (n == other.n) && (d == other.d)
    }

    override fun hashCode(): Int {
        var result = n.hashCode()
        result = 31 * result + d.hashCode()
        return result
    }

    override fun toString() = if (d == 1L) "$n" else "$n/$d"
    fun toDouble() = n.toDouble() / d.toDouble()
}

class DivideByZeroException : Throwable("/0")

private fun gcd(a: Long, b: Long): Long {
    var x = a.abs
    var y = b.abs

    while (x != 0L) {
        val tmp = x
        x = y % x
        y = tmp
    }

    return y
}

private inline val Long.abs
    get() = if (this >= 0) this else -this

private inline val Long.sign
    get() = when {
        this > 0L -> 1
        this == 0L -> 0
        else -> -1
    }

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

//Collections
inline fun <T> Iterable<T>.sumByFrac(selector: (T) -> Frac): Frac {
    var sum = Frac.ZERO
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

inline fun Iterable<Frac>.sum() = fold(Frac.ZERO, Frac::plus)