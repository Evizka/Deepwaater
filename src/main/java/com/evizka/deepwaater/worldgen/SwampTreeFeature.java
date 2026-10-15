package com.evizka.deepwaater.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * A very tall tree with a 2x2 trunk, a wide crown and several long branches,
 * with mangrove-like roots at the base. It is meant to stand out above the bog.
 *
 * Leaves are set to persistent, so they do not decay when the player is far away.
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
                    WorldGenUtil.setBlockInChunk(level, chunk, pos, Blocks.MANGROVE_ROOTS.defaultBlockState());
                }
            }
        }

        // Main crown on top of the trunk.
        placeCrown(level, chunk, random, config, new BlockPos(baseX, topY, baseZ), crown + 2);

        // Long branches that reach out over the water. Each one ends in its own smaller crown.
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
