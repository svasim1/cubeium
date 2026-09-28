package cubeium.cubeium.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.junit.jupiter.api.Test;

class SlimeChunksTest {
    @Test
    void matchesJavaUtilRandom() {
        Random inputs = new Random(1);
        for (int i = 0; i < 200_000; i++) {
            long seed = inputs.nextLong();
            int x = inputs.nextInt(2_000_000) - 1_000_000, z = inputs.nextInt(2_000_000) - 1_000_000;
            long mixed = seed + x * x * 4987142 + x * 5947611 + z * z * 4392871L + z * 389711 ^ 987234911L;
            assertEquals(new Random(mixed).nextInt(10) == 0, SlimeChunks.isSlimeChunk(seed, x, z), "seed " + seed + " chunk " + x + "," + z);
        }
    }
}
