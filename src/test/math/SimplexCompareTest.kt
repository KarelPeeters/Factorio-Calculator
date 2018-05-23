package math

import math.Frac.Companion.ZERO
import org.ojalgo.optimisation.ExpressionsBasedModel
import org.ojalgo.optimisation.Optimisation.State.*
import org.ojalgo.optimisation.Variable
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*
import kotlin.test.Test
import kotlin.test.assertFailsWith

val variableCounts = listOf(1, 2, 3, 4, 10)
val constraintCounts = listOf(1, 2, 3, 4, 10)
val iterations = 10000

class SimplexCompareTest {
    val rand = Random(0)
    fun randFrac() = Frac(rand.nextInt(10) - 5)

    @Test
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
                0 -> LTEConstraint(scalars, value)
                1 -> GTEConstraint(scalars, value)
                else -> EQConstraint(scalars, value)
            }
        }

        val prgm = LinearProgram(objective, constraints)
        val model = prgm.toModel()

        try {
            if (prgm.constraints.any { it.scalars.all { it == ZERO } && it.value != ZERO })
                assertFailsWith<ConflictingConstraintsException> { prgm.solve() }
            else {
                val real = model.maximise()
                when (real.state) {
                    OPTIMAL, DISTINCT -> {
                        val solution = prgm.solve()

                        //we can't require that the values match since there may be multiple optimal solutions
                        math.assertEquals(real.value, solution.score.toDouble(), 0.00001)
                    }
                    UNBOUNDED -> {
                        assertFailsWith<UnboundedException> { prgm.solve() }
                    }
                    INFEASIBLE, INVALID -> {
                        assertFailsWith<ConflictingConstraintsException> { prgm.solve() }
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

fun LinearProgram.toModel(): ExpressionsBasedModel {
    val vars = List(varCount) { Variable.make("[$it]").weight(objective.scalars[it].toNumber()).lower(0) }
    val model = ExpressionsBasedModel()

    model.addVariables(vars)
    constraints.forEachIndexed { i, c ->
        val expr = model.addExpression("{$i}")

        when (c) {
            is LTEConstraint -> expr.upper(c.value.toNumber())
            is GTEConstraint -> expr.lower(c.value.toNumber())
            is EQConstraint -> expr.level(c.value.toNumber())
        }
        c.scalars.forEachIndexed { j, v ->
            expr.set(vars[j], v.toDouble())
        }
    }

    return model
}

fun Frac.toBigDecimal(): BigDecimal = BigDecimal(numerator) / BigDecimal(denominator)

fun Frac.toNumber(): Number =
        if (denominator == BigInteger.ONE) numerator
        else BigDecimal(numerator) / BigDecimal(denominator)