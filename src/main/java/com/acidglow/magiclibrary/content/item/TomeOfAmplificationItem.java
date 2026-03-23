package com.acidglow.magiclibrary.content.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

public class TomeOfAmplificationItem extends Item {
    private static final int LEGENDARY_COLOR = 0xFFFFAA00;

    public TomeOfAmplificationItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(style -> style.withColor(LEGENDARY_COLOR));
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        Item.TooltipContext context,
        TooltipDisplay tooltipDisplay,
        Consumer<Component> tooltipAdder,
        TooltipFlag flag
    ) {
        tooltipAdder.accept(
            Component.translatable("item.magiclibrary.tome_of_amplification.desc")
                .withStyle(ChatFormatting.GRAY)
        );
    }
}
