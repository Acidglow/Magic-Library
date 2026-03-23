package com.acidglow.magiclibrary.registry;

import com.acidglow.magiclibrary.MagicLibrary;
import com.acidglow.magiclibrary.content.library.MagicLibraryBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MLBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MagicLibrary.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MagicLibraryBlockEntity>> MAGIC_LIBRARY =
        BLOCK_ENTITY_TYPES.register(
            "magic_library",
            () -> new BlockEntityType<>(
                MagicLibraryBlockEntity::new,
                MLBlocks.APPRENTICE_LIBRARY.get(),
                MLBlocks.ADEPT_LIBRARY.get(),
                MLBlocks.ARCHMAGE_LIBRARY.get()
            )
        );

    private MLBlockEntities() {}

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
