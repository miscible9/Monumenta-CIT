package com.monumenta.reborn.cit.load.binary

import com.monumenta.reborn.cit.parse.CitRuleParser
import com.monumenta.reborn.cit.rule.ArmorCitRule
import com.monumenta.reborn.cit.rule.CitNbtCondition
import com.monumenta.reborn.cit.rule.CitRule
import com.monumenta.reborn.cit.rule.CitRuleConstants
import com.monumenta.reborn.cit.rule.ParsedName
import com.monumenta.reborn.cit.text.CitText
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.packs.resources.Resource
import net.minecraft.world.item.Item
import net.minecraft.world.item.equipment.EquipmentAssets
import java.util.zip.GZIPInputStream

class CompiledCitIndexReader {
    fun read(resource: Resource): CompiledCitIndex {
        BinaryIndexReader(GZIPInputStream(resource.open())).use { reader ->
            reader.expectMagic(CitRuleConstants.binaryCompiledIndexMagic)
            val strings = Array(reader.readInt()) { reader.readString() }

            val rawItemRules = reader.readInt()
            val itemRules = buildList {
                repeat(rawItemRules) {
                    readItemRule(reader, strings)?.let(::add)
                }
            }

            val rawArmorRules = reader.readInt()
            val armorRules = buildList {
                repeat(rawArmorRules) {
                    readArmorRule(reader, strings)?.let(::add)
                }
            }

            return CompiledCitIndex(rawItemRules, rawArmorRules, itemRules, armorRules)
        }
    }

    private fun readItemRule(reader: BinaryIndexReader, strings: Array<String>): CitRule? {
        val id = strings[reader.readInt()]
        val items = readItems(reader, strings)
        val parsedName = readName(reader, strings)
        val hope = readNullableString(reader, strings)
        val conditions = readConditions(reader, strings)
        val weight = reader.readInt()
        val type = CitRule.Type.entries[reader.readUnsignedByte()]
        var model = readNullableIdentifier(reader, strings)
        val models = readModels(reader, strings)
        if (model == null && models.isNotEmpty()) model = models.values.first()
        if (items.isEmpty() || (model == null)) return null
        return CitRule(id, items, parsedName.value, parsedName.pattern, parsedName.ignoreCase, parsedName.regex, hope, conditions, weight, type, model, models)
    }

    private fun readArmorRule(reader: BinaryIndexReader, strings: Array<String>): ArmorCitRule? {
        val id = strings[reader.readInt()]
        val items = readItems(reader, strings)
        val parsedName = readName(reader, strings)
        val hope = readNullableString(reader, strings)
        val conditions = readConditions(reader, strings)
        val weight = reader.readInt()
        val asset = readNullableIdentifier(reader, strings) ?: return null
        if (items.isEmpty()) return null
        return ArmorCitRule(
            id = id,
            items = items,
            name = parsedName.value,
            namePattern = parsedName.pattern,
            nameIgnoreCase = parsedName.ignoreCase,
            nameRegex = parsedName.regex,
            hope = hope,
            nbtConditions = conditions,
            weight = weight,
            asset = asset,
            assetKey = ResourceKey.create(EquipmentAssets.ROOT_ID, asset),
        )
    }

    private fun readItems(reader: BinaryIndexReader, strings: Array<String>): List<Item> =
        buildList {
            repeat(reader.readInt()) {
                val id = CitRuleParser.identifier(strings[reader.readInt()]) ?: return@repeat
                BuiltInRegistries.ITEM.getValue(id)?.let(::add)
            }
        }

    private fun readName(reader: BinaryIndexReader, strings: Array<String>): ParsedName {
        if (!reader.readBoolean()) return ParsedName(null, pattern = false, ignoreCase = false, regex = null)
        val value = strings[reader.readInt()]
        val pattern = reader.readBoolean()
        val ignoreCase = reader.readBoolean()
        val regexSyntax = reader.readBoolean()
        return ParsedName(value, pattern, ignoreCase, CitText.compileNamePattern(value, pattern, ignoreCase, regexSyntax))
    }

    private fun readConditions(reader: BinaryIndexReader, strings: Array<String>): List<CitNbtCondition> =
        buildList {
            repeat(reader.readInt()) {
                add(CitNbtCondition.of(strings[reader.readInt()], strings[reader.readInt()]))
            }
        }

    private fun readModels(reader: BinaryIndexReader, strings: Array<String>): Map<String, Identifier> =
        buildMap {
            repeat(reader.readInt()) {
                val key = strings[reader.readInt()]
                val model = CitRuleParser.identifier(strings[reader.readInt()])
                if (model != null) put(key, model)
            }
        }

    private fun readNullableIdentifier(reader: BinaryIndexReader, strings: Array<String>): Identifier? =
        CitRuleParser.identifier(readNullableString(reader, strings))

    private fun readNullableString(reader: BinaryIndexReader, strings: Array<String>): String? {
        val index = reader.readInt()
        return if (index < 0) null else strings[index]
    }
}

data class CompiledCitIndex(
    val rawItemRules: Int,
    val rawArmorRules: Int,
    val itemRules: List<CitRule>,
    val armorRules: List<ArmorCitRule>,
)
