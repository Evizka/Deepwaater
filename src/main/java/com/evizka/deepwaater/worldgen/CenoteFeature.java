package com.evizka.deepwaater.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * A cenote: a round, deep sinkhole that opens to the sky and is filled with
 * groundwater at the bottom. The walls get mossy patches and hanging vines.
 *
 * The pit is carved down from the surface. Blocks are only written inside the chunk
 * that is currently being generated, so the feature never touches neighbouring chunks.
 */
public class CenoteFeature extends Feature<CenoteFeature.Config> {

    public CenoteFeature(Codec<Config> codec) {
        super(codec);
    }

    public record Config(
            IntProvider radius,
            IntProvider depth,
            float waterFraction,
            BlockStateProvider floor,
            BlockStateProvider rim
    ) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                IntProvider.codec(3, 16).fieldOf("radius").forGetter(Config::radius),
                IntProvider.codec(8, 48).fieldOf("depth").forGetter(Config::depth),
                Codec.floatRange(0.0F, 1.0F).fieldOf("water_fraction").forGetter(Config::waterFraction),
                BlockStateProvider.CODEC.fieldOf("floor").forGetter(Config::floor),
                BlockStateProvider.CODEC.fieldOf("rim").forGetter(Config::rim)
        ).apply(instance, Config::new));
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        BlockPos origin = context.origin();
        ChunkPos chunk = new ChunkPos(origin);

        int radius = config.radius().sample(random);
        int depth = config.depth().sample(random);

        // The placement uses a heightmap, so origin is the first air block above the ground.
        int surfaceY = origin.getY() - 1;
        int bottomY = Math.max(level.getMinBuildHeight() + 2, surfaceY - depth);
        int height = surfaceY - bottomY;
        if (height < 4) {
            return false;
        }
        int waterTop = bottomY + Math.round(height * config.waterFraction());
        int topY = Math.min(surfaceY + 2, level.getMaxBuildHeight() - 1);

        // A per-feature seed makes the rim slightly irregular instead of a perfect circle.
        int seed = random.nextInt();
        int reach = radius + 2;
        boolean carved = false;

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                int dist2 = dx * dx + dz * dz;
                double topRadius = radius * (0.8 + 0.4 * WorldGenUtil.hash01(x, z, seed));
                if (dist2 > (topRadius + 3) * (topRadius + 3)) {
                    continue;
                }

                for (int y = topY; y >= bottomY; y--) {
                    // t = 0 at ground level, 1 at the bottom. The pit narrows toward the bottom.
                    double t = Math.max(0.0, (surfaceY - y) / (double) height);
                    double r = topRadius * (1.0 - 0.3 * t);
                    BlockPos pos = new BlockPos(x, y, z);

                    if (dist2 <= r * r) {
                        // Inside the pit.
                        if (level.getBlockState(pos).is(Blocks.BEDROCK)) {
                            continue;
                        }
                        BlockState state;
                        if (y == bottomY) {
                            state = config.floor().getState(random, pos);
                        } else if (y <= waterTop) {
                            state = Blocks.WATER.defaultBlockState();
                        } else {
                            state = Blocks.AIR.defaultBlockState();
                            // Hanging vines on the walls above the water.
                            if (y > waterTop && dist2 > (r - 1.5) * (r - 1.5) && random.nextFloat() < 0.3F) {
                                BlockState vine = vineAgainstWall(level, pos, dx, dz, r);
                                if (vine != null) {
                                    state = vine;
                                }
                            }
                        }
                        WorldGenUtil.setBlockInChunk(level, chunk, pos, state);
                        carved = true;
                    } else if (y <= surfaceY && dist2 <= (r + 1.5) * (r + 1.5)) {
                        // Just outside the pit: add mossy patches to the existing rock/dirt wall.
                        BlockState existing = level.getBlockState(pos);
                        if (!existing.isAir() && existing.getFluidState().isEmpty() && random.nextFloat() < 0.4F) {
                            WorldGenUtil.setBlockInChunk(level, chunk, pos, config.rim().getState(random, pos));
                        }
                    }
                }
            }
        }
        return carved;
    }

    /**
     * Returns a vine state attached to the wall just outside the pit, or null if there is no solid wall there.
     * The wall is the neighbour in the direction away from the pit's centre, and it must lie outside radius r
     * (otherwise it would be carved out later and the vine would hang in mid-air).
     */
    private static BlockState vineAgainstWall(WorldGenLevel level, BlockPos pos, int dx, int dz, double r) {
        int wallDx = dx;
        int wallDz = dz;
        BlockState state;
        if (Math.abs(dx) >= Math.abs(dz)) {
            wallDx += Integer.signum(dx);
            state = Blocks.VINE.defaultBlockState().setValue(
                    dx > 0 ? VineBlock.EAST : VineBlock.WEST, true);
        } else {
            wallDz += Integer.signum(dz);
            state = Blocks.VINE.defaultBlockState().setValue(
                    dz > 0 ? VineBlock.SOUTH : VineBlock.NORTH, true);
        }
        if (wallDx * wallDx + wallDz * wallDz <= r * r) {
            return null;
        }
        BlockState wallState = level.getBlockState(pos.offset(wallDx - dx, 0, wallDz - dz));
        if (wallState.isAir() || !wallState.getFluidState().isEmpty()) {
            return null;
        }
        return state;
    }
}
