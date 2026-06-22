package com.monumenta.reborn.cit.mixin;

import com.monumenta.reborn.cit.CitRuleManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelManagerMixin {
    @Inject(method = "appendItemLayers", at = @At("HEAD"), cancellable = true)
    private void monumentaCit$applyRule(
            ItemStackRenderState renderState,
            ItemStack stack,
            ItemDisplayContext displayContext,
            Level level,
            ItemOwner itemOwner,
            int seed,
            CallbackInfo ci
    ) {
        ItemModel model = CitRuleManager.resolveItemModel(stack);
        if (model != null) {
            ClientLevel clientLevel = level instanceof ClientLevel currentLevel ? currentLevel : null;
            renderState.setOversizedInGui(true);
            model.update(renderState, stack, (ItemModelResolver) (Object) this, displayContext, clientLevel, itemOwner, seed);
            ci.cancel();
        }
    }
}
