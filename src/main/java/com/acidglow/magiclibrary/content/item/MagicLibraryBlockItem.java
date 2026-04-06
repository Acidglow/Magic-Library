package com.acidglow.magiclibrary.content.item;

import com.acidglow.magiclibrary.registry.MLBlockEntities;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class MagicLibraryBlockItem extends BlockItem {
    private static final String STORED_ENCHANT_DATA_TAG = "StoredEnchantData";

    public MagicLibraryBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        Item.TooltipContext context,
        TooltipDisplay tooltipDisplay,
        Consumer<Component> tooltipAdder,
        TooltipFlag flag
    ) {
        TypedEntityData<BlockEntityType<?>> blockEntityData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (blockEntityData == null || blockEntityData.type() != MLBlockEntities.MAGIC_LIBRARY.get()) {
            return;
        }

        CompoundTag tag = blockEntityData.copyTagWithoutId();
        int storedEnchantCount = tag.getCompound(STORED_ENCHANT_DATA_TAG).map(CompoundTag::size).orElse(0);
        tooltipAdder.accept(Component.literal("Stored enchants: " + storedEnchantCount).withStyle(ChatFormatting.GRAY));
    }
}
