package com.acidglow.magiclibrary.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class LegendaryBookMarkerUtil {
    public static final String LEGENDARY_BOOK_MARKER_KEY = "magiclibrary_legendary_generated_book";

    private LegendaryBookMarkerUtil() {}

    public static void setLegendaryGeneratedBookMarker(ItemStack stack, boolean marked) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (marked) {
                tag.putBoolean(LEGENDARY_BOOK_MARKER_KEY, true);
            } else {
                tag.remove(LEGENDARY_BOOK_MARKER_KEY);
            }
        });
    }

    public static boolean isLegendaryGeneratedBook(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }
        return customData.copyTag().getBoolean(LEGENDARY_BOOK_MARKER_KEY).orElse(false);
    }
}
