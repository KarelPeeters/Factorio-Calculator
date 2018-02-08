package factorio

import math.*
import math.Frac.Companion.ONE
import java.util.*

internal typealias RecipeCostFunc = (Recipe) -> Frac
internal typealias EffectPicker = (Recipe, Assembler) -> Effect

private val BEACON_EFFICIENCY = Frac(1, 2)
private val EMPTY_EFFECT = Effect(emptyMap())

class FactoryDSL(val data: GameData) {
    private val ores = data.resources.flatMap { it.products.map { it.item } }

    internal val production = mutableMapOf<Item, Frac>()
    internal val givenItems = mutableSetOf<Item>()
    internal var objectiveWeight: RecipeCostFunc = { Frac.ZERO }
    internal val itemBlackList = mutableListOf<Item>()
    internal val recipeBlackList = mutableListOf<Recipe>()

    private val assemblerPickers = mutableListOf<(Recipe, Set<Assembler>) -> Assembler?>()
    private var modulesEffectPicker: EffectPicker = { _, _ -> EMPTY_EFFECT }
    private var beaconsEffectPicker: EffectPicker = { _, _ -> EMPTY_EFFECT }

    enum class Time(val seconds: Int) {
        SECOND(1),
        MINUTE(60),
        HOUR(3600)
    }

    inner class ProduceDSL(val time: Time) {
        fun stack(name: String, amount: Int) = stack(name, Frac(amount))
        fun stack(name: String, amount: Frac) {
            val item = data.findItem(name)
            production[item] = (production[item] ?: Frac.ZERO) + amount / time.seconds
        }
    }

    fun produceEvery(time: Time, block: ProduceDSL.() -> Unit) {
        ProduceDSL(time).block()
    }

    inner class GivenDSL {
        fun item(name: String) {
            givenItems += data.findItem(name)
        }

        fun resources() {
            givenItems.addAll(ores)
        }

        fun water() = item("water")
    }

    fun given(block: GivenDSL.() -> Unit) {
        GivenDSL().block()
    }

    inner class MinimizeDSL {
        fun resourceUsage() = { recipe: Recipe ->
            ores.sumByFrac { recipe.countDelta(it) }
        }

        fun recipes() = { _: Recipe ->
            Frac.ONE
        }
    }

    fun minimize(block: MinimizeDSL.() -> RecipeCostFunc) {
        objectiveWeight = MinimizeDSL().block()
    }

    inner class BlackListDSL {
        fun item(name: String) {
            itemBlackList += data.findItem(name)
        }

        fun recipe(name: String) {
            recipeBlackList += data.findRecipe(name)
        }
    }

    fun blacklist(block: BlackListDSL.() -> Unit) {
        BlackListDSL().block()
    }

    inner class AssemblerDSL {
        fun ifPossible(assembler: String) {
            val machine = data.findAssembler(assembler)
            assemblerPickers += { _, _ -> machine }
        }

        fun fastest() {
            assemblerPickers += { _, assemblers -> assemblers.maxBy { it.speed } }
        }

        var modules: EffectPicker
            set(value) {
                modulesEffectPicker = value
            }
            get() = modulesEffectPicker

        var beacons: EffectPicker
            set(value) {
                beaconsEffectPicker = value
            }
            get() = beaconsEffectPicker

        fun module(name: String) = data.findModule(name)

        fun fillWith(module: Module) = { _: Recipe, assembler: Assembler ->
            module.effect * Frac(assembler.maxModules)
        }

        operator fun Int.times(other: Module) = this * other.effect

        fun matchAssembler(block: (assembler: String) -> Effect) = { _: Recipe, assembler: Assembler ->
            block(assembler.name)
        }

    }

    fun assembler(block: AssemblerDSL.() -> Unit) {
        AssemblerDSL().block()
    }

    internal fun pickAssembler(recipe: Recipe): Assembler {
        val candidates = data.assemblers.filter { recipe.category in it.craftingCategories && recipe.ingredients.size <= it.maxIngredients }.toSet()
        return assemblerPickers.map { it(recipe, candidates) }.firstOrNull()
                ?: candidates.maxBy { it.speed }
                ?: throw IllegalArgumentException("couldn't find assembler for $recipe")
    }

    internal fun pickEffect(recipe: Recipe, assembler: Assembler) =
            modulesEffectPicker(recipe, assembler) + BEACON_EFFICIENCY * beaconsEffectPicker(recipe, assembler)
}

fun factory(data: GameData, block: FactoryDSL.() -> Unit) {
    val factory = FactoryDSL(data)
    factory.block()

    val result = calculate(factory)
    println(result.entries.filter { it.value != Frac.ZERO }.joinToString("\n") { "${it.value}\t${it.value.toDouble()}\t${it.key.name}" })
}

internal fun calculate(factory: FactoryDSL): Map<Recipe, Frac> {
    val data = factory.data

    //find relevant items & recipes
    val items = mutableSetOf<Item>()
    val recipes = mutableSetOf<Recipe>()

    val toVisit = LinkedList<Item>()
    toVisit.addAll(factory.production.keys)
    while (toVisit.isNotEmpty()) {
        val next = toVisit.pop()
        if (next in factory.itemBlackList || !items.add(next)) continue

        data.recipes.filter { it !in factory.recipeBlackList && it.products.any { it.item == next } }.forEach { recipe ->
            if (recipes.add(recipe))
                recipe.ingredients.forEach { toVisit += it.item }
        }
    }

    //build & solve the problem
    val objective = LinearFunc(recipes.map(factory.objectiveWeight))
    val constraints = items.filter { it !in factory.givenItems }.map { item ->
        GTEConstraint(
                scalars = recipes.map { recipe ->
                    (recipe.products.countItem(item) * (ONE + factory.pickEffect(recipe, factory.pickAssembler(recipe))["productivity"]!!)
                            - recipe.ingredients.countItem(item))
                },
                value = factory.production[item] ?: Frac.ZERO
        )
    }
    val prgm = LinearProgram(objective, constraints)
    val sol = prgm.solve()

    return recipes.mapIndexed { i, it -> it to sol[i] }.toMap()
}

internal fun List<ItemStack>.countItem(item: Item) = filter { it.item == item }.sumByFrac { it.amount }

internal fun Recipe.countDelta(item: Item) = products.countItem(item) - ingredients.countItem(item)