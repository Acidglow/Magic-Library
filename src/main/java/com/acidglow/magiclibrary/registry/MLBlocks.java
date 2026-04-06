package com.acidglow.magiclibrary.registry;

import com.acidglow.magiclibrary.MagicLibrary;
import com.acidglow.magiclibrary.content.item.LibraryTierUpgradeItem;
import com.acidglow.magiclibrary.content.item.MagicLibraryBlockItem;
import com.acidglow.magiclibrary.content.item.TomeOfAmplificationItem;
import com.acidglow.magiclibrary.content.library.MagicLibraryBlock;
import com.acidglow.magiclibrary.content.library.MagicLibraryTier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MLBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MagicLibrary.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MagicLibrary.MODID);

    public static final DeferredBlock<MagicLibraryBlock> APPRENTICE_LIBRARY = BLOCKS.registerBlock(
        "apprentice_library",
        properties -> new MagicLibraryBlock(MagicLibraryTier.TIER1, properties),
        () -> BlockBehaviour.Properties.of().strength(2.0F).sound(SoundType.WOOD)
    );
    public static final DeferredBlock<MagicLibraryBlock> ADEPT_LIBRARY = BLOCKS.registerBlock(
        "adept_library",
        properties -> new MagicLibraryBlock(MagicLibraryTier.TIER2, properties),
        () -> BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD)
    );
    public static final DeferredBlock<MagicLibraryBlock> ARCHMAGE_LIBRARY = BLOCKS.registerBlock(
        "archmage_library",
        properties -> new MagicLibraryBlock(MagicLibraryTier.TIER3, properties),
        () -> BlockBehaviour.Properties.of().strength(3.0F).sound(SoundType.WOOD)
    );

    public static final DeferredItem<BlockItem> APPRENTICE_LIBRARY_ITEM = ITEMS.registerItem(
        "apprentice_library",
        properties -> new MagicLibraryBlockItem(APPRENTICE_LIBRARY.get(), properties),
        Item.Properties::new
    );
    public static final DeferredItem<BlockItem> ADEPT_LIBRARY_ITEM = ITEMS.registerItem(
        "adept_library",
        properties -> new MagicLibraryBlockItem(ADEPT_LIBRARY.get(), properties),
        Item.Properties::new
    );
    public static final DeferredItem<BlockItem> ARCHMAGE_LIBRARY_ITEM = ITEMS.registerItem(
        "archmage_library",
        properties -> new MagicLibraryBlockItem(ARCHMAGE_LIBRARY.get(), properties),
        Item.Properties::new
    );
    public static final DeferredItem<TomeOfAmplificationItem> TOME_OF_AMPLIFICATION = ITEMS.registerItem(
        "tome_of_amplification",
        TomeOfAmplificationItem::new,
        () -> new Item.Properties().stacksTo(1)
    );
    public static final DeferredItem<LibraryTierUpgradeItem> ADEPT_CORE = ITEMS.registerItem(
        "adept_core",
        properties -> new LibraryTierUpgradeItem(
            MagicLibraryTier.TIER1,
            ADEPT_LIBRARY,
            "item.magiclibrary.adept_core.desc",
            properties
        ),
        Item.Properties::new
    );
    public static final DeferredItem<LibraryTierUpgradeItem> ARCHMAGE_CORE = ITEMS.registerItem(
        "archmage_core",
        properties -> new LibraryTierUpgradeItem(
            MagicLibraryTier.TIER2,
            ARCHMAGE_LIBRARY,
            "item.magiclibrary.archmage_core.desc",
            properties
        ),
        Item.Properties::new
    );

    private MLBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
