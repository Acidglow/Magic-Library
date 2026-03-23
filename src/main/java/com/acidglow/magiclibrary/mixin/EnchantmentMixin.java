package com.acidglow.magiclibrary.mixin;

import com.acidglow.magiclibrary.util.RomanNumeralUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {
    /**
     * @author AcidGlow
     * @reason Render enchant levels with unrestricted Roman numerals so amplified levels above IX/X display correctly.
     */
    @Overwrite
    public static Component getFullname(Holder<Enchantment> enchantment, int level) {
        MutableComponent component = enchantment.value().description().copy();
        if (enchantment.is(EnchantmentTags.CURSE)) {
            component = ComponentUtils.mergeStyles(component, Style.EMPTY.withColor(ChatFormatting.RED));
        } else {
            component = ComponentUtils.mergeStyles(component, Style.EMPTY.withColor(ChatFormatting.GRAY));
        }

        if (level != 1 || enchantment.value().getMaxLevel() != 1) {
            component.append(CommonComponents.SPACE).append(Component.literal(RomanNumeralUtil.toRoman(level)));
        }

        return component;
    }
}
