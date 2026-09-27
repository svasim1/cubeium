package cubeium.cubeium.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DimensionTest {
    @Test
    void portalScale() {
        assertEquals(1 / 8.0, Dimension.OVERWORLD.scaleTo(Dimension.NETHER));
        assertEquals(8, Dimension.NETHER.scaleTo(Dimension.OVERWORLD));
        assertEquals(1, Dimension.END.scaleTo(Dimension.END));
        assertTrue(Double.isNaN(Dimension.OVERWORLD.scaleTo(Dimension.END)));
        assertTrue(Double.isNaN(Dimension.END.scaleTo(Dimension.NETHER)));
    }
}
