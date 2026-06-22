package com.monumenta.reborn.cit.rule

import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.equipment.EquipmentAsset
import java.util.regex.Pattern

data class CitRule(
    val id: String,
    val items: List<Item>,
    val name: String?,
    val namePattern: Boolean,
    val nameIgnoreCase: Boolean,
    val nameRegex: Pattern?,
    val hope: String?,
    val nbtConditions: List<CitNbtCondition>,
    val weight: Int,
    val type: Type,
    val model: Identifier?,
    val models: Map<String, Identifier>,
) {
    enum class Type {
        SIMPLE,
        BOW,
        CROSSBOW,
        SHIELD,
    }
}

data class ArmorCitRule(
    val id: String,
    val items: List<Item>,
    val name: String?,
    val namePattern: Boolean,
    val nameIgnoreCase: Boolean,
    val nameRegex: Pattern?,
    val hope: String?,
    val nbtConditions: List<CitNbtCondition>,
    val weight: Int,
    val asset: Identifier,
    val assetKey: ResourceKey<EquipmentAsset>,
)

data class CitNbtCondition(
    val path: String,
    val parts: List<String>,
    val expected: String,
    val lorePath: Boolean,
) {
    companion object {
        fun of(path: String, expected: String): CitNbtCondition =
            CitNbtCondition(
                path = path,
                parts = path.split("."),
                expected = expected,
                lorePath = path.startsWith("display.Lore") || path.startsWith("plain.display.Lore"),
            )
    }
}

data class ParsedName(
    val value: String?,
    val pattern: Boolean,
    val ignoreCase: Boolean,
    val regex: Pattern?,
)

data class CitRuleLoadResult(
    val itemRules: Map<Item, List<CitRule>>,
    val armorRules: Map<Item, List<ArmorCitRule>>,
    val fromCompiledIndex: Boolean,
)
