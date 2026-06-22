package com.monumenta.reborn.cit.load

import com.monumenta.reborn.cit.load.binary.CompiledCitIndexReader
import com.monumenta.reborn.cit.parse.CitRuleParser
import com.monumenta.reborn.cit.rule.ArmorCitRule
import com.monumenta.reborn.cit.rule.CitRule
import com.monumenta.reborn.cit.rule.CitRuleConstants
import com.monumenta.reborn.cit.rule.CitRuleLoadResult
import net.minecraft.item.Item
import net.minecraft.resource.Resource
import net.minecraft.resource.ResourceManager
import net.minecraft.util.Identifier
import org.slf4j.Logger
import java.util.Properties

class CitRuleLoader(private val logger: Logger) {
    fun load(manager: ResourceManager): CitRuleLoadResult {
        val itemRules = mutableMapOf<Item, MutableList<CitRule>>()
        val armorRules = mutableMapOf<Item, MutableList<ArmorCitRule>>()
        val fromCompiledIndex = loadCompiledIndex(manager, itemRules, armorRules)
        if (!fromCompiledIndex) {
            loadPropertiesRules(manager, itemRules, armorRules)
        }
        return CitRuleLoadResult(itemRules, armorRules, fromCompiledIndex)
    }

    private fun loadCompiledIndex(
        manager: ResourceManager,
        itemRules: MutableMap<Item, MutableList<CitRule>>,
        armorRules: MutableMap<Item, MutableList<ArmorCitRule>>,
    ): Boolean {
        val resource = manager.getResource(CitRuleConstants.binaryCompiledIndexId).orElse(null) ?: return false
        return try {
            val index = CompiledCitIndexReader().read(resource)
            var itemBindings = 0
            for (rule in index.itemRules) {
                for (item in rule.items) {
                    itemRules.getOrPut(item) { mutableListOf() }.add(rule)
                    itemBindings++
                }
            }

            var armorBindings = 0
            for (rule in index.armorRules) {
                for (item in rule.items) {
                    armorRules.getOrPut(item) { mutableListOf() }.add(rule)
                    armorBindings++
                }
            }

            if (itemBindings == 0 && armorBindings == 0 && (index.rawItemRules > 0 || index.rawArmorRules > 0)) {
                logger.warn(
                    "Compiled Monumenta CIT index {} contained raw rules but resolved zero Minecraft item bindings; falling back to .properties",
                    CitRuleConstants.binaryCompiledIndexId,
                )
                itemRules.clear()
                armorRules.clear()
                false
            } else {
                logger.info(
                    "Using Monumenta CIT compiled index {}: raw item rules {}, raw armor rules {}, item bindings {}, armor bindings {}",
                    CitRuleConstants.binaryCompiledIndexId,
                    index.rawItemRules,
                    index.rawArmorRules,
                    itemBindings,
                    armorBindings,
                )
                true
            }
        } catch (error: Exception) {
            logger.warn("Failed to load Monumenta CIT compiled index {}; falling back to .properties", CitRuleConstants.binaryCompiledIndexId, error)
            itemRules.clear()
            armorRules.clear()
            false
        }
    }

    private fun loadPropertiesRules(
        manager: ResourceManager,
        itemRules: MutableMap<Item, MutableList<CitRule>>,
        armorRules: MutableMap<Item, MutableList<ArmorCitRule>>,
    ) {
        for (root in CitRuleConstants.propertiesRuleRoots) {
            manager.findResources(root) { it.path.endsWith(".properties") }.forEach { (id, resource) ->
                try {
                    loadPropertiesResource(id, resource, itemRules, armorRules)
                } catch (error: Exception) {
                    logger.warn("Failed to load CIT properties from {}", id, error)
                }
            }
        }
    }

    private fun loadPropertiesResource(
        id: Identifier,
        resource: Resource,
        itemRules: MutableMap<Item, MutableList<CitRule>>,
        armorRules: MutableMap<Item, MutableList<ArmorCitRule>>,
    ) {
        val properties = Properties()
        resource.reader.use(properties::load)

        val armorRule = CitRuleParser.parseArmorRule(id, properties)
        if (armorRule != null) {
            for (item in armorRule.items) armorRules.getOrPut(item) { mutableListOf() }.add(armorRule)
            if (logger.isDebugEnabled) {
                logger.debug("Loaded armor CIT properties {} for items {} name '{}' weight {}", id, CitRuleParser.itemIds(armorRule.items), armorRule.name, armorRule.weight)
            }
            return
        }

        val itemRule = CitRuleParser.parseItemRule(id, properties) ?: return
        for (item in itemRule.items) itemRules.getOrPut(item) { mutableListOf() }.add(itemRule)
        if (logger.isDebugEnabled) {
            logger.debug("Loaded CIT properties {} for items {} name '{}' type {} weight {}", id, CitRuleParser.itemIds(itemRule.items), itemRule.name, itemRule.type, itemRule.weight)
        }
    }
}
