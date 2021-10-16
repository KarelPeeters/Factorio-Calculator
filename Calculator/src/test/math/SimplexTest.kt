package math

import math.Frac.Companion.ONE
import math.Frac.Companion.ZERO
import kotlin.test.Test
import kotlin.test.assertEquals

class SimplexTest {
    @Test
    fun objectiveIsConstraint() {
        val prgm = program(
            listOf(1, 1),
            listOf(1, 1) lte 1
        )
        val solution = prgm.solveWithSimplex()

        assertEquals(ONE, solution.score)
        assert((solution.values == listOf(ONE, ZERO)) or (solution.values == listOf(ZERO, ONE)))
    }

    @Test
    fun uniqueSolution() {
        program(
            listOf(-2, -2),
            listOf(3, 0) gte 2,
            listOf(1, 2) gte -5,
            listOf(3, 1) lte 2
        ).assertSolution(listOf(Frac(2, 3), ZERO), Frac(-4, 3))
    }

    @Test
    fun cancelObjective() {
        println(
            program(
                listOf(-1, 1),
                listOf(1, -1) gte 0
            ).solveWithSimplex()
        )
    }
}

fun LinearProgram.assertSolution(values: List<Int>, score: Int) = assertSolution(values.map(::Frac), Frac(score))

fun LinearProgram.assertSolution(values: List<Frac>, score: Frac) {
    val solution = this.solveWithSimplex()
    assertEquals(solution.values, values)
    assertEquals(solution.score, score)
}

private infix fun List<Int>.lte(const: Int) = LTEConstraint(LinearFunc(this.map(::Frac)), Frac(const))
private infix fun List<Int>.gte(const: Int) = GTEConstraint(LinearFunc(this.map(::Frac)), Frac(const))
private infix fun List<Int>.eq(const: Int) = EQConstraint(LinearFunc(this.map(::Frac)), Frac(const))

private fun program(objective: List<Int>, vararg constraints: LinearConstraint) = LinearProgram(
    objective = LinearFunc(objective.map(::Frac)),
    constraints = constraints.toList()
)
