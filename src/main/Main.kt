import factorio.FactoryDSL.Time.SECOND
import factorio.dataFromJson
import factorio.factory
import factorio.times

fun main(args: Array<String>) {
    val data = dataFromJson(::main.javaClass.getResourceAsStream("factorio/export.json").reader(Charsets.UTF_8))

    factory(data) {
        produceEvery(SECOND) {
            stack("electronic-circuit", 1)
        }

        given {
            resources()
            water()
        }

        minimize {
            resourceUsage()
        }

        assembler {
            fastest()

            modules = fillWith(module("productivity-module-3"))
            beacons = matchAssembler {
                2 * module("speed-module-3")
            }
        }

        render {
            table { recipe, assembler, effect, count ->
                listOf(recipe.name, assembler.name, )
            }
        }
    }
}