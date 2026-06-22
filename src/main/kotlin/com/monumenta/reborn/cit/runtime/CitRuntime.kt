package com.monumenta.reborn.cit.runtime

import com.monumenta.reborn.cit.load.CitRuleLoader
import com.monumenta.reborn.cit.match.CitRuleMatcher
import com.monumenta.reborn.cit.mixin.ItemModelResolverAccessor
import com.monumenta.reborn.cit.model.CitModelSelector
import com.monumenta.reborn.cit.parse.CitRuleParser
import com.monumenta.reborn.cit.rule.ArmorCitRule
import com.monumenta.reborn.cit.rule.CitRule
import com.monumenta.reborn.cit.stack.StackMatchContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.item.ItemModel
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.equipment.EquipmentAsset
import org.slf4j.Logger
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

private const val NO_ITEM_RULE_MATCH_LOG =
    "No CIT item rule matched for {} named '{}' candidates={} needsName={} needsHope={} needsCustomData={} needsLore={}"
private const val ARMOR_RESOLUTION_CACHE_LIMIT = 4096

class CitRuntime(private val logger: Logger) {
    @Volatile
    private var rulesByItem: Map<Item, ItemRuleSet> = emptyMap()
    @Volatile
    private var armorRulesByItem: Map<Item, ArmorRuleSet> = emptyMap()
    @Volatile
    private var itemModelCache: MutableMap<Identifier, ItemModel> = ConcurrentHashMap()

    private val itemResolutionCacheLock = Any()
    private val armorResolutionCacheLock = Any()

    // Key cached resolutions by ItemStack identity so cache lookups never call
    // ItemStack.hashCode(), which recursively hashes expensive component data.
    @Volatile
    private var itemResolutionCache = WeakIdentityStackCache<CachedItemResolution>()
    @Volatile
    private var armorResolutionCache = BoundedArmorResolutionCache()

    fun reload(manager: ResourceManager) {
        val loadedRules = CitRuleLoader(logger).load(manager)
        val loadedItems = loadedRules.itemRules.mapValues { (_, rules) ->
            rules.sortedWith(
                compareByDescending<CitRule> { CitRuleParser.specificity(it) }
                    .thenByDescending { it.weight }
                    .thenBy { it.id },
            )
        }
        val loadedArmor = loadedRules.armorRules.mapValues { (_, rules) ->
            rules.sortedWith(
                compareByDescending<ArmorCitRule> { CitRuleParser.specificity(it) }
                    .thenByDescending { it.weight }
                    .thenBy { it.id },
            )
        }

        rulesByItem = RuleSetFactory.itemRuleSets(loadedItems)
        armorRulesByItem = RuleSetFactory.armorRuleSets(loadedArmor)
        itemModelCache = ConcurrentHashMap()
        synchronized(itemResolutionCacheLock) {
            itemResolutionCache = WeakIdentityStackCache()
        }
        synchronized(armorResolutionCacheLock) {
            armorResolutionCache = BoundedArmorResolutionCache()
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
        if (ruleSet == null) {
            return null
        }
        if (ruleSet.rules.isEmpty()) {
            return null
        }

        val dynamicState = if (ruleSet.dynamicModels) CitModelSelector.dynamicState(stack) else 0
        val signature = StackResolutionSignature.forItem(stack, ruleSet, dynamicState)
        synchronized(itemResolutionCacheLock) {
            itemResolutionCache.get(stack)?.let { cached ->
                if (cached.matches(signature)) {
                    return cached.itemModel
                }
            }
        }

        val itemModel = resolveCachedItemModel(stack, ruleSet)
        synchronized(itemResolutionCacheLock) {
            itemResolutionCache.put(stack, CachedItemResolution(signature, itemModel))
        }
        return itemModel
    }

    fun resolveArmorAsset(stack: ItemStack, fallback: ResourceKey<EquipmentAsset>): ResourceKey<EquipmentAsset> {
        val ruleSet = armorRulesByItem[stack.item]
        if (ruleSet == null) {
            return fallback
        }
        if (ruleSet.rules.isEmpty()) {
            return fallback
        }

        val cacheKey = ArmorResolutionCacheKey.from(stack, ruleSet)
        val cachedAsset = getCachedArmorAsset(cacheKey)
        if (cachedAsset.hit) {
            return cachedAsset.assetKey ?: fallback
        }

        val resolvedAsset = resolveArmorAssetUncached(stack, ruleSet)
        putCachedArmorAsset(cacheKey, resolvedAsset)
        return resolvedAsset ?: fallback
    }

    private fun getCachedArmorAsset(cacheKey: ArmorResolutionCacheKey): ArmorCacheLookup =
        synchronized(armorResolutionCacheLock) {
            armorResolutionCache.get(cacheKey)
        }

    private fun putCachedArmorAsset(
        cacheKey: ArmorResolutionCacheKey,
        assetKey: ResourceKey<EquipmentAsset>?,
    ) {
        synchronized(armorResolutionCacheLock) {
            armorResolutionCache.put(cacheKey, assetKey)
        }
    }

    private fun resolveArmorAssetUncached(
        stack: ItemStack,
        ruleSet: ArmorRuleSet,
    ): ResourceKey<EquipmentAsset>? {
        val context = StackMatchContext.create(
            stack,
            ruleSet.needsName,
            ruleSet.needsHope,
            ruleSet.needsCustomData,
            ruleSet.needsLore
        )
        for (rule in ruleSet.candidates(context.stackName)) {
            if (!CitRuleMatcher.matches(rule, context)) {
                continue
            }
            if (logger.isDebugEnabled) {
                logger.debug(
                    "Matched armor CIT rule {} for {} named '{}' -> {}",
                    rule.id,
                    BuiltInRegistries.ITEM.getKey(stack.item),
                    context.stackNameForLog(),
                    rule.assetKey.identifier()
                )
            }
            return rule.assetKey
        }
        return null
    }

    private fun resolveCachedItemModel(stack: ItemStack, ruleSet: ItemRuleSet): ItemModel? {
        val model = resolveModel(stack, ruleSet) ?: return null

        val itemModel = itemModelCache.computeIfAbsent(model) {
            (Minecraft.getInstance().itemModelResolver as ItemModelResolverAccessor).`monumentaCit$getItemModel`(it)
        }

        if (logger.isDebugEnabled) {
            logger.debug("Resolved CIT item model {} -> {}", model, itemModel.javaClass.name)
        }

        return itemModel
    }

    private fun resolveModel(stack: ItemStack, ruleSet: ItemRuleSet): Identifier? {
        val context = StackMatchContext.create(
            stack,
            ruleSet.needsName,
            ruleSet.needsHope,
            ruleSet.needsCustomData,
            ruleSet.needsLore
        )
        val candidates = ruleSet.candidates(context.stackName)
        for (rule in candidates) {
            if (!CitRuleMatcher.matches(rule, context)) {
                continue
            }

            val model = CitModelSelector.select(rule, stack)
            if (model != null) {
                if (logger.isDebugEnabled) {
                    logger.debug(
                        "Matched CIT rule {} for {} named '{}' -> {}",
                        rule.id,
                        BuiltInRegistries.ITEM.getKey(stack.item),
                        context.stackNameForLog(),
                        model
                    )
                }
                return model
            }
        }
        if (logger.isDebugEnabled) {
            logger.debug(
                NO_ITEM_RULE_MATCH_LOG,
                BuiltInRegistries.ITEM.getKey(stack.item),
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
    val signature: StackResolutionSignature,
    val itemModel: ItemModel?,
) {
    fun matches(currentSignature: StackResolutionSignature): Boolean =
        signature.matches(currentSignature)
}

private class StackResolutionSignature(
    private val customData: Any?,
    private val customName: Any?,
    private val itemName: Any?,
    private val lore: Any?,
    private val chargedProjectiles: Any?,
    private val dynamicState: Int,
) {
    fun matches(other: StackResolutionSignature): Boolean =
        this == other

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is StackResolutionSignature) {
            return false
        }
        return customData === other.customData &&
            customName === other.customName &&
            itemName === other.itemName &&
            lore === other.lore &&
            chargedProjectiles === other.chargedProjectiles &&
            dynamicState == other.dynamicState
    }

    override fun hashCode(): Int {
        var result = identityHash(customData)
        result = 31 * result + identityHash(customName)
        result = 31 * result + identityHash(itemName)
        result = 31 * result + identityHash(lore)
        result = 31 * result + identityHash(chargedProjectiles)
        result = 31 * result + dynamicState
        return result
    }

    companion object {
        fun forItem(stack: ItemStack, ruleSet: ItemRuleSet, dynamicState: Int): StackResolutionSignature {
            val chargedProjectiles =
                if (ruleSet.dynamicModels) stack.get(DataComponents.CHARGED_PROJECTILES) else null
            return create(
                stack = stack,
                needsName = ruleSet.needsName,
                needsHope = ruleSet.needsHope,
                needsCustomData = ruleSet.needsCustomData,
                needsLore = ruleSet.needsLore,
                chargedProjectiles = chargedProjectiles,
                dynamicState = dynamicState,
            )
        }

        fun forArmor(stack: ItemStack, ruleSet: ArmorRuleSet): StackResolutionSignature =
            create(
                stack = stack,
                needsName = ruleSet.needsName,
                needsHope = ruleSet.needsHope,
                needsCustomData = ruleSet.needsCustomData,
                needsLore = ruleSet.needsLore,
                chargedProjectiles = null,
                dynamicState = 0,
            )

        private fun create(
            stack: ItemStack,
            needsName: Boolean,
            needsHope: Boolean,
            needsCustomData: Boolean,
            needsLore: Boolean,
            chargedProjectiles: Any?,
            dynamicState: Int,
        ): StackResolutionSignature {
            // Match invalidation tracks only the components CIT rules can read.
            // Component replacement changes these references without deep hashing lore/NBT.
            val usesCustomData = usesLegacyCustomData(
                needsName,
                needsHope,
                needsCustomData,
                needsLore,
            )
            return StackResolutionSignature(
                customData = if (usesCustomData) stack.get(DataComponents.CUSTOM_DATA) else null,
                customName = if (needsName) stack.get(DataComponents.CUSTOM_NAME) else null,
                itemName = if (needsName) stack.get(DataComponents.ITEM_NAME) else null,
                lore = if (needsLore) stack.get(DataComponents.LORE) else null,
                chargedProjectiles = chargedProjectiles,
                dynamicState = dynamicState,
            )
        }

        private fun usesLegacyCustomData(
            needsName: Boolean,
            needsHope: Boolean,
            needsCustomData: Boolean,
            needsLore: Boolean,
        ): Boolean =
            listOf(needsName, needsHope, needsCustomData, needsLore).any { it }
    }
}

private class ArmorResolutionCacheKey private constructor(
    private val item: Item,
    private val signature: StackResolutionSignature,
) {
    private val identityHash = 31 * identityHash(item) + signature.hashCode()

    override fun hashCode(): Int =
        identityHash

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is ArmorResolutionCacheKey) {
            return false
        }
        return item === other.item && signature == other.signature
    }

    companion object {
        fun from(stack: ItemStack, ruleSet: ArmorRuleSet): ArmorResolutionCacheKey =
            ArmorResolutionCacheKey(
                item = stack.item,
                signature = StackResolutionSignature.forArmor(stack, ruleSet),
            )
    }
}

private class BoundedArmorResolutionCache {
    private val values = object : LinkedHashMap<ArmorResolutionCacheKey, ResourceKey<EquipmentAsset>?>(
        ARMOR_RESOLUTION_CACHE_LIMIT,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<ArmorResolutionCacheKey, ResourceKey<EquipmentAsset>?>,
        ): Boolean =
            size > ARMOR_RESOLUTION_CACHE_LIMIT
    }

    fun get(key: ArmorResolutionCacheKey): ArmorCacheLookup {
        val assetKey = values[key]
        if (assetKey != null) {
            return ArmorCacheLookup.hit(assetKey)
        }
        if (values.containsKey(key)) {
            return ArmorCacheLookup.hit(null)
        }
        return ArmorCacheLookup.miss()
    }

    fun put(key: ArmorResolutionCacheKey, assetKey: ResourceKey<EquipmentAsset>?) {
        values[key] = assetKey
    }
}

private data class ArmorCacheLookup(
    val hit: Boolean,
    val assetKey: ResourceKey<EquipmentAsset>?,
) {
    companion object {
        fun hit(assetKey: ResourceKey<EquipmentAsset>?): ArmorCacheLookup =
            ArmorCacheLookup(hit = true, assetKey = assetKey)

        fun miss(): ArmorCacheLookup =
            ArmorCacheLookup(hit = false, assetKey = null)
    }
}

// WeakHashMap is not suitable here because it calls ItemStack.hashCode() during
// lookup. This cache keeps weak keys while comparing live stacks by reference.
private class WeakIdentityStackCache<V> {
    private val queue = ReferenceQueue<ItemStack>()
    private val values = HashMap<WeakIdentityStackKey, V>()

    fun get(stack: ItemStack): V? {
        drainQueue()
        return values[WeakIdentityStackKey.lookup(stack)]
    }

    fun put(stack: ItemStack, value: V) {
        drainQueue()
        values[WeakIdentityStackKey.weak(stack, queue)] = value
    }

    private fun drainQueue() {
        // Remove entries whose ItemStack keys were collected so identity caching
        // does not retain stale rendered stacks across inventory churn.
        while (true) {
            val key = queue.poll() as? WeakIdentityStackKey
            if (key == null) {
                return
            }
            values.remove(key)
        }
    }
}

private fun identityHash(value: Any?): Int =
    value?.let(System::identityHashCode) ?: 0

private class WeakIdentityStackKey private constructor(
    stack: ItemStack,
    queue: ReferenceQueue<ItemStack>?,
) : WeakReference<ItemStack>(stack, queue) {
    private val identityHash = System.identityHashCode(stack)

    override fun hashCode(): Int = identityHash

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is WeakIdentityStackKey) {
            return false
        }
        val stack = get()
        if (stack == null) {
            return false
        }
        return stack === other.get()
    }

    companion object {
        fun weak(stack: ItemStack, queue: ReferenceQueue<ItemStack>): WeakIdentityStackKey =
            WeakIdentityStackKey(stack, queue)

        fun lookup(stack: ItemStack): WeakIdentityStackKey =
            WeakIdentityStackKey(stack, null)
    }
}
