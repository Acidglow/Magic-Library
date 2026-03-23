package com.acidglow.magiclibrary.client;

import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class LegendaryBookTooltipHandler {
    private LegendaryBookTooltipHandler() {}

    public static void onItemTooltip(ItemTooltipEvent event) {
        // Enchant name lines keep vanilla styling. Only Magic Library-generated stat lines use legendary coloring.
    }
}
