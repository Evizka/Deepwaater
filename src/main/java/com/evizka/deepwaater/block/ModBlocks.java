package com.evizka.deepwaater.block;

import com.evizka.deepwaater.DeepWaater;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** All blocks of the mod. The biome, trees and caves use these instead of vanilla blocks. */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DeepWaater.MOD_ID);

    /** Trunk of the swamp trees. Keeps its axis when placed, like a vanilla log. */
    public static final DeferredBlock<RotatedPillarBlock> DEEP_BOG_LOG = BLOCKS.registerBlock(
            "deep_bog_log",
            RotatedPillarBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(2.0F)
                    .sound(SoundType.WOOD));

    /** Crown of the swamp trees. Leaves decay unless placed as persistent (the trees do that). */
    public static final DeferredBlock<LeavesBlock> DEEP_BOG_LEAVES = BLOCKS.registerBlock(
            "deep_bog_leaves",
            LeavesBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT)
                    .strength(0.2F)
                    .randomTicks()
                    .sound(SoundType.GRASS)
                    .noOcclusion()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)
                    .pushReaction(PushReaction.DESTROY));

    /** Roots that grow down into the soil from the base of the trees. */
    public static final DeferredBlock<Block> DEEP_BOG_ROOTS = BLOCKS.registerSimpleBlock(
            "deep_bog_roots",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT)
                    .strength(0.7F)
                    .sound(SoundType.ROOTED_DIRT));

    /** Soft mud at the bottom of the cenotes. */
    public static final DeferredBlock<Block> BOG_MUD = BLOCKS.registerSimpleBlock(
            "bog_mud",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT)
                    .strength(0.6F)
                    .sound(SoundType.MUD));

    /** Moss on the walls of the cenotes. */
    public static final DeferredBlock<Block> BOG_MOSS = BLOCKS.registerSimpleBlock(
            "bog_moss",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT)
                    .strength(0.6F)
                    .sound(SoundType.MOSS));

    private ModBlocks() {
    }
}
