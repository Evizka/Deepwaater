package com.evizka.deepwaater.worldgen;

import com.evizka.deepwaater.block.ModBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * A very tall tree with a 2x2 trunk, a wide crown and long branches.
 *
 * Its roots are the important part of the bog: a taproot and several spreading roots
 * go down into the ground (through soil, mud and soft stone) instead of only sticking up
 * at the base. Leaves are persistent, so they do not decay.
 */
public class SwampTreeFeature extends Feature<SwampTreeFeature.Config> {

    public SwampTreeFeature(Codec<Config> codec) {
        super(codec);
    }

    public record Config(
            IntProvider height,
            IntProvider crownRadius,
            BlockStateProvider trunk,
            BlockStateProvider leaves
    ) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                IntProvider.codec(12, 48).fieldOf("height").forGetter(Config::height),
                IntProvider.codec(2, 10).fieldOf("crown_radius").forGetter(Config::crownRadius),
                BlockStateProvider.CODEC.fieldOf("trunk").forGetter(Config::trunk),
                BlockStateProvider.CODEC.fieldOf("leaves").forGetter(Config::leaves)
        ).apply(instance, Config::new));
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        BlockPos origin = context.origin();
        ChunkPos chunk = new ChunkPos(origin);

        int trunkHeight = config.height().sample(random);
        int crown = config.crownRadius().sample(random);
        int baseX = origin.getX();
        int baseY = origin.getY();
        int baseZ = origin.getZ();
        int topY = baseY + trunkHeight;

        // 2x2 trunk from the ground up.
        for (int y = 0; y < trunkHeight; y++) {
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    placeWood(level, chunk, random, config, new BlockPos(baseX + dx, baseY + y, baseZ + dz));
                }
            }
        }

        // Roots that stick up around the base of the trunk.
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                boolean insideTrunk = dx >= 0 && dx <= 1 && dz >= 0 && dz <= 1;
                if (insideTrunk || random.nextFloat() > 0.35F) {
                    continue;
                }
                BlockPos pos = new BlockPos(baseX + dx, baseY, baseZ + dz);
                if (isReplaceable(level, pos)) {
                    WorldGenUtil.setBlockInChunk(level, chunk, pos, ModBlocks.DEEP_BOG_ROOTS.get().defaultBlockState());
                }
            }
        }

        // Deep roots: a taproot straight down, then spreading roots that keep going down.
        placeTaproot(level, chunk, random, baseX, baseY, baseZ);
        placeSpreadingRoots(level, chunk, random, baseX, baseY, baseZ);

        // Main crown on top of the trunk.
        placeCrown(level, chunk, random, config, new BlockPos(baseX, topY, baseZ), crown + 2);

        // Long branches. Each one ends in its own smaller crown.
        int branches = 4 + random.nextInt(3);
        for (int i = 0; i < branches; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            int startY = topY - crown - random.nextInt(Math.max(1, trunkHeight / 3));
            int length = crown + 2 + random.nextInt(crown + 2);

            int endX = baseX;
            int endY = startY;
            int endZ = baseZ;
            for (int s = 1; s <= length; s++) {
                endX = baseX + (int) Math.round(cos * s);
                endZ = baseZ + (int) Math.round(sin * s);
                endY = startY + s / 3;
                placeWood(level, chunk, random, config, new BlockPos(endX, endY, endZ));
            }
            placeCrown(level, chunk, random, config, new BlockPos(endX, endY, endZ),
                    Math.max(2, crown * 2 / 3 + random.nextInt(2)));
        }
        return true;
    }

    /** A root that goes straight down from the trunk, 8–15 blocks deep. */
    private static void placeTaproot(WorldGenLevel level, ChunkPos chunk, RandomSource random,
                                     int baseX, int baseY, int baseZ) {
        int length = 8 + random.nextInt(8);
        for (int i = 1; i <= length; i++) {
            if (!placeRoot(level, chunk, new BlockPos(baseX, baseY - i, baseZ))) {
                break;
            }
        }
    }

    /** Roots that leave the trunk in several directions and sink as they go. */
    private static void placeSpreadingRoots(WorldGenLevel level, ChunkPos chunk, RandomSource random,
                                            int baseX, int baseY, int baseZ) {
        int arms = 5 + random.nextInt(3);
        for (int arm = 0; arm < arms; arm++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double x = baseX + 0.5;
            double z = baseZ + 0.5;
            int y = baseY - 1;
            int length = 8 + random.nextInt(8);
            for (int step = 0; step < length; step++) {
                x += Math.cos(angle) * 0.8;
                z += Math.sin(angle) * 0.8;
                // Sink one block every second step, so the roots go deep rather than spreading flat.
                if (step % 2 == 1) {
                    y--;
                }
                if (!placeRoot(level, chunk, new BlockPos((int) Math.floor(x), y, (int) Math.floor(z)))) {
                    break;
                }
            }
        }
    }

    /**
     * Puts one root block at pos if the spot is free or is soft ground.
     * Returns false when the root hits something it cannot grow through (bedrock, ore, deepslate...),
     * so the root stops there.
     */
    private static boolean placeRoot(WorldGenLevel level, ChunkPos chunk, BlockPos pos) {
        if (!new ChunkPos(pos).equals(chunk)) {
            // Outside this chunk: keep going, the neighbouring chunk writes its own part.
            return true;
        }
        BlockState state = level.getBlockState(pos);
        boolean open = state.isAir() || !state.getFluidState().isEmpty();
        float hardness = state.getDestroySpeed(level, pos);
        // Dirt, mud, sand, gravel, clay and ordinary stone are fine; ores and deepslate stop the root.
        boolean soft = hardness >= 0.0F && hardness <= 2.0F;
        if (open || soft) {
            WorldGenUtil.setBlockInChunk(level, chunk, pos, ModBlocks.DEEP_BOG_ROOTS.get().defaultBlockState());
            return true;
        }
        return false;
    }

    private static void placeWood(WorldGenLevel level, ChunkPos chunk, RandomSource random, Config config, BlockPos pos) {
        if (!isReplaceable(level, pos)) {
            return;
        }
        WorldGenUtil.setBlockInChunk(level, chunk, pos, config.trunk().getState(random, pos));
    }

    /**
     * A flattened, slightly noisy ball of leaves. The ball is squashed vertically,
     * so the crown is wider than it is tall.
     */
    private static void placeCrown(WorldGenLevel level, ChunkPos chunk, RandomSource random, Config config,
                                   BlockPos center, int radius) {
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius + 1; dy <= radius - 1; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double dist = dx * dx + dz * dz + dy * dy * 1.6;
                    double limit = r2 * (0.8 + 0.4 * random.nextDouble());
                    if (dist > limit) {
                        continue;
                    }
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!isReplaceable(level, pos)) {
                        continue;
                    }
                    BlockState leaf = config.leaves().getState(random, pos);
                    if (leaf.hasProperty(LeavesBlock.PERSISTENT)) {
                        leaf = leaf.setValue(LeavesBlock.PERSISTENT, true);
                    }
                    WorldGenUtil.setBlockInChunk(level, chunk, pos, leaf);
                }
            }
        }
    }

    private static boolean isReplaceable(WorldGenLevel level, BlockPos pos) {
        return WorldGenUtil.isReplaceable(level.getBlockState(pos));
    }
}
