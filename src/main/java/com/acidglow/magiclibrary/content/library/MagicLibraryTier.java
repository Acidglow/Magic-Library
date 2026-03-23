package com.acidglow.magiclibrary.content.library;

public enum MagicLibraryTier {
    TIER1(1, "Apprentice"),
    TIER2(2, "Adept"),
    TIER3(3, "Archmage");

    private final int tier;
    private final String displayName;

    MagicLibraryTier(int tier, String displayName) {
        this.tier = tier;
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public int getTier() {
        return this.tier;
    }
}
