package com.monumenta.reborn.cit.parse

import com.monumenta.reborn.cit.rule.ArmorCitRule
import com.monumenta.reborn.cit.rule.CitNbtCondition
import com.monumenta.reborn.cit.rule.CitRule
import com.monumenta.reborn.cit.rule.CitRuleConstants
import com.monumenta.reborn.cit.text.CitText
import net.minecraft.item.Item
import net.minecraft.item.equipment.EquipmentAssetKeys
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.util.Identifier
import java.util.Properties

object CitRuleParser {
    private val modelProperties = linkedMapOf(
        "model" to "base",
        "model.bow_pulling_0" to "pulling_0",
        "model.bow_pulling_1" to "pulling_1",
        "model.bow_pulling_2" to "pulling_2",
        "model.bow_pulling_3" to "pulling_3",
        "model.bow_pulling_4" to "pulling_4",
        "model.crossbow_standby" to "standby",
        "model.crossbow_pulling_0" to "pulling_0",
        "model.crossbow_pulling_1" to "pulling_1",
        "model.crossbow_pulling_2" to "pulling_2",
        "model.crossbow_arrow" to "arrow",
        "model.crossbow_firework" to "firework",
        "model.shield_blocking" to "blocking",
    )

    fun parseItemRule(source: Identifier, properties: Properties): CitRule? {
        if (properties.getProperty("type", "item") != "item") return null
        val items = itemList(properties.getProperty("items", properties.getProperty("matchItems")))
        if (items.isEmpty()) return null

        val parsedName = CitText.parseName(firstNonBlank(properties.getProperty("nbt.display.Name"), properties.getProperty("nbt.plain.display.Name")))
        val hope = properties.getProperty("nbt.${CitRuleConstants.hopePath}")
        val conditions = nbtConditions(properties)
        val models = propertyModels(properties)
        var model = propertyModel(properties, "model")
        if (model == null && models.isEmpty()) return null
        if (isLegacyOptifineModel(model) && models.values.all(::isLegacyOptifineModel)) return null

        val type = typeFor(items, models)
        if (type == CitRule.Type.SIMPLE && model == null && models.isNotEmpty()) {
            model = models.values.first()
        }

        return CitRule(
            id = source.toString(),
            items = items,
            name = parsedName.value,
            namePattern = parsedName.pattern,
            nameIgnoreCase = parsedName.ignoreCase,
            nameRegex = parsedName.regex,
            hope = hope,
            nbtConditions = conditions,
            weight = properties.getProperty("weight").parseIntOr(0),
            type = type,
            model = model,
            models = models,
        )
    }

    fun parseArmorRule(source: Identifier, properties: Properties): ArmorCitRule? {
        if (properties.getProperty("type", "item") != "armor") return null
        val items = itemList(properties.getProperty("items", properties.getProperty("matchItems")))
        if (items.isEmpty() || !hasArmorLayerTexture(properties)) return null

        val parsedName = CitText.parseName(firstNonBlank(properties.getProperty("nbt.display.Name"), properties.getProperty("nbt.plain.display.Name")))
        val asset = equipmentAssetId(source)
        return ArmorCitRule(
            id = source.toString(),
            items = items,
            name = parsedName.value,
            namePattern = parsedName.pattern,
            nameIgnoreCase = parsedName.ignoreCase,
            nameRegex = parsedName.regex,
            hope = properties.getProperty("nbt.${CitRuleConstants.hopePath}"),
            nbtConditions = nbtConditions(properties),
            weight = properties.getProperty("weight").parseIntOr(0),
            asset = asset,
            assetKey = RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, asset),
        )
    }

    fun specificity(rule: CitRule): Int =
        rule.nbtConditions.size * 10 +
            if (rule.hope != null) 10 else 0 +
            if (rule.name != null) if (rule.namePattern) 1 else 2 else 0

    fun specificity(rule: ArmorCitRule): Int =
        rule.nbtConditions.size * 10 +
            if (rule.hope != null) 10 else 0 +
            if (rule.name != null) if (rule.namePattern) 1 else 2 else 0

    fun identifier(raw: String?): Identifier? {
        if (raw.isNullOrBlank()) return null
        return try {
            Identifier.of(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun itemIds(items: List<Item>): String =
        items.map { Registries.ITEM.getId(it).toString() }.toString()

    fun itemModelId(raw: String?): Identifier? {
        if (raw.isNullOrBlank()) return null
        val id = try {
            Identifier.of(raw)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return if (id.path.startsWith("item/")) Identifier.of(id.namespace, id.path.removePrefix("item/")) else id
    }

    private fun nbtConditions(properties: Properties): List<CitNbtCondition> =
        properties.stringPropertyNames()
            .asSequence()
            .filter { it.startsWith("nbt.") }
            .filterNot { it == "nbt.display.Name" || it == "nbt.plain.display.Name" || it == "nbt.${CitRuleConstants.hopePath}" }
            .mapNotNull { key ->
                val value = properties.getProperty(key)?.trim()
                if (value.isNullOrBlank()) null else CitNbtCondition.of(key.removePrefix("nbt."), CitText.stripJsonText(value))
            }
            .toList()

    private fun itemList(raw: String?): List<Item> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.trim().split(Regex("\\s+"))
            .map { if (":" in it) it else "minecraft:$it" }
            .mapNotNull { identifier(it) }
            .map { Registries.ITEM.get(it) }
    }

    private fun propertyModels(properties: Properties): Map<String, Identifier> =
        buildMap {
            for ((property, key) in modelProperties) {
                propertyModel(properties, property)?.let { put(key, it) }
            }
        }

    private fun propertyModel(properties: Properties, property: String): Identifier? =
        itemModelId(properties.getProperty(property)?.trim())

    private fun isLegacyOptifineModel(model: Identifier?): Boolean =
        model != null && model.namespace == "minecraft" && (model.path.startsWith("optifine/") || model.path.startsWith("mcpatcher/"))

    private fun typeFor(items: List<Item>, models: Map<String, Identifier>): CitRule.Type {
        for (item in items) {
            val itemPath = Registries.ITEM.getId(item).path
            if (itemPath == "bow" && hasPullingModel(models)) return CitRule.Type.BOW
            if (itemPath == "crossbow" && hasCrossbowModel(models)) return CitRule.Type.CROSSBOW
            if (itemPath == "shield" && "blocking" in models) return CitRule.Type.SHIELD
        }
        return CitRule.Type.SIMPLE
    }

    private fun hasPullingModel(models: Map<String, Identifier>): Boolean =
        listOf("pulling_0", "pulling_1", "pulling_2", "pulling_3", "pulling_4").any(models::containsKey)

    private fun hasCrossbowModel(models: Map<String, Identifier>): Boolean =
        hasPullingModel(models) || "arrow" in models || "firework" in models

    private fun hasArmorLayerTexture(properties: Properties): Boolean =
        properties.stringPropertyNames().any { it.startsWith("texture.") && "_layer_" in it }

    private fun equipmentAssetId(source: Identifier): Identifier {
        var path = source.path
        for (root in CitRuleConstants.propertiesRuleRoots) {
            val prefix = "$root/"
            if (path.startsWith(prefix)) {
                path = path.removePrefix(prefix)
                break
            }
        }
        if (path.endsWith(".properties")) path = path.removeSuffix(".properties")
        return Identifier.of(source.namespace, path)
    }

    private fun firstNonBlank(first: String?, second: String?): String? =
        if (!first.isNullOrBlank()) first else second

    private fun String?.parseIntOr(fallback: Int): Int =
        this?.trim()?.toIntOrNull() ?: fallback
}
