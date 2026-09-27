package cubeium.cubeium.world;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.xpple.cubiomes.Cubiomes;
import dev.xpple.cubiomes.Pos;
import dev.xpple.cubiomes.StructureConfig;

/**
 * Finds structures of one {@link WorldGenerator}, following cubiomes-viewer: one generation attempt
 * per structure region ({@code getStructurePos}), kept only if the biome/terrain checks pass.
 * Results are cached per block of regions and hold only what was found, so panning back over an
 * area costs nothing and chunk-based structures stay cheap to keep. Thread-safe.
 */
public final class StructureFinder {
    public record Found(StructureKind kind, int x, int z) {
    }

    private static final int[] NONE = new int[0];
    /** Regions per cache block side. */
    private static final int BLOCK = 16;
    private static final int MAX_CACHED_BLOCKS = 50_000;
    private static final int STRONGHOLD_COUNT = 128;

    private final WorldGenerator world;
    /** Per kind: cache block key -> positions found in it, as {x0, z0, x1, z1, ...}. */
    private final Map<StructureKind, Map<Long, int[]>> blocks = new EnumMap<>(StructureKind.class);
    private final Map<StructureKind, Integer> regionBlocks = new EnumMap<>(StructureKind.class);
    private volatile int[] strongholds;

    public StructureFinder(WorldGenerator world) {
        this.world = world;
        for (StructureKind kind : StructureKind.values()) {
            blocks.put(kind, new ConcurrentHashMap<>());
            regionBlocks.put(kind, loadRegionBlocks(kind));
        }
    }

    public WorldGenerator world() {
        return world;
    }

    /** Whether the structure exists in this world's version and dimension. */
    public boolean supports(StructureKind kind) {
        return kind.dimension() == world.dimension() && (kind == StructureKind.STRONGHOLD || regionBlocks.get(kind) > 0);
    }

    /** Side length of the kind's placement region in blocks (0 if unsupported). */
    public int regionBlocks(StructureKind kind) {
        return regionBlocks.get(kind);
    }

    /** Structures of a kind whose position lies in the block rectangle [x0, x1) x [z0, z1). */
    public List<Found> find(StructureKind kind, int x0, int z0, int x1, int z1) {
        List<Found> out = new ArrayList<>();
        if (!supports(kind)) {
            return out;
        }
        if (kind == StructureKind.STRONGHOLD) {
            int[] all = strongholds();
            for (int i = 0; i < all.length; i += 2) {
                if (all[i] >= x0 && all[i] < x1 && all[i + 1] >= z0 && all[i + 1] < z1) {
                    out.add(new Found(kind, all[i], all[i + 1]));
                }
            }
            return out;
        }

        long span = (long) regionBlocks.get(kind) * BLOCK;
        Map<Long, int[]> cache = blocks.get(kind);
        if (cache.size() > MAX_CACHED_BLOCKS) {
            cache.clear();
        }
        for (long bx = Math.floorDiv(x0, span); bx <= Math.floorDiv(x1 - 1L, span); bx++) {
            for (long bz = Math.floorDiv(z0, span); bz <= Math.floorDiv(z1 - 1L, span); bz++) {
                int blockX = (int) bx, blockZ = (int) bz;
                int[] positions = cache.computeIfAbsent((bx << 32) | (bz & 0xFFFFFFFFL), k -> locateBlock(kind, blockX, blockZ));
                for (int i = 0; i < positions.length; i += 2) {
                    if (positions[i] >= x0 && positions[i] < x1 && positions[i + 1] >= z0 && positions[i + 1] < z1) {
                        out.add(new Found(kind, positions[i], positions[i + 1]));
                    }
                }
            }
        }
        return out;
    }

    /** Accurate stronghold positions, computed once. */
    public int[] strongholds() {
        int[] result = strongholds;
        if (result == null) {
            result = world.strongholds(STRONGHOLD_COUNT);
            strongholds = result;
        }
        return result;
    }

    private int[] locateBlock(StructureKind kind, int blockX, int blockZ) {
        int[] found = new int[8];
        int count = 0;
        for (int rx = blockX * BLOCK; rx < (blockX + 1) * BLOCK; rx++) {
            for (int rz = blockZ * BLOCK; rz < (blockZ + 1) * BLOCK; rz++) {
                int[] pos = locate(kind, rx, rz);
                if (pos.length == 2) {
                    if (count + 2 > found.length) {
                        found = Arrays.copyOf(found, found.length * 2);
                    }
                    found[count++] = pos[0];
                    found[count++] = pos[1];
                }
            }
        }
        return count == 0 ? NONE : Arrays.copyOf(found, count);
    }

    private int[] locate(StructureKind kind, int regionX, int regionZ) {
        int type = kind.cubiomesType();
        int x, z;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pos = Pos.allocate(arena);
            if (Cubiomes.getStructurePos(type, world.mcVersion(), world.seed(), regionX, regionZ, pos) == 0) {
                return NONE;
            }
            x = Pos.x(pos);
            z = Pos.z(pos);
        }
        boolean viable = switch (kind) {
            case END_CITY -> world.isViableEndCity(x, z) && !world.endCityHasShip(x, z);
            case END_CITY_SHIP -> world.isViableEndCity(x, z) && world.endCityHasShip(x, z);
            default -> world.isViableStructure(type, x, z);
        };
        return viable ? new int[] {x, z} : NONE;
    }

    private int loadRegionBlocks(StructureKind kind) {
        if (kind == StructureKind.STRONGHOLD) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment config = StructureConfig.allocate(arena);
            if (Cubiomes.getStructureConfig(kind.cubiomesType(), world.mcVersion(), config) == 0) {
                return 0;
            }
            return StructureConfig.regionSize(config) * 16;
        }
    }
}
