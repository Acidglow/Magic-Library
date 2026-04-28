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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {
    /**
     * @author AcidGlow
     * @reason Replace the vanilla capped numeral rendering after other mixins have applied so compatibility is preserved.
     */
    @Inject(method = "getFullname", at = @At("RETURN"), cancellable = true)
    private static void magiclibrary$replaceCappedRomanNumerals(
        Holder<Enchantment> enchantment,
        int level,
        CallbackInfoReturnable<Component> cir
    ) {
        MutableComponent component = enchantment.value().description().copy();
        Component original = cir.getReturnValue();

        if (original != null) {
            component = component.withStyle(original.getStyle());
        } else if (enchantment.is(EnchantmentTags.CURSE)) {
            component = ComponentUtils.mergeStyles(component, Style.EMPTY.withColor(ChatFormatting.RED));
        } else {
            component = ComponentUtils.mergeStyles(component, Style.EMPTY.withColor(ChatFormatting.GRAY));
        }

        if (level != 1 || enchantment.value().getMaxLevel() != 1) {
            component.append(CommonComponents.SPACE).append(Component.literal(RomanNumeralUtil.toRoman(level)));
        }

        cir.setReturnValue(component);
    }
}
