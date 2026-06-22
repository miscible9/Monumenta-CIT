package com.monumenta.reborn.cit.mixin;

import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemModelResolver.class)
public interface ItemModelResolverAccessor {
    @Invoker("getItemModel")
    ItemModel monumentaCit$getItemModel(Identifier modelId);
}
