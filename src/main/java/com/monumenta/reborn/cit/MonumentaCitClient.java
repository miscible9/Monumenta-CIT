package com.monumenta.reborn.cit;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MonumentaCitClient implements ClientModInitializer {
    public static final String MOD_ID = "monumenta_cit";
    private static final Logger LOGGER = LoggerFactory.getLogger("Monumenta CIT");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Monumenta CIT runtime");
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public @NonNull Identifier getFabricId() {
                return Identifier.fromNamespaceAndPath(MOD_ID, "cit_rules");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                CitRuleManager.reload(manager);
            }
        });
    }
}
