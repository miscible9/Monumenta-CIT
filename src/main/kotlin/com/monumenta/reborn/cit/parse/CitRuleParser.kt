package com.monumenta.reborn.cit.parse

import com.monumenta.reborn.cit.rule.ArmorCitRule
import com.monumenta.reborn.cit.rule.CitNbtCondition
import com.monumenta.reborn.cit.rule.CitRule
import com.monumenta.reborn.cit.rule.CitRuleConstants
import com.monumenta.reborn.cit.rule.ParsedName
import com.monumenta.reborn.cit.text.CitText
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.equipment.EquipmentAssets
import java.util.Properties

object CitRuleParser {
    private const val ITEM_TYPE = "item"
    private const val ARMOR_TYPE = "armor"
    private const val DISPLAY_NAME_KEY = "nbt.display.Name"
    private const val PLAIN_DISPLAY_NAME_KEY = "nbt.plain.display.Name"
    private val hopeKey = "nbt.${CitRuleConstants.hopePath}"

    private val dynamicModelProperties = linkedMapOf(
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
        if (!isRuleType(properties, ITEM_TYPE)) {
            return null
        }

        val items = itemList(properties.itemListProperty())
        if (items.isEmpty()) {
            return null
        }

        val modelSelection = modelSelection(properties, items) ?: return null
        val metadata = parsedRuleMetadata(source, properties, items)
        return CitRule(
            id = metadata.id,
            items = metadata.items,
            name = metadata.name.value,
            namePattern = metadata.name.pattern,
            nameIgnoreCase = metadata.name.ignoreCase,
            nameRegex = metadata.name.regex,
            hope = metadata.hope,
            nbtConditions = metadata.nbtConditions,
            weight = metadata.weight,
            type = modelSelection.type,
            model = modelSelection.model,
            models = modelSelection.models,
        )
    }

    fun parseArmorRule(source: Identifier, properties: Properties): ArmorCitRule? {
        if (!isRuleType(properties, ARMOR_TYPE)) {
            return null
        }

        val items = itemList(properties.itemListProperty())
        if (items.isEmpty()) {
            return null
        }
        if (!hasArmorLayerTexture(properties)) {
            return null
        }

        val metadata = parsedRuleMetadata(source, properties, items)
        val asset = equipmentAssetId(source)
        return ArmorCitRule(
            id = metadata.id,
            items = metadata.items,
            name = metadata.name.value,
            namePattern = metadata.name.pattern,
            nameIgnoreCase = metadata.name.ignoreCase,
            nameRegex = metadata.name.regex,
            hope = metadata.hope,
            nbtConditions = metadata.nbtConditions,
            weight = metadata.weight,
            asset = asset,
            assetKey = ResourceKey.create(EquipmentAssets.ROOT_ID, asset),
        )
    }

    fun specificity(rule: CitRule): Int =
        specificity(rule.nbtConditions, rule.hope, rule.name, rule.namePattern)

    fun specificity(rule: ArmorCitRule): Int =
        specificity(rule.nbtConditions, rule.hope, rule.name, rule.namePattern)

    fun identifier(raw: String?): Identifier? {
        if (raw.isNullOrBlank()) {
            return null
        }

        return try {
            Identifier.parse(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun itemIds(items: List<Item>): String =
        items.map { BuiltInRegistries.ITEM.getKey(it).toString() }.toString()

    fun itemModelId(raw: String?): Identifier? {
        if (raw.isNullOrBlank()) {
            return null
        }

        val id = identifier(raw) ?: return null
        if (id.path.startsWith("item/")) {
            return Identifier.fromNamespaceAndPath(id.namespace, id.path.removePrefix("item/"))
        }
        return id
    }

    private fun parsedRuleMetadata(
        source: Identifier,
        properties: Properties,
        items: List<Item>,
    ): ParsedRuleMetadata {
        val rawName = firstNonBlank(
            properties.getProperty(DISPLAY_NAME_KEY),
            properties.getProperty(PLAIN_DISPLAY_NAME_KEY),
        )
        return ParsedRuleMetadata(
            id = source.toString(),
            items = items,
            name = CitText.parseName(rawName),
            hope = properties.getProperty(hopeKey),
            nbtConditions = nbtConditions(properties),
            weight = properties.getProperty("weight").parseIntOr(0),
        )
    }

    private fun modelSelection(properties: Properties, items: List<Item>): ModelSelection? {
        val dynamicModels = dynamicModels(properties)
        val baseModel = propertyModel(properties, "model")
        if (!hasAnyModel(baseModel, dynamicModels)) {
            return null
        }
        if (allModelsAreLegacy(baseModel, dynamicModels)) {
            return null
        }

        val type = typeFor(items, dynamicModels)
        return ModelSelection(
            type = type,
            model = selectedBaseModel(type, baseModel, dynamicModels),
            models = dynamicModels,
        )
    }

    private fun nbtConditions(properties: Properties): List<CitNbtCondition> =
        properties.stringPropertyNames()
            .asSequence()
            .filter { it.startsWith("nbt.") }
            .filterNot(::isMetadataNbtKey)
            .mapNotNull { key -> nbtCondition(properties, key) }
            .toList()

    private fun nbtCondition(properties: Properties, key: String): CitNbtCondition? {
        val value = properties.getProperty(key)?.trim()
        if (value.isNullOrBlank()) {
            return null
        }
        return CitNbtCondition.of(key.removePrefix("nbt."), CitText.stripJsonText(value))
    }

    private fun itemList(raw: String?): List<Item> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }

        return raw.trim().split(Regex("\\s+"))
            .map(::namespacedItemId)
            .mapNotNull(::identifier)
            .mapNotNull { BuiltInRegistries.ITEM.getValue(it) }
    }

    private fun dynamicModels(properties: Properties): Map<String, Identifier> =
        buildMap {
            for ((property, key) in dynamicModelProperties) {
                propertyModel(properties, property)?.let { put(key, it) }
            }
        }

    private fun propertyModel(properties: Properties, property: String): Identifier? =
        itemModelId(properties.getProperty(property)?.trim())

    private fun allModelsAreLegacy(baseModel: Identifier?, dynamicModels: Map<String, Identifier>): Boolean {
        if (!isLegacyOptifineModel(baseModel)) {
            return false
        }
        return dynamicModels.values.all(::isLegacyOptifineModel)
    }

    private fun isLegacyOptifineModel(model: Identifier?): Boolean {
        if (model == null) {
            return false
        }
        if (model.namespace != "minecraft") {
            return false
        }
        return legacyModelPrefixes.any { model.path.startsWith(it) }
    }

    private fun typeFor(items: List<Item>, models: Map<String, Identifier>): CitRule.Type {
        for (item in items) {
            val itemPath = BuiltInRegistries.ITEM.getKey(item).path
            val type = dynamicTypeFor(itemPath, models)
            if (type != null) {
                return type
            }
        }
        return CitRule.Type.SIMPLE
    }

    private fun dynamicTypeFor(itemPath: String, models: Map<String, Identifier>): CitRule.Type? =
        when {
            itemPath == "bow" && hasPullingModel(models) -> CitRule.Type.BOW
            itemPath == "crossbow" && hasCrossbowModel(models) -> CitRule.Type.CROSSBOW
            itemPath == "shield" && hasShieldBlockingModel(models) -> CitRule.Type.SHIELD
            else -> null
        }

    private fun hasPullingModel(models: Map<String, Identifier>): Boolean =
        pullingModelKeys.any(models::containsKey)

    private fun hasCrossbowModel(models: Map<String, Identifier>): Boolean =
        crossbowModelKeys.any(models::containsKey)

    private fun hasShieldBlockingModel(models: Map<String, Identifier>): Boolean =
        "blocking" in models

    private fun hasArmorLayerTexture(properties: Properties): Boolean =
        properties.stringPropertyNames()
            .any { it.startsWith("texture.") && "_layer_" in it }

    private fun equipmentAssetId(source: Identifier): Identifier {
        val path = source.path
            .removeKnownRuleRoot()
            .removeSuffix(".properties")
        return Identifier.fromNamespaceAndPath(source.namespace, path)
    }

    private fun selectedBaseModel(
        type: CitRule.Type,
        baseModel: Identifier?,
        dynamicModels: Map<String, Identifier>,
    ): Identifier? {
        if (type != CitRule.Type.SIMPLE) {
            return baseModel
        }
        return baseModel ?: dynamicModels.values.firstOrNull()
    }

    private fun hasAnyModel(baseModel: Identifier?, dynamicModels: Map<String, Identifier>): Boolean =
        listOf(baseModel != null, dynamicModels.isNotEmpty()).any { it }

    private fun specificity(
        nbtConditions: List<CitNbtCondition>,
        hope: String?,
        name: String?,
        namePattern: Boolean,
    ): Int =
        nbtConditions.size * 10 +
            if (hope != null) 10 else 0 +
            if (name != null) nameSpecificity(namePattern) else 0

    private fun nameSpecificity(pattern: Boolean): Int =
        if (pattern) 1 else 2

    private fun Properties.itemListProperty(): String? =
        getProperty("items", getProperty("matchItems"))

    private fun isRuleType(properties: Properties, expectedType: String): Boolean =
        properties.getProperty("type", ITEM_TYPE) == expectedType

    private fun isMetadataNbtKey(key: String): Boolean =
        metadataNbtKeys.contains(key)

    private fun namespacedItemId(itemId: String): String {
        if (":" in itemId) {
            return itemId
        }
        return "minecraft:$itemId"
    }

    private fun String.removeKnownRuleRoot(): String {
        for (root in CitRuleConstants.propertiesRuleRoots) {
            val prefix = "$root/"
            if (startsWith(prefix)) {
                return removePrefix(prefix)
            }
        }
        return this
    }

    private fun firstNonBlank(first: String?, second: String?): String? {
        if (!first.isNullOrBlank()) {
            return first
        }
        return second
    }

    private fun String?.parseIntOr(fallback: Int): Int =
        this?.trim()?.toIntOrNull() ?: fallback

    private val metadataNbtKeys = setOf(
        DISPLAY_NAME_KEY,
        PLAIN_DISPLAY_NAME_KEY,
        hopeKey,
    )

    private val pullingModelKeys = setOf(
        "pulling_0",
        "pulling_1",
        "pulling_2",
        "pulling_3",
        "pulling_4",
    )

    private val crossbowModelKeys = pullingModelKeys + setOf("arrow", "firework")

    private val legacyModelPrefixes = setOf("optifine/", "mcpatcher/")
}

private data class ParsedRuleMetadata(
    val id: String,
    val items: List<Item>,
    val name: ParsedName,
    val hope: String?,
    val nbtConditions: List<CitNbtCondition>,
    val weight: Int,
)

private data class ModelSelection(
    val type: CitRule.Type,
    val model: Identifier?,
    val models: Map<String, Identifier>,
)
