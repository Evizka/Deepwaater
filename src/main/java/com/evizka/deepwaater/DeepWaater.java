package com.evizka.deepwaater;

import com.evizka.deepwaater.block.ModBlocks;
import com.evizka.deepwaater.item.ModItems;
import com.evizka.deepwaater.worldgen.DeepBogRegion;
import com.evizka.deepwaater.worldgen.ModFeatures;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import terrablender.api.Regions;

// The value here must match the modId in META-INF/neoforge.mods.toml (filled from gradle.properties).
@Mod(DeepWaater.MOD_ID)
public class DeepWaater {
    public static final String MOD_ID = "deepwaater";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DeepWaater(IEventBus modEventBus) {
        // Blocks, block items and the creative tab.
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_TABS.register(modEventBus);

        // World generation features: cenote network and swamp trees.
        ModFeatures.FEATURES.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // TerraBlender needs regions to be registered on the main thread.
        event.enqueueWork(() -> {
            // Weight 2 keeps the bog rare compared to vanilla biomes.
            Regions.register(new DeepBogRegion(ResourceLocation.fromNamespaceAndPath(MOD_ID, "overworld"), 2));
            LOGGER.info("Deepwaater: registered Deep Bog overworld region");
        });
    }
}
