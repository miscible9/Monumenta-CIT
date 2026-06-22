package com.monumenta.reborn.cit

import com.monumenta.reborn.cit.runtime.CitRuntime
import net.minecraft.client.render.item.model.ItemModel
import net.minecraft.item.ItemStack
import net.minecraft.item.equipment.EquipmentAsset
import net.minecraft.registry.RegistryKey
import net.minecraft.resource.ResourceManager
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
    fun resolveArmorAsset(stack: ItemStack, fallback: RegistryKey<EquipmentAsset>): RegistryKey<EquipmentAsset> =
        runtime.resolveArmorAsset(stack, fallback)
}
