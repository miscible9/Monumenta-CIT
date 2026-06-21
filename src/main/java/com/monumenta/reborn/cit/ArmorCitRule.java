package com.monumenta.reborn.cit;

import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.item.Item;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

record ArmorCitRule(
    String id,
    List<Item> items,
    String name,
    boolean namePattern,
    boolean nameIgnoreCase,
    Pattern nameRegex,
    String hope,
    List<CitNbtCondition> nbtConditions,
    int weight,
    Identifier asset,
    RegistryKey<EquipmentAsset> assetKey
) {
}
