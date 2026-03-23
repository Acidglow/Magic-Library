package com.acidglow.magiclibrary.registry;

import com.acidglow.magiclibrary.MagicLibrary;
import com.acidglow.magiclibrary.content.library.MagicLibraryMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MLMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MagicLibrary.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<MagicLibraryMenu>> MAGIC_LIBRARY_MENU =
        MENUS.register("magic_library", () -> IMenuTypeExtension.create(MagicLibraryMenu::new));

    private MLMenus() {}

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
