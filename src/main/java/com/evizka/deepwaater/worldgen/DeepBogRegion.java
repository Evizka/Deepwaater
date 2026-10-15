package com.evizka.deepwaater.worldgen;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import terrablender.api.ParameterUtils;
import terrablender.api.Region;
import terrablender.api.RegionType;

import java.util.function.Consumer;

/**
 * Tells TerraBlender where in the overworld climate map the Deep Bog should appear.
 * The ranges below describe warm, wet, low-lying land near the coast/inland border,
 * which is roughly where vanilla swamps are generated.
 */
public class DeepBogRegion extends Region {
    public DeepBogRegion(ResourceLocation name, int weight) {
        super(name, RegionType.OVERWORLD, weight);
    }

    @Override
    public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        new ParameterUtils.ParameterPointListBuilder()
                .temperature(ParameterUtils.Temperature.span(
                        ParameterUtils.Temperature.NEUTRAL, ParameterUtils.Temperature.WARM))
                .humidity(ParameterUtils.Humidity.span(
                        ParameterUtils.Humidity.WET, ParameterUtils.Humidity.HUMID))
                .continentalness(ParameterUtils.Continentalness.span(
                        ParameterUtils.Continentalness.NEAR_INLAND, ParameterUtils.Continentalness.MID_INLAND))
                .erosion(ParameterUtils.Erosion.span(
                        ParameterUtils.Erosion.EROSION_3, ParameterUtils.Erosion.EROSION_4))
                .depth(ParameterUtils.Depth.SURFACE)
                .weirdness(ParameterUtils.Weirdness.span(
                        ParameterUtils.Weirdness.LOW_SLICE_NORMAL_DESCENDING,
                        ParameterUtils.Weirdness.LOW_SLICE_VARIANT_ASCENDING))
                .build()
                .forEach(point -> mapper.accept(Pair.of(point, ModBiomes.DEEP_BOG)));
    }
}
