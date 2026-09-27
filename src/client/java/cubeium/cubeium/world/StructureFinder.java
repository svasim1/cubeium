package cubeium.cubeium.world;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
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
 * Results are cached per region, so panning back over an area costs nothing. Thread-safe.
 */
public final class StructureFinder {
    public record Found(StructureKind kind, int x, int z) {
    }

    private static final int[] NONE = new int[0];
    private static final int MAX_CACHED_REGIONS = 500_000;
    private static final int STRONGHOLD_COUNT = 128;

    private final WorldGenerator world;
    private final Map<StructureKind, Map<Long, int[]>> regions = new EnumMap<>(StructureKind.class);
    private final Map<StructureKind, Integer> regionBlocks = new EnumMap<>(StructureKind.class);
    private volatile int[] strongholds;

    public StructureFinder(WorldGenerator world) {
        this.world = world;
        for (StructureKind kind : StructureKind.values()) {
            regions.put(kind, new ConcurrentHashMap<>());
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

        int size = regionBlocks.get(kind);
        Map<Long, int[]> cache = regions.get(kind);
        if (cache.size() > MAX_CACHED_REGIONS) {
            cache.clear();
        }
        for (int rx = Math.floorDiv(x0, size); rx <= Math.floorDiv(x1 - 1, size); rx++) {
            for (int rz = Math.floorDiv(z0, size); rz <= Math.floorDiv(z1 - 1, size); rz++) {
                int regionX = rx, regionZ = rz;
                int[] pos = cache.computeIfAbsent(((long) rx << 32) | (rz & 0xFFFFFFFFL), k -> locate(kind, regionX, regionZ));
                if (pos.length == 2 && pos[0] >= x0 && pos[0] < x1 && pos[1] >= z0 && pos[1] < z1) {
                    out.add(new Found(kind, pos[0], pos[1]));
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
