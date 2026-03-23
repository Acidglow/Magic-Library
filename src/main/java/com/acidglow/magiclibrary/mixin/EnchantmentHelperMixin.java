package com.acidglow.magiclibrary.mixin;

import com.acidglow.magiclibrary.util.MagicLibraryEnchantScaling;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
    /**
     * @author Acidglow
     * @reason Route gameplay level resolution through Magic Library amplification rules.
     */
    @Overwrite
    @Deprecated
    public static int getItemEnchantmentLevel(Holder<Enchantment> enchantment, ItemStack stack) {
        int rawLevel = stack.getEnchantmentLevel(enchantment);
        return MagicLibraryEnchantScaling.getEffectiveGameplayLevel(enchantment, rawLevel);
    }

    /**
     * @author Acidglow
     * @reason Ensure all gameplay enchant iteration uses cap-aware amplification levels.
     */
    @Overwrite
    public static void runIterationOnItem(ItemStack stack, EnchantmentHelper.EnchantmentVisitor visitor) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        // Neo: Respect gameplay-only enchantments when doing iterations.
        var lookup = net.neoforged.neoforge.common.CommonHooks.resolveLookup(net.minecraft.core.registries.Registries.ENCHANTMENT);
        if (lookup != null) {
            enchantments = stack.getAllEnchantments(lookup);
        }

        for (Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            int level = MagicLibraryEnchantScaling.getEffectiveGameplayLevel(entry.getKey(), entry.getIntValue());
            if (level > 0) {
                visitor.accept(entry.getKey(), level);
            }
        }
    }

    /**
     * @author Acidglow
     * @reason Ensure slot-filtered gameplay enchant iteration uses cap-aware amplification levels.
     */
    @Overwrite
    public static void runIterationOnItem(
        ItemStack stack, EquipmentSlot slot, LivingEntity entity, EnchantmentHelper.EnchantmentInSlotVisitor visitor
    ) {
        if (!stack.isEmpty()) {
            ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);

            // Neo: Respect gameplay-only enchantments when doing iterations.
            enchantments = stack.getAllEnchantments(entity.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT));

            if (enchantments != null && !enchantments.isEmpty()) {
                EnchantedItemInUse enchantedItemInUse = new EnchantedItemInUse(stack, slot, entity);

                for (Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
                    Holder<Enchantment> enchantment = entry.getKey();
                    if (enchantment.value().matchingSlot(slot)) {
                        int level = MagicLibraryEnchantScaling.getEffectiveGameplayLevel(enchantment, entry.getIntValue());
                        if (level > 0) {
                            visitor.accept(enchantment, level, enchantedItemInUse);
                        }
                    }
                }
            }
        }
    }
}
