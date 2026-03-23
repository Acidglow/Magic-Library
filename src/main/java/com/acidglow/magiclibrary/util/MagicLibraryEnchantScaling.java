package com.acidglow.magiclibrary.util;

import com.acidglow.magiclibrary.MagicLibraryConfig;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

public final class MagicLibraryEnchantScaling {
    private MagicLibraryEnchantScaling() {
    }

    public static float getSharpnessDamageBonus(int level) {
        if (level <= 0) {
            return 0.0F;
        }
        return 0.5F * Math.max(0, level - 1) + 1.0F;
    }

    public static float getSmiteDamageBonus(int level) {
        return level <= 0 ? 0.0F : 2.5F * level;
    }

    public static float getBaneOfArthropodsDamageBonus(int level) {
        return level <= 0 ? 0.0F : 2.5F * level;
    }

    public static float getImpalingDamageBonus(int level) {
        return level <= 0 ? 0.0F : 2.5F * level;
    }

    public static float getDensityDamageBonusPerFallenBlock(int level) {
        return level <= 0 ? 0.0F : 0.5F * level;
    }

    public static int getEfficiencyMiningSpeedBonus(int level) {
        if (level <= 0) {
            return 0;
        }
        return level * level + 1;
    }

    public static int getProtectionDamageReductionPercent(int level) {
        if (level <= 0) {
            return 0;
        }
        return Math.max(0, level * 4);
    }

    public static int getFireProtectionDamageReductionPercent(int level) {
        if (level <= 0) {
            return 0;
        }
        return Math.min(80, Math.max(0, level * 8));
    }

    public static int getBlastProtectionDamageReductionPercent(int level) {
        if (level <= 0) {
            return 0;
        }
        return Math.min(80, Math.max(0, level * 8));
    }

    public static int getProjectileProtectionDamageReductionPercent(int level) {
        if (level <= 0) {
            return 0;
        }
        return Math.min(80, Math.max(0, level * 8));
    }

    public static int getFeatherFallingDamageReductionPercent(int level) {
        if (level <= 0) {
            return 0;
        }
        return Math.min(80, Math.max(0, level * 12));
    }

    public static float getPowerArrowDamageBonus(int level) {
        if (level <= 0) {
            return 0.0F;
        }
        return 0.5F * level + 0.5F;
    }

    public static int getEffectiveGameplayLevel(Holder<Enchantment> enchantment, int rawLevel) {
        if (rawLevel <= 0) {
            return 0;
        }

        Identifier enchantmentId = getEnchantRegistryId(enchantment);
        if (enchantmentId == null) {
            return rawLevel;
        }

        int vanillaMaxLevel = Math.max(1, enchantment.value().getMaxLevel());
        int configuredLimit = MagicLibraryConfig.getConfiguredLibraryLevelLimit(enchantmentId, vanillaMaxLevel);
        int cappedByConfig = Math.min(rawLevel, configuredLimit);
        return Math.max(0, cappedByConfig);
    }

    public static boolean isAboveVanillaMax(Holder<Enchantment> enchantment, int level) {
        return level > Math.max(1, enchantment.value().getMaxLevel());
    }

    public static @Nullable Identifier getEnchantRegistryId(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey().map(resourceKey -> resourceKey.identifier()).orElse(null);
    }
}
