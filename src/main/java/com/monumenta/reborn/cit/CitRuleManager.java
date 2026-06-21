package com.monumenta.reborn.cit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.item.equipment.EquipmentAssetKeys;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CitRuleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Monumenta CIT");
    private static final List<String> PROPERTIES_RULE_ROOTS = List.of("optifine/cit", "mcpatcher/cit");
    private static final String HOPE_PATH = "Monumenta.PlayerModified.Infusions.Hope.Infuser";
    private static final List<String> HOPE_PARTS = List.of(HOPE_PATH.split("\\."));
    private static volatile Map<Item, ItemRuleSet> rulesByItem = Map.of();
    private static volatile Map<Item, ArmorRuleSet> armorRulesByItem = Map.of();
    private static volatile Map<Identifier, ItemModel> itemModelCache = Map.of();

    private CitRuleManager() {
    }

    public static void reload(ResourceManager manager) {
        Map<Item, List<CitRule>> loaded = new HashMap<>();
        Map<Item, List<ArmorCitRule>> loadedArmor = new HashMap<>();
        for (String root : PROPERTIES_RULE_ROOTS) {
            manager.findResources(root, id -> id.getPath().endsWith(".properties")).forEach((id, resource) -> {
                try {
                    loadPropertiesResource(id, resource, loaded, loadedArmor);
                } catch (Exception error) {
                    LOGGER.warn("Failed to load CIT properties from {}", id, error);
                }
            });
        }
        loaded.values().forEach(rules -> rules.sort(
            Comparator.comparingInt((CitRule rule) -> specificity(rule)).reversed()
                .thenComparing(Comparator.comparingInt(CitRule::weight).reversed())
                .thenComparing(CitRule::id)
        ));
        loadedArmor.values().forEach(rules -> rules.sort(
            Comparator.comparingInt((ArmorCitRule rule) -> specificity(rule)).reversed()
                .thenComparing(Comparator.comparingInt(ArmorCitRule::weight).reversed())
                .thenComparing(ArmorCitRule::id)
        ));
        rulesByItem = itemRuleSets(loaded);
        armorRulesByItem = armorRuleSets(loadedArmor);
        itemModelCache = new ConcurrentHashMap<>();
        LOGGER.info(
            "Loaded {} Monumenta CIT item rules and {} armor rules",
            loaded.values().stream().mapToInt(List::size).sum(),
            loadedArmor.values().stream().mapToInt(List::size).sum()
        );
    }

    private static Map<Item, ItemRuleSet> itemRuleSets(Map<Item, List<CitRule>> loaded) {
        Map<Item, ItemRuleSet> ruleSets = new HashMap<>();
        loaded.forEach((item, rules) -> ruleSets.put(item, new ItemRuleSet(
            List.copyOf(rules),
            itemCandidateIndex(rules),
            rules.stream().anyMatch(rule -> rule.name() != null),
            rules.stream().anyMatch(rule -> rule.hope() != null),
            rules.stream().anyMatch(CitRuleManager::hasCustomDataCondition),
            rules.stream().anyMatch(CitRuleManager::hasLoreCondition)
        )));
        return Map.copyOf(ruleSets);
    }

    private static Map<Item, ArmorRuleSet> armorRuleSets(Map<Item, List<ArmorCitRule>> loaded) {
        Map<Item, ArmorRuleSet> ruleSets = new HashMap<>();
        loaded.forEach((item, rules) -> ruleSets.put(item, new ArmorRuleSet(
            List.copyOf(rules),
            armorCandidateIndex(rules),
            rules.stream().anyMatch(rule -> rule.name() != null),
            rules.stream().anyMatch(rule -> rule.hope() != null),
            rules.stream().anyMatch(CitRuleManager::hasCustomDataCondition),
            rules.stream().anyMatch(CitRuleManager::hasLoreCondition)
        )));
        return Map.copyOf(ruleSets);
    }

    private static CandidateIndex<CitRule> itemCandidateIndex(List<CitRule> rules) {
        List<CitRule> fallbackRules = rules.stream()
            .filter(rule -> rule.name() == null || rule.namePattern())
            .toList();
        Map<String, List<CitRule>> exactCandidates = new HashMap<>();
        Map<String, List<CitRule>> ignoreCaseCandidates = new HashMap<>();
        for (CitRule rule : rules) {
            if (rule.name() == null || rule.namePattern()) continue;
            exactCandidates.computeIfAbsent(rule.name(), name -> itemCandidatesFor(rules, name));
            if (rule.nameIgnoreCase()) {
                ignoreCaseCandidates.computeIfAbsent(rule.name().toLowerCase(Locale.ROOT), name -> itemIgnoreCaseCandidatesFor(rules, name));
            }
        }
        return new CandidateIndex<>(fallbackRules, Map.copyOf(exactCandidates), Map.copyOf(ignoreCaseCandidates));
    }

    private static CandidateIndex<ArmorCitRule> armorCandidateIndex(List<ArmorCitRule> rules) {
        List<ArmorCitRule> fallbackRules = rules.stream()
            .filter(rule -> rule.name() == null || rule.namePattern())
            .toList();
        Map<String, List<ArmorCitRule>> exactCandidates = new HashMap<>();
        Map<String, List<ArmorCitRule>> ignoreCaseCandidates = new HashMap<>();
        for (ArmorCitRule rule : rules) {
            if (rule.name() == null || rule.namePattern()) continue;
            exactCandidates.computeIfAbsent(rule.name(), name -> armorCandidatesFor(rules, name));
            if (rule.nameIgnoreCase()) {
                ignoreCaseCandidates.computeIfAbsent(rule.name().toLowerCase(Locale.ROOT), name -> armorIgnoreCaseCandidatesFor(rules, name));
            }
        }
        return new CandidateIndex<>(fallbackRules, Map.copyOf(exactCandidates), Map.copyOf(ignoreCaseCandidates));
    }

    private static List<CitRule> itemCandidatesFor(List<CitRule> rules, String stackName) {
        return rules.stream()
            .filter(rule -> rule.name() == null || rule.namePattern() || matchesExactName(rule.name(), rule.nameIgnoreCase(), stackName))
            .toList();
    }

    private static List<CitRule> itemIgnoreCaseCandidatesFor(List<CitRule> rules, String lowerStackName) {
        return rules.stream()
            .filter(rule -> rule.name() == null || rule.namePattern() || matchesIndexedName(rule.name(), rule.nameIgnoreCase(), lowerStackName))
            .toList();
    }

    private static List<ArmorCitRule> armorCandidatesFor(List<ArmorCitRule> rules, String stackName) {
        return rules.stream()
            .filter(rule -> rule.name() == null || rule.namePattern() || matchesExactName(rule.name(), rule.nameIgnoreCase(), stackName))
            .toList();
    }

    private static List<ArmorCitRule> armorIgnoreCaseCandidatesFor(List<ArmorCitRule> rules, String lowerStackName) {
        return rules.stream()
            .filter(rule -> rule.name() == null || rule.namePattern() || matchesIndexedName(rule.name(), rule.nameIgnoreCase(), lowerStackName))
            .toList();
    }

    private static boolean hasCustomDataCondition(CitRule rule) {
        return rule.nbtConditions().stream().anyMatch(condition -> !condition.lorePath());
    }

    private static boolean hasCustomDataCondition(ArmorCitRule rule) {
        return rule.nbtConditions().stream().anyMatch(condition -> !condition.lorePath());
    }

    private static boolean hasLoreCondition(CitRule rule) {
        return rule.nbtConditions().stream().anyMatch(CitNbtCondition::lorePath);
    }

    private static boolean hasLoreCondition(ArmorCitRule rule) {
        return rule.nbtConditions().stream().anyMatch(CitNbtCondition::lorePath);
    }

    private static Identifier resolveModel(ItemStack stack) {
        ItemRuleSet ruleSet = rulesByItem.get(stack.getItem());
        if (ruleSet == null || ruleSet.rules().isEmpty()) {
            return null;
        }

        StackMatchContext context = StackMatchContext.create(stack, ruleSet.needsName(), ruleSet.needsHope(), ruleSet.needsCustomData(), ruleSet.needsLore());
        for (CitRule rule : ruleSet.candidates(context.stackName())) {
            if (rule.name() != null && !matchesName(rule, context.stackName())) continue;
            if (rule.hope() != null && !rule.hope().equals(context.hope())) continue;
            if (!matchesNbtConditions(rule.nbtConditions(), context)) continue;
            Identifier model = selectModel(rule, stack);
            if (model != null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Matched CIT rule {} for {} named '{}' -> {}", rule.id(), Registries.ITEM.getId(stack.getItem()), context.stackNameForLog(), model);
                }
                return model;
            }
        }
        return null;
    }

    public static ItemModel resolveItemModel(ItemStack stack) {
        Identifier model = resolveModel(stack);
        if (model == null) {
            return null;
        }
        ItemModel itemModel = itemModelCache.computeIfAbsent(
            model,
            id -> MinecraftClient.getInstance().getBakedModelManager().getItemModel(id)
        );
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Resolved CIT item model {} -> {}", model, itemModel.getClass().getName());
        }
        return itemModel;
    }

    public static RegistryKey<EquipmentAsset> resolveArmorAsset(ItemStack stack, RegistryKey<EquipmentAsset> fallback) {
        ArmorRuleSet ruleSet = armorRulesByItem.get(stack.getItem());
        if (ruleSet == null || ruleSet.rules().isEmpty()) {
            return fallback;
        }

        StackMatchContext context = StackMatchContext.create(stack, ruleSet.needsName(), ruleSet.needsHope(), ruleSet.needsCustomData(), ruleSet.needsLore());
        for (ArmorCitRule rule : ruleSet.candidates(context.stackName())) {
            if (rule.name() != null && !matchesName(rule.name(), rule.namePattern(), rule.nameIgnoreCase(), rule.nameRegex(), context.stackName())) continue;
            if (rule.hope() != null && !rule.hope().equals(context.hope())) continue;
            if (!matchesNbtConditions(rule.nbtConditions(), context)) continue;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Matched armor CIT rule {} for {} named '{}' -> {}", rule.id(), Registries.ITEM.getId(stack.getItem()), context.stackNameForLog(), rule.assetKey().getValue());
            }
            return rule.assetKey();
        }
        return fallback;
    }

    private static Identifier selectModel(CitRule rule, ItemStack stack) {
        return switch (rule.type()) {
            case SIMPLE -> rule.model();
            case BOW -> selectBowModel(rule, stack);
            case CROSSBOW -> selectCrossbowModel(rule, stack);
            case SHIELD -> selectShieldModel(rule, stack);
        };
    }

    private static Identifier selectBowModel(CitRule rule, ItemStack stack) {
        Map<String, Identifier> models = rule.models();
        if (isActiveStack(stack)) {
            int useTicks = MinecraftClient.getInstance().player.getItemUseTime();
            if (useTicks >= 20) return first(models, "pulling_4", "pulling_3", "pulling_2", "pulling_1", "pulling_0", "base");
            if (useTicks >= 15) return first(models, "pulling_3", "pulling_2", "pulling_1", "pulling_0", "base");
            if (useTicks >= 10) return first(models, "pulling_2", "pulling_1", "pulling_0", "base");
            if (useTicks >= 5) return first(models, "pulling_1", "pulling_0", "base");
            return first(models, "pulling_0", "base");
        }
        return first(models, "base", "standby");
    }

    private static Identifier selectCrossbowModel(CitRule rule, ItemStack stack) {
        Map<String, Identifier> models = rule.models();
        if (isChargedWithRocket(stack)) return first(models, "firework", "arrow", "base", "standby");
        if (isCharged(stack)) return first(models, "arrow", "base", "standby");
        if (isActiveStack(stack)) {
            int useTicks = MinecraftClient.getInstance().player.getItemUseTime();
            if (useTicks >= 20) return first(models, "pulling_2", "pulling_1", "pulling_0", "standby", "base");
            if (useTicks >= 10) return first(models, "pulling_1", "pulling_0", "standby", "base");
            return first(models, "pulling_0", "standby", "base");
        }
        return first(models, "standby", "base");
    }

    private static Identifier selectShieldModel(CitRule rule, ItemStack stack) {
        if (isActiveStack(stack)) return first(rule.models(), "blocking", "base");
        return first(rule.models(), "base");
    }

    private static boolean isActiveStack(ItemStack stack) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null
            && client.player.isUsingItem()
            && ItemStack.areItemsAndComponentsEqual(client.player.getActiveItem(), stack);
    }

    private static boolean matchesName(CitRule rule, String stackName) {
        return matchesName(rule.name(), rule.namePattern(), rule.nameIgnoreCase(), rule.nameRegex(), stackName);
    }

    private static boolean matchesName(String name, boolean namePattern, boolean nameIgnoreCase, Pattern nameRegex, String stackName) {
        if (namePattern) {
            return nameRegex != null && nameRegex.matcher(stackName).matches();
        }
        return matchesExactName(name, nameIgnoreCase, stackName);
    }

    private static boolean matchesExactName(String name, boolean nameIgnoreCase, String stackName) {
        return nameIgnoreCase ? name.equalsIgnoreCase(stackName) : name.equals(stackName);
    }

    private static boolean matchesIndexedName(String name, boolean nameIgnoreCase, String lowerStackName) {
        return nameIgnoreCase && name.toLowerCase(Locale.ROOT).equals(lowerStackName);
    }

    private static boolean matchesNbtConditions(List<CitNbtCondition> nbtConditions, StackMatchContext context) {
        for (CitNbtCondition condition : nbtConditions) {
            if (condition.lorePath()) {
                if (!matchesLore(context, condition.expected())) return false;
                continue;
            }
            String actual = customDataString(context.customData(), condition.parts());
            if (!condition.expected().equals(actual)) return false;
        }
        return true;
    }

    private static boolean matchesLore(StackMatchContext context, String expected) {
        for (String line : context.loreLines()) {
            if (line.contains(expected)) return true;
        }
        return false;
    }

    private static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (int index = 0; index < glob.length(); index++) {
            char character = glob.charAt(index);
            switch (character) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '\\', '.', '[', ']', '(', ')', '{', '}', '+', '^', '$', '|' -> regex.append('\\').append(character);
                default -> regex.append(character);
            }
        }
        return regex.toString();
    }

    private static Identifier first(Map<String, Identifier> models, String... keys) {
        for (String key : keys) {
            Identifier model = models.get(key);
            if (model != null) return model;
        }
        return null;
    }

    private static boolean isCharged(ItemStack stack) {
        ChargedProjectilesComponent projectiles = stack.get(DataComponentTypes.CHARGED_PROJECTILES);
        return projectiles != null && !projectiles.isEmpty();
    }

    private static boolean isChargedWithRocket(ItemStack stack) {
        ChargedProjectilesComponent projectiles = stack.get(DataComponentTypes.CHARGED_PROJECTILES);
        return projectiles != null && projectiles.contains(Items.FIREWORK_ROCKET);
    }

    private static NbtCompound customData(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        return customData == null ? null : customData.copyNbt();
    }

    private static String customDataString(NbtCompound customData, List<String> parts) {
        if (customData == null) return null;
        NbtCompound current = customData;
        for (int index = 0; index < parts.size(); index++) {
            String part = parts.get(index);
            if (index == parts.size() - 1) {
                NbtElement value = current.get(part);
                return value == null ? null : value.asString().orElse(value.toString());
            }
            Optional<NbtCompound> next = current.getCompound(part);
            if (next.isEmpty()) return null;
            current = next.get();
        }
        return null;
    }

    private static void loadPropertiesResource(Identifier id, Resource resource, Map<Item, List<CitRule>> out, Map<Item, List<ArmorCitRule>> armorOut) throws Exception {
        try (BufferedReader reader = resource.getReader()) {
            Properties properties = new Properties();
            properties.load(reader);
            ArmorCitRule armorRule = parseArmorPropertiesRule(id, properties);
            if (armorRule != null) {
                for (Item item : armorRule.items()) {
                    armorOut.computeIfAbsent(item, ignored -> new ArrayList<>()).add(armorRule);
                }
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Loaded armor CIT properties {} for items {} name '{}' weight {}", id, itemIds(armorRule.items()), armorRule.name(), armorRule.weight());
                }
                return;
            }
            CitRule rule = parsePropertiesRule(id, properties);
            if (rule != null) {
                for (Item item : rule.items()) {
                    out.computeIfAbsent(item, ignored -> new ArrayList<>()).add(rule);
                }
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Loaded CIT properties {} for items {} name '{}' type {} weight {}", id, itemIds(rule.items()), rule.name(), rule.type(), rule.weight());
                }
            }
        }
    }

    private static CitRule parsePropertiesRule(Identifier source, Properties properties) {
        if (!"item".equals(properties.getProperty("type", "item"))) return null;
        List<Item> items = items(properties.getProperty("items", properties.getProperty("matchItems")));
        if (items.isEmpty()) return null;

        ParsedName parsedName = parseName(firstNonBlank(properties.getProperty("nbt.display.Name"), properties.getProperty("nbt.plain.display.Name")));
        String hope = properties.getProperty("nbt." + HOPE_PATH);
        List<CitNbtCondition> nbtConditions = nbtConditions(properties);
        Map<String, Identifier> models = propertiesModels(properties);
        Identifier model = propertyModel(properties, "model");
        if (model == null && models.isEmpty()) return null;
        if (isLegacyOptifineModel(model) && models.values().stream().allMatch(CitRuleManager::isLegacyOptifineModel)) {
            LOGGER.debug("Ignoring legacy OptiFine CIT rule {} with unresolved model references", source);
            return null;
        }
        CitRule.Type type = typeFor(items, models);
        if (type == CitRule.Type.SIMPLE && model == null && !models.isEmpty()) {
            model = models.values().iterator().next();
        }
        int weight = parseInt(properties.getProperty("weight"), 0);
        return new CitRule(source.toString(), List.copyOf(items), parsedName.value(), parsedName.pattern(), parsedName.ignoreCase(), parsedName.regex(), hope, List.copyOf(nbtConditions), weight, type, model, Map.copyOf(models));
    }

    private static ArmorCitRule parseArmorPropertiesRule(Identifier source, Properties properties) {
        if (!"armor".equals(properties.getProperty("type", "item"))) return null;
        List<Item> items = items(properties.getProperty("items", properties.getProperty("matchItems")));
        if (items.isEmpty() || !hasArmorLayerTexture(properties)) return null;

        ParsedName parsedName = parseName(firstNonBlank(properties.getProperty("nbt.display.Name"), properties.getProperty("nbt.plain.display.Name")));
        String hope = properties.getProperty("nbt." + HOPE_PATH);
        List<CitNbtCondition> nbtConditions = nbtConditions(properties);
        int weight = parseInt(properties.getProperty("weight"), 0);
        Identifier asset = equipmentAssetId(source);
        return new ArmorCitRule(
            source.toString(),
            List.copyOf(items),
            parsedName.value(),
            parsedName.pattern(),
            parsedName.ignoreCase(),
            parsedName.regex(),
            hope,
            List.copyOf(nbtConditions),
            weight,
            asset,
            RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, asset)
        );
    }

    private static boolean hasArmorLayerTexture(Properties properties) {
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("texture.") && key.contains("_layer_")) return true;
        }
        return false;
    }

    private static List<CitNbtCondition> nbtConditions(Properties properties) {
        List<CitNbtCondition> conditions = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("nbt.")) continue;
            if ("nbt.display.Name".equals(key) || "nbt.plain.display.Name".equals(key) || ("nbt." + HOPE_PATH).equals(key)) continue;
            String path = key.substring("nbt.".length());
            String value = properties.getProperty(key);
            if (value != null && !value.isBlank()) conditions.add(CitNbtCondition.of(path, stripJsonText(value.trim())));
        }
        return conditions;
    }

    private static int specificity(CitRule rule) {
        int score = rule.nbtConditions().size() * 10;
        if (rule.hope() != null) score += 10;
        if (rule.name() != null) score += rule.namePattern() ? 1 : 2;
        return score;
    }

    private static int specificity(ArmorCitRule rule) {
        int score = rule.nbtConditions().size() * 10;
        if (rule.hope() != null) score += 10;
        if (rule.name() != null) score += rule.namePattern() ? 1 : 2;
        return score;
    }

    private static List<Item> items(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<Item> items = new ArrayList<>();
        String[] tokens = raw.trim().split("\\s+");
        for (String token : tokens) {
            String itemId = token.contains(":") ? token : "minecraft:" + token;
            Item item = Registries.ITEM.get(Identifier.of(itemId));
            if (item != null) items.add(item);
        }
        return items;
    }

    private static String itemIds(List<Item> items) {
        return items.stream().map(item -> Registries.ITEM.getId(item).toString()).toList().toString();
    }

    private static ParsedName parseName(String raw) {
        if (raw == null || raw.isBlank()) return new ParsedName(null, false, false, null);
        boolean pattern = false;
        boolean ignoreCase = false;
        String value = raw;
        if (value.startsWith("ipattern:")) {
            pattern = true;
            ignoreCase = true;
            value = value.substring("ipattern:".length());
        } else if (value.startsWith("pattern:")) {
            pattern = true;
            value = value.substring("pattern:".length());
        } else if (value.startsWith("iregex:")) {
            pattern = true;
            ignoreCase = true;
            value = value.substring("iregex:".length());
        } else if (value.startsWith("regex:")) {
            pattern = true;
            value = value.substring("regex:".length());
        }
        String stripped = stripJsonText(value);
        return new ParsedName(stripped, pattern, ignoreCase, compileNamePattern(stripped, pattern, ignoreCase));
    }

    private static Pattern compileNamePattern(String name, boolean namePattern, boolean nameIgnoreCase) {
        if (!namePattern) return null;
        int flags = nameIgnoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
        try {
            return Pattern.compile(globToRegex(name), flags);
        } catch (PatternSyntaxException ignored) {
            return null;
        }
    }

    private static String stripJsonText(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("{") && trimmed.contains("\"text\"")) {
            try {
                JsonObject object = JsonParser.parseString(trimmed).getAsJsonObject();
                if (object.has("text")) return object.get("text").getAsString();
            } catch (Exception ignored) {
                return value;
            }
        }
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return value;
    }

    private static Map<String, Identifier> propertiesModels(Properties properties) {
        Map<String, Identifier> models = new HashMap<>();
        putPropertyModel(properties, models, "model", "base");
        putPropertyModel(properties, models, "model.bow_pulling_0", "pulling_0");
        putPropertyModel(properties, models, "model.bow_pulling_1", "pulling_1");
        putPropertyModel(properties, models, "model.bow_pulling_2", "pulling_2");
        putPropertyModel(properties, models, "model.bow_pulling_3", "pulling_3");
        putPropertyModel(properties, models, "model.bow_pulling_4", "pulling_4");
        putPropertyModel(properties, models, "model.crossbow_standby", "standby");
        putPropertyModel(properties, models, "model.crossbow_pulling_0", "pulling_0");
        putPropertyModel(properties, models, "model.crossbow_pulling_1", "pulling_1");
        putPropertyModel(properties, models, "model.crossbow_pulling_2", "pulling_2");
        putPropertyModel(properties, models, "model.crossbow_arrow", "arrow");
        putPropertyModel(properties, models, "model.crossbow_firework", "firework");
        putPropertyModel(properties, models, "model.shield_blocking", "blocking");
        return models;
    }

    private static void putPropertyModel(Properties properties, Map<String, Identifier> models, String property, String key) {
        Identifier model = propertyModel(properties, property);
        if (model != null) {
            models.put(key, model);
        }
    }

    private static Identifier propertyModel(Properties properties, String property) {
        String value = properties.getProperty(property);
        return value == null || value.isBlank() ? null : itemModelId(value.trim());
    }

    private static boolean isLegacyOptifineModel(Identifier model) {
        return model != null
            && "minecraft".equals(model.getNamespace())
            && (model.getPath().startsWith("optifine/") || model.getPath().startsWith("mcpatcher/"));
    }

    private static CitRule.Type typeFor(List<Item> items, Map<String, Identifier> models) {
        for (Item item : items) {
            Identifier itemId = Registries.ITEM.getId(item);
            if ("bow".equals(itemId.getPath()) && hasPullingModel(models)) return CitRule.Type.BOW;
            if ("crossbow".equals(itemId.getPath()) && hasCrossbowModel(models)) return CitRule.Type.CROSSBOW;
            if ("shield".equals(itemId.getPath()) && models.containsKey("blocking")) return CitRule.Type.SHIELD;
        }
        return CitRule.Type.SIMPLE;
    }

    private static boolean hasPullingModel(Map<String, Identifier> models) {
        return models.containsKey("pulling_0")
            || models.containsKey("pulling_1")
            || models.containsKey("pulling_2")
            || models.containsKey("pulling_3")
            || models.containsKey("pulling_4");
    }

    private static boolean hasCrossbowModel(Map<String, Identifier> models) {
        return hasPullingModel(models) || models.containsKey("arrow") || models.containsKey("firework");
    }

    private static Identifier itemModelId(String raw) {
        Identifier id;
        try {
            id = Identifier.of(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        if (id.getPath().startsWith("item/")) {
            return Identifier.of(id.getNamespace(), id.getPath().substring("item/".length()));
        }
        return id;
    }

    private static Identifier equipmentAssetId(Identifier source) {
        String path = source.getPath();
        for (String root : PROPERTIES_RULE_ROOTS) {
            String prefix = root + "/";
            if (path.startsWith(prefix)) {
                path = path.substring(prefix.length());
                break;
            }
        }
        if (path.endsWith(".properties")) {
            path = path.substring(0, path.length() - ".properties".length());
        }
        return Identifier.of(source.getNamespace(), path);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second;
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record ItemRuleSet(
        List<CitRule> rules,
        CandidateIndex<CitRule> candidateIndex,
        boolean needsName,
        boolean needsHope,
        boolean needsCustomData,
        boolean needsLore
    ) {
        private List<CitRule> candidates(String stackName) {
            if (stackName == null) return rules;
            List<CitRule> exact = candidateIndex.exactCandidates().get(stackName);
            if (exact != null) return exact;
            List<CitRule> ignoreCase = candidateIndex.ignoreCaseCandidates().get(stackName.toLowerCase(Locale.ROOT));
            return ignoreCase != null ? ignoreCase : candidateIndex.fallbackRules();
        }
    }

    private record ArmorRuleSet(
        List<ArmorCitRule> rules,
        CandidateIndex<ArmorCitRule> candidateIndex,
        boolean needsName,
        boolean needsHope,
        boolean needsCustomData,
        boolean needsLore
    ) {
        private List<ArmorCitRule> candidates(String stackName) {
            if (stackName == null) return rules;
            List<ArmorCitRule> exact = candidateIndex.exactCandidates().get(stackName);
            if (exact != null) return exact;
            List<ArmorCitRule> ignoreCase = candidateIndex.ignoreCaseCandidates().get(stackName.toLowerCase(Locale.ROOT));
            return ignoreCase != null ? ignoreCase : candidateIndex.fallbackRules();
        }
    }

    private record CandidateIndex<T>(
        List<T> fallbackRules,
        Map<String, List<T>> exactCandidates,
        Map<String, List<T>> ignoreCaseCandidates
    ) {
    }

    private record StackMatchContext(
        ItemStack stack,
        String stackName,
        String hope,
        NbtCompound customData,
        List<String> loreLines
    ) {
        private static StackMatchContext create(ItemStack stack, boolean needsName, boolean needsHope, boolean needsCustomData, boolean needsLore) {
            NbtCompound customData = needsHope || needsCustomData ? CitRuleManager.customData(stack) : null;
            return new StackMatchContext(
                stack,
                needsName ? stack.getName().getString() : null,
                needsHope ? customDataString(customData, HOPE_PARTS) : null,
                customData,
                needsLore ? CitRuleManager.loreLines(stack) : List.of()
            );
        }

        private String stackNameForLog() {
            return stackName != null ? stackName : stack.getName().getString();
        }
    }

    private static List<String> loreLines(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null || lore.lines().isEmpty()) return List.of();
        return lore.lines().stream().map(line -> line.getString()).toList();
    }

    private record ParsedName(String value, boolean pattern, boolean ignoreCase, Pattern regex) {
    }
}
