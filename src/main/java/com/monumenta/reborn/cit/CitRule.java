package com.monumenta.reborn.cit;

import java.util.Map;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

record CitRule(
    String id,
    List<Item> items,
    String name,
    boolean namePattern,
    boolean nameIgnoreCase,
    Pattern nameRegex,
    String hope,
    List<CitNbtCondition> nbtConditions,
    int weight,
    Type type,
    Identifier model,
    Map<String, Identifier> models
) {
    enum Type {
        SIMPLE,
        BOW,
        CROSSBOW,
        SHIELD
    }
}
