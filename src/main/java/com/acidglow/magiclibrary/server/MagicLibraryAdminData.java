package com.acidglow.magiclibrary.server;

import net.minecraft.server.level.ServerPlayer;

public final class MagicLibraryAdminData {
    public static final String PENDING_SUPREME_TAG = "magiclibrary_pending_supreme";

    private MagicLibraryAdminData() {
    }

    public static void setPendingSupreme(ServerPlayer player, boolean pending) {
        player.getPersistentData().putBoolean(PENDING_SUPREME_TAG, pending);
    }

    public static boolean hasPendingSupreme(ServerPlayer player) {
        return player.getPersistentData().getBoolean(PENDING_SUPREME_TAG).orElse(false);
    }

    public static boolean consumePendingSupreme(ServerPlayer player) {
        if (!hasPendingSupreme(player)) {
            return false;
        }

        setPendingSupreme(player, false);
        return true;
    }
}
