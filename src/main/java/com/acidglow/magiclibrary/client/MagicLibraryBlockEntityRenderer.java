package com.acidglow.magiclibrary.client;

import com.acidglow.magiclibrary.content.library.MagicLibraryBlock;
import com.acidglow.magiclibrary.content.library.MagicLibraryBlockEntity;
import com.acidglow.magiclibrary.content.library.MagicLibraryTier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class MagicLibraryBlockEntityRenderer
    implements BlockEntityRenderer<MagicLibraryBlockEntity, MagicLibraryBlockEntityRenderState> {
    private enum FloatingDisplayType {
        DEFAULT,
        SWORD,
        AXE,
        PICKAXE,
        HOE,
        SHOVEL,
        SPEAR,
        MACE,
        TRIDENT,
        SHIELD,
        SHEARS,
        HORSE_ARMOR,
        BOW,
        CROSSBOW,
        ARMOR,
        ELYTRA
    }

    private record FloatingDisplayProfile(
        double offsetX,
        double offsetY,
        double offsetZ,
        float yawOffsetDegrees,
        float rotationSpeedDegrees,
        float balanceTiltDegrees,
        float tipDownDegrees,
        float scale
    ) {}

    private static final double BASE_HEIGHT = 1.45D;
    private static final double DISPLAY_VERTICAL_OFFSET = 0.10D;
    private static final FloatingDisplayProfile DEFAULT_DISPLAY_PROFILE = new FloatingDisplayProfile(0.5D, 1.55D, 0.5D, 0.0F, 0.0F, 0.0F, 45.0F, 0.7F);
    private static final FloatingDisplayProfile SWORD_DISPLAY_PROFILE = new FloatingDisplayProfile(0.5D, 1.55D, 0.5D, 0.0F, 0.0F, 0.0F, 225.0F, 0.7F);
    private static final FloatingDisplayProfile AXE_DISPLAY_PROFILE = new FloatingDisplayProfile(0.5D, 1.55D, 0.5D, 0.0F, 0.0F, 0.0F, 45.0F, 0.7F);
    private static final FloatingDisplayProfile PICKAXE_DISPLAY_PROFILE = new FloatingDisplayProfile(0.5D, 1.55D, 0.5D, 0.0F, 0.0F, 0.0F, 45.0F, 0.7F);
    private static final FloatingDisplayProfile HOE_DISPLAY_PROFILE = new FloatingDisplayProfile(0.5D, 1.55D, 0.5D, 0.0F, 0.0F, 0.0F, 45.0F, 0.7F);
    private static final FloatingDisplayProfile SHOVEL_DISPLAY_PROFILE = new FloatingDisplayProfile(0.5D, 1.55D, 0.5D, 0.0F, 0.0F, 0.0F, 45.0F, 0.7F);
    private static final FloatingDisplayProfile SPEAR_DISPLAY_PROFILE = new FloatingDisplayProfile(0.5D, 1.55D, 0.5D, 0.0F, 0.0F, 0.0F, -45.0F, 0.7F);
    private static final FloatingDisplayProfile MACE_DISPLAY_PROFILE = new FloatingDisplayProfile(0.5D, 1.55D, 0.5D, 0.0F, 0.0F, 0.0F, 45.0F, 0.7F);
    private static final FloatingDisplayProfile TRIDENT_DISPLAY_PROFILE = new FloatingDisplayProfile(0.85D, 2.6D, 0.85D, 0.0F, 0.0F, 0.0F, 0.0F, 0.7F);
    private static final FloatingDisplayProfile SHIELD_DISPLAY_PROFILE = new FloatingDisplayProfile(0.5D, 1.55D, 0.5D, 0.0F, 0.0F, 0.0F, 45.0F, 0.7F);
    private static final FloatingDisplayProfile SHEARS_DISPLAY_PROFILE = new FloatingDisplayProfile(0.5D, 1.55D, 0.5D, 0.0F, 0.0F, 0.0F, 45.0F, 0.7F);
    private static final FloatingDisplayProfile HORSE_ARMOR_DISPLAY_PROFILE = new FloatingDisplayProfile(0.5D, 1.55D, 0.5D, 0.0F, 0.0F, 0.0F, 0.0F, 0.7F);
    private static final FloatingDisplayProfile BOW_DISPLAY_PROFILE = new FloatingDisplayProfile(0.5D, 1.55D, 0.5D, 0.0F, 0.0F, 0.0F, 45.0F, 0.7F);
    private static final FloatingDisplayProfile CROSSBOW_DISPLAY_PROFILE = new FloatingDisplayProfile(0.5D, 1.55D, 0.5D, 0.0F, 0.0F, 0.0F, 45.0F, 0.7F);
    private static final FloatingDisplayProfile ARMOR_DISPLAY_PROFILE = new FloatingDisplayProfile(0.5D, 1.55D, 0.5D, 0.0F, 0.0F, 0.0F, 0.0F, 0.7F);
    private static final FloatingDisplayProfile ELYTRA_DISPLAY_PROFILE = new FloatingDisplayProfile(0.5D, 1.55D, 0.5D, 0.0F, 0.0F, 0.0F, 0.0F, 0.7F);
    private static final float BOOK_BASE_HEIGHT = 1.10F;
    private static final float BOOK_HOVER_BASE = 0.10F;
    private static final float BOOK_HOVER_SCALE = 0.01F;
    private static final float BOOK_STATIC_ROTATION = (float) (Math.PI / 2.0D);
    private static final float BOOK_STATIC_PAGE_FLIP = 1.00F;
    private static final float BOOK_STATIC_OPEN = 0.9F;
    private static final double RENDER_HEIGHT = 2.25D;

    private final SpriteGetter sprites;
    private final BookModel bookModel;
    private final ItemModelResolver itemModelResolver;

    public MagicLibraryBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.sprites = context.sprites();
        this.bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public MagicLibraryBlockEntityRenderState createRenderState() {
        return new MagicLibraryBlockEntityRenderState();
    }

    @Override
    public void extractRenderState(
        MagicLibraryBlockEntity blockEntity,
        MagicLibraryBlockEntityRenderState renderState,
        float partialTick,
        Vec3 cameraPosition,
        ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
    ) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, renderState, partialTick, cameraPosition, crumblingOverlay);
        renderState.displayItem.clear();
        renderState.displayMode = MagicLibraryBlockEntityRenderState.DisplayMode.NONE;
        renderState.facing = getFacing(blockEntity.getBlockState());
        renderState.animationTime = getAnimationTime(blockEntity, partialTick);
        renderState.bookRotation = 0.0F;
        renderState.bookFlip = 0.0F;
        renderState.bookOpen = 0.0F;
        applyFloatingDisplayProfile(renderState, DEFAULT_DISPLAY_PROFILE);

        ItemStack displayStack = blockEntity.getOutputItemView();
        MagicLibraryBlockEntityRenderState.DisplayMode displayMode = getDisplayModeForOutput(blockEntity, displayStack);
        renderState.displayMode = displayMode;
        if (displayMode == MagicLibraryBlockEntityRenderState.DisplayMode.NONE) {
            return;
        }

        if (blockEntity.getLevel() != null) {
            renderState.lightCoords = getDisplayLight(blockEntity.getBlockPos(), blockEntity.getLevel());
        }

        if (displayMode == MagicLibraryBlockEntityRenderState.DisplayMode.ANIMATED_BOOK) {
            setupBookAnimation(renderState);
            return;
        }

        applyFloatingDisplayProfile(renderState, getDisplayProfile(displayStack));

        this.itemModelResolver.updateForTopItem(
            renderState.displayItem,
            displayStack,
            ItemDisplayContext.NONE,
            blockEntity.getLevel(),
            null,
            HashCommon.long2int(blockEntity.getBlockPos().asLong())
        );
    }

    @Override
    public void submit(
        MagicLibraryBlockEntityRenderState renderState,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        CameraRenderState cameraRenderState
    ) {
        if (renderState.displayMode == MagicLibraryBlockEntityRenderState.DisplayMode.ANIMATED_BOOK) {
            renderAnimatedBook(renderState, poseStack, submitNodeCollector);
        } else if (
            renderState.displayMode == MagicLibraryBlockEntityRenderState.DisplayMode.FLOATING_GEAR
                && !renderState.displayItem.isEmpty()
        ) {
            renderFloatingGear(renderState, poseStack, submitNodeCollector);
        }
    }

    @Override
    public AABB getRenderBoundingBox(MagicLibraryBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0D, pos.getY() + RENDER_HEIGHT, pos.getZ() + 1.0D);
    }

    private void renderFloatingGear(
        MagicLibraryBlockEntityRenderState renderState,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector
    ) {
        Direction facing = renderState.facing;
        float hoverOffset = getHoverOffset(renderState.animationTime);
        float rotationDegrees = renderState.animationTime * renderState.displayRotationSpeedDegrees;
        double centeredOffsetX = renderState.displayOffsetX - 0.5D;
        double centeredOffsetZ = renderState.displayOffsetZ - 0.5D;

        poseStack.pushPose();
        poseStack.translate(0.5D, renderState.displayOffsetY + hoverOffset, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(centeredOffsetX, 0.0D, centeredOffsetZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(renderState.displayYawOffsetDegrees + rotationDegrees));
        poseStack.mulPose(Axis.XP.rotationDegrees(renderState.displayBalanceTiltDegrees));
        poseStack.mulPose(Axis.ZP.rotationDegrees(renderState.displayTipDownDegrees));
        poseStack.scale(renderState.displayScale, renderState.displayScale, renderState.displayScale);
        renderState.displayItem.submit(poseStack, submitNodeCollector, renderState.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    private void renderAnimatedBook(
        MagicLibraryBlockEntityRenderState renderState,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector
    ) {
        Direction facing = renderState.facing;

        poseStack.pushPose();
        poseStack.translate(0.5F, BOOK_BASE_HEIGHT + BOOK_HOVER_BASE + getBookHoverOffset(renderState.animationTime), 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.mulPose(Axis.YP.rotation(-renderState.bookRotation));
        poseStack.mulPose(Axis.ZP.rotationDegrees(80.0F));

        float pageFlip1 = Mth.clamp(Mth.frac(renderState.bookFlip + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
        float pageFlip2 = Mth.clamp(Mth.frac(renderState.bookFlip + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
        BookModel.State bookState = BookModel.State.forAnimation(renderState.animationTime, pageFlip1, pageFlip2, renderState.bookOpen);

        submitNodeCollector.submitModel(
            this.bookModel,
            bookState,
            poseStack,
            renderState.lightCoords,
            OverlayTexture.NO_OVERLAY,
            -1,
            EnchantTableRenderer.BOOK_TEXTURE,
            this.sprites,
            0,
            renderState.breakProgress
        );
        poseStack.popPose();
    }

    private static MagicLibraryBlockEntityRenderState.DisplayMode getDisplayModeForOutput(
        MagicLibraryBlockEntity blockEntity,
        ItemStack outputStack
    ) {
        if (isEnchantedBook(outputStack)) {
            return MagicLibraryBlockEntityRenderState.DisplayMode.ANIMATED_BOOK;
        }
        if (blockEntity.getTier() == MagicLibraryTier.TIER3 && isEnchantableGear(outputStack)) {
            return MagicLibraryBlockEntityRenderState.DisplayMode.FLOATING_GEAR;
        }
        return MagicLibraryBlockEntityRenderState.DisplayMode.NONE;
    }

    private static boolean isEnchantableGear(ItemStack stack) {
        return !stack.isEmpty() && !isEnchantedBook(stack) && (stack.isEnchantable() || stack.isEnchanted() || isSupportedGearItem(stack));
    }

    private static boolean isEnchantedBook(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.ENCHANTED_BOOK);
    }

    private static boolean isSupportedGearItem(ItemStack stack) {
        return stack.is(Items.ELYTRA)
            || stack.is(Items.SHIELD)
            || stack.is(Items.SHEARS)
            || stack.getItem().getDescriptionId().contains("horse_armor");
    }

    private static void applyFloatingDisplayProfile(
        MagicLibraryBlockEntityRenderState renderState,
        FloatingDisplayProfile profile
    ) {
        renderState.displayYawOffsetDegrees = profile.yawOffsetDegrees();
        renderState.displayRotationSpeedDegrees = profile.rotationSpeedDegrees();
        renderState.displayBalanceTiltDegrees = profile.balanceTiltDegrees();
        renderState.displayTipDownDegrees = profile.tipDownDegrees();
        renderState.displayScale = profile.scale();
        renderState.displayOffsetX = profile.offsetX();
        renderState.displayOffsetY = profile.offsetY();
        renderState.displayOffsetZ = profile.offsetZ();
    }

    private static FloatingDisplayProfile getDisplayProfile(ItemStack stack) {
        return switch (getFloatingDisplayType(stack)) {
            case SWORD -> SWORD_DISPLAY_PROFILE;
            case AXE -> AXE_DISPLAY_PROFILE;
            case PICKAXE -> PICKAXE_DISPLAY_PROFILE;
            case HOE -> HOE_DISPLAY_PROFILE;
            case SHOVEL -> SHOVEL_DISPLAY_PROFILE;
            case SPEAR -> SPEAR_DISPLAY_PROFILE;
            case MACE -> MACE_DISPLAY_PROFILE;
            case TRIDENT -> TRIDENT_DISPLAY_PROFILE;
            case SHIELD -> SHIELD_DISPLAY_PROFILE;
            case SHEARS -> SHEARS_DISPLAY_PROFILE;
            case HORSE_ARMOR -> HORSE_ARMOR_DISPLAY_PROFILE;
            case BOW -> BOW_DISPLAY_PROFILE;
            case CROSSBOW -> CROSSBOW_DISPLAY_PROFILE;
            case ARMOR -> ARMOR_DISPLAY_PROFILE;
            case ELYTRA -> ELYTRA_DISPLAY_PROFILE;
            case DEFAULT -> DEFAULT_DISPLAY_PROFILE;
        };
    }

    private static FloatingDisplayType getFloatingDisplayType(ItemStack stack) {
        if (stack.isEmpty()) {
            return FloatingDisplayType.DEFAULT;
        }

        if (stack.is(Items.ELYTRA)) {
            return FloatingDisplayType.ELYTRA;
        }
        if (stack.is(Items.SHIELD) || stack.getItem() instanceof ShieldItem) {
            return FloatingDisplayType.SHIELD;
        }
        if (stack.is(Items.TRIDENT) || stack.getItem() instanceof TridentItem) {
            return FloatingDisplayType.TRIDENT;
        }
        if (stack.getItem() instanceof BowItem) {
            return FloatingDisplayType.BOW;
        }
        if (stack.getItem() instanceof CrossbowItem) {
            return FloatingDisplayType.CROSSBOW;
        }
        if (stack.getItem() instanceof ShearsItem) {
            return FloatingDisplayType.SHEARS;
        }
        if (stack.getItem() instanceof AxeItem) {
            return FloatingDisplayType.AXE;
        }
        if (stack.getItem() instanceof HoeItem) {
            return FloatingDisplayType.HOE;
        }
        if (stack.getItem() instanceof ShovelItem) {
            return FloatingDisplayType.SHOVEL;
        }

        String descriptionId = stack.getItem().getDescriptionId();
        if (containsAnyToken(descriptionId, "sword")) {
            return FloatingDisplayType.SWORD;
        }
        if (containsAnyToken(descriptionId, "pickaxe")) {
            return FloatingDisplayType.PICKAXE;
        }
        if (containsAnyToken(descriptionId, "horse_armor")) {
            return FloatingDisplayType.HORSE_ARMOR;
        }
        if (containsAnyToken(descriptionId, "helmet", "chestplate", "leggings", "boots")) {
            return FloatingDisplayType.ARMOR;
        }
        if (containsAnyToken(descriptionId, "spear", "lance", "javelin")) {
            return FloatingDisplayType.SPEAR;
        }
        if (containsAnyToken(descriptionId, "mace")) {
            return FloatingDisplayType.MACE;
        }

        return FloatingDisplayType.DEFAULT;
    }

    private static boolean containsAnyToken(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static void setupBookAnimation(MagicLibraryBlockEntityRenderState renderState) {
        renderState.bookRotation = BOOK_STATIC_ROTATION;
        renderState.bookFlip = BOOK_STATIC_PAGE_FLIP;
        renderState.bookOpen = BOOK_STATIC_OPEN;
    }

    private static Direction getFacing(BlockState state) {
        return state.hasProperty(MagicLibraryBlock.FACING) ? state.getValue(MagicLibraryBlock.FACING) : Direction.NORTH;
    }

    private static float getHoverOffset(float animationTime) {
        return Mth.sin(animationTime * 0.1F) * 0.03F;
    }

    private static float getBookHoverOffset(float animationTime) {
        return Mth.sin(animationTime * 0.1F) * BOOK_HOVER_SCALE;
    }

    private static float getAnimationTime(MagicLibraryBlockEntity blockEntity, float partialTick) {
        if (blockEntity.getLevel() == null) {
            return partialTick;
        }
        return blockEntity.getLevel().getGameTime() + partialTick;
    }

    private static int getDisplayLight(BlockPos blockPos, net.minecraft.world.level.Level level) {
        int baseLight = LevelRenderer.getLightCoords(level, blockPos);
        int displayLight = LevelRenderer.getLightCoords(level, blockPos.above());
        return LightCoordsUtil.pack(
            Math.max(LightCoordsUtil.block(baseLight), LightCoordsUtil.block(displayLight)),
            Math.max(LightCoordsUtil.sky(baseLight), LightCoordsUtil.sky(displayLight))
        );
    }
}
