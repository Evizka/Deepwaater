package com.evizka.deepwaater.worldgen;

import com.evizka.deepwaater.DeepWaater;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public final class ModBiomes {
    /** Biome definition lives in data/deepwaater/worldgen/biome/deep_bog.json */
    public static final ResourceKey<Biome> DEEP_BOG = ResourceKey.create(
            Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(DeepWaater.MOD_ID, "deep_bog"));

    private ModBiomes() {
    }
}
