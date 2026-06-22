package com.monumenta.reborn.cit

import com.monumenta.reborn.cit.runtime.CitRuntime
import net.minecraft.client.renderer.item.ItemModel
import net.minecraft.resources.ResourceKey
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.equipment.EquipmentAsset
import org.slf4j.LoggerFactory

object CitRuleManager {
    private val runtime = CitRuntime(LoggerFactory.getLogger("Monumenta CIT"))

    @JvmStatic
    fun reload(manager: ResourceManager) {
        runtime.reload(manager)
    }

    @JvmStatic
    fun resolveItemModel(stack: ItemStack): ItemModel? =
        runtime.resolveItemModel(stack)

    @JvmStatic
    fun resolveArmorAsset(stack: ItemStack, fallback: ResourceKey<EquipmentAsset>): ResourceKey<EquipmentAsset> =
        runtime.resolveArmorAsset(stack, fallback)
}
