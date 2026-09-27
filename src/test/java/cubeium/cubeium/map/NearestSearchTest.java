package cubeium.cubeium.map;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import cubeium.cubeium.world.Biomes;
import cubeium.cubeium.world.Dimension;
import cubeium.cubeium.world.StructureFinder;
import cubeium.cubeium.world.StructureKind;
import cubeium.cubeium.world.WorldGenerator;
import dev.xpple.cubiomes.Cubiomes;

class NearestSearchTest {
    private final StructureFinder finder = new StructureFinder(new WorldGenerator(Cubiomes.MC_26_3(), 42L, Dimension.OVERWORLD));

    @Test
    void findsTheClosestStructure() throws Exception {
        int x = 1234, z = -777;
        Optional<NearestSearch.Hit> hit = NearestSearch.find(finder, new NearestSearch.StructureTarget(StructureKind.VILLAGE), x, z).get();
        assertTrue(hit.isPresent());

        double best = Double.MAX_VALUE;
        for (StructureFinder.Found found : finder.find(StructureKind.VILLAGE, x - 20_000, z - 20_000, x + 20_000, z + 20_000)) {
            best = Math.min(best, Math.hypot(found.x() - x, found.z() - z));
        }
        assertEquals(best, hit.get().distance(), 1e-9);
    }

    @Test
    void findsABiomeThatIsReallyThere() throws Exception {
        int plains = -1;
        for (int id : Biomes.mapBiomes(Cubiomes.MC_26_3(), Dimension.OVERWORLD)) {
            if ("plains".equals(Biomes.name(Cubiomes.MC_26_3(), id))) {
                plains = id;
            }
        }
        Optional<NearestSearch.Hit> hit = NearestSearch.find(finder, new NearestSearch.BiomeTarget(plains), 0, 0).get();
        assertTrue(hit.isPresent());
        assertEquals(plains, finder.world().biomes(16, Math.floorDiv(hit.get().x(), 16), Math.floorDiv(hit.get().z(), 16), 1, 1)[0]);
    }

    @Test
    void reportsNothingForStructuresOfAnotherDimension() throws Exception {
        assertTrue(NearestSearch.find(finder, new NearestSearch.StructureTarget(StructureKind.END_CITY), 0, 0).get().isEmpty());
    }
}
