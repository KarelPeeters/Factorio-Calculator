package math

/**
 * @param objective The objective function to **maximize**
 * @param constraints The constraints
 */
class LinearProgram(val objective: LinearFunc, val constraints: List<LinearConstraint>) {
    val varCount = objective.scalars.size

    init {
        constraints.forEach {
            if (it.lhs.scalars.size != varCount)
                throw IllegalArgumentException("all constraints should have the same varCount as objective")
        }
    }

    fun checkIsSolution(solution: Solution) {
        for (const in this.constraints) {
            require(const.evaluate(solution)) { "Solution $solution does not solve constraint $const" }
        }

        val actualScore = objective.evaluate(solution)
        require(actualScore == solution.score) {
            "Solution $solution has wrong score ${solution.score}, should be $actualScore"
        }
    }

    override fun toString(): String {
        return "LinearProgram(\n\tmax $objective\n\twhere\t${
            constraints.joinToString("\n\t     \t")
        }\n)"
    }
}

data class Solution(val values: List<Frac>, val score: Frac)

class LinearFunc(val scalars: List<Frac>) {
    override fun toString() = scalars.toLinString()

    fun evaluate(solution: Solution): Frac {
        require((this.scalars.size == solution.values.size)) { "Wrong number of variables in solution" }
        return scalars.zip(solution.values)
            .sumByFrac { (scalar, value) -> scalar * value }
    }
}

sealed class LinearConstraint(val lhs: LinearFunc, val value: Frac) {
    abstract fun evaluate(solution: Solution): Boolean
}

class GTEConstraint(lhs: LinearFunc, value: Frac) : LinearConstraint(lhs, value) {
    override fun evaluate(solution: Solution): Boolean {
        return this.lhs.evaluate(solution) >= value
    }

    override fun toString() = "$lhs >= $value"
}

class LTEConstraint(lhs: LinearFunc, value: Frac) : LinearConstraint(lhs, value) {
    override fun evaluate(solution: Solution): Boolean {
        return lhs.evaluate(solution) <= value
    }

    override fun toString() = "$lhs <= $value"
}

class EQConstraint(lhs: LinearFunc, value: Frac) : LinearConstraint(lhs, value) {
    override fun evaluate(solution: Solution): Boolean {
        return lhs.evaluate(solution) == value
    }

    override fun toString() = "$lhs == $value"
}

private fun List<Frac>.toLinString() = this
    .mapIndexed { index, frac -> "$frac [$index]" }
    .joinToString(" + ")
    .replace("+ -", "- ")