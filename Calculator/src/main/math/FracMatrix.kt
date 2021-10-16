package math

import math.Frac.Companion.ONE
import math.Frac.Companion.ZERO
import kotlin.math.max

class FracMatrix {
    val width: Int
    var height: Int

    private val rows: MutableList<MutableList<Frac>>

    constructor(rows: List<List<Frac>>) {
        height = rows.size
        width = if (height == 0) 0 else rows[0].size

        this.rows = MutableList(height) {
            if (rows[it].size != width)
                throw IllegalArgumentException("all rows must have the same width $width")

            rows[it].toMutableList()
        }
    }

    constructor(width: Int, height: Int) {
        this.width = width
        this.height = height

        rows = MutableList(height) { MutableList(width) { ZERO } }
    }

    operator fun get(row: Int): List<Frac> = rows[row]
    operator fun set(row: Int, value: List<Frac>) {
        if (value.size != width)
            throw IllegalArgumentException("all rows must have the same size")

        rows[row] = value.toMutableList()
    }

    operator fun get(row: Int, col: Int) = rows[row][col]
    operator fun set(row: Int, col: Int, value: Frac) {
        rows[row][col] = value
    }

    fun col(col: Int) = List(height) { get(it, col) }

    fun pivot(row: Int, col: Int) {
        this[row] = this[row] / this[row][col]

        //for each other row
        for (r in 0 until height) {
            if (r == row || this[r][col] == ZERO)
                continue

            addToRow(r, this[row], -this[r][col])
        }
    }

    fun addToRow(row: Int, add: List<Frac>, factor: Frac = ONE) {
        if (add.size != width)
            throw IllegalArgumentException("add list should have width $width instead of ${add.size}")

        for (c in 0 until width) {
            this[row, c] += factor * add[c]
        }
    }

    override fun toString(): String {
        val strings = rows.map { row -> row.map { it.toString() } }

        val sizes =
            rows.foldIndexed(List(width) { 0 }) { i, a, _ -> a.zip(strings[i]).map { (x, y) -> max(x, y.length) } }

        return "[\n" + strings.joinToString(",\n") {
            "[${it.withIndex().joinToString(",") { (i, s) -> leftPad(s, max(2, sizes[i])) }}]"
        } + "\n]"
    }

    fun removeRow(row: Int) {
        rows.removeAt(row)
        height -= 1
    }
}

private fun leftPad(str: String, length: Int) =
    if (length > str.length)
        " ".repeat(length - str.length) + str
    else
        str

private operator fun List<Frac>.plus(other: List<Frac>) = this.zip(other, Frac::times)
private operator fun List<Frac>.minus(other: List<Frac>) = this.zip(other, Frac::minus)

private operator fun List<Frac>.times(other: Frac) = this.map { it * other }
private operator fun List<Frac>.div(other: Frac) = this.map { it / other }