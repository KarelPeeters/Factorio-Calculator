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
        val solution = prgm.checkedSolution()

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
            ).solve()
        )
    }
}

fun LinearProgram.assertSolution(values: List<Int>, score: Int) = assertSolution(values.map(::Frac), Frac(score))

fun LinearProgram.assertSolution(values: List<Frac>, score: Frac) {
    val solution = this.checkedSolution()
    assertEquals(solution.values, values)
    assertEquals(solution.score, score)
}

fun basicChecks(prgm: LinearProgram, solution: Solution) {
    assertEquals(convolve(prgm.objective.scalars, solution.values), solution.score)
    prgm.constraints.forEach {
        val actual = convolve(it.scalars, solution.values)
        when (it) {
            is LTEConstraint -> assert(actual <= it.value)
            is GTEConstraint -> assert(actual >= it.value)
            is EQConstraint -> assertEquals(actual, solution.score)
        }
    }
}

fun LinearProgram.checkedSolution() = this.solve().also { basicChecks(this, it) }

private infix fun List<Int>.lte(const: Int) = LTEConstraint(this.map(::Frac), Frac(const))
private infix fun List<Int>.gte(const: Int) = GTEConstraint(this.map(::Frac), Frac(const))
private infix fun List<Int>.eq(const: Int) = EQConstraint(this.map(::Frac), Frac(const))

private fun program(objective: List<Int>, vararg constraints: LinearConstraint) = LinearProgram(
    objective = LinearFunc(objective.map(::Frac)),
    constraints = constraints.toList()
)

fun convolve(left: List<Frac>, right: List<Frac>): Frac {
    assert(left.size == right.size)
    return left.zip(right, Frac::times).sum()
}