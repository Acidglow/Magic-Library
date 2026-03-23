package com.acidglow.magiclibrary;

import com.acidglow.magiclibrary.command.MagicLibraryCommands;
import com.acidglow.magiclibrary.config.EnchantCapConfigLoader;
import com.acidglow.magiclibrary.registry.MLBlockEntities;
import com.acidglow.magiclibrary.registry.MLBlocks;
import com.acidglow.magiclibrary.registry.MLMenus;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

@Mod(MagicLibrary.MODID)
public class MagicLibrary {
    public static final String MODID = "magiclibrary";

    public MagicLibrary(IEventBus modEventBus, ModContainer modContainer) {
        MLBlocks.register(modEventBus);
        MLBlockEntities.register(modEventBus);
        MLMenus.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, MagicLibraryConfig.SPEC, "magiclibrary-common.toml");
        EnchantCapConfigLoader.initialize();

        modEventBus.addListener(this::addCreativeContents);
        modEventBus.addListener(MagicLibraryConfig::onCommonConfigLoading);
        modEventBus.addListener(MagicLibraryConfig::onCommonConfigReloading);
        NeoForge.EVENT_BUS.addListener(MagicLibraryCommands::register);
        NeoForge.EVENT_BUS.addListener(this::syncEnchantCapConfig);
    }

    private void addCreativeContents(BuildCreativeModeTabContentsEvent event) {
        if (CreativeModeTabs.FUNCTIONAL_BLOCKS.equals(event.getTabKey())) {
            event.accept(MLBlocks.APPRENTICE_LIBRARY_ITEM);
            event.accept(MLBlocks.ADEPT_LIBRARY_ITEM);
            event.accept(MLBlocks.ARCHMAGE_LIBRARY_ITEM);
            event.accept(MLBlocks.ADEPT_CORE);
            event.accept(MLBlocks.ARCHMAGE_CORE);
            event.accept(MLBlocks.TOME_OF_AMPLIFICATION);
        }

        if (CreativeModeTabs.INGREDIENTS.equals(event.getTabKey())) {
            event.accept(MLBlocks.ADEPT_CORE);
            event.accept(MLBlocks.ARCHMAGE_CORE);
        }
    }

    private void syncEnchantCapConfig(ServerAboutToStartEvent event) {
        EnchantCapConfigLoader.appendMissingEnchantEntries(event.getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT));
    }
}
