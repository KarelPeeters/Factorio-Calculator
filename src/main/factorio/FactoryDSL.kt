package factorio

import math.*
import math.Frac.Companion.ONE
import math.Frac.Companion.ZERO
import org.apache.commons.collections4.bidimap.DualHashBidiMap
import org.apache.commons.collections4.multimap.HashSetValuedHashMap
import java.util.*
import kotlin.coroutines.experimental.buildSequence
import kotlin.math.max

@DslMarker
annotation class FactoryDSLMarker

typealias RecipeCostFunc = (Recipe) -> Frac
typealias Modules = Map<Module, Int>
typealias ModulesPicker = (Recipe, Assembler) -> Modules?

private val BEACON_EFFICIENCY = Frac(1, 2)

data class ModuleLayout(val modules: Modules, val beacons: Modules) {
    val effect = modules.effect() + BEACON_EFFICIENCY * beacons.effect()
    fun totalEffect(name: String) = ONE + effect[name]

    private fun Modules.effect() = entries.map { (m, c) -> c * m.effect }.fold(Effect(emptyMap()), Effect::plus)
}

@FactoryDSLMarker
class FactoryDSL(val data: GameData) {
    private val resourceItems = data.resources.flatMap { it.products.map { it.item } }

    val production = mutableMapOf<Item, Frac>()
    val givenItems = mutableSetOf<Item>()
    var objectiveMinimizeWeight: RecipeCostFunc = { Frac.ZERO }

    val itemBlackList = mutableListOf<Item>()
    val recipeBlackList = mutableListOf<Recipe>()

    val alwaysInline = mutableSetOf<Item>()
    val inlineInto = HashSetValuedHashMap<Recipe, Item>()

    private val assemblerPickers = mutableListOf<(Recipe, Set<Assembler>) -> Assembler?>()
    private val modulePickers = mutableListOf<ModulesPicker>()
    private val beaconPickers = mutableListOf<ModulesPicker>()

    enum class Time(val seconds: Int) {
        SECOND(1),
        MINUTE(60),
        HOUR(3600)
    }

    @FactoryDSLMarker
    inner class ProduceDSL(val time: Time) {
        fun stack(name: String, amount: Int) = stack(name, amount.frac)
        fun stack(name: String, amount: Frac) {
            val item = this@FactoryDSL.data.findItem(name)
            this@FactoryDSL.production[item] = (this@FactoryDSL.production[item] ?: Frac.ZERO) + amount / time.seconds
        }
    }

    fun produceEvery(time: Time, block: ProduceDSL.() -> Unit) = ProduceDSL(time).block()

    @FactoryDSLMarker
    inner class GivenDSL {
        fun item(name: String) {
            this@FactoryDSL.givenItems += this@FactoryDSL.data.findItem(name)
        }

        fun resources() {
            this@FactoryDSL.givenItems.addAll(this@FactoryDSL.resourceItems)
        }

        fun water() = item("water")
    }

    fun given(block: GivenDSL.() -> Unit) = GivenDSL().block()

    @FactoryDSLMarker
    inner class MinimizeDSL {
        fun resourceUsage() = { recipe: Recipe ->
            this@FactoryDSL.resourceItems.sumByFrac { -recipe.countDelta(it) }
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

    @FactoryDSLMarker
    inner class BlackListDSL {
        fun item(name: String) {
            this@FactoryDSL.itemBlackList += this@FactoryDSL.data.findItem(name)
        }

        fun recipe(name: String) {
            this@FactoryDSL.recipeBlackList += this@FactoryDSL.data.findRecipe(name)
        }

        fun matchItem(predicate: (Item) -> Boolean) {
            this@FactoryDSL.itemBlackList.addAll(this@FactoryDSL.data.items.filter(predicate))
        }

        fun matchRecipe(predicate: (Recipe) -> Boolean) {
            this@FactoryDSL.recipeBlackList.addAll(this@FactoryDSL.data.recipes.filter(predicate))
        }
    }

    fun blacklist(block: BlackListDSL.() -> Unit) = BlackListDSL().block()

    @FactoryDSLMarker
    inner class AssemblerDSL {
        fun ifPossible(assembler: String) {
            val machine = this@FactoryDSL.data.findAssembler(assembler)
            this@FactoryDSL.assemblerPickers += { _, _ -> machine }
        }

        fun fastest() {
            this@FactoryDSL.assemblerPickers += { _, assemblers -> assemblers.maxBy { it.speed } }
        }

        @FactoryDSLMarker
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

            fun module(name: String) = this@FactoryDSL.data.findModule(name)

            operator fun Module.times(other: Int): Modules = mapOf(this to other)
            operator fun Modules.times(other: Int): Modules = mapValues { (_, v) -> v * other }

            operator fun Int.times(module: Module): Modules = mapOf(module to this)
            operator fun Module.plus(modules: Modules): Modules = mapOf(this to modules.getOrDefault(this, 0)) + modules
        }

        inner class ModuleDSL : EffectDSL(this@FactoryDSL.modulePickers) {
            fun fillWith(name: String) {
                val module = this@FactoryDSL.data.findModule(name)
                this@FactoryDSL.modulePickers += { recipe, assembler ->
                    module.takeIf { it.allowedOn(recipe) }
                            ?.let { mapOf(module to assembler.maxModules) }
                }
            }
        }

        inner class BeaconDSL : EffectDSL(this@FactoryDSL.beaconPickers) {
            fun repeat(count: Int, modules: Modules): Modules {
                if (modules.size > 2) this@FactoryDSL.error("${modules.size} > 2 modules in beacon")
                return modules * count
            }
        }

        fun modules(block: ModuleDSL.() -> Unit) = ModuleDSL().apply(block)
        fun beacons(block: BeaconDSL.() -> Unit) = BeaconDSL().apply(block)
    }

    fun assembler(block: AssemblerDSL.() -> Unit) = AssemblerDSL().block()

    @FactoryDSLMarker
    inner class InlineDSL {
        fun always(itemName: String) {
            this@FactoryDSL.alwaysInline += this@FactoryDSL.data.findItem(itemName)
        }

        infix fun String.into(recipe: String) {
            this@FactoryDSL.inlineInto[this@FactoryDSL.data.findRecipe(recipe)] += this@FactoryDSL.data.findItem(this)
        }
    }

    fun inline(block: InlineDSL.() -> Unit) = InlineDSL().block()

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

    //interpret solution
    return recipes.mapIndexedNotNull { i, recipe ->
        val count = solution.values[i]
        if (count == ZERO) null
        else {
            val assembler = pickAssembler(recipe)
            val layout = pickLayout(recipe, assembler)
            val assemblerCount = count * recipe.energy / layout.totalEffect("speed") / assembler.speed

            Production(recipe, assembler, layout, count, assemblerCount)
        }
    }
}

private operator fun Production.times(scale: Frac) = copy(
        count = count * scale,
        assemblerCount = assemblerCount * scale
)

data class ProductionGroup(
        val production: Production,
        val children: List<ProductionGroup>
)

fun FactoryDSL.groupInlines(productions: List<Production>): List<ProductionGroup> {
    val itemRecipeMap = buildSequence {
        yieldAll(alwaysInline)
        yieldAll(inlineInto.values())
    }.mapNotNull { item ->
        val recipes = productions.map { it.recipe }.filter { it.hasProduct(item) }

        when (recipes.size) {
            0 -> null
            1 -> {
                val recipe = recipes.first()
                if (recipe.products.size > 1)
                    error("Can't inline recipe with more than one product, found ${recipe.products.joinToString { it.item.name }}")
                item to recipe
            }
            else -> error("Can't inline item with more than one used recipe, found ${recipes.joinToString { it.name }}")
        }
    }.toMap().run { DualHashBidiMap(this) }

    val totalConsumption = productions.countItemConsumption()
    val countedConsumption = mutableMapOf<Item, Frac>()

    val productionsLeft = productions.toMutableList()

    fun recursiveGroup(usedFraction: Frac, parent: Production): ProductionGroup {
        val recipe = parent.recipe
        val children = recipe.ingredients.mapNotNull { (item, amount) ->
            val used = amount * parent.count * usedFraction
            countedConsumption[item] = countedConsumption[item] + used

            if ((item in alwaysInline || item in inlineInto[recipe]) && itemRecipeMap.contains(item)) {
                val prod = productions.first { it.recipe == itemRecipeMap[item] }
                if (totalConsumption[item] - countedConsumption[item] == ZERO)
                    productionsLeft.remove(prod)
                recursiveGroup(used / totalConsumption.getValue(item), prod)
            } else null

        }
        return ProductionGroup(parent * usedFraction, children)
    }

    val groups = mutableListOf<ProductionGroup>()

    while (productionsLeft.isNotEmpty()) {
        val curr = productionsLeft.first { prod ->
            if (itemRecipeMap.containsValue(prod.recipe)) {
                val item = itemRecipeMap.getKey(prod.recipe)
                if (item in alwaysInline) {
                    //recipe always inlined
                    productionsLeft.none { it.recipe.ingredients.any { it.item == item } }
                } else {
                    //recipe inlined into
                    productionsLeft.none { item in inlineInto[it.recipe] && it.recipe.ingredients.any { it.item == item } }
                }
            } else {
                //recipe never inlined
                true
            }
        }

        groups += recursiveGroup(ONE, curr)
        productionsLeft -= curr
    }
    return groups
}

fun List<Production>.countItemConsumption(): MutableMap<Item, Frac> {
    val map = mutableMapOf<Item, Frac>()
    forEach { production ->
        production.recipe.ingredients.forEach { (item, amount) ->
            map[item] = (map[item] ?: ZERO) + amount * production.count
        }
    }
    return map
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

enum class Align { L, R }

class RenderDSL(val factory: FactoryDSL, val flat: List<Production>, val groups: List<ProductionGroup>) {
    private val builder = StringBuilder()

    inner class TableDSL {
        inner class Column(val name: String, val align: Align, val value: (Production) -> Any?)

        private val _columns = mutableListOf<Column>()
        val columns: List<Column> = _columns

        var columnDistance = 3
        var nestIndent = 3

        fun column(name: String, align: Align, value: Production.() -> Any?) {
            _columns += Column(name, align, value)
        }
    }

    private fun TableDSL.render() {
        val rows = mutableListOf<List<String>>()

        fun walk(depth: Int, group: ProductionGroup) {
            rows += columns.mapIndexed { i, c ->
                val str = c.value(group.production).toString()
                if (i == 0) " ".repeat(depth * nestIndent) + str else str
            }

            group.children.forEach { walk(depth + 1, it) }
        }

        groups.sortedBy { it.production.recipe.name }.forEach { walk(0, it) }
        Table(columns.map { ColumnSettings(it.name, it.align) }, columnDistance, rows).renderTo(builder)
        builder.append("\n\n")
    }

    fun groupTable(block: TableDSL.() -> Unit) = TableDSL().apply(block).render()

    fun <T> totalTable(itemName: String, keyStr: (T) -> String, valueStr: (Frac) -> String, finder: Production.() -> Map<T, Frac>) {
        val total = mutableMapOf<T, Frac>()
        groups.forEach {
            val add = finder(it.production)
            add.forEach { t, v -> total[t] = total.getOrDefault(t, ZERO) + v }
        }

        val tbl = Table(
                columnsSettings = listOf(ColumnSettings(itemName, Align.L), ColumnSettings("Count", Align.R)),
                columnDistance = 3,
                rows = total.toList().sortedBy { it.second }.map { (k, v) -> listOf(keyStr(k), valueStr(v)) }
        )

        tbl.renderTo(builder)
        builder.append("\n\n")
    }

    fun custom(block: StringBuilder.(groups: List<ProductionGroup>) -> Unit) {
        builder.block(groups)
    }

    fun line(value: Any? = null) {
        builder.append(value)
        builder.append('\n')
    }

    fun renderToString() = builder.toString()
}

fun FactoryDSL.render(block: RenderDSL.() -> Unit): String {
    val flat = calculate()
    val groups = groupInlines(flat)
    return RenderDSL(this, flat, groups).apply(block).renderToString()
}

fun String.println() = println(this)

data class ColumnSettings(val name: String, val align: Align)

private class Table(
        val columnsSettings: List<ColumnSettings>,
        val columnDistance: Int,
        val rows: List<List<String>>
) {
    init {
        rows.forEach { require(it.size == columnsSettings.size) }
    }

    fun renderTo(builder: StringBuilder) {
        val widths = columnsSettings.map { it.name.length }.toMutableList()
        rows.forEach { row ->
            row.forEachIndexed { i, s -> widths[i] = max(widths[i], s.length) }
        }

        with(builder) {
            buildSequence {
                yield(columnsSettings.map { it.name })
                yieldAll(rows)
            }.forEach { row ->
                row.forEachIndexed { i, s ->
                    val column = columnsSettings[i]
                    append(align(s, widths[i], column.align))
                    append(" ".repeat(columnDistance))
                }
                append('\n')
            }
        }
    }
}

private fun align(str: String, length: Int, align: Align): String {
    val padding = " ".repeat(max(length - str.length, 0))
    return when (align) {
        Align.L -> str + padding
        Align.R -> padding + str
    }
}

private operator fun Frac?.plus(other: Frac?) = (this ?: ZERO) + (other ?: ZERO)
private operator fun Frac?.minus(other: Frac?) = (this ?: ZERO) - (other ?: ZERO)