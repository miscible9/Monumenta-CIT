package com.monumenta.reborn.cit.model

import com.monumenta.reborn.cit.rule.CitRule
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ChargedProjectiles

object CitModelSelector {
    fun select(rule: CitRule, stack: ItemStack): Identifier? =
        when (rule.type) {
            CitRule.Type.SIMPLE -> rule.model
            CitRule.Type.BOW -> selectBowModel(rule, stack)
            CitRule.Type.CROSSBOW -> selectCrossbowModel(rule, stack)
            CitRule.Type.SHIELD -> selectShieldModel(rule, stack)
        }

    fun dynamicState(stack: ItemStack): Int {
        var state = 0
        if (isActiveStack(stack)) {
            state = state or 1
            val player = Minecraft.getInstance().player
            if (player != null)
                state = state or (useStage(player.useItemRemainingTicks) shl 1)
        }
        val projectiles = stack.get(DataComponents.CHARGED_PROJECTILES)
        if (projectiles != null && !projectiles.isEmpty) {
            state = state or (1 shl 5)
            if (projectiles.contains(Items.FIREWORK_ROCKET)) {
                state = state or (1 shl 6)
            }
        }
        return state
    }

    private fun selectBowModel(rule: CitRule, stack: ItemStack): Identifier? {
        val models = rule.models
        if (isActiveStack(stack)) {
            val useTicks = Minecraft.getInstance().player?.useItemRemainingTicks ?: 0
            if (useTicks >= 20) return first(models, "pulling_4", "pulling_3", "pulling_2", "pulling_1", "pulling_0", "base")
            if (useTicks >= 15) return first(models, "pulling_3", "pulling_2", "pulling_1", "pulling_0", "base")
            if (useTicks >= 10) return first(models, "pulling_2", "pulling_1", "pulling_0", "base")
            if (useTicks >= 5) return first(models, "pulling_1", "pulling_0", "base")
            return first(models, "pulling_0", "base")
        }
        return first(models, "base", "standby")
    }

    private fun selectCrossbowModel(rule: CitRule, stack: ItemStack): Identifier? {
        val models = rule.models
        if (isChargedWithRocket(stack)) return first(models, "firework", "arrow", "base", "standby")
        if (isCharged(stack)) return first(models, "arrow", "base", "standby")
        if (isActiveStack(stack)) {
            val useTicks = Minecraft.getInstance().player?.useItemRemainingTicks ?: 0
            if (useTicks >= 20) return first(models, "pulling_2", "pulling_1", "pulling_0", "standby", "base")
            if (useTicks >= 10) return first(models, "pulling_1", "pulling_0", "standby", "base")
            return first(models, "pulling_0", "standby", "base")
        }
        return first(models, "standby", "base")
    }

    private fun selectShieldModel(rule: CitRule, stack: ItemStack): Identifier? =
        if (isActiveStack(stack)) first(rule.models, "blocking", "base") else first(rule.models, "base")

    private fun isActiveStack(stack: ItemStack): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return player.isUsingItem && ItemStack.isSameItemSameComponents(player.useItem, stack)
    }

    private fun useStage(useTicks: Int): Int =
        when {
            useTicks >= 20 -> 4
            useTicks >= 15 -> 3
            useTicks >= 10 -> 2
            useTicks >= 5 -> 1
            else -> 0
        }

    private fun first(models: Map<String, Identifier>, vararg keys: String): Identifier? =
        keys.firstNotNullOfOrNull(models::get)

    private fun isCharged(stack: ItemStack): Boolean {
        val projectiles: ChargedProjectiles? = stack.get(DataComponents.CHARGED_PROJECTILES)
        return projectiles != null && !projectiles.isEmpty
    }

    private fun isChargedWithRocket(stack: ItemStack): Boolean {
        val projectiles: ChargedProjectiles? = stack.get(DataComponents.CHARGED_PROJECTILES)
        return projectiles != null && projectiles.contains(Items.FIREWORK_ROCKET)
    }
}
