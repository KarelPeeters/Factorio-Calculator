package math

import math.Frac.Companion.ZERO

class FracMatrix {
    val width: Int
    val height: Int

    private val rows: Array<Array<Frac>>

    constructor(matrix: Array<Array<Frac>>) {
        height = matrix.size
        width = if (height == 0) 0 else matrix[0].size

        rows = Array(height) {
            if (matrix[it].size != width)
                throw IllegalArgumentException("all rows must have the same size")

            matrix[it]
        }

    }

    constructor(width: Int, height: Int) {
        this.width = width
        this.height = height

        rows = Array(height) { Array(width) { ZERO } }
    }

    operator fun get(row: Int) = rows[row]
    operator fun set(row: Int, value: Array<Frac>) {
        if (value.size != width)
            throw IllegalArgumentException("all rows must have the same size")

        rows[row] = value
    }

    operator fun get(row: Int, col: Int) = rows[row][col]
    operator fun set(row: Int, col: Int, value: Frac) {
        rows[row][col] = value
    }

    fun pivot(row: Int, col: Int) {
        this[row] = this[row] / this[row][col]

        //for each other row
        for (r in 0 until height) {
            if (r == row || this[r][col] == ZERO)
                continue

            this[r] = this[r] - (this[row] * this[r][col])
        }
    }

    override fun toString(): String {
        val strings = rows.map { row -> row.map { it.toString() } }
        val size = strings.flatten().map { it.length }.max() ?: 0

        return "[\n" + strings.joinToString(",\n") {
            "[${it.joinToString(",") { leftPad(it, size) }}]"
        } + "\n]"
    }
}

private fun leftPad(str: String, length: Int) =
        if (length > str.length)
            " ".repeat(length - str.length) + str
        else
            str

private operator fun Array<Frac>.plus(other: Array<Frac>) = this.mapIndexed { i, prev -> prev + other[i] }.toTypedArray()
private operator fun Array<Frac>.minus(other: Array<Frac>) = this.mapIndexed { i, prev -> prev - other[i] }.toTypedArray()

private operator fun Array<Frac>.times(other: Frac) = this.map { it * other }.toTypedArray()
private operator fun Array<Frac>.div(other: Frac) = this.map { it / other }.toTypedArray()