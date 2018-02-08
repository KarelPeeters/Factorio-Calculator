package math

fun main(args: Array<String>) {
    val prgm = LinearProgram(
            LinearFunc(listOf(Frac(1), Frac(0))),
            listOf(
                    LTEConstraint(listOf(Frac(0), Frac(1)), Frac(420))
            )
    )

    println(prgm.solve())
}