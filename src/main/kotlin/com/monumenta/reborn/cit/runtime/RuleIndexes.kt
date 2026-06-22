package com.monumenta.reborn.cit.runtime

import com.monumenta.reborn.cit.match.CitRuleMatcher
import com.monumenta.reborn.cit.rule.ArmorCitRule
import com.monumenta.reborn.cit.rule.CitRule
import net.minecraft.world.item.Item
import java.util.Locale

data class ItemRuleSet(
    val rules: List<CitRule>,
    val candidateIndex: CandidateIndex<CitRule>,
    val needsName: Boolean,
    val needsHope: Boolean,
    val needsCustomData: Boolean,
    val needsLore: Boolean,
    val dynamicModels: Boolean,
) {
    fun candidates(stackName: String?): List<CitRule> = candidateIndex.candidates(stackName)
}

data class ArmorRuleSet(
    val rules: List<ArmorCitRule>,
    val candidateIndex: CandidateIndex<ArmorCitRule>,
    val needsName: Boolean,
    val needsHope: Boolean,
    val needsCustomData: Boolean,
    val needsLore: Boolean,
) {
    fun candidates(stackName: String?): List<ArmorCitRule> = candidateIndex.candidates(stackName)
}

data class CandidateIndex<T>(
    val allRules: List<T>,
    val fallbackRules: List<T>,
    val exactCandidates: Map<String, List<T>>,
    val ignoreCaseCandidates: Map<String, List<T>>,
) {
    fun candidates(stackName: String?): List<T> {
        if (stackName == null) return allRules
        exactCandidates[stackName]?.let { return it }
        return ignoreCaseCandidates[stackName.lowercase(Locale.ROOT)] ?: allRules
    }
}

object RuleSetFactory {
    fun itemRuleSets(loaded: Map<Item, List<CitRule>>): Map<Item, ItemRuleSet> =
        loaded.mapValues { (_, rules) ->
            ItemRuleSet(
                rules = rules.toList(),
                candidateIndex = itemCandidateIndex(rules),
                needsName = rules.any { it.name != null },
                needsHope = rules.any { it.hope != null },
                needsCustomData = rules.any(::hasCustomDataCondition),
                needsLore = rules.any(::hasLoreCondition),
                dynamicModels = rules.any { it.type != CitRule.Type.SIMPLE },
            )
        }

    fun armorRuleSets(loaded: Map<Item, List<ArmorCitRule>>): Map<Item, ArmorRuleSet> =
        loaded.mapValues { (_, rules) ->
            ArmorRuleSet(
                rules = rules.toList(),
                candidateIndex = armorCandidateIndex(rules),
                needsName = rules.any { it.name != null },
                needsHope = rules.any { it.hope != null },
                needsCustomData = rules.any(::hasCustomDataCondition),
                needsLore = rules.any(::hasLoreCondition),
            )
        }

    private fun itemCandidateIndex(rules: List<CitRule>): CandidateIndex<CitRule> {
        val fallbackRules = rules.filter { it.name == null || it.namePattern }
        val exactCandidates = mutableMapOf<String, List<CitRule>>()
        val ignoreCaseCandidates = mutableMapOf<String, List<CitRule>>()
        for (rule in rules) {
            val name = rule.name ?: continue
            if (rule.namePattern) continue
            exactCandidates.getOrPut(name) { itemCandidatesFor(rules, name) }
            if (rule.nameIgnoreCase) {
                ignoreCaseCandidates.getOrPut(name.lowercase(Locale.ROOT)) { itemIgnoreCaseCandidatesFor(rules, name.lowercase(Locale.ROOT)) }
            }
        }
        return CandidateIndex(rules, fallbackRules, exactCandidates, ignoreCaseCandidates)
    }

    private fun armorCandidateIndex(rules: List<ArmorCitRule>): CandidateIndex<ArmorCitRule> {
        val fallbackRules = rules.filter { it.name == null || it.namePattern }
        val exactCandidates = mutableMapOf<String, List<ArmorCitRule>>()
        val ignoreCaseCandidates = mutableMapOf<String, List<ArmorCitRule>>()
        for (rule in rules) {
            val name = rule.name ?: continue
            if (rule.namePattern) continue
            exactCandidates.getOrPut(name) { armorCandidatesFor(rules, name) }
            if (rule.nameIgnoreCase) {
                ignoreCaseCandidates.getOrPut(name.lowercase(Locale.ROOT)) { armorIgnoreCaseCandidatesFor(rules, name.lowercase(Locale.ROOT)) }
            }
        }
        return CandidateIndex(rules, fallbackRules, exactCandidates, ignoreCaseCandidates)
    }

    private fun itemCandidatesFor(rules: List<CitRule>, stackName: String): List<CitRule> =
        rules.filter { it.name == null || it.namePattern || CitRuleMatcher.matchesExactName(it.name, it.nameIgnoreCase, stackName) }

    private fun itemIgnoreCaseCandidatesFor(rules: List<CitRule>, lowerStackName: String): List<CitRule> =
        rules.filter { it.name == null || it.namePattern || CitRuleMatcher.matchesIndexedName(it.name, it.nameIgnoreCase, lowerStackName) }

    private fun armorCandidatesFor(rules: List<ArmorCitRule>, stackName: String): List<ArmorCitRule> =
        rules.filter { it.name == null || it.namePattern || CitRuleMatcher.matchesExactName(it.name, it.nameIgnoreCase, stackName) }

    private fun armorIgnoreCaseCandidatesFor(rules: List<ArmorCitRule>, lowerStackName: String): List<ArmorCitRule> =
        rules.filter { it.name == null || it.namePattern || CitRuleMatcher.matchesIndexedName(it.name, it.nameIgnoreCase, lowerStackName) }

    private fun hasCustomDataCondition(rule: CitRule): Boolean =
        rule.nbtConditions.any { !it.lorePath }

    private fun hasCustomDataCondition(rule: ArmorCitRule): Boolean =
        rule.nbtConditions.any { !it.lorePath }

    private fun hasLoreCondition(rule: CitRule): Boolean =
        rule.nbtConditions.any { it.lorePath }

    private fun hasLoreCondition(rule: ArmorCitRule): Boolean =
        rule.nbtConditions.any { it.lorePath }
}
