package com.evizka.deepwaater.worldgen;

import com.evizka.deepwaater.DeepWaater;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, DeepWaater.MOD_ID);

    /** "deepwaater:cenote" — deep sinkholes filled with groundwater. */
    public static final DeferredHolder<Feature<?>, CenoteFeature> CENOTE =
            FEATURES.register("cenote", () -> new CenoteFeature(CenoteFeature.Config.CODEC));

    /** "deepwaater:swamp_tree" — 2x2 trunk with a wide crown, growing out of the bog. */
    public static final DeferredHolder<Feature<?>, SwampTreeFeature> SWAMP_TREE =
            FEATURES.register("swamp_tree", () -> new SwampTreeFeature(SwampTreeFeature.Config.CODEC));

    private ModFeatures() {
    }
}
