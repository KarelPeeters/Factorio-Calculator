package factorio

import math.*
import math.Frac.Companion.ONE
import math.Frac.Companion.ZERO
import java.util.*
import kotlin.math.max

typealias RecipeCostFunc = (Recipe) -> Frac
typealias Modules = Map<Module, Int>
typealias ModulesPicker = (Recipe, Assembler) -> Modules?

private val BEACON_EFFICIENCY = Frac(1, 2)

data class ModuleLayout(val modules: Modules, val beacons: Modules) {
    val effect = modules.effect() + BEACON_EFFICIENCY * beacons.effect()
    fun totalEffect(name: String) = ONE + effect[name]

    private fun Modules.effect() = entries.map { (m, c) -> c * m.effect }.fold(Effect(emptyMap()), Effect::plus)
}

class FactoryDSL(val data: GameData) {
    private val ores = data.resources.flatMap { it.products.map { it.item } }

    val production = mutableMapOf<Item, Frac>()
    val givenItems = mutableSetOf<Item>()
    var objectiveMinimizeWeight: RecipeCostFunc = { Frac.ZERO }
    val itemBlackList = mutableListOf<Item>()
    val recipeBlackList = mutableListOf<Recipe>()

    private val assemblerPickers = mutableListOf<(Recipe, Set<Assembler>) -> Assembler?>()
    private val modulePickers = mutableListOf<ModulesPicker>()
    private val beaconPickers = mutableListOf<ModulesPicker>()

    fun item(name: String) = data.findItem(name)
    fun recipe(name: String) = data.findRecipe(name)
    fun module(name: String) = data.findModule(name)

    enum class Time(val seconds: Int) {
        SECOND(1),
        MINUTE(60),
        HOUR(3600)
    }

    inner class ProduceDSL(val time: Time) {
        fun stack(name: String, amount: Int) = stack(name, amount.frac)
        fun stack(name: String, amount: Frac) {
            val item = data.findItem(name)
            production[item] = (production[item] ?: Frac.ZERO) + amount / time.seconds
        }
    }

    fun produceEvery(time: Time, block: ProduceDSL.() -> Unit) = ProduceDSL(time).block()

    inner class GivenDSL {
        fun item(name: String) {
            givenItems += data.findItem(name)
        }

        fun resources() {
            givenItems.addAll(ores)
        }

        fun water() = item("water")
    }

    fun given(block: GivenDSL.() -> Unit) = GivenDSL().block()

    inner class MinimizeDSL {
        fun resourceUsage() = { recipe: Recipe ->
            ores.sumByFrac { -recipe.countDelta(it) }
        }

        fun recipes() = { _: Recipe ->
            Frac.ONE
        }
    }

    fun minimize(block: MinimizeDSL.() -> RecipeCostFunc) {
        objectiveMinimizeWeight = MinimizeDSL().block()
    }

    fun maximize(block: MinimizeDSL.() -> RecipeCostFunc) {
        val obj = MinimizeDSL().block()
        objectiveMinimizeWeight = { recipe -> -obj(recipe) }
    }

    inner class BlackListDSL {
        fun item(name: String) {
            itemBlackList += data.findItem(name)
        }

        fun recipe(name: String) {
            recipeBlackList += data.findRecipe(name)
        }

        fun matchItem(predicate: (Item) -> Boolean) {
            itemBlackList.addAll(data.items.filter(predicate))
        }

        fun matchRecipe(predicate: (Recipe) -> Boolean) {
            recipeBlackList.addAll(data.recipes.filter(predicate))
        }
    }

    fun blacklist(block: BlackListDSL.() -> Unit) = BlackListDSL().block()

    inner class AssemblerDSL {
        fun ifPossible(assembler: String) {
            val machine = data.findAssembler(assembler)
            assemblerPickers += { _, _ -> machine }
        }

        fun fastest() {
            assemblerPickers += { _, assemblers -> assemblers.maxBy { it.speed } }
        }

        abstract inner class EffectDSL(val pickerList: MutableList<ModulesPicker>) {
            fun perRecipe(picker: (name: String) -> Modules?) {
                pickerList += { recipe, _ ->
                    picker(recipe.name)
                }
            }

            fun perAssembler(picker: (name: String) -> Modules?) {
                pickerList += { _, assembler ->
                    picker(assembler.name)
                }
            }

            operator fun Int.times(module: Module): Modules = mapOf(module to this)
            operator fun Module.plus(modules: Modules): Modules = mapOf(this to modules.getOrDefault(this, 0)) + modules
        }

        inner class ModuleDSL : EffectDSL(modulePickers) {
            fun fillWith(name: String) {
                val module = data.findModule(name)
                modulePickers += { recipe, assembler ->
                    module.takeIf { it.allowedOn(recipe) }
                            ?.let { mapOf(module to assembler.maxModules) }
                }
            }
        }

        inner class BeaconDSL : EffectDSL(beaconPickers) {
            fun repeat(count: Int, modules: Modules): Modules {
                if (modules.size > 2) error("${modules.size} > 2 modules in beacon")
                return modules * count
            }
        }

        operator fun Module.times(other: Int): Modules = mapOf(this to other)
        operator fun Modules.times(other: Int): Modules = mapValues { (_, v) -> v * other }

        fun modules(block: ModuleDSL.() -> Unit) = ModuleDSL().apply(block)
        fun beacons(block: BeaconDSL.() -> Unit) = BeaconDSL().apply(block)
    }

    fun assembler(block: AssemblerDSL.() -> Unit) = AssemblerDSL().block()

    fun error(message: String): Nothing = throw IllegalArgumentException(message)

    fun pickAssembler(recipe: Recipe): Assembler {
        val candidates = data.assemblers.filter { recipe.category in it.craftingCategories && recipe.ingredients.size <= it.maxIngredients }.toSet()
        return assemblerPickers.map { it(recipe, candidates) }.firstOrNull()
                ?: candidates.maxBy { it.speed }
                ?: throw IllegalArgumentException("couldn't find assembler for $recipe")
    }

    fun pickLayout(recipe: Recipe, assembler: Assembler) = ModuleLayout(
            modules = modulePickers.asSequence().map { it(recipe, assembler) }.find { it != null } ?: emptyMap(),
            beacons = beaconPickers.asSequence().map { it(recipe, assembler) }.find { it != null } ?: emptyMap()
    )
}

fun factory(data: GameData, block: FactoryDSL.() -> Unit) = FactoryDSL(data).apply(block)

fun FactoryDSL.calculate(): List<Production> {
    //find relevant items & recipes
    val items = mutableSetOf<Item>()
    val recipes = mutableSetOf<Recipe>()

    val toVisit = LinkedList<Item>()
    toVisit.addAll(production.keys)
    while (toVisit.isNotEmpty()) {
        val next = toVisit.pop()
        if (next in itemBlackList || !items.add(next)) continue

        data.recipes.filter { it !in recipeBlackList && it.hasProduct(next) }.forEach { recipe ->
            if (recipes.add(recipe))
                recipe.ingredients.forEach { toVisit += it.item }
        }
    }

    //safely pick assemblers and layouts
    val assemblers = recipes.map { recipe ->
        val assembler = pickAssembler(recipe)
        if (recipe.category !in assembler.craftingCategories) error("wrong assembler ${assembler.name} for ${recipe.name}")
        recipe to assembler
    }.toMap()
    val layouts = recipes.map { recipe ->
        val layout = pickLayout(recipe, assemblers.getValue(recipe))
        val allModules = layout.modules.filter { it.value != 0 }.keys + layout.beacons.filter { it.value != 0 }.keys
        allModules.find { !it.allowedOn(recipe) }?.let {
            error("module ${it.name} not allowed on ${recipe.name}")
        }
        recipe to layout
    }.toMap()

    //build & solve the problem
    val objective = LinearFunc(recipes.map(objectiveMinimizeWeight).map(Frac::unaryMinus))
    val constraints = items.filter { it !in givenItems }.map { item ->
        GTEConstraint(
                scalars = recipes.map { recipe ->
                    (recipe.products.countItem(item) * (layouts.getValue(recipe).totalEffect("productivity"))
                            - recipe.ingredients.countItem(item))
                },
                value = production[item] ?: Frac.ZERO
        )
    }
    val prgm = LinearProgram(objective, constraints)
    val solution = prgm.solve()

    //interpret & return solution
    return recipes.mapIndexed { i, recipe ->
        val count = solution[i]
        val assembler = pickAssembler(recipe)
        val layout = pickLayout(recipe, assembler)
        val assemblerCount = count * recipe.energy / layout.totalEffect("speed") / assembler.speed

        Production(recipe, assembler, layout, count, assemblerCount)
    }.filter { it.count != ZERO }
}

private fun Recipe.hasProduct(item: Item) = products.any { it.item == item }

private fun List<ItemStack>.countItem(item: Item) = filter { it.item == item }.sumByFrac { it.amount }

private fun Recipe.countDelta(item: Item) = products.countItem(item) - ingredients.countItem(item)

data class Production(
        val recipe: Recipe,
        val assembler: Assembler,
        val moduleLayout: ModuleLayout,
        val count: Frac,
        val assemblerCount: Frac
)

class RenderDSL(val factory: FactoryDSL, val result: List<Production>) {
    val buffer = StringBuffer()

    fun table(header: List<String>? = null, row: (Production) -> List<Any?>) {
        val rows = (header?.let { listOf(header) } ?: emptyList()) + result.map(row).map { it.map(Any?::toString) }

        val widths = rows.fold(listOf<Int>()) { acc, next ->
            (0 until max(acc.size, next.size)).map { i -> max(acc.getOrNull(i) ?: 0, next.getOrNull(i)?.length ?: 0) }
        }

        with(buffer) {
            rows.forEach { row ->
                append(row.zip(widths).joinToString("    ") { (element, width) -> leftPad(element, width) })
                append('\n')
            }
        }
    }

    fun string() = buffer.toString()
}

fun FactoryDSL.render(block: RenderDSL.() -> Unit) = RenderDSL(this, this.calculate()).apply(block).string()

fun String.println() = println(this)

private fun leftPad(str: String, length: Int) = " ".repeat(max(length - str.length, 0)) + str