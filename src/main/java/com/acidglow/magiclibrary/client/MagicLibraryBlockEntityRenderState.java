package com.acidglow.magiclibrary.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public final class MagicLibraryBlockEntityRenderState extends BlockEntityRenderState {
    public enum DisplayMode {
        NONE,
        FLOATING_GEAR,
        ANIMATED_BOOK
    }

    public final ItemStackRenderState displayItem = new ItemStackRenderState();
    public DisplayMode displayMode = DisplayMode.NONE;
    public float animationTime;
    public float bookRotation;
    public float bookFlip;
    public float bookOpen;
    public float displayYawOffsetDegrees;
    public float displayRotationSpeedDegrees;
    public float displayBalanceTiltDegrees;
    public float displayTipDownDegrees;
    public float displayScale;
    public double displayOffsetX;
    public double displayOffsetY;
    public double displayOffsetZ;
}
