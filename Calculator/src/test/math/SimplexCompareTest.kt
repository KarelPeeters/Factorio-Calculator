package math

import math.Frac.Companion.ZERO
import org.junit.Ignore
import org.ojalgo.optimisation.ExpressionsBasedModel
import org.ojalgo.optimisation.Optimisation.State.*
import org.ojalgo.optimisation.Variable
import java.util.*
import kotlin.test.Test
import kotlin.test.assertFailsWith

val variableCounts = listOf(1, 2, 3, 4, 10)
val constraintCounts = listOf(1, 2, 3, 4, 10)
const val iterations = 10000

class SimplexCompareTest {
    val rand = Random(0)
    fun randFrac() = Frac(rand.nextInt(10) - 5)

    @Test
    @Ignore
    fun randomProblemCompareTest() {
        for (variableCount in variableCounts) {
            for (constraintCount in constraintCounts) {
                repeat(iterations) {
                    compareRandomProgram(variableCount, constraintCount)
                }
            }
        }
    }

    fun compareRandomProgram(variableCount: Int, constraintCount: Int) {
        val objective = LinearFunc(List(variableCount) { randFrac() })
        val constraints = List(constraintCount) {
            val scalars = List(variableCount) { randFrac() }
            val value = randFrac()

            when (rand.nextInt(3)) {
                0 -> LTEConstraint(LinearFunc(scalars), value)
                1 -> GTEConstraint(LinearFunc(scalars), value)
                else -> EQConstraint(LinearFunc(scalars), value)
            }
        }

        val prgm = LinearProgram(objective, constraints)
        val model = prgm.toBigDecimalModel()

        try {
            if (prgm.constraints.any { const -> const.lhs.scalars.all { it == ZERO } && const.value != ZERO })
                assertFailsWith<ConflictingConstraintsException> { prgm.solveWithSimplex() }
            else {
                val real = model.maximise()
                when (real.state) {
                    OPTIMAL, DISTINCT -> {
                        val solution = prgm.solveWithSimplex()

                        //we can't require that the values match since there may be multiple optimal solutions
                        assertEquals(real.value, solution.score.toDouble(), 0.00001)
                    }
                    UNBOUNDED -> {
                        assertFailsWith<UnboundedException> { prgm.solveWithSimplex() }
                    }
                    INFEASIBLE, INVALID -> {
                        assertFailsWith<ConflictingConstraintsException> { prgm.solveWithSimplex() }
                    }
                    else -> throw IllegalStateException("unknown state ${real.state}")
                }

            }
        } catch (e: Throwable) {
            println(prgm)
            e.printStackTrace()
        }
    }
}

fun LinearProgram.toBigDecimalModel(): ExpressionsBasedModel {
    val vars = List(varCount) { Variable.make("[$it]").weight(objective.scalars[it].toBigDecimal()).lower(0) }
    val model = ExpressionsBasedModel()

    model.addVariables(vars)
    constraints.forEachIndexed { i, c ->
        val expr = model.addExpression("{$i}")

        when (c) {
            is LTEConstraint -> expr.upper(c.value.toBigDecimal())
            is GTEConstraint -> expr.lower(c.value.toBigDecimal())
            is EQConstraint -> expr.level(c.value.toBigDecimal())
        }
        c.lhs.scalars.forEachIndexed { j, v ->
            expr.set(vars[j], v.toDouble())
        }
    }

    return model
}
