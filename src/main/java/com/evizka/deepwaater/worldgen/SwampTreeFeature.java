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
 * A tall swamp tree with a 2x2 trunk, bending limbs and leafy crowns.
 *
 * Roots only grow into solid soil and stone, and only where something solid holds them up.
 * So they never hang in the air, and they never stick out over water or a cenote.
 * Leaves are persistent, so they do not decay.
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
        int baseZ = origin.getZ();
        // The trunk must stand on solid ground. Use the lowest ground under the 2x2 footprint, and
        // refuse the spot if there is water, a cenote or a hole there.
        int groundY = Integer.MAX_VALUE;
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                int found = findGround(level, baseX + dx, origin.getY() - 1, baseZ + dz);
                if (found == Integer.MIN_VALUE) {
                    return false;
                }
                groundY = Math.min(groundY, found);
            }
        }
        int baseY = groundY + 1;
        int topY = baseY + trunkHeight;

        // 2x2 trunk from the ground up.
        for (int y = 0; y < trunkHeight; y++) {
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    placeWood(level, chunk, random, config, new BlockPos(baseX + dx, baseY + y, baseZ + dz));
                }
            }
        }

        // Roots that come out of the soil around the base. They start at the ground level, so
        // placeRoot only replaces solid soil, and nothing sticks up in the air.
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                boolean insideTrunk = dx >= 0 && dx <= 1 && dz >= 0 && dz <= 1;
                if (insideTrunk || random.nextFloat() > 0.35F) {
                    continue;
                }
                placeRoot(level, chunk, new BlockPos(baseX + dx, groundY, baseZ + dz));
            }
        }

        // Deep roots: a taproot straight down, then spreading roots that keep going down.
        placeTaproot(level, chunk, random, baseX, baseY, baseZ);
        placeSpreadingRoots(level, chunk, random, baseX, baseY, baseZ);

        // Main crown on top of the trunk.
        placeCrown(level, chunk, random, config, new BlockPos(baseX, topY, baseZ), crown + 2);

        // Limbs leave the upper trunk in several directions and spread evenly around it.
        int limbs = 5 + random.nextInt(3);
        for (int i = 0; i < limbs; i++) {
            double angle = (i + random.nextDouble()) * Math.PI * 2.0 / limbs;
            int startY = topY - 2 - random.nextInt(Math.max(2, trunkHeight / 3));
            growLimb(level, chunk, random, config, new BlockPos(baseX, startY, baseZ), angle,
                    9 + random.nextInt(6), crown, 0);
        }
        return true;
    }

    /**
     * A limb that grows outward, bends and droops towards its tip. Limbs of the first level can split
     * into twigs. Each tip ends in a leafy crown.
     */
    private static void growLimb(WorldGenLevel level, ChunkPos chunk, RandomSource random, Config config,
                                 BlockPos start, double angle, int length, int crown, int depth) {
        double x = start.getX() + 0.5;
        double y = start.getY() + 0.5;
        double z = start.getZ() + 0.5;
        double heading = angle;
        BlockPos last = start;
        for (int step = 1; step <= length; step++) {
            double progress = step / (double) length;
            // Wobble a little, so the limb is not a straight line.
            heading += (random.nextDouble() - 0.5) * 0.35;
            double speed = depth == 0 ? 0.85 : 0.7;
            x += Math.cos(heading) * speed;
            z += Math.sin(heading) * speed;
            // Rises a little near the trunk, then droops towards the tip.
            y += 0.2 - 0.7 * progress + (random.nextDouble() - 0.5) * 0.15;

            BlockPos next = new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
            connect(level, chunk, random, config, last, next);
            last = next;

            // Twigs from the middle of a main limb.
            if (depth == 0 && step > 3 && step < length - 2 && random.nextFloat() < 0.35F) {
                double side = heading + (random.nextBoolean() ? 1 : -1) * (0.7 + random.nextDouble() * 0.5);
                growLimb(level, chunk, random, config, last, side, 4 + random.nextInt(3), crown, depth + 1);
            }
        }
        int tipRadius = depth == 0 ? Math.max(3, crown * 2 / 3) : 2 + random.nextInt(2);
        placeCrown(level, chunk, random, config, last, tipRadius);
    }

    /** Joins two block positions with a line of wood: first along x, then z, then y. Keeps branches connected. */
    private static void connect(WorldGenLevel level, ChunkPos chunk, RandomSource random, Config config,
                                BlockPos from, BlockPos to) {
        int x = from.getX();
        int y = from.getY();
        int z = from.getZ();
        while (x != to.getX()) {
            x += Integer.signum(to.getX() - x);
            placeWood(level, chunk, random, config, new BlockPos(x, y, z));
        }
        while (z != to.getZ()) {
            z += Integer.signum(to.getZ() - z);
            placeWood(level, chunk, random, config, new BlockPos(x, y, z));
        }
        while (y != to.getY()) {
            y += Integer.signum(to.getY() - y);
            placeWood(level, chunk, random, config, new BlockPos(x, y, z));
        }
    }

    /**
     * The top of the ground at x, z, looking at most 3 blocks down from startY.
     * Returns MIN_VALUE if there is water or lava first (a cenote or pond), or no ground nearby (a hole).
     */
    private static int findGround(WorldGenLevel level, int x, int startY, int z) {
        for (int y = startY; y >= startY - 2 && y > level.getMinBuildHeight(); y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (!state.getFluidState().isEmpty()) {
                return Integer.MIN_VALUE;
            }
            if (!state.isAir()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
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
     * Puts one root block at pos. Returns false when the root cannot go there, so it stops:
     * the spot is air, water or a cave, the block below is not solid (the root would hang),
     * or the block is too hard (ore, deepslate, bedrock).
     */
    private static boolean placeRoot(WorldGenLevel level, ChunkPos chunk, BlockPos pos) {
        if (!WorldGenUtil.inRegion(pos, chunk)) {
            // Far outside the writable area: keep going, the blocks there are skipped.
            return true;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        // Dirt, mud, sand, gravel, clay and ordinary stone are fine; ores and deepslate stop the root.
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F || hardness > 2.0F) {
            return false;
        }
        BlockState below = level.getBlockState(pos.below());
        if (below.isAir() || !below.getFluidState().isEmpty()) {
            return false;
        }
        WorldGenUtil.setBlockInRegion(level, chunk, pos, ModBlocks.DEEP_BOG_ROOTS.get().defaultBlockState());
        return true;
    }

    private static void placeWood(WorldGenLevel level, ChunkPos chunk, RandomSource random, Config config, BlockPos pos) {
        if (!isReplaceable(level, pos)) {
            return;
        }
        WorldGenUtil.setBlockInRegion(level, chunk, pos, config.trunk().getState(random, pos));
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
                    WorldGenUtil.setBlockInRegion(level, chunk, pos, leaf);
                }
            }
        }
    }

    private static boolean isReplaceable(WorldGenLevel level, BlockPos pos) {
        return WorldGenUtil.isReplaceable(level.getBlockState(pos));
    }
}
