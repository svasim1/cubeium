package cubeium.cubeium.world.generation;

import cubeium.cubeium.world.CubiomesInterface;

/**
 * Represents a structure type with metadata for rendering and display
 */
public class StructureType {
    private final int id;
    private final String name;
    private final String displayName;
    
    // Static instances for common structure types
    public static final StructureType VILLAGE = new StructureType(CubiomesInterface.VILLAGE, "village", "Village");
    public static final StructureType DESERT_PYRAMID = new StructureType(CubiomesInterface.DESERT_PYRAMID, "desert_pyramid", "Desert Pyramid");
    public static final StructureType JUNGLE_TEMPLE = new StructureType(CubiomesInterface.JUNGLE_TEMPLE, "jungle_pyramid", "Jungle Temple");
    public static final StructureType SWAMP_HUT = new StructureType(CubiomesInterface.SWAMP_HUT, "swamp_hut", "Swamp Hut");
    public static final StructureType IGLOO = new StructureType(CubiomesInterface.IGLOO, "igloo", "Igloo");
    public static final StructureType OUTPOST = new StructureType(CubiomesInterface.OUTPOST, "outpost", "Pillager Outpost");
    public static final StructureType OCEAN_MONUMENT = new StructureType(CubiomesInterface.OCEAN_MONUMENT, "monument", "Ocean Monument");
    public static final StructureType WOODLAND_MANSION = new StructureType(CubiomesInterface.WOODLAND_MANSION, "mansion", "Woodland Mansion");
    public static final StructureType STRONGHOLD = new StructureType(CubiomesInterface.STRONGHOLD, "stronghold", "Stronghold");
    public static final StructureType MINESHAFT = new StructureType(CubiomesInterface.MINESHAFT, "mineshaft", "Mineshaft");
    public static final StructureType END_CITY = new StructureType(CubiomesInterface.END_CITY, "end_city", "End City");
    public static final StructureType NETHER_FORTRESS = new StructureType(CubiomesInterface.NETHER_FORTRESS, "fortress", "Nether Fortress");
    public static final StructureType BASTION = new StructureType(CubiomesInterface.BASTION, "bastion", "Bastion Remnant");
    public static final StructureType RUINED_PORTAL = new StructureType(CubiomesInterface.RUINED_PORTAL, "ruined_portal", "Ruined Portal");
    
    public StructureType(int id, String name, String displayName) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
    }
    
    public int getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Get StructureType from ID
     * @param id Structure type ID
     * @return StructureType or null if not found
     */
    public static StructureType fromId(int id) {
        switch (id) {
            case CubiomesInterface.VILLAGE: return VILLAGE;
            case CubiomesInterface.DESERT_PYRAMID: return DESERT_PYRAMID;
            case CubiomesInterface.JUNGLE_TEMPLE: return JUNGLE_TEMPLE;
            case CubiomesInterface.SWAMP_HUT: return SWAMP_HUT;
            case CubiomesInterface.IGLOO: return IGLOO;
            case CubiomesInterface.OUTPOST: return OUTPOST;
            case CubiomesInterface.OCEAN_MONUMENT: return OCEAN_MONUMENT;
            case CubiomesInterface.WOODLAND_MANSION: return WOODLAND_MANSION;
            case CubiomesInterface.STRONGHOLD: return STRONGHOLD;
            case CubiomesInterface.MINESHAFT: return MINESHAFT;
            case CubiomesInterface.END_CITY: return END_CITY;
            case CubiomesInterface.NETHER_FORTRESS: return NETHER_FORTRESS;
            case CubiomesInterface.BASTION: return BASTION;
            case CubiomesInterface.RUINED_PORTAL: return RUINED_PORTAL;
            default: return new StructureType(id, "unknown", "Unknown Structure");
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof StructureType)) return false;
        StructureType other = (StructureType) obj;
        return id == other.id;
    }
    
    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
