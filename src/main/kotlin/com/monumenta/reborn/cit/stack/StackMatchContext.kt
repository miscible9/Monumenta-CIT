package com.monumenta.reborn.cit.stack

import com.monumenta.reborn.cit.rule.CitRuleConstants
import com.monumenta.reborn.cit.text.CitText
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.text.Text

data class StackMatchContext(
    val stack: ItemStack,
    val stackName: String?,
    val hope: String?,
    val customData: NbtCompound?,
    val loreLines: List<String>,
) {
    fun stackNameForLog(): String = stackName ?: stack.name.string

    companion object {
        private val plainDisplayNameParts = listOf("plain", "display", "Name")
        private val displayNameParts = listOf("display", "Name")
        private val plainDisplayLoreParts = listOf("plain", "display", "Lore")
        private val displayLoreParts = listOf("display", "Lore")

        fun create(
            stack: ItemStack,
            needsName: Boolean,
            needsHope: Boolean,
            needsCustomData: Boolean,
            needsLore: Boolean,
        ): StackMatchContext {
            val customData = if (needsName || needsHope || needsCustomData || needsLore) customData(stack) else null
            return StackMatchContext(
                stack = stack,
                stackName = if (needsName) stackName(stack, customData) else null,
                hope = if (needsHope) customDataString(customData, CitRuleConstants.hopeParts) else null,
                customData = customData,
                loreLines = if (needsLore) loreLines(stack, customData) else emptyList(),
            )
        }

        fun customDataString(customData: NbtCompound?, parts: List<String>): String? {
            val value = customDataElement(customData, parts) ?: return null
            return value.asString().orElse(value.toString())
        }

        private fun customData(stack: ItemStack): NbtCompound? =
            stack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt()

        private fun customDataElement(customData: NbtCompound?, parts: List<String>): NbtElement? {
            var current = customData ?: return null
            for ((index, part) in parts.withIndex()) {
                if (index == parts.lastIndex) return current[part]
                val next = current.getCompound(part)
                if (next.isEmpty) return null
                current = next.get()
            }
            return null
        }

        private fun loreLines(stack: ItemStack, customData: NbtCompound?): List<String> {
            val lines = mutableListOf<String>()
            stack.get(DataComponentTypes.LORE)?.lines()?.mapTo(lines, Text::getString)
            addCustomDataLoreLines(customData, displayLoreParts, lines)
            addCustomDataLoreLines(customData, plainDisplayLoreParts, lines)
            return lines.toList()
        }

        private fun addCustomDataLoreLines(customData: NbtCompound?, parts: List<String>, lines: MutableList<String>) {
            when (val lore = customDataElement(customData, parts)) {
                null -> return
                is NbtList -> lore.forEach { lines.add(nbtText(it)) }
                else -> lines.add(nbtText(lore))
            }
        }

        private fun nbtText(value: NbtElement): String =
            CitText.stripJsonText(value.asString().orElse(value.toString()))

        private fun stackName(stack: ItemStack, customData: NbtCompound?): String {
            val plainName = customDataString(customData, plainDisplayNameParts)
            if (!plainName.isNullOrBlank()) return CitText.stripJsonText(plainName)
            val displayName = customDataString(customData, displayNameParts)
            if (!displayName.isNullOrBlank()) return CitText.stripJsonText(displayName)
            return stack.name.string
        }
    }
}
