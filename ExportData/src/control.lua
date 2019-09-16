local json = require "lib/json"
local default_file = "export.json"

local function round(number, digits)
    return math.floor(number * 10 ^ digits + 0.5) / 10 ^ digits
end

local function itemStackTable(stack)
    local amount
    if (stack.amount ~= nil) then
        amount = stack.amount
    else
        amount = stack.probability * (stack.amount_min + stack.amount_max) / 2
    end

    return {
        name = stack.name,
        type = stack.type,
        amount = amount
    }
end

local function itemStackListTable(list)
    local result = {}
    for i, stack in ipairs(list) do
        local t = itemStackTable(stack)
        if (t) then
            table.insert(result, itemStackTable(stack))
        end
    end
    return result
end

local function moduleTable(module)
    local result = {
        name = module.name,
        module_effects = {},
        category = module.category,
        tier = module.tier
    }

    if (#module.limitations ~= 0) then
        result.limitations = module.limitations
    end

    for effect, tbl in pairs(module.module_effects) do
        result.module_effects[effect] = round(tbl.bonus, 4)
    end

    return result
end

local function recipeTable(recipe)
    local result = {
        name = recipe.name,
        category = recipe.category,
        ingredients = recipe.ingredients,
        energy = recipe.energy
    }

    result.products = itemStackListTable(recipe.products)
    return result
end

local function itemTable(item)
    return {
        name = item.name,
        type = item.type,
        stack_size = item.stack_size
    }
end

local function fluidTable(fluid)
    return {
        name = fluid.name,
        type = "fluid"
    }
end

local function assemblerTable(assembler)
    local result = {
        name = assembler.name,
        crafting_speed = assembler.crafting_speed,
        ingredient_count = assembler.ingredient_count,
        allowed_effects = assembler.allowed_effects,
        module_inventory_size = assembler.module_inventory_size,
        crafting_categories = {}
    }

    for category, bool in pairs(assembler.crafting_categories) do
        if bool then
            table.insert(result.crafting_categories, category)
        end
    end

    return result
end

local function minerTable(miner)
    local result = {
        name = miner.name,
        mining_speed = miner.mining_speed,
        resource_categories = {},
        allowed_effects = miner.allowed_effects or {}
    }

    for category, bool in pairs(miner.resource_categories) do
        if bool then
            table.insert(result.resource_categories, category)
        end
    end

    return result
end

local function resourceTable(resource)
    result = {
        name = resource.name,
        resource_category = resource.resource_category,
        mining_time = resource.mineable_properties.mining_time,

        infinite = resource.infinite_resource ,
        minimum = resource.minimum_resource_amount ,
        
        products  = itemStackListTable(resource.mineable_properties.products )
    }

    if (resource.mineable_properties.required_fluid ~= nil) then
        result.required_fluid = {
            name = resource.mineable_properties .required_fluid,
            amount = resource.mineable_properties .fluid_amount,
            type = "fluid"
        }
    end

    return result
end

local function launchProductRecipe(item)
    local stacks = itemStackListTable(item.rocket_launch_products)
    if #stacks == 0 then
        return nil
    elseif #stacks == 1 then
        return {
            name = stacks[1].name,
            category = "rocket-building",
            energy = 42, --the time required for the launch animation
            ingredients = {
                {
                    name = item.name,
                    type = item.type,
                    amount = 1
                },
                {
                    name = "rocket-part",
                    type = "item",
                    amount = game.entity_prototypes["rocket-silo"].rocket_parts_required
                }
            },
            products = stacks
        }
    else
        error(#stacks .. " launch products for " .. item.name)
    end
end

local function onCommand(event)
    local output = {}

    output.recipes = {}
    for name, recipe in pairs(game.recipe_prototypes) do
        output.recipes[name] = recipeTable(recipe)
    end

    output.items = {}
    output.modules = {}
    for name, item in pairs(game.item_prototypes) do
        output.items[name] = itemTable(item)

        if (item.type == "module") then
            output.modules[name] = moduleTable(item)
        end

        if (item.rocket_launch_products) then
            local launch = launchProductRecipe(item)
            if launch then
                if output.recipes[launch.name] then
                    error(
                        "there's already a recipe called " .. stacks[1].name .. ", can't overwrite with launch recipe"
                    )
                end
                output.recipes[launch.name] = launch
            end
        end
    end
    for name, fluid in pairs(game.fluid_prototypes) do
        output.items[name] = fluidTable(fluid)
    end

    output.assemblers = {}
    output.miners = {}
    output.resources = {}
    for name, entity in pairs(game.entity_prototypes) do
        if (entity.type == "assembling-machine" or entity.type == "furnace" or entity.type == "rocket-silo" ) then
            output.assemblers[name] = assemblerTable(entity)
        end
        if (entity.type == "mining-drill") then
            output.miners[name] = minerTable(entity)
        end
        if (entity.type == "resource") then
            output.resources[name] = resourceTable(entity)
        end
    end

    output.versions = game.active_mods

    local file = event.parameter or default_file
    game.remove_path(file)
    game.write_file(file, json:encode(output), true)

    game.players[event.player_index].print("Export successful")
end

commands.add_command("export", nil, onCommand)
