package com.acidglow.magiclibrary.content.library;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record EnchantData(long storedPoints, int maxDiscoveredLevel) {
    public static final Codec<EnchantData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.LONG.fieldOf("stored_points").forGetter(EnchantData::storedPoints),
            Codec.INT.fieldOf("max_discovered_level").forGetter(EnchantData::maxDiscoveredLevel)
        ).apply(instance, EnchantData::new)
    );
}
