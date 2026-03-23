package com.acidglow.magiclibrary.client;

import com.acidglow.magiclibrary.util.EnchantDisplayUtil;
import com.acidglow.magiclibrary.util.MagicLibraryEnchantScaling;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jspecify.annotations.Nullable;

public final class AmplifiedGearTooltipHandler {
    private record ResolvedAttributeLine(String text, int color) {}
    private record AttributeResolution(double totalValue, boolean modified, boolean aboveVanillaMax) {}
    private record TooltipSection(int startIndex, int endIndex) {}

    private static final int LEGENDARY_COLOR = EnchantDisplayUtil.LEGENDARY_COLOR;
    private static final int VANILLA_BLUE_COLOR = EnchantDisplayUtil.NORMAL_ENCHANT_COLOR;
    private static final String MINING_EFFICIENCY_LABEL = "Mining Efficiency";

    private AmplifiedGearTooltipHandler() {}

    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || stack.is(Items.ENCHANTED_BOOK)) {
            return;
        }

        HolderLookup.RegistryLookup<Enchantment> enchantLookup = event.getContext().registries().lookupOrThrow(Registries.ENCHANTMENT);
        List<Component> tooltip = event.getToolTip();

        replaceAttackDamageTooltip(stack, enchantLookup, tooltip);
        replaceAttackSpeedTooltip(stack, tooltip);
        replaceMiningSpeedTooltip(stack, enchantLookup, tooltip);
        List<Component> protectionTooltips = collectProtectionTooltips(stack, enchantLookup, tooltip);
        if (!protectionTooltips.isEmpty()) {
            appendArmorEnchantBonusesToVanillaSection(stack, tooltip, protectionTooltips);
        }
        addSweepingEdgeTooltip(stack, enchantLookup, tooltip);
        addPowerTooltip(stack, enchantLookup, tooltip);
        updateRespirationTooltip(stack, enchantLookup, tooltip);
        updateModdedTooltipStatColors(stack, enchantLookup, tooltip);
    }

    private static void replaceAttackDamageTooltip(
        ItemStack stack,
        HolderLookup.RegistryLookup<Enchantment> enchantLookup,
        List<Component> tooltip
    ) {
        ResolvedAttributeLine resolvedAttackDamageLine = resolveAttackDamageLine(stack, enchantLookup);
        if (resolvedAttackDamageLine != null) {
            replaceTooltipLineInAttributeSection(
                tooltip,
                EquipmentSlotGroup.MAINHAND,
                getAttackDamageLabel(),
                resolvedAttackDamageLine.text(),
                resolvedAttackDamageLine.color()
            );
        }

        int smiteLevel = getEnchantLevel(stack, enchantLookup, Enchantments.SMITE);
        int baneLevel = getEnchantLevel(stack, enchantLookup, Enchantments.BANE_OF_ARTHROPODS);
        int impalingLevel = getEnchantLevel(stack, enchantLookup, Enchantments.IMPALING);
        int densityLevel = getEnchantLevel(stack, enchantLookup, Enchantments.DENSITY);

        if (resolvedAttackDamageLine == null && smiteLevel <= 0 && baneLevel <= 0 && impalingLevel <= 0 && densityLevel <= 0) {
            return;
        }

        if (smiteLevel > 0) {
            int vanillaSmiteMax = getVanillaMaxLevel(enchantLookup, Enchantments.SMITE, 5);
            tooltip.add(
                createStatLine(
                    "+" + formatValue(MagicLibraryEnchantScaling.getSmiteDamageBonus(smiteLevel)) + " Undead Damage",
                    getEnchantColor(smiteLevel, vanillaSmiteMax)
                )
            );
        }

        if (baneLevel > 0) {
            int vanillaBaneMax = getVanillaMaxLevel(enchantLookup, Enchantments.BANE_OF_ARTHROPODS, 5);
            tooltip.add(
                createStatLine(
                    "+" + formatValue(MagicLibraryEnchantScaling.getBaneOfArthropodsDamageBonus(baneLevel)) + " Arthropod Damage",
                    getEnchantColor(baneLevel, vanillaBaneMax)
                )
            );
        }

        if (impalingLevel > 0) {
            int vanillaImpalingMax = getVanillaMaxLevel(enchantLookup, Enchantments.IMPALING, 5);
            tooltip.add(
                createStatLine(
                    "+" + formatValue(MagicLibraryEnchantScaling.getImpalingDamageBonus(impalingLevel)) + " Aquatic Damage",
                    getEnchantColor(impalingLevel, vanillaImpalingMax)
                )
            );
        }

        if (densityLevel > 0) {
            int vanillaDensityMax = getVanillaMaxLevel(enchantLookup, Enchantments.DENSITY, 5);
            tooltip.add(
                createStatLine(
                    "+" + formatValue(MagicLibraryEnchantScaling.getDensityDamageBonusPerFallenBlock(densityLevel)) + " Smash Damage / Fallen Block",
                    getEnchantColor(densityLevel, vanillaDensityMax)
                )
            );
        }
    }

    private static void replaceAttackSpeedTooltip(ItemStack stack, List<Component> tooltip) {
        ResolvedAttributeLine resolvedAttackSpeedLine = resolveMainhandAttributeLine(stack, Attributes.ATTACK_SPEED, getAttackSpeedLabel());
        if (resolvedAttackSpeedLine != null) {
            replaceTooltipLineInAttributeSection(
                tooltip,
                EquipmentSlotGroup.MAINHAND,
                getAttackSpeedLabel(),
                resolvedAttackSpeedLine.text(),
                resolvedAttackSpeedLine.color()
            );
        }
    }

    private static void replaceMiningSpeedTooltip(
        ItemStack stack,
        HolderLookup.RegistryLookup<Enchantment> enchantLookup,
        List<Component> tooltip
    ) {
        int efficiencyLevel = getEnchantLevel(stack, enchantLookup, Enchantments.EFFICIENCY);
        if (efficiencyLevel <= 0) {
            return;
        }

        Tool toolData = stack.get(DataComponents.TOOL);
        if (toolData == null) {
            return;
        }

        int bonusMiningSpeed = MagicLibraryEnchantScaling.getEfficiencyMiningSpeedBonus(efficiencyLevel);
        int vanillaEfficiencyMax = getVanillaMaxLevel(enchantLookup, Enchantments.EFFICIENCY, 5);
        replaceTooltipLine(
            tooltip,
            MINING_EFFICIENCY_LABEL,
            "+" + formatValue(bonusMiningSpeed) + " " + MINING_EFFICIENCY_LABEL,
            getEnchantColor(efficiencyLevel, vanillaEfficiencyMax)
        );
    }

    private static List<Component> collectProtectionTooltips(
        ItemStack stack,
        HolderLookup.RegistryLookup<Enchantment> enchantLookup,
        List<Component> tooltip
    ) {
        List<Component> protectionTooltips = new ArrayList<>();

        int protectionLevel = getEnchantLevel(stack, enchantLookup, Enchantments.PROTECTION);
        if (protectionLevel > 0) {
            int vanillaProtectionMax = getVanillaMaxLevel(enchantLookup, Enchantments.PROTECTION, 4);
            protectionTooltips.add(
                createStatLine(
                    formatSignedPercentStat(MagicLibraryEnchantScaling.getProtectionDamageReductionPercent(protectionLevel), "Damage Reduction"),
                    getEnchantColor(protectionLevel, vanillaProtectionMax)
                )
            );
        }

        int fireProtectionLevel = getEnchantLevel(stack, enchantLookup, Enchantments.FIRE_PROTECTION);
        if (fireProtectionLevel > 0) {
            int vanillaFireProtectionMax = getVanillaMaxLevel(enchantLookup, Enchantments.FIRE_PROTECTION, 4);
            int fireProtectionColor = getEnchantColor(fireProtectionLevel, vanillaFireProtectionMax);
            protectionTooltips.add(
                createStatLine(
                    formatSignedPercentStat(MagicLibraryEnchantScaling.getFireProtectionDamageReductionPercent(fireProtectionLevel), "Fire Protection"),
                    fireProtectionColor
                )
            );
            removeTooltipLinesContaining(tooltip, "Burning Time", "Burn Time Reduction");
            protectionTooltips.add(
                createStatLine(
                    formatSignedPercentStat(getBurnTimeReductionPercent(fireProtectionLevel), "Burn Time Reduction"),
                    fireProtectionColor
                )
            );
        }

        int blastProtectionLevel = getEnchantLevel(stack, enchantLookup, Enchantments.BLAST_PROTECTION);
        if (blastProtectionLevel > 0) {
            int vanillaBlastProtectionMax = getVanillaMaxLevel(enchantLookup, Enchantments.BLAST_PROTECTION, 4);
            int blastProtectionColor = getEnchantColor(blastProtectionLevel, vanillaBlastProtectionMax);
            protectionTooltips.add(
                createStatLine(
                    formatSignedPercentStat(MagicLibraryEnchantScaling.getBlastProtectionDamageReductionPercent(blastProtectionLevel), "Explosion Protection"),
                    blastProtectionColor
                )
            );
            String explosionKnockbackResistanceLine = extractFirstTooltipLine(tooltip, "Explosion Knockback Resistance");
            if (explosionKnockbackResistanceLine != null) {
                protectionTooltips.add(createStatLine(explosionKnockbackResistanceLine, blastProtectionColor));
            }
        }

        int projectileProtectionLevel = getEnchantLevel(stack, enchantLookup, Enchantments.PROJECTILE_PROTECTION);
        if (projectileProtectionLevel > 0) {
            int vanillaProjectileProtectionMax = getVanillaMaxLevel(enchantLookup, Enchantments.PROJECTILE_PROTECTION, 4);
            protectionTooltips.add(
                createStatLine(
                    formatSignedPercentStat(
                        MagicLibraryEnchantScaling.getProjectileProtectionDamageReductionPercent(projectileProtectionLevel),
                        "Projectile Protection"
                    ),
                    getEnchantColor(projectileProtectionLevel, vanillaProjectileProtectionMax)
                )
            );
        }

        int featherFallingLevel = getEnchantLevel(stack, enchantLookup, Enchantments.FEATHER_FALLING);
        if (featherFallingLevel > 0) {
            int vanillaFeatherFallingMax = getVanillaMaxLevel(enchantLookup, Enchantments.FEATHER_FALLING, 4);
            protectionTooltips.add(
                createStatLine(
                    formatSignedPercentStat(
                        MagicLibraryEnchantScaling.getFeatherFallingDamageReductionPercent(featherFallingLevel),
                        "Fall Damage Reduction"
                    ),
                    getEnchantColor(featherFallingLevel, vanillaFeatherFallingMax)
                )
            );
        }

        removeExistingArmorBonusStatLines(tooltip);
        removeEmptyAttributeSection(tooltip, getAttributeSectionHeader(EquipmentSlotGroup.ARMOR));
        return protectionTooltips;
    }

    private static void addSweepingEdgeTooltip(
        ItemStack stack,
        HolderLookup.RegistryLookup<Enchantment> enchantLookup,
        List<Component> tooltip
    ) {
        int sweepingEdgeLevel = getEnchantLevel(stack, enchantLookup, Enchantments.SWEEPING_EDGE);
        if (sweepingEdgeLevel <= 0) {
            return;
        }

        int vanillaSweepingMax = getVanillaMaxLevel(enchantLookup, Enchantments.SWEEPING_EDGE, 3);
        removeTooltipLinesContaining(tooltip, "Sweeping Damage Ratio");
        tooltip.add(
            createStatLine(
                formatSignedPercentStat(getSweepDamagePercent(sweepingEdgeLevel), "Sweep Damage"),
                getEnchantColor(sweepingEdgeLevel, vanillaSweepingMax)
            )
        );
    }

    private static void addPowerTooltip(
        ItemStack stack,
        HolderLookup.RegistryLookup<Enchantment> enchantLookup,
        List<Component> tooltip
    ) {
        int powerLevel = getEnchantLevel(stack, enchantLookup, Enchantments.POWER);
        if (powerLevel <= 0) {
            return;
        }

        int vanillaPowerMax = getVanillaMaxLevel(enchantLookup, Enchantments.POWER, 5);
        tooltip.add(
            createStatLine(
                formatSignedFlatStat(MagicLibraryEnchantScaling.getPowerArrowDamageBonus(powerLevel), "Arrow Damage"),
                getEnchantColor(powerLevel, vanillaPowerMax)
            )
        );
    }

    private static void updateRespirationTooltip(
        ItemStack stack,
        HolderLookup.RegistryLookup<Enchantment> enchantLookup,
        List<Component> tooltip
    ) {
        int respirationLevel = getEnchantLevel(stack, enchantLookup, Enchantments.RESPIRATION);
        if (respirationLevel <= 0) {
            return;
        }

        int vanillaRespirationMax = getVanillaMaxLevel(enchantLookup, Enchantments.RESPIRATION, 3);
        recolorTooltipLinesContaining(tooltip, getEnchantColor(respirationLevel, vanillaRespirationMax), "Oxygen Bonus");
    }

    private static void updateModdedTooltipStatColors(
        ItemStack stack,
        HolderLookup.RegistryLookup<Enchantment> enchantLookup,
        List<Component> tooltip
    ) {
        Map<String, Integer> colorByToken = new LinkedHashMap<>();

        EnchantmentHelper.runIterationOnItem(stack, (enchantment, level) -> {
            if (!EnchantDisplayUtil.isModdedEnchant(enchantment)) {
                return;
            }

            int color = EnchantDisplayUtil.getEnchantTextColor(enchantment, level);
            for (String token : getModdedStatLineTokens(enchantment)) {
                if (!isSafeModdedStatToken(enchantLookup, stack, token)) {
                    continue;
                }
                colorByToken.merge(token, color, AmplifiedGearTooltipHandler::preferLegendaryColor);
            }
        });

        for (Map.Entry<String, Integer> entry : colorByToken.entrySet()) {
            recolorGeneratedTooltipLinesContaining(tooltip, entry.getValue(), entry.getKey());
        }
    }

    private static int getEnchantLevel(
        ItemStack stack,
        HolderLookup.RegistryLookup<Enchantment> enchantLookup,
        ResourceKey<Enchantment> enchantmentKey
    ) {
        return enchantLookup.get(enchantmentKey).map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack)).orElse(0);
    }

    private static int getVanillaMaxLevel(
        HolderLookup.RegistryLookup<Enchantment> enchantLookup,
        ResourceKey<Enchantment> enchantmentKey,
        int fallback
    ) {
        return enchantLookup.get(enchantmentKey).map(holder -> Math.max(1, holder.value().getMaxLevel())).orElse(fallback);
    }

    private static ResolvedAttributeLine resolveAttackDamageLine(
        ItemStack stack,
        HolderLookup.RegistryLookup<Enchantment> enchantLookup
    ) {
        AttributeResolution resolution = resolveMainhandAttributeResolution(stack, Attributes.ATTACK_DAMAGE);
        double totalAttackDamage = resolution.totalValue();
        boolean modified = resolution.modified();
        boolean aboveVanillaMax = resolution.aboveVanillaMax();

        int sharpnessLevel = getEnchantLevel(stack, enchantLookup, Enchantments.SHARPNESS);
        if (sharpnessLevel > 0) {
            totalAttackDamage += MagicLibraryEnchantScaling.getSharpnessDamageBonus(sharpnessLevel);
            modified = true;
            aboveVanillaMax |= sharpnessLevel > getVanillaMaxLevel(enchantLookup, Enchantments.SHARPNESS, 5);
        }

        if (!modified) {
            return null;
        }

        return new ResolvedAttributeLine(formatValue(totalAttackDamage) + " " + getAttackDamageLabel(), getLegendaryAwareColor(aboveVanillaMax));
    }

    private static @Nullable ResolvedAttributeLine resolveMainhandAttributeLine(
        ItemStack stack,
        Holder<Attribute> attribute,
        String label
    ) {
        AttributeResolution resolution = resolveMainhandAttributeResolution(stack, attribute);
        if (!resolution.modified()) {
            return null;
        }
        return new ResolvedAttributeLine(formatValue(resolution.totalValue()) + " " + label, getLegendaryAwareColor(resolution.aboveVanillaMax()));
    }

    private static AttributeResolution resolveMainhandAttributeResolution(ItemStack stack, Holder<Attribute> attribute) {
        double defaultValue = attribute.value().getDefaultValue();
        double[] totalValue = {getBaseAttributeValue(stack, attribute, EquipmentSlot.MAINHAND, defaultValue)};
        boolean[] modified = {false};
        boolean[] aboveVanillaMax = {false};

        EnchantmentHelper.runIterationOnItem(stack, (enchantment, level) -> {
            boolean applied = false;
            for (var effect : enchantment.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES)) {
                if (!effect.attribute().equals(attribute) || !enchantment.value().matchingSlot(EquipmentSlot.MAINHAND)) {
                    continue;
                }
                totalValue[0] = applyAttributeModifier(totalValue[0], defaultValue, effect.getModifier(level, EquipmentSlot.MAINHAND));
                applied = true;
            }

            if (applied) {
                modified[0] = true;
                aboveVanillaMax[0] |= level > Math.max(1, enchantment.value().getMaxLevel());
            }
        });

        return new AttributeResolution(totalValue[0], modified[0], aboveVanillaMax[0]);
    }

    private static double getBaseAttributeValue(ItemStack stack, Holder<Attribute> attribute, EquipmentSlot slot, double defaultValue) {
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return modifiers.compute(attribute, defaultValue, slot);
    }

    private static double applyAttributeModifier(double currentValue, double defaultValue, AttributeModifier modifier) {
        return currentValue + switch (modifier.operation()) {
            case ADD_VALUE -> modifier.amount();
            case ADD_MULTIPLIED_BASE -> modifier.amount() * defaultValue;
            case ADD_MULTIPLIED_TOTAL -> modifier.amount() * currentValue;
        };
    }

    private static String getAttackDamageLabel() {
        return Component.translatable(Attributes.ATTACK_DAMAGE.value().getDescriptionId()).getString();
    }

    private static String getAttackSpeedLabel() {
        return Component.translatable(Attributes.ATTACK_SPEED.value().getDescriptionId()).getString();
    }

    private static Component createStatLine(String text, int color) {
        return Component.literal(text).withStyle(style -> style.withColor(color));
    }

    private static void appendArmorEnchantBonusesToVanillaSection(ItemStack stack, List<Component> tooltip, List<Component> armorBonusLines) {
        EquipmentSlot slot = resolveEquipmentSlotSafely(stack);
        if (slot == null || !slot.isArmor()) {
            tooltip.addAll(armorBonusLines);
            return;
        }

        String slotHeader = getAttributeSectionHeader(EquipmentSlotGroup.bySlot(slot));
        int insertIndex = findAttributeSectionInsertIndex(tooltip, slotHeader);
        if (insertIndex < 0) {
            tooltip.addAll(armorBonusLines);
            return;
        }

        tooltip.addAll(insertIndex, armorBonusLines);
    }

    private static EquipmentSlot resolveEquipmentSlotSafely(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null) {
            return equippable.slot();
        }

        try {
            return stack.getEquipmentSlot();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int findAttributeSectionInsertIndex(List<Component> tooltip, String sectionHeader) {
        for (int i = 0; i < tooltip.size(); i++) {
            if (!sectionHeader.equals(tooltip.get(i).getString())) {
                continue;
            }

            int insertIndex = i + 1;
            while (insertIndex < tooltip.size() && !isAttributeSectionHeader(tooltip.get(insertIndex)) && !tooltip.get(insertIndex).getString().isBlank()) {
                insertIndex++;
            }
            return insertIndex;
        }
        return -1;
    }

    private static void replaceTooltipLine(List<Component> tooltip, String matchToken, String replacementText, int color) {
        replaceTooltipLineInternal(tooltip, replacementText, color, false, matchToken);
    }

    private static boolean replaceTooltipLineInAttributeSection(
        List<Component> tooltip,
        EquipmentSlotGroup section,
        String matchToken,
        String replacementText,
        int color
    ) {
        TooltipSection tooltipSection = findTooltipSection(tooltip, getAttributeSectionHeader(section));
        if (tooltipSection == null) {
            return false;
        }
        return replaceTooltipLineInRange(tooltip, tooltipSection.startIndex() + 1, tooltipSection.endIndex(), replacementText, color, matchToken);
    }

    private static @Nullable TooltipSection findTooltipSection(List<Component> tooltip, String sectionHeader) {
        for (int i = 0; i < tooltip.size(); i++) {
            if (!sectionHeader.equals(tooltip.get(i).getString())) {
                continue;
            }

            int endIndex = i + 1;
            while (endIndex < tooltip.size() && !isAttributeSectionHeader(tooltip.get(endIndex)) && !tooltip.get(endIndex).getString().isBlank()) {
                endIndex++;
            }
            return new TooltipSection(i, endIndex);
        }
        return null;
    }

    private static boolean replaceTooltipLineInRange(
        List<Component> tooltip,
        int startIndex,
        int endIndex,
        String replacementText,
        int color,
        String... matchTokens
    ) {
        int matchIndex = -1;
        String indentation = "";
        for (int i = startIndex; i < endIndex && i < tooltip.size(); i++) {
            String line = tooltip.get(i).getString();
            if (!containsAnyToken(line, matchTokens)) {
                continue;
            }
            if (matchIndex < 0) {
                matchIndex = i;
                indentation = getLeadingWhitespace(line);
                tooltip.set(i, Component.literal(indentation + replacementText).withStyle(style -> style.withColor(color)));
            } else {
                tooltip.remove(i);
                i--;
                endIndex--;
            }
        }
        return matchIndex >= 0;
    }

    private static void replaceTooltipLineInternal(
        List<Component> tooltip,
        String replacementText,
        int color,
        boolean appendIfMissing,
        String... matchTokens
    ) {
        int matchIndex = -1;
        String indentation = "";
        for (int i = 0; i < tooltip.size(); i++) {
            String line = tooltip.get(i).getString();
            if (!containsAnyToken(line, matchTokens)) {
                continue;
            }
            if (matchIndex < 0) {
                matchIndex = i;
                indentation = getLeadingWhitespace(line);
                tooltip.set(i, Component.literal(indentation + replacementText).withStyle(style -> style.withColor(color)));
            } else {
                tooltip.remove(i);
                i--;
            }
        }
        if (matchIndex < 0 && appendIfMissing) {
            tooltip.add(createStatLine(replacementText, color));
        }
    }

    private static void removeTooltipLinesContaining(List<Component> tooltip, String... matchTokens) {
        for (int i = 0; i < tooltip.size(); i++) {
            String line = tooltip.get(i).getString();
            if (containsAnyToken(line, matchTokens)) {
                tooltip.remove(i);
                i--;
            }
        }
    }

    private static void recolorTooltipLinesContaining(List<Component> tooltip, int color, String... matchTokens) {
        for (int i = 0; i < tooltip.size(); i++) {
            String line = tooltip.get(i).getString();
            if (!containsAnyToken(line, matchTokens)) {
                continue;
            }
            tooltip.set(i, Component.literal(line).withStyle(style -> style.withColor(color)));
        }
    }

    private static void recolorGeneratedTooltipLinesContaining(List<Component> tooltip, int color, String... matchTokens) {
        for (int i = 0; i < tooltip.size(); i++) {
            String line = tooltip.get(i).getString();
            if (!isGeneratedStatLine(line.trim(), matchTokens)) {
                continue;
            }
            tooltip.set(i, Component.literal(line).withStyle(style -> style.withColor(color)));
        }
    }

    private static String extractFirstTooltipLine(List<Component> tooltip, String... matchTokens) {
        String firstMatch = null;
        for (int i = 0; i < tooltip.size(); i++) {
            String line = tooltip.get(i).getString();
            if (!containsAnyToken(line, matchTokens)) {
                continue;
            }
            if (firstMatch == null) {
                firstMatch = line;
            }
            tooltip.remove(i);
            i--;
        }
        return firstMatch;
    }

    private static void removeEmptyAttributeSection(List<Component> tooltip, String sectionHeader) {
        for (int i = 0; i < tooltip.size(); i++) {
            if (!sectionHeader.equals(tooltip.get(i).getString())) {
                continue;
            }

            int nextIndex = i + 1;
            if (nextIndex < tooltip.size() && !tooltip.get(nextIndex).getString().isBlank() && !isAttributeSectionHeader(tooltip.get(nextIndex))) {
                return;
            }

            tooltip.remove(i);
            if (i > 0 && tooltip.get(i - 1).getString().isBlank()) {
                tooltip.remove(i - 1);
            }
            return;
        }
    }

    private static void removeExistingArmorBonusStatLines(List<Component> tooltip) {
        removeTooltipLinesMatchingStatLabel(
            tooltip,
            "Damage Reduction",
            "Fire Protection",
            "Burn Time Reduction",
            "Burning Time",
            "Explosion Protection",
            "Explosion Knockback Resistance",
            "Projectile Protection",
            "Fall Protection",
            "Fall Damage Reduction"
        );
    }

    private static void removeTooltipLinesMatchingStatLabel(List<Component> tooltip, String... labels) {
        for (int i = 0; i < tooltip.size(); i++) {
            String line = tooltip.get(i).getString().trim();
            if (isGeneratedStatLine(line, labels)) {
                tooltip.remove(i);
                i--;
            }
        }
    }

    private static boolean isGeneratedStatLine(String line, String... labels) {
        if (line.isEmpty()) {
            return false;
        }
        char first = line.charAt(0);
        if (!(first == '+' || first == '-' || Character.isDigit(first))) {
            return false;
        }
        return containsAnyToken(line, labels);
    }

    private static boolean containsAnyToken(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> getModdedStatLineTokens(Holder<Enchantment> enchantment) {
        List<String> tokens = new ArrayList<>();
        String path = enchantment.unwrapKey().map(key -> key.identifier().getPath()).orElse("").toLowerCase(Locale.ROOT);

        if (hasAttributeEffect(enchantment, Attributes.ATTACK_SPEED) || containsKeyword(path, "attack_speed", "attackspeed")) {
            tokens.add("Attack Speed");
        }
        if (containsKeyword(path, "lifesteal", "life_steal", "vampir", "leech", "drain")) {
            tokens.add("Life Steal");
        }
        if (containsKeyword(path, "sneak", "swift_sneak", "sneaking")) {
            tokens.add("Sneaking Speed");
        }
        if (containsKeyword(path, "speed", "swiftness", "rapid", "quick", "frenzy")) {
            tokens.add("Speed");
        }
        if (containsKeyword(path, "efficiency", "mining", "dig", "excavat", "quarry", "haste")) {
            tokens.add("Mining Speed");
        }

        return tokens;
    }

    private static boolean isSafeModdedStatToken(
        HolderLookup.RegistryLookup<Enchantment> enchantLookup,
        ItemStack stack,
        String token
    ) {
        return switch (token) {
            case "Sneaking Speed", "Speed" -> getEnchantLevel(stack, enchantLookup, Enchantments.SWIFT_SNEAK) <= 0
                && getEnchantLevel(stack, enchantLookup, Enchantments.SOUL_SPEED) <= 0;
            case "Mining Speed" -> getEnchantLevel(stack, enchantLookup, Enchantments.EFFICIENCY) <= 0;
            default -> true;
        };
    }

    private static boolean hasAttributeEffect(Holder<Enchantment> enchantment, Holder<Attribute> attribute) {
        for (var effect : enchantment.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES)) {
            if (effect.attribute().equals(attribute)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsKeyword(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static int preferLegendaryColor(int firstColor, int secondColor) {
        return firstColor == LEGENDARY_COLOR || secondColor == LEGENDARY_COLOR ? LEGENDARY_COLOR : VANILLA_BLUE_COLOR;
    }

    private static boolean isAttributeSectionHeader(Component component) {
        String line = component.getString();
        for (EquipmentSlotGroup group : EquipmentSlotGroup.values()) {
            if (getAttributeSectionHeader(group).equals(line)) {
                return true;
            }
        }
        return false;
    }

    private static String getAttributeSectionHeader(EquipmentSlotGroup group) {
        return Component.translatable("item.modifiers." + group.getSerializedName()).getString();
    }

    private static String getLeadingWhitespace(String value) {
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return value.substring(0, index);
    }

    private static int getEnchantColor(int level, int vanillaMaxLevel) {
        return level > vanillaMaxLevel ? LEGENDARY_COLOR : VANILLA_BLUE_COLOR;
    }

    private static int getLegendaryAwareColor(boolean aboveVanillaMax) {
        return aboveVanillaMax ? LEGENDARY_COLOR : VANILLA_BLUE_COLOR;
    }

    private static int getBurnTimeReductionPercent(int level) {
        return Math.max(0, level * 15);
    }

    private static double getSweepDamagePercent(int level) {
        if (level <= 0) {
            return 0.0D;
        }
        return (level / (double) (level + 1)) * 100.0D;
    }

    private static String formatPercentStat(double value, String label) {
        return formatValue(value) + "% " + label;
    }

    private static String formatSignedPercentStat(double value, String label) {
        return "+" + formatValue(value) + "% " + label;
    }

    private static String formatSignedFlatStat(double value, String label) {
        return "+" + formatValue(value) + " " + label;
    }

    private static String formatValue(double value) {
        double rounded = Math.round(value * 100.0D) / 100.0D;
        if (Math.abs(rounded - Math.rint(rounded)) < 1.0E-6D) {
            return Integer.toString((int) Math.rint(rounded));
        }
        if (Math.abs(rounded * 10.0D - Math.rint(rounded * 10.0D)) < 1.0E-6D) {
            return String.format(Locale.ROOT, "%.1f", rounded);
        }
        return String.format(Locale.ROOT, "%.2f", rounded);
    }
}
