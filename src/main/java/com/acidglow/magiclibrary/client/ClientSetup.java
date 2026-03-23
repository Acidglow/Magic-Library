package com.acidglow.magiclibrary.client;

import com.acidglow.magiclibrary.MagicLibrary;
import com.acidglow.magiclibrary.registry.MLMenus;
import com.acidglow.magiclibrary.registry.MLBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = MagicLibrary.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private static boolean tooltipListenerRegistered;

    private ClientSetup() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(MLMenus.MAGIC_LIBRARY_MENU.get(), MagicLibraryScreen::new);
        if (!tooltipListenerRegistered) {
            tooltipListenerRegistered = true;
            NeoForge.EVENT_BUS.addListener(LegendaryBookTooltipHandler::onItemTooltip);
            NeoForge.EVENT_BUS.addListener(AmplifiedGearTooltipHandler::onItemTooltip);
        }
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MLBlockEntities.MAGIC_LIBRARY.get(), MagicLibraryBlockEntityRenderer::new);
    }
}
