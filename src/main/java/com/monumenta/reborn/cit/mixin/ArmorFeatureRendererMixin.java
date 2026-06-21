package com.monumenta.reborn.cit.mixin;

import com.monumenta.reborn.cit.CitRuleManager;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.registry.RegistryKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin {
    @Redirect(
        method = "renderArmor",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/equipment/EquipmentRenderer;render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;II)V"
        )
    )
    private void monumentaCit$applyArmorRule(
        EquipmentRenderer renderer,
        EquipmentModel.LayerType layerType,
        RegistryKey<EquipmentAsset> assetId,
        Model model,
        Object state,
        ItemStack stack,
        MatrixStack matrices,
        OrderedRenderCommandQueue commandQueue,
        int light,
        int outlineColor
    ) {
        renderer.render(layerType, CitRuleManager.resolveArmorAsset(stack, assetId), model, state, stack, matrices, commandQueue, light, outlineColor);
    }
}
