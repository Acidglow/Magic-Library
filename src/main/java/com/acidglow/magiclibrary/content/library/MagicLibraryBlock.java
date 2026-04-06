package com.acidglow.magiclibrary.content.library;

import com.acidglow.magiclibrary.registry.MLBlockEntities;
import com.acidglow.magiclibrary.server.MagicLibraryAdminData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class MagicLibraryBlock extends Block implements EntityBlock {
    public static final EnumProperty<net.minecraft.core.Direction> FACING = HorizontalDirectionalBlock.FACING;
    private static final float HAND_BREAK_PROGRESS = 1.0F / 60.0F; // Equivalent to breaking wood by hand.
    private static final float TOOL_BREAK_PROGRESS = 4.0F / 45.0F; // Equivalent to stone pickaxe breaking stone.

    private final MagicLibraryTier tier;

    public MagicLibraryBlock(MagicLibraryTier tier, BlockBehaviour.Properties properties) {
        super(properties);
        this.tier = tier;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    public MagicLibraryTier getTier() {
        return this.tier;
    }

    public static boolean upgradeLibrary(Level level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (level.isClientSide()) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof MagicLibraryBlockEntity libraryBlockEntity)) {
            return false;
        }

        CompoundTag savedData = libraryBlockEntity.saveUpgradeData(level.registryAccess());
        if (!level.setBlock(pos, newState, Block.UPDATE_ALL)) {
            return false;
        }

        if (!(level.getBlockEntity(pos) instanceof MagicLibraryBlockEntity upgradedBlockEntity)) {
            return false;
        }

        upgradedBlockEntity.loadUpgradeData(savedData, level.registryAccess(), newState);
        upgradedBlockEntity.setChanged();
        level.sendBlockUpdated(pos, oldState, newState, Block.UPDATE_ALL);
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hitResult
    ) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MagicLibraryBlockEntity magicLibraryBlockEntity) {
                if (magicLibraryBlockEntity.getTier() == MagicLibraryTier.TIER3
                    && MagicLibraryAdminData.consumePendingSupreme(serverPlayer)) {
                    magicLibraryBlockEntity.applySupremeUpgrade();
                }
                serverPlayer.openMenu(magicLibraryBlockEntity, buffer -> buffer.writeBlockPos(pos));
                return InteractionResult.CONSUME;
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MagicLibraryBlockEntity(pos, state);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return 0.0F;
        }
        return isToolLikeItem(player.getMainHandItem()) ? TOOL_BREAK_PROGRESS : HAND_BREAK_PROGRESS;
    }

    @Override
    public void playerDestroy(
        Level level,
        Player player,
        BlockPos pos,
        BlockState state,
        @Nullable BlockEntity blockEntity,
        ItemStack tool
    ) {
        player.awardStat(net.minecraft.stats.Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);

        if (level.isClientSide() || player.isCreative()) {
            return;
        }

        ItemStack dropStack = createLibraryDropStack(level, blockEntity);
        if (!dropStack.isEmpty()) {
            popResource(level, pos, dropStack);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide()) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof MagicLibraryBlockEntity libraryBlockEntity) {
            libraryBlockEntity.applyStowedUpkeep(level.getGameTime());
        }
    }

    private ItemStack createLibraryDropStack(Level level, @Nullable BlockEntity blockEntity) {
        ItemStack dropStack = new ItemStack(this);
        if (blockEntity instanceof MagicLibraryBlockEntity libraryBlockEntity) {
            saveLibraryDataToItem(level, libraryBlockEntity, dropStack);
        }
        return dropStack;
    }

    private static void saveLibraryDataToItem(Level level, MagicLibraryBlockEntity blockEntity, ItemStack dropStack) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        blockEntity.saveCustomOnly(output);
        output.putLong("StowedGameTime", level.getGameTime());
        BlockItem.setBlockEntityData(dropStack, blockEntity.getType(), output);
    }

    private static boolean isToolLikeItem(ItemStack stack) {
        return !stack.isEmpty()
            && (
                stack.has(DataComponents.TOOL)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES)
                || stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.SPEARS)
            );
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide() || blockEntityType != MLBlockEntities.MAGIC_LIBRARY.get()) {
            return null;
        }

        return (tickLevel, tickPos, tickState, tickBlockEntity) ->
            MagicLibraryBlockEntity.serverTick(tickLevel, tickPos, tickState, (MagicLibraryBlockEntity) tickBlockEntity);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }
}
