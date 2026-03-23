package com.acidglow.magiclibrary.content.item;

import com.acidglow.magiclibrary.content.library.MagicLibraryBlock;
import com.acidglow.magiclibrary.content.library.MagicLibraryTier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class LibraryTierUpgradeItem extends Item {
    private final MagicLibraryTier sourceTier;
    private final Supplier<? extends Block> targetBlock;
    private final String descriptionKey;

    public LibraryTierUpgradeItem(
        MagicLibraryTier sourceTier,
        Supplier<? extends Block> targetBlock,
        String descriptionKey,
        Properties properties
    ) {
        super(properties);
        this.sourceTier = sourceTier;
        this.targetBlock = targetBlock;
        this.descriptionKey = descriptionKey;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockState clickedState = level.getBlockState(context.getClickedPos());
        if (!(clickedState.getBlock() instanceof MagicLibraryBlock libraryBlock) || libraryBlock.getTier() != this.sourceTier) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockState upgradedState = this.targetBlock.get().defaultBlockState();
        if (clickedState.hasProperty(MagicLibraryBlock.FACING) && upgradedState.hasProperty(MagicLibraryBlock.FACING)) {
            upgradedState = upgradedState.setValue(MagicLibraryBlock.FACING, clickedState.getValue(MagicLibraryBlock.FACING));
        }

        if (!MagicLibraryBlock.upgradeLibrary(level, context.getClickedPos(), clickedState, upgradedState)) {
            return InteractionResult.FAIL;
        }

        Player player = context.getPlayer();
        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }

        level.playSound(
            null,
            context.getClickedPos(),
            upgradedState.getSoundType(level, context.getClickedPos(), player).getPlaceSound(),
            net.minecraft.sounds.SoundSource.BLOCKS,
            1.0F,
            1.0F
        );
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        Item.TooltipContext context,
        TooltipDisplay tooltipDisplay,
        Consumer<Component> tooltipAdder,
        TooltipFlag flag
    ) {
        tooltipAdder.accept(Component.translatable(this.descriptionKey).withStyle(ChatFormatting.GRAY));
    }
}
