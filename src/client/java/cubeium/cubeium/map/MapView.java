package cubeium.cubeium.map;

/**
 * Where the map looks: a center in block coordinates and a zoom level. Zoom is stored as an
 * exponent so every level is a power of two, which keeps tiles aligned: blocks per GUI pixel =
 * 2^zoom, from 1/4 (4 pixels per block) to 256.
 */
public final class MapView {
    public static final int MIN_ZOOM = -2;
    public static final int MAX_ZOOM = 8;
    public static final int DEFAULT_ZOOM = 2;
    /** Minecraft's world border; the view never leaves it, which also keeps tile math in range. */
    public static final double WORLD_LIMIT = 30_000_000;

    private double centerX;
    private double centerZ;
    private int zoom = DEFAULT_ZOOM;

    public double centerX() {
        return centerX;
    }

    public double centerZ() {
        return centerZ;
    }

    public int zoom() {
        return zoom;
    }

    /** Blocks per GUI pixel. */
    public double blocksPerPixel() {
        return Math.scalb(1.0, zoom);
    }

    public void center(double x, double z) {
        centerX = Math.clamp(x, -WORLD_LIMIT, WORLD_LIMIT);
        centerZ = Math.clamp(z, -WORLD_LIMIT, WORLD_LIMIT);
    }

    public void setZoom(int zoom) {
        this.zoom = Math.clamp(zoom, MIN_ZOOM, MAX_ZOOM);
    }

    /** Zooms by {@code steps} levels keeping the block under GUI offset (dx, dz) from the center fixed. */
    public void zoomAround(int steps, double dx, double dz) {
        double before = blocksPerPixel();
        setZoom(zoom + steps);
        double after = blocksPerPixel();
        center(centerX + dx * (before - after), centerZ + dz * (before - after));
    }

    /** Pans by a GUI-pixel delta (dragging the map moves the world the other way). */
    public void panPixels(double dx, double dy) {
        double bpp = blocksPerPixel();
        center(centerX - dx * bpp, centerZ - dy * bpp);
    }
}
