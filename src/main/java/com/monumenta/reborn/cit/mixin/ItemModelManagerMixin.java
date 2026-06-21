package com.monumenta.reborn.cit.mixin;

import com.monumenta.reborn.cit.CitRuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelManager.class)
public abstract class ItemModelManagerMixin {
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void monumentaCit$applyRule(
        ItemRenderState renderState,
        ItemStack stack,
        ItemDisplayContext displayContext,
        World world,
        HeldItemContext heldItemContext,
        int seed,
        CallbackInfo ci
    ) {
        ItemModel model = CitRuleManager.resolveItemModel(stack);
        if (model != null) {
            ClientWorld clientWorld = world instanceof ClientWorld currentWorld ? currentWorld : null;
            renderState.setOversizedInGui(true);
            model.update(renderState, stack, (ItemModelManager) (Object) this, displayContext, clientWorld, heldItemContext, seed);
            ci.cancel();
        }
    }
}
