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
        centerX = x;
        centerZ = z;
    }

    public void setZoom(int zoom) {
        this.zoom = Math.clamp(zoom, MIN_ZOOM, MAX_ZOOM);
    }

    /** Zooms by {@code steps} levels keeping the block under GUI offset (dx, dz) from the center fixed. */
    public void zoomAround(int steps, double dx, double dz) {
        double before = blocksPerPixel();
        setZoom(zoom + steps);
        double after = blocksPerPixel();
        centerX += dx * (before - after);
        centerZ += dz * (before - after);
    }

    /** Pans by a GUI-pixel delta (dragging the map moves the world the other way). */
    public void panPixels(double dx, double dy) {
        double bpp = blocksPerPixel();
        centerX -= dx * bpp;
        centerZ -= dy * bpp;
    }
}
