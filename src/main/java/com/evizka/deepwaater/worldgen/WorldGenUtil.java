package com.evizka.deepwaater.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Small helpers shared by the custom features. */
final class WorldGenUtil {
    private WorldGenUtil() {
    }

    /**
     * Writes a block only if the position is inside the chunk currently being generated
     * and inside the world's build height. Features that cross into a neighbouring chunk
     * would otherwise trigger warnings or get cut off.
     */
    static void setBlockInChunk(WorldGenLevel level, ChunkPos chunk, BlockPos pos, BlockState state) {
        if (!new ChunkPos(pos).equals(chunk)) {
            return;
        }
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
            return;
        }
        level.setBlock(pos, state, 2);
    }

    /** Air or any block that a tree or vine may grow into (water, grass, flowers...). */
    static boolean isReplaceable(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    /** Deterministic pseudo-random value in [0, 1] for an (x, z) column and a seed. */
    static double hash01(int x, int z, int seed) {
        int h = seed ^ (x * 0x27d4eb2d) ^ (z * 0x165667b1);
        h ^= (h >>> 15);
        h *= 0x2c1b3c6d;
        h ^= (h >>> 12);
        h *= 0x297a2d39;
        h ^= (h >>> 15);
        return (h & 0xFFFF) / 65535.0;
    }
}
