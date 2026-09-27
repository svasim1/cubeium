package cubeium.cubeium.world;

import java.util.Random;

/** Slime chunks: Minecraft's formula (Slime spawn rules), independent of biomes and version. */
public final class SlimeChunks {
    private static final long SALT = 987234911L;

    private SlimeChunks() {
    }

    public static boolean isSlimeChunk(long seed, int chunkX, int chunkZ) {
        // The int products overflow exactly like Minecraft's before widening to long.
        long mixed = seed + chunkX * chunkX * 4987142 + chunkX * 5947611 + chunkZ * chunkZ * 4392871L + chunkZ * 389711 ^ SALT;
        return new Random(mixed).nextInt(10) == 0;
    }
}
