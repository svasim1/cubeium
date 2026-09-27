package cubeium.cubeium.map;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cubeium.cubeium.world.StructureFinder;
import cubeium.cubeium.world.StructureKind;
import cubeium.cubeium.world.WorldGenerator;

/**
 * Finds the nearest structure or biome around a point, in the background. The search grows in
 * doubling squares and stops once the nearest hit is closer than the square's half size, so the
 * answer is exact (biomes to their 16-block sample cell).
 */
public final class NearestSearch {
    public sealed interface Target permits StructureTarget, BiomeTarget {
    }

    public record StructureTarget(StructureKind kind) implements Target {
    }

    public record BiomeTarget(int biomeId) implements Target {
    }

    public record Hit(int x, int z, double distance) {
    }

    private static final int MAX_STRUCTURE_RADIUS = 60_000;
    private static final int MAX_DENSE_RADIUS = 4_000;
    private static final int MAX_BIOME_RADIUS = 10_000;
    private static final int BIOME_SCALE = 16;

    private static final ExecutorService SEARCH = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Cubeium nearest search");
        thread.setDaemon(true);
        return thread;
    });

    private NearestSearch() {
    }

    public static CompletableFuture<Optional<Hit>> find(StructureFinder finder, Target target, int x, int z) {
        return CompletableFuture.supplyAsync(() -> switch (target) {
            case StructureTarget s -> structure(finder, s.kind(), x, z);
            case BiomeTarget b -> biome(finder.world(), b.biomeId(), x, z);
        }, SEARCH);
    }

    private static Optional<Hit> structure(StructureFinder finder, StructureKind kind, int x, int z) {
        if (!finder.supports(kind)) {
            return Optional.empty();
        }
        int max = kind.isDense() ? MAX_DENSE_RADIUS : MAX_STRUCTURE_RADIUS;
        for (int radius = Math.max(256, finder.regionBlocks(kind) * 2); ; radius *= 2) {
            radius = Math.min(radius, max);
            Hit best = null;
            for (StructureFinder.Found found : finder.find(kind, x - radius, z - radius, x + radius, z + radius)) {
                double distance = Math.hypot(found.x() - x, found.z() - z);
                if (best == null || distance < best.distance()) {
                    best = new Hit(found.x(), found.z(), distance);
                }
            }
            if (best != null && (best.distance() <= radius || radius == max)) {
                return Optional.of(best);
            }
            if (radius == max) {
                return Optional.empty();
            }
        }
    }

    private static Optional<Hit> biome(WorldGenerator world, int biomeId, int x, int z) {
        for (int radius = 512; ; radius *= 2) {
            radius = Math.min(radius, MAX_BIOME_RADIUS);
            int cells = 2 * radius / BIOME_SCALE;
            int cellX0 = Math.floorDiv(x - radius, BIOME_SCALE), cellZ0 = Math.floorDiv(z - radius, BIOME_SCALE);
            int[] ids = world.biomes(BIOME_SCALE, cellX0, cellZ0, cells, cells);
            Hit best = null;
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] != biomeId) {
                    continue;
                }
                int bx = (cellX0 + i % cells) * BIOME_SCALE + BIOME_SCALE / 2;
                int bz = (cellZ0 + i / cells) * BIOME_SCALE + BIOME_SCALE / 2;
                double distance = Math.hypot(bx - x, bz - z);
                if (best == null || distance < best.distance()) {
                    best = new Hit(bx, bz, distance);
                }
            }
            if (best != null && (best.distance() <= radius || radius == MAX_BIOME_RADIUS)) {
                return Optional.of(best);
            }
            if (radius == MAX_BIOME_RADIUS) {
                return Optional.empty();
            }
        }
    }
}
