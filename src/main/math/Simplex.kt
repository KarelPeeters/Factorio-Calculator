package math

import math.Frac.Companion.ZERO

fun LinearProgram.solve() = Simplex(this).solve()

private class Simplex(val prgm: LinearProgram) {
    val tab: FracMatrix
    val basics: Array<Int>

    val varCount: Int = prgm.varCount
    val slackCount: Int
    val artificialCount: Int

    val constCol get() = tab.width - 1
    val objectiveRow get() = tab.height - 1

    init {
        val signs = prgm.constraints.map(::constraintSigns)
        slackCount = signs.count(VarSign::hasS)
        artificialCount = signs.count(VarSign::hasA)

        tab = FracMatrix(
                width = varCount + slackCount + artificialCount + 1,
                height = prgm.constraints.size + 1
        )
        basics = Array(prgm.constraints.size) { -1 }

        var slackIndex = varCount
        var artificialIndex = varCount + slackCount

        //initialize constraints
        prgm.constraints.zip(signs).forEachIndexed { row, (constraint, sign) ->
            //constraint itself
            constraint.scalars.forEachIndexed { col, scalar ->
                tab[row, col] = sign.x * scalar
            }
            tab[row, constCol] = constraint.value

            //slack and artificial + pricing out phase 1 objective
            basics[row] = if (sign.hasA) artificialIndex else slackIndex
            if (sign.hasS) tab[row, slackIndex++] = sign.s.frac
            if (sign.hasA) {
                tab[objectiveRow, artificialIndex] -= sign.a.frac
                tab[row, artificialIndex++] = sign.a.frac
                tab.addToRow(objectiveRow, tab[row])
            }
        }
    }

    fun initPhase2() {
        prgm.objective.scalars.forEachIndexed { col, scalar ->
            tab[objectiveRow, col] = scalar
        }
    }

    fun solve(): List<Frac> {
        optimize(dropLastCols = 0)
        if (tab[objectiveRow, constCol] != Frac.ZERO)
            throw ConflictingConstraintsException()

        initPhase2()
        optimize(dropLastCols = artificialCount)

        return readSolution()
    }

    fun optimize(dropLastCols: Int) {
        while (true) {
            val pivot = pickPivot(objectiveRow, dropLastCols) ?: break
            pivot(pivot)
        }
    }

    fun pivot(pivot: Pair<Int, Int>) {
        val (row, col) = pivot

        tab.pivot(row, col)
        basics[row] = col
    }

    fun pickPivot(objectiveRow: Int, dropLastCols: Int): Pair<Int, Int>? {
        val col = pickCol(objectiveRow, dropLastCols) ?: return null
        val row = pickRow(col) ?: throw UnboundedException(col)

        return row to col
    }

    fun pickCol(objectiveRow: Int, dropLastCols: Int) = tab[objectiveRow].dropLast(1 + dropLastCols).withIndex()
            .find { it.value > 0 }?.index

    fun pickRow(col: Int) = tab.col(col).dropLast(1).withIndex()
            .filter { it.value > 0 }
            .minBy { tab[it.index, constCol] / tab[it.index, col] }?.index

    fun readSolution(): List<Frac> {
        val solution = MutableList(varCount) { ZERO }
        basics.forEachIndexed { row, col ->
            if (col < varCount)
                solution[col] = tab[row, constCol] / tab[row, col]
        }
        return solution
    }
}

class ConflictingConstraintsException : Exception()
class UnboundedException(variable: Int) : Exception("[$variable]")

private data class VarSign(val x: Int, val s: Int, val a: Int) {
    val hasS = s != 0
    val hasA = a != 0
}

private fun constraintSigns(constraint: LinearConstraint): VarSign {
    val b = constraint.value.signum
    val x = if (b >= 0) 1 else -1

    return if ((constraint is GTEConstraint && b >= 0) || (constraint is LTEConstraint && b < 0)) {
        //x >= b
        VarSign(x = x, s = -1, a = 1)
    } else if ((constraint is LTEConstraint && b >= 0) || (constraint is GTEConstraint && b < 0)) {
        //x <= b
        VarSign(x = x, s = 1, a = 0)
    } else {
        //x == b
        VarSign(x = x, s = 0, a = 1)
    }
}