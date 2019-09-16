package factorio

import math.Frac
import math.Frac.Companion.ZERO
import math.frac

data class Item(
        val name: String,
        val type: String
)

data class ItemStack(
        val item: Item,
        val amount: Frac
)

data class Recipe(
        val name: String,
        val category: String,
        val ingredients: List<ItemStack>,
        val products: List<ItemStack>,
        val energy: Frac
)

data class Assembler(
        val name: String,
        val speed: Frac,
        val maxIngredients: Int,
        val craftingCategories: Set<String>,
        val allowedEffects: Map<String, Boolean>,
        val maxModules: Int
)

data class Resource(
        val name: String,
        val products: List<ItemStack>,
        val miningTime: Frac,
        val resourceCategory: String,

        val requiredFluid: ItemStack?,
        val normalAmount: Frac?
)

data class Miner(
        val name: String,
        val speed: Frac,
        val resourceCategories: Set<String>,
        val allowedEffects: Map<String, Boolean>
)

data class Module(
        val name: String,
        val category: String,
        val tier: Int,
        val effect: Effect,
        val limitations: Set<Recipe>?
) {
    fun allowedOn(recipe: Recipe) = limitations?.let { recipe in it } ?: true
}

data class Effect(val effects: Map<String, Frac>) {
    operator fun get(effect: String) = effects.getOrDefault(effect, ZERO)
}

operator fun Effect.plus(other: Effect) = Effect((this.effects.keys + other.effects.keys).map {
    it to ((this.effects[it] ?: ZERO) + (other.effects[it] ?: ZERO))
}.toMap())

operator fun Effect.times(other: Frac) = Effect(this.effects.mapValues { (_, v) -> v * other })
operator fun Frac.times(other: Effect) = other * this
operator fun Effect.times(other: Int) = this * other.frac
operator fun Int.times(other: Effect) = other * this

class GameData(
        val items: Set<Item>,
        val recipes: Set<Recipe>,
        val assemblers: Set<Assembler>,
        val resources: Set<Resource>,
        val miners: Set<Miner>,
        val modules: Set<Module>
) {
    fun findItem(name: String) = items.find { it.name == name }
            ?: throw IllegalArgumentException("item '$name' not found, similar: " + items.map { it.name }.suggestions(name))

    fun findRecipe(name: String) = recipes.find { it.name == name }
            ?: throw IllegalArgumentException("recipe '$name' not found, similar: " + recipes.map { it.name }.suggestions(name))

    fun findModule(name: String) = modules.find { it.name == name }
            ?: throw IllegalArgumentException("module '$name' not found, similar: " + modules.map { it.name }.suggestions(name))

    fun findAssembler(name: String) = assemblers.find { it.name == name }
            ?: throw IllegalArgumentException("assembler '$name' not found, similar: " + assemblers.map { it.name }.suggestions(name))
}

private fun Iterable<String>.suggestions(string: String): List<String> {
    val set = string.split('-').toSet()
    return filter {
        it.split('-').toSet().intersect(set).isNotEmpty()
    }
}

