package factorio

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import math.Frac
import java.io.InputStreamReader
import java.math.BigInteger

fun dataFromJson(reader: InputStreamReader): GameData {
    val json = Klaxon().parseJsonObject(reader)

    val items = json.obj("items")?.values?.map {
        it as JsonObject
        Item(
            name = it.string("name") ?: missing("name in item"),
            type = it.string("type") ?: missing("type in item")
        )
    }?.toSet() ?: missing("items")

    val recipes = json.obj("recipes")?.values?.map { obj ->
        obj as JsonObject
        Recipe(
            name = obj.string("name") ?: missing("name in recipe"),
            category = obj.string("category") ?: missing("category in recipe"),
            ingredients = obj.array<JsonObject>("ingredients")?.value?.map { objToItemStack(it, items) }
                ?: missing("ingredients in recipe"),
            products = obj.array<JsonObject>("products")?.value?.map { objToItemStack(it, items) }
                ?: missing("products in recipe"),
            energy = obj.frac("energy") ?: missing("energy in recipe")
        )
    }?.toSet() ?: missing("items")

    val assemblers = json.obj("assemblers")?.values?.map {
        it as JsonObject
        Assembler(
            name = it.string("name") ?: missing("name in assembler"),
            speed = it.frac("crafting_speed") ?: missing("speed in assembler"),
            maxIngredients = it.int("ingredient_count") ?: missing("ingredient_count in assembler"),
            craftingCategories = it.array<String>("crafting_categories")?.value?.toSet()
                ?: missing("crafting_categories in assembler"),
            allowedEffects = it.obj("allowed_effects")?.let(::objToAllowedEffects)
                ?: missing("allowed_effects in assembler"),
            maxModules = it.int("module_inventory_size") ?: missing("module_inventory_size in assembler")
        )
    }?.toSet() ?: missing("assemblers")

    val resources = json.obj("resources")?.values?.map { obj ->
        obj as JsonObject
        Resource(
            name = obj.string("name") ?: missing("name in resource"),
            products = obj.array<JsonObject>("products")?.value?.map { objToItemStack(it, items) }
                ?: missing("products in resource"),
            miningTime = obj.frac("mining_time") ?: missing("mining_time in resource"),
            resourceCategory = obj.string("resource_category") ?: missing("resource_category in resource"),
            requiredFluid = obj.obj("required_fluid")?.let { objToItemStack(it, items) },
            normalAmount = obj.frac("normal_amount")
        )
    }?.toSet() ?: missing("resources")

    val miners = json.obj("miners")?.values?.map {
        it as JsonObject
        Miner(
            name = it.string("name") ?: missing("name in miner"),
            speed = it.frac("mining_speed") ?: missing("mining_speed in miner"),
            resourceCategories = it.array<String>("resource_categories")?.value?.toSet()
                ?: missing("resource_categories in miner"),
            allowedEffects = it.obj("allowed_effects")?.let(::objToAllowedEffects)
                ?: missing("allowed_effects in miner")
        )
    }?.toSet() ?: missing("miners")

    val modules = json.obj("modules")?.values?.map { obj ->
        obj as JsonObject
        Module(
            name = obj.string("name") ?: missing("name in module"),
            category = obj.string("category") ?: missing("category in module"),
            tier = obj.int("tier") ?: missing("tier in module"),
            effect = obj.obj("module_effects")?.map { (effect, strength) ->
                effect to decimalToFrac(strength.toString())
            }?.toMap()?.let {
                Effect(it)
            } ?: missing("module_effects in module"),
            limitations = obj.array<String>("limitations")?.map { limit ->
                recipes.find { it.name == limit } ?: notFound("recipe '$limit'")
            }?.toSet()
        )
    }?.toSet() ?: missing("modules")

    return GameData(items, recipes, assemblers, resources, miners, modules)
}

private fun JsonObject.frac(fieldName: String) = get(fieldName)?.toString()?.run(::decimalToFrac)

private fun objToItemStack(obj: JsonObject, items: Set<Item>) = ItemStack(
    items.find { it.name == obj.string("name") } ?: notFound("item '${obj.string("name")}'"),
    Frac(obj.int("amount") ?: obj.int("minimum_resource_amount") ?: missing("amount"))
)

private fun objToAllowedEffects(obj: JsonObject) = obj.mapValues { (_, allowed) -> allowed as Boolean }

private val decimalRegex = """-?(\d*)(?:\.(\d+))?""".toRegex()

private fun decimalToFrac(str: String): Frac {
    val match = decimalRegex.matchEntire(str) ?: throw IllegalArgumentException("'$str' isn't a decimal")
    val before = match.groups[1]?.value ?: "0"
    val after = match.groups[2]?.value ?: "0"

    val d = (before + after).toBigInteger()
    val n = BigInteger.TEN.pow(after.length)
    val f = Frac(d, n)

    return if (str.startsWith('-')) -f else f
}

private fun notFound(str: String): Nothing = error("$str not found")
private fun missing(str: String): Nothing = error("missing $str")
private fun error(str: String): Nothing = throw IllegalArgumentException(str)