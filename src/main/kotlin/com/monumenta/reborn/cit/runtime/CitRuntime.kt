package com.monumenta.reborn.cit.runtime

import com.monumenta.reborn.cit.load.CitRuleLoader
import com.monumenta.reborn.cit.match.CitRuleMatcher
import com.monumenta.reborn.cit.model.CitModelSelector
import com.monumenta.reborn.cit.parse.CitRuleParser
import com.monumenta.reborn.cit.rule.ArmorCitRule
import com.monumenta.reborn.cit.rule.CitRule
import com.monumenta.reborn.cit.stack.StackMatchContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.item.model.ItemModel
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.equipment.EquipmentAsset
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.resource.ResourceManager
import net.minecraft.util.Identifier
import org.slf4j.Logger
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

class CitRuntime(private val logger: Logger) {
    @Volatile private var rulesByItem: Map<Item, ItemRuleSet> = emptyMap()
    @Volatile private var armorRulesByItem: Map<Item, ArmorRuleSet> = emptyMap()
    @Volatile private var itemModelCache: MutableMap<Identifier, ItemModel> = ConcurrentHashMap()

    private val itemResolutionCacheLock = Any()
    private val armorResolutionCacheLock = Any()

    @Volatile private var itemResolutionCache: MutableMap<ItemStack, CachedItemResolution> = WeakHashMap()
    @Volatile private var armorResolutionCache: MutableMap<ItemStack, CachedArmorResolution> = WeakHashMap()

    fun reload(manager: ResourceManager) {
        val loadedRules = CitRuleLoader(logger).load(manager)
        val loadedItems = loadedRules.itemRules.mapValues { (_, rules) ->
            rules.sortedWith(compareByDescending<CitRule> { CitRuleParser.specificity(it) }
                .thenByDescending { it.weight }
                .thenBy { it.id })
        }
        val loadedArmor = loadedRules.armorRules.mapValues { (_, rules) ->
            rules.sortedWith(compareByDescending<ArmorCitRule> { CitRuleParser.specificity(it) }
                .thenByDescending { it.weight }
                .thenBy { it.id })
        }

        rulesByItem = RuleSetFactory.itemRuleSets(loadedItems)
        armorRulesByItem = RuleSetFactory.armorRuleSets(loadedArmor)
        itemModelCache = ConcurrentHashMap()
        synchronized(itemResolutionCacheLock) {
            itemResolutionCache = WeakHashMap()
        }
        synchronized(armorResolutionCacheLock) {
            armorResolutionCache = WeakHashMap()
        }

        logger.info(
            "Loaded {} Monumenta CIT item rules and {} armor rules{}",
            loadedItems.values.sumOf { it.size },
            loadedArmor.values.sumOf { it.size },
            if (loadedRules.fromCompiledIndex) " from compiled index" else "",
        )
    }

    fun resolveItemModel(stack: ItemStack): ItemModel? {
        val ruleSet = rulesByItem[stack.item]
        if (ruleSet == null || ruleSet.rules.isEmpty()) return null

        val stackHash = ItemStack.hashCode(stack)
        val dynamicState = if (ruleSet.dynamicModels) CitModelSelector.dynamicState(stack) else 0
        synchronized(itemResolutionCacheLock) {
            itemResolutionCache[stack]?.let { cached ->
                if (cached.matches(stackHash, dynamicState)) return cached.itemModel
            }
        }

        val model = resolveModel(stack, ruleSet)
        if (model == null) {
            synchronized(itemResolutionCacheLock) {
                itemResolutionCache[stack] = CachedItemResolution(stackHash, dynamicState, null)
            }
            return null
        }

        val itemModel = itemModelCache.computeIfAbsent(model) {
            MinecraftClient.getInstance().bakedModelManager.getItemModel(it)
        }
        if (logger.isDebugEnabled) {
            logger.debug("Resolved CIT item model {} -> {}", model, itemModel.javaClass.name)
        }
        synchronized(itemResolutionCacheLock) {
            itemResolutionCache[stack] = CachedItemResolution(stackHash, dynamicState, itemModel)
        }
        return itemModel
    }

    fun resolveArmorAsset(stack: ItemStack, fallback: RegistryKey<EquipmentAsset>): RegistryKey<EquipmentAsset> {
        val ruleSet = armorRulesByItem[stack.item]
        if (ruleSet == null || ruleSet.rules.isEmpty()) return fallback

        val stackHash = ItemStack.hashCode(stack)
        synchronized(armorResolutionCacheLock) {
            armorResolutionCache[stack]?.let { cached ->
                if (cached.matches(stackHash)) return cached.assetKey ?: fallback
            }
        }

        val context = StackMatchContext.create(stack, ruleSet.needsName, ruleSet.needsHope, ruleSet.needsCustomData, ruleSet.needsLore)
        for (rule in ruleSet.candidates(context.stackName)) {
            if (!CitRuleMatcher.matches(rule, context)) continue
            if (logger.isDebugEnabled) {
                logger.debug("Matched armor CIT rule {} for {} named '{}' -> {}", rule.id, Registries.ITEM.getId(stack.item), context.stackNameForLog(), rule.assetKey.value)
            }
            synchronized(armorResolutionCacheLock) {
                armorResolutionCache[stack] = CachedArmorResolution(stackHash, rule.assetKey)
            }
            return rule.assetKey
        }

        synchronized(armorResolutionCacheLock) {
            armorResolutionCache[stack] = CachedArmorResolution(stackHash, null)
        }
        return fallback
    }

    private fun resolveModel(stack: ItemStack, ruleSet: ItemRuleSet): Identifier? {
        val context = StackMatchContext.create(stack, ruleSet.needsName, ruleSet.needsHope, ruleSet.needsCustomData, ruleSet.needsLore)
        val candidates = ruleSet.candidates(context.stackName)
        for (rule in candidates) {
            if (!CitRuleMatcher.matches(rule, context)) continue
            val model = CitModelSelector.select(rule, stack)
            if (model != null) {
                if (logger.isDebugEnabled) {
                    logger.debug("Matched CIT rule {} for {} named '{}' -> {}", rule.id, Registries.ITEM.getId(stack.item), context.stackNameForLog(), model)
                }
                return model
            }
        }
        if (logger.isDebugEnabled) {
            logger.debug(
                "No CIT item rule matched for {} named '{}' candidates={} needsName={} needsHope={} needsCustomData={} needsLore={}",
                Registries.ITEM.getId(stack.item),
                context.stackNameForLog(),
                candidates.size,
                ruleSet.needsName,
                ruleSet.needsHope,
                ruleSet.needsCustomData,
                ruleSet.needsLore,
            )
        }
        return null
    }
}

private data class CachedItemResolution(
    val stackHash: Int,
    val dynamicState: Int,
    val itemModel: ItemModel?,
) {
    fun matches(currentStackHash: Int, currentDynamicState: Int): Boolean =
        stackHash == currentStackHash && dynamicState == currentDynamicState
}

private data class CachedArmorResolution(
    val stackHash: Int,
    val assetKey: RegistryKey<EquipmentAsset>?,
) {
    fun matches(currentStackHash: Int): Boolean =
        stackHash == currentStackHash
}
