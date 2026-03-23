package com.acidglow.magiclibrary.util;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;

public final class EnchantDisplayUtil {
    private static final String MINECRAFT_NAMESPACE = "minecraft";
    public static final int LEGENDARY_COLOR = 0xFFFFAA00;
    public static final int NORMAL_ENCHANT_COLOR = 0xFF5555FF;

    private EnchantDisplayUtil() {}

    public static int getOriginalEnchantMaxLevel(Holder<Enchantment> enchantment) {
        return getOriginalEnchantMaxLevel(enchantment.value());
    }

    public static int getOriginalEnchantMaxLevel(Enchantment enchantment) {
        return Math.max(1, enchantment.getMaxLevel());
    }

    public static boolean isAboveOriginalMax(Holder<Enchantment> enchantment, int level) {
        return level > getOriginalEnchantMaxLevel(enchantment);
    }

    public static boolean isModdedEnchant(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey().map(key -> !MINECRAFT_NAMESPACE.equals(key.identifier().getNamespace())).orElse(false);
    }

    public static boolean isAboveOriginalMax(Enchantment enchantment, int level) {
        return level > getOriginalEnchantMaxLevel(enchantment);
    }

    public static int getEnchantTextColor(Holder<Enchantment> enchantment, int level) {
        return getEnchantTextColor(enchantment.value(), level);
    }

    public static int getEnchantTextColor(Enchantment enchantment, int level) {
        return isAboveOriginalMax(enchantment, level) ? LEGENDARY_COLOR : NORMAL_ENCHANT_COLOR;
    }
}
