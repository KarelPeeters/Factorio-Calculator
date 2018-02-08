package math

import math.Frac.Companion.ZERO

fun LinearProgram.solve(): List<Frac> = Simplex(this).solve()

/**
 * An implementation of the two-phase-simplex algorithm.
 */
private class Simplex(val prgm: LinearProgram) {
    val varCount: Int = prgm.varCount
    var slackCount: Int = -1
    var extraCount: Int = -1

    val extraCol get() = varCount + slackCount
    val valueCol get() = tab.width - 1
    val funcRow get() = tab.height - 1

    lateinit var tab: FracMatrix
    lateinit var basicVars: Array<Int>

    fun solve(): List<Frac> {
        initPhase1()
        optimize(true)
        initPhase2()
        optimize()
        return extractSolutions()
    }

    private fun initPhase1() {
        slackCount = 0
        extraCount = 0

        prgm.constraints.forEach {
            val signs = getSigns(it)
            if (signs.hasSlack) slackCount++
            if (signs.hasExtra) extraCount++
        }

        tab = FracMatrix(
                width = varCount + slackCount + extraCount + 1,
                height = slackCount + 1
        )
        basicVars = Array(slackCount) { -1 }

        var slackIndex = varCount
        var extraIndex = varCount + slackCount

        prgm.constraints.forEachIndexed { row, constraint ->
            val signs = getSigns(constraint)

            //set scalars and value
            constraint.scalars.forEachIndexed { col, scalar ->
                tab[row][col] = scalar * signs.scalars
            }
            tab[row][valueCol] = constraint.value

            //set basicVars
            if (signs.hasExtra)
                basicVars[row] = extraIndex
            else    //has slack
                basicVars[row] = slackIndex

            //set slack and extra
            if (signs.hasSlack) tab[row][slackIndex++] = Frac(signs.slack)
            if (signs.hasExtra) tab[row][extraIndex++] = Frac(signs.extra)

            //set objective to minimize extra
            if (signs.hasExtra) {
                for (col in (0 until extraCol) + valueCol) {
                    tab[funcRow][col] -= tab[row][col]
                }
            }
        }
    }

    private fun initPhase2() {
        if (tab[funcRow][valueCol] != ZERO)
            throw ConflictingConstraintsException()

        val old = tab
        tab = FracMatrix(
                width = old.width - extraCount,
                height = old.height
        )

        //copy old tab
        for (row in 0 until funcRow) {
            //copy scalars
            for (col in 0 until valueCol) {
                tab[row][col] = old[row][col]
            }

            //copy value
            tab[row][valueCol] = old[row][old.width - 1]
        }

        //calc objective
        prgm.objective.scalars.forEachIndexed { col, scalar ->
            val row = basicVars.indexOf(col)

            if (row == -1) {
                tab[funcRow][col] -= scalar
            } else {
                //scalars
                for (c in 0 until valueCol) {
                    if (c == col)
                        continue
                    tab[funcRow][c] += tab[row][c] * scalar
                }
                //value
                tab[funcRow][valueCol] += tab[row][valueCol] * scalar
            }
        }
    }

    private fun optimize(stopWhenFuncZero: Boolean = false) {
        while (true) {
            if (stopWhenFuncZero && tab[funcRow][valueCol] == ZERO)
                break

            val col = pickColumn() ?: break
            val row = pickRow(col) ?: throw UnboundedException()

            tab.pivot(row, col)
            basicVars[row] = col
        }
    }

    private fun extractSolutions(): List<Frac> {
        return List(varCount) { col ->
            val row = basicVars.indexOf(col)
            if (row == -1)
                ZERO
            else
                tab[row][valueCol]
        }
    }

    private fun pickColumn(): Int? {
        return (0 until valueCol).asSequence().map { c ->
            c to tab[funcRow][c]
        }.minBy { (_, value) ->
            value
        }?.takeIf { (_, value) ->
            value < 0
        }?.first
    }

    private fun pickRow(col: Int): Int? {
        return (0 until funcRow).asSequence().filter { r ->
            tab[r][col] > 0
        }.map { r ->
            val value = tab[r][col]
            r to if (value == Frac.ZERO) Frac.ZERO else tab[r].last() / value
        }.filter {
            it.second >= 0
        }.minBy {
            it.second
        }?.first
    }

}

private data class VarSigns(
        val scalars: Int,
        val slack: Int,
        val extra: Int
) {
    val hasSlack = slack != 0
    val hasExtra = extra != 0
}

private val signList = listOf(
        //x=b
        VarSigns(1, 0, 1),
        //x=0
        VarSigns(-1, 0, 1),
        //x=-b
        VarSigns(-1, 0, 1),
        //x<=b
        VarSigns(1, 1, 0),
        //x<=0
        VarSigns(1, 1, 0),
        //x<=-b
        VarSigns(-1, -1, 1),
        //x>=b
        VarSigns(1, -1, 1),
        //x>=0
        VarSigns(-1, 1, 0),
        //x>=-b
        VarSigns(-1, 1, 0)
)

private fun getSigns(constraint: LinearConstraint): VarSigns {
    val typeIndex = when (constraint) {
        is EQConstraint -> 0
        is LTEConstraint -> 3
        is GTEConstraint -> 6
    }
    val valueSignIndex = -constraint.value.sign + 1
    return signList[typeIndex + valueSignIndex]
}

sealed class UnsolvableException : Exception()
class ConflictingConstraintsException : UnsolvableException()
class UnboundedException : UnsolvableException()