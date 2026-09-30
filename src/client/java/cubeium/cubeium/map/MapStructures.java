package cubeium.cubeium.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import cubeium.cubeium.Cubeium;
import cubeium.cubeium.world.StructureFinder;
import cubeium.cubeium.world.StructureKind;

/**
 * Structure markers for the visible area, searched in the background. Each search covers the view
 * plus a margin, so small pans reuse the last result; a newer request always wins.
 */
public final class MapStructures {
    /** Minimum on-screen distance between a kind's placement regions for its markers to be shown. */
    private static final double MIN_REGION_PIXELS = 6;

    private static final ExecutorService SEARCH = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Cubeium structure search");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private final StructureFinder finder;
    private final AtomicLong generation = new AtomicLong();
    private volatile List<StructureFinder.Found> found = List.of();
    private volatile Query covered;
    private Query requested;

    public MapStructures(StructureFinder finder) {
        this.finder = finder;
    }

    /** Structures found so far; may lag behind the view by one search. */
    public List<StructureFinder.Found> found() {
        return found;
    }

    /** Whether the kind exists in this world's Minecraft version. */
    public boolean supports(StructureKind kind) {
        return finder.supports(kind);
    }

    /** Whether a kind is searched at this zoom (markers of widely spaced kinds would otherwise pile up). */
    public boolean visibleAt(StructureKind kind, double blocksPerPixel) {
        if (!finder.supports(kind)) {
            return false;
        }
        if (kind == StructureKind.STRONGHOLD) {
            return true;
        }
        return switch (kind) {
            // Per-chunk kinds: zoom limits by how often they occur, so markers do not swamp the map.
            case MINESHAFT, BURIED_TREASURE -> blocksPerPixel <= 8;
            case END_GATEWAY -> blocksPerPixel <= 16;
            default -> finder.regionBlocks(kind) / blocksPerPixel >= MIN_REGION_PIXELS;
        };
    }

    /** Enabled kinds of this dimension that the current zoom hides. */
    public List<StructureKind> hiddenAt(Set<StructureKind> enabled, double blocksPerPixel) {
        return enabled.stream().filter(k -> finder.supports(k) && !visibleAt(k, blocksPerPixel)).sorted().toList();
    }

    /** Called every frame with the view rectangle in blocks; starts a search when needed. */
    public void update(Set<StructureKind> enabled, double left, double top, double right, double bottom, double blocksPerPixel) {
        List<StructureKind> kinds = enabled.stream().filter(k -> visibleAt(k, blocksPerPixel)).sorted().toList();
        if (covered != null && covered.contains(kinds, left, top, right, bottom)
                || requested != null && requested.contains(kinds, left, top, right, bottom)) {
            return;
        }
        double marginX = (right - left) / 2, marginZ = (bottom - top) / 2;
        Query query = new Query(kinds, (int) Math.floor(left - marginX), (int) Math.floor(top - marginZ),
                (int) Math.ceil(right + marginX), (int) Math.ceil(bottom + marginZ));
        requested = query;
        long id = generation.incrementAndGet();
        SEARCH.execute(() -> search(query, id));
    }

    private void search(Query query, long id) {
        List<StructureFinder.Found> result = new ArrayList<>();
        try {
            for (StructureKind kind : query.kinds) {
                if (generation.get() != id) {
                    return; // superseded by a newer view
                }
                result.addAll(finder.find(kind, query.x0, query.z0, query.x1, query.z1));
            }
        } catch (RuntimeException e) {
            Cubeium.LOGGER.warn("Structure search failed", e);
            return;
        }
        if (generation.get() == id) {
            found = List.copyOf(result);
            covered = query;
        }
    }

    private record Query(List<StructureKind> kinds, int x0, int z0, int x1, int z1) {
        boolean contains(List<StructureKind> otherKinds, double left, double top, double right, double bottom) {
            return Objects.equals(kinds, otherKinds) && left >= x0 && top >= z0 && right <= x1 && bottom <= z1;
        }
    }
}
