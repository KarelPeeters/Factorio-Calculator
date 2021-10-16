package math

/**
 * @param objective The objective function to **maximize**
 * @param constraints The constraints
 */
class LinearProgram(val objective: LinearFunc, val constraints: List<LinearConstraint>) {
    val varCount = objective.scalars.size

    init {
        constraints.forEach {
            if (it.scalars.size != varCount)
                throw IllegalArgumentException("all constraints should have the same varCount as objective")
        }
    }

    override fun toString(): String {
        return "LinearProgram(\n\tmax $objective\n\twhere\t${
            constraints.joinToString("\n\t     \t")
        }\n)"
    }
}

class LinearFunc(val scalars: List<Frac>) {
    override fun toString() = scalars.toLinString()
}

sealed class LinearConstraint(val scalars: List<Frac>, val value: Frac)

class GTEConstraint(scalars: List<Frac>, value: Frac) : LinearConstraint(scalars, value) {
    override fun toString() = "${scalars.toLinString()} >= $value"
}

class LTEConstraint(scalars: List<Frac>, value: Frac) : LinearConstraint(scalars, value) {
    override fun toString() = "${scalars.toLinString()} <= $value"
}

class EQConstraint(scalars: List<Frac>, value: Frac) : LinearConstraint(scalars, value) {
    override fun toString() = "${scalars.toLinString()} == $value"
}

private fun List<Frac>.toLinString() = this
    .mapIndexed { index, frac -> "$frac[$index]" }
    .joinToString(" + ")
    .replace("+ -", "- ")