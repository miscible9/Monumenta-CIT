package com.monumenta.reborn.cit;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MonumentaCitClient implements ClientModInitializer {
    public static final String MOD_ID = "monumenta_cit";
    private static final Logger LOGGER = LoggerFactory.getLogger("Monumenta CIT");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Monumenta CIT runtime");
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.of(MOD_ID, "cit_rules");
            }

            @Override
            public void reload(ResourceManager manager) {
                CitRuleManager.reload(manager);
            }
        });
    }
}
