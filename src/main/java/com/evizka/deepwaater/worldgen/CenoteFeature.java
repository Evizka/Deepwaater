package com.evizka.deepwaater.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Cenotes: round, deep sinkholes that open to the sky and are filled with groundwater.
 * Neighbouring cenotes are joined by underground tunnels, so the bog has a cave network.
 *
 * How the network stays consistent across chunks:
 * every chunk computes the same layout for the cenotes around it, from the world seed and
 * the chunk coordinates only. Each chunk then writes just the blocks that fall inside itself.
 * Chunks that share a pit or a tunnel therefore carve exactly the same shape along their border.
 */
public class CenoteFeature extends Feature<CenoteFeature.Config> {

    /** Chunks around the current chunk whose cenotes and tunnels are considered. */
    private static final int REACH = 2;
    /** Extra chunks so that the far end of a tunnel is also known. */
    private static final int GRID = REACH + 1;
    private static final long SALT_PIT = 0x5EED_0001L;
    private static final long SALT_LINK = 0x5EED_0002L;

    public CenoteFeature(Codec<Config> codec) {
        super(codec);
    }

    public record Config(
            IntProvider radius,
            IntProvider depth,
            float waterFraction,
            float chance,
            float linkChance,
            int tunnelRadius,
            BlockStateProvider floor,
            BlockStateProvider rim
    ) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                IntProvider.codec(3, 16).fieldOf("radius").forGetter(Config::radius),
                IntProvider.codec(8, 48).fieldOf("depth").forGetter(Config::depth),
                Codec.floatRange(0.0F, 1.0F).fieldOf("water_fraction").forGetter(Config::waterFraction),
                Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(Config::chance),
                Codec.floatRange(0.0F, 1.0F).fieldOf("link_chance").forGetter(Config::linkChance),
                Codec.intRange(1, 4).fieldOf("tunnel_radius").forGetter(Config::tunnelRadius),
                BlockStateProvider.CODEC.fieldOf("floor").forGetter(Config::floor),
                BlockStateProvider.CODEC.fieldOf("rim").forGetter(Config::rim)
        ).apply(instance, Config::new));
    }

    /** One cenote. Its layout is derived from the world seed and its chunk only. */
    private record Pit(int x, int z, int surfaceY, int bottomY, int waterTop, int tunnelY, int radius, int seed) {
        int reach() {
            return radius + 4;
        }
    }

    /** A tunnel between two neighbouring cenotes. */
    private record Tunnel(Pit a, Pit b, int radius) {
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        Config config = context.config();
        ChunkPos chunk = new ChunkPos(context.origin());
        ChunkGenerator generator = context.chunkGenerator();
        RandomState randomState = level.getLevel().getChunkSource().randomState();
        long worldSeed = level.getSeed();

        // 1. Compute the cenotes of the grid around this chunk (same result in every chunk).
        int size = GRID * 2 + 1;
        Pit[][] grid = new Pit[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                grid[i][j] = computePit(level, generator, randomState, config, worldSeed,
                        chunk.x - GRID + i, chunk.z - GRID + j);
            }
        }

        // 2. Collect the tunnels between neighbouring cenotes that could reach this chunk.
        List<Tunnel> tunnels = new ArrayList<>();
        for (int i = GRID - REACH; i <= GRID + REACH; i++) {
            for (int j = GRID - REACH; j <= GRID + REACH; j++) {
                Pit a = grid[i][j];
                if (a == null) {
                    continue;
                }
                // Each pair is visited once: towards +x and towards +z.
                for (int dir = 0; dir < 2; dir++) {
                    int di = dir == 0 ? 1 : 0;
                    int dj = dir == 0 ? 0 : 1;
                    Pit b = grid[i + di][j + dj];
                    if (b == null) {
                        continue;
                    }
                    int cx = chunk.x - GRID + i;
                    int cz = chunk.z - GRID + j;
                    double roll = unit(mix(worldSeed, cx, cz, SALT_LINK + dir));
                    if (roll < config.linkChance()) {
                        Tunnel tunnel = new Tunnel(a, b, config.tunnelRadius());
                        if (tunnelTouchesChunk(tunnel, chunk)) {
                            tunnels.add(tunnel);
                        }
                    }
                }
            }
        }

        // 3. Collect the cenotes that reach into this chunk.
        List<Pit> pits = new ArrayList<>();
        for (Pit[] column : grid) {
            for (Pit pit : column) {
                if (pit != null && pitTouchesChunk(pit, chunk)) {
                    pits.add(pit);
                }
            }
        }
        if (pits.isEmpty() && tunnels.isEmpty()) {
            return false;
        }

        // 4. Carve. Tunnels first, then the pits (a pit's walls are written after the tunnels).
        for (Tunnel tunnel : tunnels) {
            carveTunnel(level, chunk, tunnel);
        }
        boolean carved = !tunnels.isEmpty();
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                for (Pit pit : pits) {
                    carved |= carvePitColumn(level, chunk, context.random(), config, pit, x, z);
                }
            }
        }
        // 5. Vines go last, when the walls are final, so that none of them hangs in mid-air.
        hangVines(level, chunk, context.random(), pits);
        return carved;
    }

    /** Returns the cenote of chunk (cx, cz), or null if that chunk has none. */
    private static Pit computePit(WorldGenLevel level, ChunkGenerator generator, RandomState randomState,
                                  Config config, long worldSeed, int cx, int cz) {
        long h = mix(worldSeed, cx, cz, SALT_PIT);
        if (unit(h) >= config.chance()) {
            return null;
        }
        RandomSource random = RandomSource.create(h);
        int radius = config.radius().sample(random);
        int depth = config.depth().sample(random);
        int x = (cx << 4) + 3 + random.nextInt(10);
        int z = (cz << 4) + 3 + random.nextInt(10);

        // Surface height from the terrain noise, so it is known even for chunks that are not generated yet.
        int surfaceY = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState) - 1;
        int bottomY = Math.max(level.getMinBuildHeight() + 2, surfaceY - depth);
        int height = surfaceY - bottomY;
        if (height < 8) {
            return null;
        }
        int waterTop = bottomY + Math.round(height * config.waterFraction());
        // Tunnels run just above the water line, so they stay dry.
        int tunnelY = Math.min(waterTop + 2, surfaceY - 3);
        return new Pit(x, z, surfaceY, bottomY, waterTop, tunnelY, radius, (int) (h ^ (h >>> 32)));
    }

    private static boolean pitTouchesChunk(Pit pit, ChunkPos chunk) {
        int reach = pit.reach();
        return pit.x() + reach >= chunk.getMinBlockX() && pit.x() - reach <= chunk.getMaxBlockX()
                && pit.z() + reach >= chunk.getMinBlockZ() && pit.z() - reach <= chunk.getMaxBlockZ();
    }

    private static boolean tunnelTouchesChunk(Tunnel tunnel, ChunkPos chunk) {
        int margin = tunnel.radius() + 2;
        int minX = Math.min(tunnel.a().x(), tunnel.b().x()) - margin;
        int maxX = Math.max(tunnel.a().x(), tunnel.b().x()) + margin;
        int minZ = Math.min(tunnel.a().z(), tunnel.b().z()) - margin;
        int maxZ = Math.max(tunnel.a().z(), tunnel.b().z()) + margin;
        return maxX >= chunk.getMinBlockX() && minX <= chunk.getMaxBlockX()
                && maxZ >= chunk.getMinBlockZ() && minZ <= chunk.getMaxBlockZ();
    }

    /** Carves a tunnel as a chain of spheres along the line between the two pit centres. */
    private static void carveTunnel(WorldGenLevel level, ChunkPos chunk, Tunnel tunnel) {
        Pit a = tunnel.a();
        Pit b = tunnel.b();
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        int steps = (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) * 2.0);
        int waterLevel = Math.min(a.waterTop(), b.waterTop());
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double px = a.x() + 0.5 + dx * t;
            double pz = a.z() + 0.5 + dz * t;
            // A gentle wave in height makes the tunnel look natural instead of perfectly flat.
            double py = a.tunnelY() + (b.tunnelY() - a.tunnelY()) * t + 1.5 * Math.sin(t * Math.PI * 2.0);
            carveSphere(level, chunk, px, py, pz, tunnel.radius(), waterLevel);
        }
    }

    private static void carveSphere(WorldGenLevel level, ChunkPos chunk, double px, double py, double pz,
                                    int radius, int waterLevel) {
        int minX = Math.max(chunk.getMinBlockX(), (int) Math.floor(px) - radius);
        int maxX = Math.min(chunk.getMaxBlockX(), (int) Math.floor(px) + radius);
        int minZ = Math.max(chunk.getMinBlockZ(), (int) Math.floor(pz) - radius);
        int maxZ = Math.min(chunk.getMaxBlockZ(), (int) Math.floor(pz) + radius);
        int minY = Math.max(level.getMinBuildHeight() + 2, (int) Math.floor(py) - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, (int) Math.floor(py) + radius);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Keep tunnels inside the ground: never open them up to the sky or into trees.
                int groundY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                for (int y = minY; y <= maxY; y++) {
                    double dx = x + 0.5 - px;
                    double dy = y + 0.5 - py;
                    double dz = z + 0.5 - pz;
                    if (dx * dx + dy * dy + dz * dz > radius * radius || y > groundY - 4) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).is(Blocks.BEDROCK)) {
                        continue;
                    }
                    BlockState state = y <= waterLevel ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    WorldGenUtil.setBlockInChunk(level, chunk, pos, state);
                }
            }
        }
    }

    /** Carves one column of a pit (inside the chunk) and decorates its walls. Returns true if it carved anything. */
    private static boolean carvePitColumn(WorldGenLevel level, ChunkPos chunk, RandomSource random, Config config,
                                          Pit pit, int x, int z) {
        int dx = x - pit.x();
        int dz = z - pit.z();
        int reach = pit.radius() + 3;
        if (Math.abs(dx) > reach || Math.abs(dz) > reach) {
            return false;
        }
        int dist2 = dx * dx + dz * dz;
        double topRadius = pit.radius() * (0.8 + 0.4 * unit(mix(pit.seed(), x, z)));
        if (dist2 > (topRadius + 3) * (topRadius + 3)) {
            return false;
        }

        boolean carved = false;
        int topY = Math.min(pit.surfaceY() + 2, level.getMaxBuildHeight() - 1);
        int height = pit.surfaceY() - pit.bottomY();
        for (int y = topY; y >= pit.bottomY(); y--) {
            // t = 0 at ground level, 1 at the bottom. The pit narrows toward the bottom.
            double t = Math.max(0.0, (pit.surfaceY() - y) / (double) height);
            double r = topRadius * (1.0 - 0.3 * t);
            BlockPos pos = new BlockPos(x, y, z);

            if (dist2 <= r * r) {
                if (level.getBlockState(pos).is(Blocks.BEDROCK)) {
                    continue;
                }
                BlockState state;
                if (y == pit.bottomY()) {
                    state = config.floor().getState(random, pos);
                } else if (y <= pit.waterTop()) {
                    state = Blocks.WATER.defaultBlockState();
                } else {
                    state = Blocks.AIR.defaultBlockState();
                }
                WorldGenUtil.setBlockInChunk(level, chunk, pos, state);
                carved = true;
            } else if (y <= pit.surfaceY() && dist2 <= (r + 1.5) * (r + 1.5)) {
                // Just outside the pit: add moss to the existing rock/dirt wall.
                BlockState existing = level.getBlockState(pos);
                if (!existing.isAir() && existing.getFluidState().isEmpty() && random.nextFloat() < 0.4F) {
                    WorldGenUtil.setBlockInChunk(level, chunk, pos, config.rim().getState(random, pos));
                }
            }
        }
        return carved;
    }

    /**
     * Hangs vines on the solid walls of the pits above the water. Runs after all carving, so it
     * sees the final blocks. A vine is placed only where it is attached to a full, solid face
     * inside this chunk. Walls in other chunks are skipped, because those chunks may carve them later.
     */
    private static void hangVines(WorldGenLevel level, ChunkPos chunk, RandomSource random, List<Pit> pits) {
        for (Pit pit : pits) {
            int reach = pit.radius() + 2;
            int minX = Math.max(chunk.getMinBlockX(), pit.x() - reach);
            int maxX = Math.min(chunk.getMaxBlockX(), pit.x() + reach);
            int minZ = Math.max(chunk.getMinBlockZ(), pit.z() - reach);
            int maxZ = Math.min(chunk.getMaxBlockZ(), pit.z() + reach);
            int minY = Math.max(pit.waterTop() + 1, level.getMinBuildHeight() + 1);
            int maxY = Math.min(pit.surfaceY(), level.getMaxBuildHeight() - 1);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!level.getBlockState(pos).isAir() || random.nextFloat() >= 0.3F) {
                            continue;
                        }
                        BlockState vine = attachedVine(level, chunk, pos);
                        if (vine != null) {
                            WorldGenUtil.setBlockInChunk(level, chunk, pos, vine);
                        }
                    }
                }
            }
        }
    }

    /** A vine with one face per solid horizontal neighbour inside the chunk, or null if there is none. */
    private static BlockState attachedVine(WorldGenLevel level, ChunkPos chunk, BlockPos pos) {
        BlockState vine = Blocks.VINE.defaultBlockState();
        boolean attached = false;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos wall = pos.relative(dir);
            if (!new ChunkPos(wall).equals(chunk)) {
                continue;
            }
            BlockState wallState = level.getBlockState(wall);
            if (!wallState.isAir() && wallState.getFluidState().isEmpty()
                    && Block.isFaceFull(wallState.getCollisionShape(level, wall), dir.getOpposite())) {
                vine = vine.setValue(faceProperty(dir), true);
                attached = true;
            }
        }
        return attached ? vine : null;
    }

    private static BooleanProperty faceProperty(Direction dir) {
        return switch (dir) {
            case NORTH -> VineBlock.NORTH;
            case EAST -> VineBlock.EAST;
            case SOUTH -> VineBlock.SOUTH;
            default -> VineBlock.WEST;
        };
    }

    /** Stable 64-bit hash of the inputs, used instead of random numbers so chunks agree. */
    private static long mix(long... values) {
        long h = 0x9E3779B97F4A7C15L;
        for (long v : values) {
            h = splitMix(h ^ v);
        }
        return h;
    }

    private static long splitMix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Maps a hash to [0, 1). */
    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }
}
