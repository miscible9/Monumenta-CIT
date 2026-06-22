package com.monumenta.reborn.cit.match

import com.monumenta.reborn.cit.rule.ArmorCitRule
import com.monumenta.reborn.cit.rule.CitNbtCondition
import com.monumenta.reborn.cit.rule.CitRule
import com.monumenta.reborn.cit.stack.StackMatchContext
import java.util.Locale
import java.util.regex.Pattern

object CitRuleMatcher {
    fun matches(rule: CitRule, context: StackMatchContext): Boolean {
        if (rule.name != null && !matchesName(rule.name, rule.namePattern, rule.nameIgnoreCase, rule.nameRegex, context.stackName)) return false
        if (rule.hope != null && rule.hope != context.hope) return false
        return matchesNbtConditions(rule.nbtConditions, context)
    }

    fun matches(rule: ArmorCitRule, context: StackMatchContext): Boolean {
        if (rule.name != null && !matchesName(rule.name, rule.namePattern, rule.nameIgnoreCase, rule.nameRegex, context.stackName)) return false
        if (rule.hope != null && rule.hope != context.hope) return false
        return matchesNbtConditions(rule.nbtConditions, context)
    }

    fun matchesExactName(name: String, ignoreCase: Boolean, stackName: String): Boolean =
        if (ignoreCase) name.equals(stackName, ignoreCase = true) else name == stackName

    fun matchesIndexedName(name: String, ignoreCase: Boolean, lowerStackName: String): Boolean =
        ignoreCase && name.lowercase(Locale.ROOT) == lowerStackName

    private fun matchesName(name: String, pattern: Boolean, ignoreCase: Boolean, regex: Pattern?, stackName: String?): Boolean {
        if (stackName == null) return false
        return if (pattern) regex?.matcher(stackName)?.matches() == true else matchesExactName(name, ignoreCase, stackName)
    }

    private fun matchesNbtConditions(conditions: List<CitNbtCondition>, context: StackMatchContext): Boolean {
        for (condition in conditions) {
            if (condition.lorePath) {
                if (!matchesLore(context, condition.expected)) return false
                continue
            }
            val actual = StackMatchContext.customDataString(context.customData, condition.parts)
            if (!matchesNbtValue(condition.expected, actual)) return false
        }
        return true
    }

    private fun matchesNbtValue(expected: String, actual: String?): Boolean {
        if (expected == actual) return true
        if (actual == null) return false
        return normalizeNbtNumber(expected) == normalizeNbtNumber(actual)
    }

    private fun normalizeNbtNumber(value: String): String {
        if (value.length < 2) return value
        val suffix = value.last()
        if (suffix !in "bBsSlLfFdD") return value
        val number = value.dropLast(1)
        return if (number.matches(Regex("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)"))) number else value
    }

    private fun matchesLore(context: StackMatchContext, expected: String): Boolean =
        context.loreLines.any { expected in it }
}
