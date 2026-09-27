package cubeium.cubeium.config;

/** A named map pin. Stored per world in {@link CubeiumConfig#waypoints}. */
public final class Waypoint {
    public String name;
    public int x;
    public int z;
    /** Dimension key, e.g. "overworld" (see cubeium.cubeium.world.Dimension#key). */
    public String dimension;
    /** Index into the banner colors used as its icon. */
    public int color;

    public Waypoint(String name, int x, int z, String dimension, int color) {
        this.name = name;
        this.x = x;
        this.z = z;
        this.dimension = dimension;
        this.color = color;
    }
}
