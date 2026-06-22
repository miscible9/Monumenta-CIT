package com.monumenta.reborn.cit.stack

import com.monumenta.reborn.cit.rule.CitRuleConstants
import com.monumenta.reborn.cit.text.CitText
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

data class StackMatchContext(
    val stack: ItemStack,
    val stackName: String?,
    val hope: String?,
    val customData: CompoundTag?,
    val loreLines: List<String>,
) {
    fun stackNameForLog(): String = stackName ?: stack.hoverName.string

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

        fun customDataString(customData: CompoundTag?, parts: List<String>): String? {
            val value = customDataElement(customData, parts) ?: return null
            return value.asString().orElse(value.toString())
        }

        private fun customData(stack: ItemStack): CompoundTag? =
            stack.get(DataComponents.CUSTOM_DATA)?.copyTag()

        private fun customDataElement(customData: CompoundTag?, parts: List<String>): Tag? {
            var current = customData ?: return null
            for ((index, part) in parts.withIndex()) {
                if (index == parts.lastIndex) return current[part]
                val next = current.getCompound(part)
                if (next.isEmpty) return null
                current = next.get()
            }
            return null
        }

        private fun loreLines(stack: ItemStack, customData: CompoundTag?): List<String> {
            val lines = mutableListOf<String>()
            stack.get(DataComponents.LORE)?.lines()?.mapTo(lines, Component::getString)
            addCustomDataLoreLines(customData, displayLoreParts, lines)
            addCustomDataLoreLines(customData, plainDisplayLoreParts, lines)
            return lines.toList()
        }

        private fun addCustomDataLoreLines(customData: CompoundTag?, parts: List<String>, lines: MutableList<String>) {
            when (val lore = customDataElement(customData, parts)) {
                null -> return
                is ListTag -> lore.forEach { lines.add(nbtText(it)) }
                else -> lines.add(nbtText(lore))
            }
        }

        private fun nbtText(value: Tag): String =
            CitText.stripJsonText(value.asString().orElse(value.toString()))

        private fun stackName(stack: ItemStack, customData: CompoundTag?): String {
            val plainName = customDataString(customData, plainDisplayNameParts)
            if (!plainName.isNullOrBlank()) return CitText.stripJsonText(plainName)
            val displayName = customDataString(customData, displayNameParts)
            if (!displayName.isNullOrBlank()) return CitText.stripJsonText(displayName)
            return stack.hoverName.string
        }
    }
}
