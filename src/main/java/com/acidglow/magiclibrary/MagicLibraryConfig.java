package com.acidglow.magiclibrary;

import com.acidglow.magiclibrary.config.EnchantCapConfigLoader;
import com.acidglow.magiclibrary.content.library.MagicLibraryTier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jspecify.annotations.Nullable;

public final class MagicLibraryConfig {
    public record AmplificationRule(boolean canAmplify, int levelCap, String scalingRuleId, String scalingDescription) {
        public AmplificationRule {
            levelCap = Math.max(1, levelCap);
            scalingRuleId = sanitizeScalingToken(scalingRuleId, "fallback_disabled");
            scalingDescription = sanitizeScalingText(scalingDescription, "No scaling.");
        }

        public AmplificationRule withAmplification(boolean canAmplifyOverride, int levelCapOverride) {
            return new AmplificationRule(canAmplifyOverride, levelCapOverride, this.scalingRuleId, this.scalingDescription);
        }

        public static AmplificationRule disabled(String scalingRuleId, String scalingDescription) {
            return new AmplificationRule(false, 1, scalingRuleId, scalingDescription);
        }
    }

    private record AmplificationRuleOverride(boolean canAmplify, int levelCap) {}

    private record ParsedAmplificationRuleOverrideEntry(Identifier enchantmentId, AmplificationRuleOverride override) {}

    private static final AmplificationRule SINGLE_LEVEL_DISABLED_RULE = AmplificationRule.disabled(
        "binary_no_scaling",
        "No scaling; binary effect."
    );
    private static final AmplificationRule MULTI_LEVEL_FALLBACK_RULE = AmplificationRule.disabled(
        "fallback_vanilla_formula_extension",
        "Unknown enchant fallback: disabled by default. If explicitly enabled, uses vanilla enchant implementation."
    );
    private static final Path LEGACY_COMMON_CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve(MagicLibrary.MODID + "-common.toml");
    private static final Pattern QUOTED_TOML_STRING_PATTERN = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");

    private static final Map<Identifier, AmplificationRule> DEFAULT_VANILLA_AMPLIFICATION_RULES = createDefaultVanillaAmplificationRules();
    private static final List<String> DEFAULT_VANILLA_ENCHANT_RULE_OVERRIDE_ENTRIES = List.of(
        "minecraft:aqua_affinity=disabled",
        "minecraft:bane_of_arthropods=100",
        "minecraft:blast_protection=10",
        "minecraft:breach=7",
        "minecraft:channeling=disabled",
        "minecraft:density=100",
        "minecraft:depth_strider=3",
        "minecraft:efficiency=100",
        "minecraft:feather_falling=7",
        "minecraft:fire_aspect=100",
        "minecraft:fire_protection=10",
        "minecraft:flame=disabled",
        "minecraft:fortune=100",
        "minecraft:frost_walker=6",
        "minecraft:impaling=100",
        "minecraft:infinity=disabled",
        "minecraft:knockback=5",
        "minecraft:looting=100",
        "minecraft:loyalty=100",
        "minecraft:luck_of_the_sea=100",
        "minecraft:lure=5",
        "minecraft:mending=disabled",
        "minecraft:multishot=disabled",
        "minecraft:piercing=100",
        "minecraft:power=100",
        "minecraft:projectile_protection=10",
        "minecraft:protection=10",
        "minecraft:punch=5",
        "minecraft:quick_charge=5",
        "minecraft:respiration=100",
        "minecraft:riptide=5",
        "minecraft:sharpness=100",
        "minecraft:silk_touch=disabled",
        "minecraft:smite=100",
        "minecraft:soul_speed=100",
        "minecraft:sweeping_edge=100",
        "minecraft:swift_sneak=5",
        "minecraft:thorns=7",
        "minecraft:unbreaking=100",
        "minecraft:wind_burst=5"
    );

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ALLOW_INCOMPATIBLE_ENCHANTS;

    public static final ModConfigSpec.BooleanValue ENABLE_UPKEEP;
    public static final ModConfigSpec.DoubleValue TIER1_BASE_UPKEEP;
    public static final ModConfigSpec.DoubleValue TIER2_BASE_UPKEEP;
    public static final ModConfigSpec.DoubleValue TIER3_BASE_UPKEEP;
    public static final ModConfigSpec.DoubleValue PER_ENCHANT_TYPE_UPKEEP;

    public static final ModConfigSpec.LongValue REDSTONE_FUEL_ME;
    public static final ModConfigSpec.LongValue GLOWSTONE_DUST_FUEL_ME;
    public static final ModConfigSpec.LongValue AMETHYST_SHARD_FUEL_ME;

    public static final ModConfigSpec.IntValue EXTRACTION_DAMAGE_PERCENT;
    public static final ModConfigSpec.IntValue AMPLIFICATION_SOFT_CAP;
    public static final ModConfigSpec.IntValue AMPLIFICATION_XP_BASE;
    public static final ModConfigSpec.IntValue AMPLIFICATION_XP_STEP;

    public static final ModConfigSpec SPEC;
    private static long cachedLegacyAmplificationRuleOverrideTimestamp = Long.MIN_VALUE;
    private static Map<Identifier, AmplificationRuleOverride> cachedLegacyAmplificationRuleOverrides = Map.of();

    static {
        BUILDER.push("general");
        ALLOW_INCOMPATIBLE_ENCHANTS = BUILDER
            .comment("If true, allows enchantments that normally conflict to be prepared together.")
            .define("allowIncompatibleEnchants", false);
        BUILDER.pop();

        BUILDER.push("upkeep");
        ENABLE_UPKEEP = BUILDER
            .comment("If false, libraries do not consume ME upkeep.")
            .define("enable upkeep", true);
        TIER1_BASE_UPKEEP = BUILDER
            .comment("Base ME upkeep per tick for tier 1 libraries.")
            .defineInRange("tier1Base", 1.0D, 0.0D, 1_000_000.0D);
        TIER2_BASE_UPKEEP = BUILDER
            .comment("Base ME upkeep per tick for tier 2 libraries.")
            .defineInRange("tier2Base", 2.0D, 0.0D, 1_000_000.0D);
        TIER3_BASE_UPKEEP = BUILDER
            .comment("Base ME upkeep per tick for tier 3 libraries.")
            .defineInRange("tier3Base", 4.0D, 0.0D, 1_000_000.0D);
        PER_ENCHANT_TYPE_UPKEEP = BUILDER
            .comment("Additional ME upkeep per stored enchant type.")
            .defineInRange("perEnchantType", 0.1D, 0.0D, 1_000_000.0D);
        BUILDER.pop();

        BUILDER.push("fuel");
        REDSTONE_FUEL_ME = BUILDER
            .comment("Active fuel ME provided by Redstone.")
            .defineInRange("redstone", 1_000L, 0L, Long.MAX_VALUE);
        GLOWSTONE_DUST_FUEL_ME = BUILDER
            .comment("Active fuel ME provided by Glowstone Dust.")
            .defineInRange("glowstoneDust", 5_000L, 0L, Long.MAX_VALUE);
        AMETHYST_SHARD_FUEL_ME = BUILDER
            .comment("Active fuel ME provided by Amethyst Shard.")
            .defineInRange("amethystShard", 10_000L, 0L, Long.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("extraction");
        EXTRACTION_DAMAGE_PERCENT = BUILDER
            .comment("Percent of max durability consumed when extracting from damageable gear.")
            .defineInRange("damagePercent", 25, 0, 100);
        BUILDER.pop();

        BUILDER.push("amplification");
        AMPLIFICATION_SOFT_CAP = BUILDER
            .comment("Maximum level an enchant can be amplified to in Tome of Amplification mode.")
            .defineInRange("softCap", 255, 1, 1024);
        AMPLIFICATION_XP_BASE = BUILDER
            .comment("XP level cost for amplification upgrade #1.")
            .defineInRange("xpBase", 10, 0, Integer.MAX_VALUE);
        AMPLIFICATION_XP_STEP = BUILDER
            .comment("Additional XP levels added per amplification step after the first.")
            .defineInRange("xpStep", 15, 0, Integer.MAX_VALUE);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private MagicLibraryConfig() {
    }

    public static void onCommonConfigLoading(ModConfigEvent.Loading event) {
        if (isMagicLibraryCommonConfig(event.getConfig())) {
            EnchantCapConfigLoader.reload(getLegacyAmplificationRuleEntriesForMigration());
        }
    }

    public static void onCommonConfigReloading(ModConfigEvent.Reloading event) {
        if (isMagicLibraryCommonConfig(event.getConfig())) {
            EnchantCapConfigLoader.reload(getLegacyAmplificationRuleEntriesForMigration());
        }
    }

    public static boolean allowIncompatibleEnchants() {
        return ALLOW_INCOMPATIBLE_ENCHANTS.get();
    }

    public static boolean isUpkeepEnabled() {
        return ENABLE_UPKEEP.get();
    }

    public static int getBaseUpkeepTenths(MagicLibraryTier tier) {
        if (!isUpkeepEnabled()) {
            return 0;
        }
        return switch (tier) {
            case TIER1 -> toTenths(TIER1_BASE_UPKEEP.get());
            case TIER2 -> toTenths(TIER2_BASE_UPKEEP.get());
            case TIER3 -> toTenths(TIER3_BASE_UPKEEP.get());
        };
    }

    public static int getPerEnchantTypeUpkeepTenths() {
        if (!isUpkeepEnabled()) {
            return 0;
        }
        return toTenths(PER_ENCHANT_TYPE_UPKEEP.get());
    }

    public static long getRedstoneFuelME() {
        return REDSTONE_FUEL_ME.get();
    }

    public static long getGlowstoneDustFuelME() {
        return GLOWSTONE_DUST_FUEL_ME.get();
    }

    public static long getAmethystShardFuelME() {
        return AMETHYST_SHARD_FUEL_ME.get();
    }

    public static int getExtractionDamagePercent() {
        return EXTRACTION_DAMAGE_PERCENT.get();
    }

    public static int getAmplificationSoftCap() {
        return AMPLIFICATION_SOFT_CAP.get();
    }

    public static int getAmplificationXpCost(int upgradeNumber) {
        if (upgradeNumber <= 0) {
            return 0;
        }
        long base = AMPLIFICATION_XP_BASE.get();
        long step = AMPLIFICATION_XP_STEP.get();
        long cost = base + (step * (upgradeNumber - 1L));
        if (cost >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.max(0L, cost);
    }

    public static List<String> getDefaultAmplificationEnchantRuleEntries() {
        return DEFAULT_VANILLA_ENCHANT_RULE_OVERRIDE_ENTRIES;
    }

    public static synchronized List<String> getLegacyAmplificationRuleEntriesForMigration() {
        Map<Identifier, AmplificationRuleOverride> legacyOverrides = getLegacyAmplificationRuleOverrides();
        if (legacyOverrides.isEmpty()) {
            return List.of();
        }

        List<String> legacyEntries = new ArrayList<>();
        for (Map.Entry<Identifier, AmplificationRuleOverride> entry : legacyOverrides.entrySet()) {
            AmplificationRuleOverride override = entry.getValue();
            String ruleToken = override.canAmplify() ? Integer.toString(Math.max(1, override.levelCap())) : "disabled";
            legacyEntries.add(entry.getKey() + "=" + ruleToken);
        }
        return List.copyOf(legacyEntries);
    }

    public static boolean isAmplifiableEnchant(Identifier enchantmentId, int vanillaMaxLevel) {
        return resolveAmplificationRule(enchantmentId, vanillaMaxLevel).canAmplify();
    }

    public static int getConfiguredEnchantSoftcap(Identifier enchantmentId, int vanillaMaxLevel) {
        return Math.max(1, resolveAmplificationRule(enchantmentId, vanillaMaxLevel).levelCap());
    }

    public static int getEffectiveAmplifiedMaxLevel(Identifier enchantmentId, int vanillaMaxLevel) {
        int globalCap = Math.max(1, getAmplificationSoftCap());
        int enchantSpecificCap = getConfiguredEnchantSoftcap(enchantmentId, vanillaMaxLevel);
        return Math.min(globalCap, enchantSpecificCap);
    }

    public static boolean isAtEffectiveMax(int currentLevel, int effectiveMaxLevel) {
        int sanitizedCurrentLevel = Math.max(0, currentLevel);
        int sanitizedEffectiveMaxLevel = Math.max(1, effectiveMaxLevel);
        return sanitizedCurrentLevel >= sanitizedEffectiveMaxLevel;
    }

    public static int getSelectableTargetLevel(int currentLevel, int requestedLevel, int effectiveMaxLevel) {
        int sanitizedCurrentLevel = Math.max(0, currentLevel);
        int sanitizedRequestedLevel = Math.max(sanitizedCurrentLevel, requestedLevel);
        int sanitizedEffectiveMaxLevel = Math.max(1, effectiveMaxLevel);
        return Math.min(sanitizedRequestedLevel, sanitizedEffectiveMaxLevel);
    }

    public static boolean isAtEffectiveAmplifiedMax(Identifier enchantmentId, int vanillaMaxLevel, int currentMaxLevel) {
        if (!isAmplifiableEnchant(enchantmentId, vanillaMaxLevel)) {
            return true;
        }
        return isAtEffectiveMax(currentMaxLevel, getEffectiveAmplifiedMaxLevel(enchantmentId, vanillaMaxLevel));
    }

    public static int getAmplificationUpgradeNumber(int vanillaMaxLevel, int currentMaxLevel) {
        int sanitizedVanillaMaxLevel = Math.max(1, vanillaMaxLevel);
        int sanitizedCurrentMaxLevel = Math.max(sanitizedVanillaMaxLevel, currentMaxLevel);
        int amplificationCount = Math.max(0, sanitizedCurrentMaxLevel - sanitizedVanillaMaxLevel);
        return amplificationCount + 1;
    }

    public static AmplificationRule resolveAmplificationRule(Identifier enchantmentId, int vanillaMaxLevel) {
        int sanitizedVanillaMaxLevel = Math.max(1, vanillaMaxLevel);
        AmplificationRule baseRule = DEFAULT_VANILLA_AMPLIFICATION_RULES.get(enchantmentId);
        if (baseRule == null) {
            baseRule = getFallbackAmplificationRule(sanitizedVanillaMaxLevel);
        }

        EnchantCapConfigLoader.EnchantCapRule configuredRule = EnchantCapConfigLoader.getEnchantCapRule(enchantmentId);
        if (configuredRule != null) {
            return baseRule.withAmplification(configuredRule.canAmplify(), configuredRule.levelCap());
        }

        if (!EnchantCapConfigLoader.existsOnDisk()) {
            AmplificationRuleOverride legacyOverride = getLegacyAmplificationRuleOverrides().get(enchantmentId);
            if (legacyOverride != null) {
                return baseRule.withAmplification(legacyOverride.canAmplify(), legacyOverride.levelCap());
            }
        }

        return baseRule;
    }

    public static int getEffectiveAmplificationCap(Identifier enchantmentId, int vanillaMaxLevel) {
        return getEffectiveAmplifiedMaxLevel(enchantmentId, vanillaMaxLevel);
    }

    public static int getConfiguredLibraryLevelLimit(Identifier enchantmentId, int vanillaMaxLevel) {
        int sanitizedVanillaMaxLevel = Math.max(1, vanillaMaxLevel);
        if (!isAmplifiableEnchant(enchantmentId, sanitizedVanillaMaxLevel)) {
            return sanitizedVanillaMaxLevel;
        }
        return getEffectiveAmplifiedMaxLevel(enchantmentId, sanitizedVanillaMaxLevel);
    }

    public static int getEffectiveLibraryMaxLevel(
        Identifier enchantmentId,
        int vanillaMaxLevel,
        int discoveredMaxLevel,
        int amplifiedOverrideLevel
    ) {
        int rawLibraryMax = Math.max(Math.max(0, discoveredMaxLevel), Math.max(0, amplifiedOverrideLevel));
        if (rawLibraryMax <= 0) {
            return 0;
        }

        int configuredLimit = getConfiguredLibraryLevelLimit(enchantmentId, vanillaMaxLevel);
        return Math.max(1, Math.min(rawLibraryMax, configuredLimit));
    }

    public static boolean canAmplifyEnchantment(Identifier enchantmentId, int vanillaMaxLevel, int currentMaxLevel) {
        if (!isAmplifiableEnchant(enchantmentId, vanillaMaxLevel)) {
            return false;
        }
        return !isAtEffectiveAmplifiedMax(enchantmentId, vanillaMaxLevel, currentMaxLevel);
    }

    public static boolean isValidTomeUpgradeTarget(Identifier enchantmentId, int vanillaMaxLevel, int currentMaxLevel) {
        int sanitizedVanillaMaxLevel = Math.max(1, vanillaMaxLevel);
        if (sanitizedVanillaMaxLevel <= 1) {
            return false;
        }
        return canAmplifyEnchantment(enchantmentId, sanitizedVanillaMaxLevel, currentMaxLevel);
    }

    public static String getAmplificationScalingRuleId(Identifier enchantmentId, int vanillaMaxLevel) {
        return resolveAmplificationRule(enchantmentId, vanillaMaxLevel).scalingRuleId();
    }

    public static String getAmplificationScalingDescription(Identifier enchantmentId, int vanillaMaxLevel) {
        return resolveAmplificationRule(enchantmentId, vanillaMaxLevel).scalingDescription();
    }

    private static boolean isMagicLibraryCommonConfig(ModConfig config) {
        return config.getType() == ModConfig.Type.COMMON && MagicLibrary.MODID.equals(config.getModId());
    }

    private static AmplificationRule getFallbackAmplificationRule(int vanillaMaxLevel) {
        if (vanillaMaxLevel <= 1) {
            return SINGLE_LEVEL_DISABLED_RULE;
        }
        return MULTI_LEVEL_FALLBACK_RULE;
    }

    private static synchronized Map<Identifier, AmplificationRuleOverride> getLegacyAmplificationRuleOverrides() {
        if (!Files.exists(LEGACY_COMMON_CONFIG_PATH)) {
            cachedLegacyAmplificationRuleOverrideTimestamp = Long.MIN_VALUE;
            cachedLegacyAmplificationRuleOverrides = Map.of();
            return cachedLegacyAmplificationRuleOverrides;
        }

        long lastModified = getLastModifiedMillis(LEGACY_COMMON_CONFIG_PATH);
        if (lastModified == cachedLegacyAmplificationRuleOverrideTimestamp) {
            return cachedLegacyAmplificationRuleOverrides;
        }

        String contents;
        try {
            contents = Files.readString(LEGACY_COMMON_CONFIG_PATH, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read legacy config: " + LEGACY_COMMON_CONFIG_PATH, exception);
        }

        List<String> entries = extractLegacyAmplificationRuleOverrideEntries(contents);
        cachedLegacyAmplificationRuleOverrideTimestamp = lastModified;
        cachedLegacyAmplificationRuleOverrides = Map.copyOf(parseAmplificationRuleOverrideEntries(entries));
        return cachedLegacyAmplificationRuleOverrides;
    }

    private static long getLastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect legacy config: " + path, exception);
        }
    }

    private static List<String> extractLegacyAmplificationRuleOverrideEntries(String contents) {
        int propertyIndex = contents.indexOf("enchantRuleOverrides");
        if (propertyIndex < 0) {
            return List.of();
        }

        int listStart = contents.indexOf('[', propertyIndex);
        if (listStart < 0) {
            return List.of();
        }

        int listEnd = findClosingListBracket(contents, listStart);
        if (listEnd < 0) {
            return List.of();
        }

        List<String> entries = new ArrayList<>();
        Matcher matcher = QUOTED_TOML_STRING_PATTERN.matcher(contents.substring(listStart + 1, listEnd));
        while (matcher.find()) {
            entries.add(unescapeTomlString(matcher.group(1)));
        }
        return List.copyOf(entries);
    }

    private static int findClosingListBracket(String contents, int listStart) {
        boolean inString = false;
        boolean escaped = false;
        for (int i = listStart + 1; i < contents.length(); i++) {
            char current = contents.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (!inString && current == ']') {
                return i;
            }
        }
        return -1;
    }

    private static String unescapeTomlString(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static Map<Identifier, AmplificationRuleOverride> parseAmplificationRuleOverrideEntries(List<? extends String> entries) {
        Map<Identifier, AmplificationRuleOverride> parsed = new HashMap<>();
        for (String entry : entries) {
            ParsedAmplificationRuleOverrideEntry parsedEntry = parseAmplificationRuleOverrideEntry(entry);
            if (parsedEntry != null) {
                parsed.put(parsedEntry.enchantmentId(), parsedEntry.override());
            }
        }
        return parsed;
    }

    private static @Nullable ParsedAmplificationRuleOverrideEntry parseAmplificationRuleOverrideEntry(String rawEntry) {
        String entry = rawEntry == null ? "" : rawEntry.trim();
        if (entry.isEmpty()) {
            return null;
        }

        int splitIndex = entry.indexOf('=');
        if (splitIndex <= 0 || splitIndex >= entry.length() - 1) {
            return null;
        }

        String idToken = entry.substring(0, splitIndex).trim();
        String ruleToken = entry.substring(splitIndex + 1).trim();
        Identifier enchantmentId = Identifier.tryParse(idToken);
        if (enchantmentId == null || ruleToken.isEmpty()) {
            return null;
        }

        if ("disabled".equals(ruleToken.toLowerCase(Locale.ROOT))) {
            return new ParsedAmplificationRuleOverrideEntry(enchantmentId, new AmplificationRuleOverride(false, 1));
        }

        try {
            int cap = Integer.parseInt(ruleToken);
            if (cap < 1) {
                return null;
            }
            return new ParsedAmplificationRuleOverrideEntry(enchantmentId, new AmplificationRuleOverride(true, cap));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Map<Identifier, AmplificationRule> createDefaultVanillaAmplificationRules() {
        Map<Identifier, AmplificationRule> rules = new HashMap<>();

        putRule(rules, "minecraft:aqua_affinity", false, 1, "binary_no_scaling", "No scaling; binary effect.");
        putRule(rules, "minecraft:bane_of_arthropods", true, 100, "bonus_damage_arthropods_2_5_per_level", "+2.5 bonus damage vs arthropods per level.");
        putRule(rules, "minecraft:blast_protection", true, 10, "blast_reduction_8pct_capped_80", "+8% explosion damage reduction per level, capped at 80%.");
        putRule(rules, "minecraft:breach", true, 7, "armor_bypass_15pct_capped_100", "+15% armor bypass per level, capped at 100%.");
        putRule(rules, "minecraft:channeling", false, 1, "binary_no_scaling", "No scaling; binary effect.");
        putRule(rules, "minecraft:density", true, 100, "smash_damage_0_5_per_fall_block_per_level", "+0.5 extra smash damage per fallen block per level.");
        putRule(rules, "minecraft:depth_strider", true, 3, "vanilla_breakpoint_only", "Vanilla movement breakpoints only; no scaling beyond level 3.");
        putRule(rules, "minecraft:efficiency", true, 100, "mining_speed_level_sq_plus_one", "Mining speed bonus = level^2 + 1.");
        putRule(rules, "minecraft:feather_falling", true, 7, "fall_reduction_12pct_capped_80", "+12% fall damage reduction per level, capped at 80%.");
        putRule(rules, "minecraft:fire_aspect", true, 100, "burn_duration_4s_per_level", "Burn duration = 4 seconds per level.");
        putRule(rules, "minecraft:fire_protection", true, 10, "fire_reduction_8pct_capped_80", "+8% fire damage reduction per level, capped at 80%.");
        putRule(rules, "minecraft:flame", false, 1, "binary_no_scaling", "No scaling; binary effect.");
        putRule(rules, "minecraft:fortune", true, 100, "fortune_average_multiplier_formula", "Average ore multiplier = 1/(level+2) + (level+1)/2.");
        putRule(rules, "minecraft:frost_walker", true, 6, "freeze_radius_two_plus_level", "Freeze radius = 2 + level blocks.");
        putRule(rules, "minecraft:impaling", true, 100, "bonus_damage_2_5_per_level", "+2.5 bonus damage per level.");
        putRule(rules, "minecraft:infinity", false, 1, "binary_no_scaling", "No scaling; binary effect.");
        putRule(rules, "minecraft:knockback", true, 5, "knockback_plus_one_per_level", "+1 knockback strength per level.");
        putRule(rules, "minecraft:looting", true, 100, "looting_linear_drop_and_rare_chance", "Common drops +1 max item per level; rare/equipment odds +1% per level.");
        putRule(rules, "minecraft:loyalty", true, 100, "trident_return_speed_plus_0_05_per_level", "Trident return speed bonus = +0.05 per level.");
        putRule(rules, "minecraft:luck_of_the_sea", true, 100, "fishing_luck_plus_one_per_level", "Fishing luck +1 per level.");
        putRule(rules, "minecraft:lure", true, 5, "bite_wait_minus_5s_per_level", "Reduces bite wait by 5 seconds per level.");
        putRule(rules, "minecraft:mending", false, 1, "binary_no_scaling", "No scaling; binary effect.");
        putRule(rules, "minecraft:multishot", false, 1, "binary_no_scaling", "No scaling; binary effect.");
        putRule(rules, "minecraft:piercing", true, 100, "pierce_entities_level_plus_one_hits", "Pierces level entities; total hits = level + 1.");
        putRule(rules, "minecraft:power", true, 100, "arrow_damage_0_5_level_plus_0_5", "Extra arrow damage = 0.5 * level + 0.5.");
        putRule(rules, "minecraft:projectile_protection", true, 10, "projectile_reduction_8pct_capped_80", "+8% projectile damage reduction per level, capped at 80%.");
        putRule(rules, "minecraft:protection", true, 10, "general_reduction_4pct_per_level", "+4% general damage reduction per level.");
        putRule(rules, "minecraft:punch", true, 5, "bow_knockback_0_6_per_level", "Knockback bonus = 0.6 * level.");
        putRule(rules, "minecraft:quick_charge", true, 5, "crossbow_reload_1_25_minus_0_25_level", "Reload time = 1.25 - 0.25 * level seconds; no scaling beyond level 5.");
        putRule(rules, "minecraft:respiration", true, 100, "breath_plus_15s_per_level_air_skip_level_over_level_plus_one", "+15 seconds breath per level; air-loss skip chance = level/(level+1).");
        putRule(rules, "minecraft:riptide", true, 5, "launch_distance_3_plus_6_level", "Launch distance = 3 + 6 * level blocks.");
        putRule(rules, "minecraft:sharpness", true, 100, "sharpness_damage_0_5_max_0_level_minus_one_plus_one", "Extra damage = 0.5 * max(0, level - 1) + 1.");
        putRule(rules, "minecraft:silk_touch", false, 1, "binary_no_scaling", "No scaling; binary effect.");
        putRule(rules, "minecraft:smite", true, 100, "bonus_damage_undead_2_5_per_level", "+2.5 bonus damage vs undead per level.");
        putRule(rules, "minecraft:soul_speed", true, 100, "soul_speed_multiplier_1_3_plus_0_105_level", "Speed multiplier on soul blocks = 1.3 + 0.105 * level.");
        putRule(rules, "minecraft:sweeping_edge", true, 100, "sweep_fraction_level_over_level_plus_one", "Sweep damage fraction = level/(level+1) of hit damage.");
        putRule(rules, "minecraft:swift_sneak", true, 5, "sneak_speed_30pct_plus_15pct_level_capped_100", "Sneak speed = 30% walk speed + 15% per level, capped at 100%.");
        putRule(rules, "minecraft:thorns", true, 7, "thorns_proc_15pct_per_level_capped_100", "Proc chance = 15% per level, capped at 100%.");
        putRule(rules, "minecraft:unbreaking", true, 100, "durability_loss_chance_inverse_level_plus_one", "Durability loss chance = 1/(level+1); expected lifetime ~= (level+1)x.");
        putRule(rules, "minecraft:wind_burst", true, 5, "rebound_height_7_per_level", "Rebound height = 7 blocks per level.");

        return Map.copyOf(rules);
    }

    private static void putRule(
        Map<Identifier, AmplificationRule> rules,
        String enchantId,
        boolean canAmplify,
        int levelCap,
        String scalingRuleId,
        String scalingDescription
    ) {
        Identifier id = Identifier.tryParse(enchantId);
        if (id == null) {
            throw new IllegalArgumentException("Invalid enchant id in default amplification rule: " + enchantId);
        }
        rules.put(id, new AmplificationRule(canAmplify, levelCap, scalingRuleId, scalingDescription));
    }

    private static String sanitizeScalingToken(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String sanitizeScalingText(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static int toTenths(double upkeepPerTick) {
        if (upkeepPerTick <= 0.0D) {
            return 0;
        }

        double scaled = upkeepPerTick * 10.0D;
        if (scaled >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        return Math.max(0, (int) Math.round(scaled));
    }
}
