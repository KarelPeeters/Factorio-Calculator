package factorio

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import math.Frac
import java.io.InputStreamReader

//TODO: replace get calls with string calls or double and convert toString
fun dataFromJson(reader: InputStreamReader): GameData {
    val json = Klaxon().parseJsonObject(reader)

    val items = json.obj("items")?.values?.map {
        it as JsonObject
        Item(
                name = it.string("name") ?: missing("name in item"),
                type = it.string("type") ?: missing("type in item")
        )
    }?.toSet() ?: missing("items")

    val recipes = json.obj("recipes")?.values?.map {
        it as JsonObject
        Recipe(
                name = it.string("name") ?: missing("name in recipe"),
                category = it.string("category") ?: missing("category in recipe"),
                ingredients = it.array<JsonObject>("ingredients")?.value?.map { objToItemStack(it, items) }
                        ?: missing("ingredients in recipe"),
                products = it.array<JsonObject>("products")?.value?.map { objToItemStack(it, items) }
                        ?: missing("products in recipe"),
                energy = decimalToFrac(it.get("energy")?.toString() ?: missing("energy in recipe"))
        )
    }?.toSet() ?: missing("items")

    val assemblers = json.obj("assemblers")?.values?.map {
        it as JsonObject
        Assembler(
                name = it.string("name") ?: missing("name in assembler"),
                speed = decimalToFrac(it.get("crafting_speed")?.toString() ?: missing("speed in assembler")),
                maxIngredients = it.int("ingredient_count") ?: missing("ingredient_count in assembler"),
                craftingCategories = it.array<String>("crafting_categories")?.value?.toSet()
                        ?: missing("crafting_categories in assembler"),
                allowedEffects = it.obj("allowed_effects")?.map { (name, allowed) -> name to (allowed as Boolean) }?.toMap()
                        ?: missing("allowed_effects in assembler"),
                maxModules = it.int("module_inventory_size") ?: missing("module_inventory_size in assembler")
        )
    }?.toSet() ?: missing("assemblers")

    val resources = json.obj("resources")?.values?.map {
        it as JsonObject
        Resource(
                name = it.string("name") ?: missing("name in resource"),
                products = it.array<JsonObject>("products")?.value?.map { objToItemStack(it, items) }
                        ?: missing("products in resource"),
                hardness = decimalToFrac(it.get("hardness")?.toString() ?: missing("hardness in resource")),
                miningTime = decimalToFrac(it.get("mining_time")?.toString() ?: missing("mining_time in resource")),
                resourceCategory = it.string("resource_categorie") ?: missing("resource_categorie in resource"),
                required_fluid = if (it.containsKey("required_fluid")) {
                    ItemStack(
                            item = items.first { item -> item.name == it.string("required_fluid") },
                            amount = decimalToFrac(it.get("fluid_amount")?.toString()
                                    ?: missing("fluid_amount in resource with required_fluid"))
                    )
                } else null
        )
    }?.toSet() ?: missing("resources")

    val modules = json.obj("modules")?.values?.map {
        it as JsonObject
        Module(
                name = it.string("name") ?: missing("name in module"),
                category = it.string("category") ?: missing("category in module"),
                tier = it.int("tier") ?: missing("tier in module"),
                effect = it.obj("module_effects")?.map { (effect, strength) -> effect to decimalToFrac(strength.toString()) }?.toMap()?.let { Effect(it) }
                        ?: missing("module_effects in module"),
                limitations = it.array<String>("limitations")?.map { limit ->
                    recipes.find { it.name == limit } ?: notFound("recipe '$limit'")
                }?.toSet() ?: emptySet()
        )
    }?.toSet() ?: missing("modules")

    return GameData(items, recipes, assemblers, resources, modules)
}

private fun objToItemStack(obj: JsonObject, items: Set<Item>) = ItemStack(
        items.find { it.name == obj.string("name") } ?: notFound("item '${obj.string("name")}'"),
        Frac(obj.int("amount") ?: obj.int("minimum_resource_amount") ?: missing("amount"))
)

private val decimalRegex = """-?(\d*)(?:\.(\d+))?""".toRegex()

private fun decimalToFrac(str: String): Frac {
    val match = decimalRegex.matchEntire(str) ?: throw IllegalArgumentException("'$str' isn't a decimal")
    val before = match.groups[1]?.value ?: "0"
    val after = match.groups[2]?.value ?: "0"

    val d = (before + after).toLongOrNull() ?: throw ArithmeticException("'$str' denominator is too big")
    val n = pow(10, after.length)

    return Frac(d, n)
}

private fun notFound(str: String): Nothing = error("$str not found")
private fun missing(str: String): Nothing = error("missing $str")
private fun error(str: String): Nothing = throw IllegalArgumentException(str)

private fun pow(a: Long, b: Int): Long = when {
    b == 0 -> 1
    b == 1 -> a
    b % 2 == 0 -> pow(a * a, b / 2)
    else -> Math.multiplyExact(a, pow(a * a, b / 2))
}