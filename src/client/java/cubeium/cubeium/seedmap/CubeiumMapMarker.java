package cubeium.cubeium.seedmap;

/**
 * Represents a marker on the seed map.
 * Markers are world-space positions with type and optional metadata.
 */
public class CubeiumMapMarker {
    public enum MarkerType {
        ORIGIN,       // Fixed at (0, 0)
        PLAYER,       // Player's current position
        VILLAGE,      // Village location
        STRONGHOLD,   // Stronghold location
        CUSTOM        // User-placed or plugin markers
    }

    public final MarkerType type;
    public int worldX;
    public int worldZ;
    public String label;
    public boolean visible;

    // Type-specific metadata
    public Object metadata;

    public CubeiumMapMarker(MarkerType type, int worldX, int worldZ) {
        this.type = type;
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.label = type.toString();
        this.visible = true;
        this.metadata = null;
    }

    public CubeiumMapMarker(MarkerType type, int worldX, int worldZ, String label) {
        this(type, worldX, worldZ);
        this.label = label;
    }

    /**
     * Returns the icon size in pixels (will be scaled to this on map display)
     */
    public int getIconSize() {
        return 16; // Default 16x16 pixels
    }

    /**
     * Update marker position (for dynamic markers like player)
     */
    public void setPosition(int x, int z) {
        this.worldX = x;
        this.worldZ = z;
    }

    @Override
    public String toString() {
        return String.format("CubeiumMapMarker<%s>(%d, %d, label=%s)", type, worldX, worldZ, label);
    }
}
