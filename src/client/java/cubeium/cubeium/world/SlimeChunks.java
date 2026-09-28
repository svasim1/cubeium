package cubeium.cubeium.world;

/** Slime chunks: Minecraft's formula from the slime spawn rules, independent of biomes and version. */
public final class SlimeChunks {
    private static final long SALT = 987234911L;
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long MASK = (1L << 48) - 1;

    private SlimeChunks() {
    }

    /** Same result as seeding a {@code java.util.Random} and calling {@code nextInt(10) == 0}, without allocating. */
    public static boolean isSlimeChunk(long seed, int chunkX, int chunkZ) {
        // The int products overflow exactly like Minecraft's before widening to long.
        long state = (seed + chunkX * chunkX * 4987142 + chunkX * 5947611 + chunkZ * chunkZ * 4392871L + chunkZ * 389711 ^ SALT ^ MULTIPLIER) & MASK;
        while (true) {
            state = (state * MULTIPLIER + 0xBL) & MASK;
            int bits = (int) (state >>> 17);
            int value = bits % 10;
            if (bits - value + 9 >= 0) {
                return value == 0;
            }
        }
    }
}
