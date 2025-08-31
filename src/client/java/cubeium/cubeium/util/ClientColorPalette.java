package cubeium.cubeium.util;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Color palette for client-side map rendering.
 * Provides colors for biomes, structures, and other map elements.
 */
public class ClientColorPalette {
    
    // Biome colors based on Minecraft's biome system
    private static final Map<Integer, Color> BIOME_COLORS = new HashMap<>();
    private static final Map<String, Color> STRUCTURE_COLORS = new HashMap<>();
    
    // Default colors
    private static final Color DEFAULT_BIOME_COLOR = new Color(128, 128, 128);
    private static final Color OCEAN_COLOR = new Color(64, 64, 255);
    private static final Color DEFAULT_STRUCTURE_COLOR = new Color(255, 255, 0);
    
    static {
        initializeBiomeColors();
        initializeStructureColors();
    }
    
    /**
     * Initialize biome color mappings
     */
    private static void initializeBiomeColors() {
        // Ocean biomes
        BIOME_COLORS.put(0, new Color(0, 0, 172)); // Ocean
        BIOME_COLORS.put(44, new Color(0, 0, 112)); // Deep Ocean
        BIOME_COLORS.put(46, new Color(64, 64, 255)); // Cold Ocean
        BIOME_COLORS.put(47, new Color(32, 32, 112)); // Deep Cold Ocean
        BIOME_COLORS.put(48, new Color(0, 119, 190)); // Frozen Ocean
        BIOME_COLORS.put(49, new Color(32, 56, 95)); // Deep Frozen Ocean
        BIOME_COLORS.put(45, new Color(68, 106, 191)); // Lukewarm Ocean
        BIOME_COLORS.put(50, new Color(32, 96, 112)); // Deep Lukewarm Ocean
        BIOME_COLORS.put(43, new Color(0, 64, 221)); // Warm Ocean
        
        // Plains and grassland
        BIOME_COLORS.put(1, new Color(141, 179, 96)); // Plains
        BIOME_COLORS.put(129, new Color(183, 219, 138)); // Sunflower Plains
        
        // Desert
        BIOME_COLORS.put(2, new Color(250, 148, 24)); // Desert
        BIOME_COLORS.put(130, new Color(255, 188, 64)); // Desert Hills
        
        // Mountains and hills
        BIOME_COLORS.put(3, new Color(96, 96, 96)); // Mountains
        BIOME_COLORS.put(131, new Color(128, 128, 128)); // Gravelly Mountains
        BIOME_COLORS.put(34, new Color(144, 144, 144)); // Modified Gravelly Mountains
        
        // Forest biomes
        BIOME_COLORS.put(4, new Color(5, 102, 33)); // Forest
        BIOME_COLORS.put(132, new Color(45, 142, 73)); // Flower Forest
        BIOME_COLORS.put(5, new Color(11, 102, 89)); // Taiga
        BIOME_COLORS.put(133, new Color(51, 142, 129)); // Taiga Hills
        BIOME_COLORS.put(6, new Color(7, 249, 178)); // Swamp
        BIOME_COLORS.put(134, new Color(47, 255, 218)); // Swamp Hills
        
        // Cold biomes
        BIOME_COLORS.put(12, new Color(255, 255, 255)); // Snowy Tundra
        BIOME_COLORS.put(140, new Color(180, 220, 220)); // Ice Spikes
        BIOME_COLORS.put(30, new Color(160, 160, 255)); // Cold Taiga
        BIOME_COLORS.put(158, new Color(200, 200, 255)); // Cold Taiga Hills
        
        // Jungle
        BIOME_COLORS.put(21, new Color(83, 123, 9)); // Jungle
        BIOME_COLORS.put(149, new Color(123, 163, 49)); // Modified Jungle
        BIOME_COLORS.put(22, new Color(103, 143, 29)); // Jungle Hills
        BIOME_COLORS.put(23, new Color(123, 163, 49)); // Jungle Edge
        BIOME_COLORS.put(151, new Color(143, 183, 69)); // Modified Jungle Edge
        
        // Badlands
        BIOME_COLORS.put(37, new Color(217, 69, 21)); // Badlands
        BIOME_COLORS.put(165, new Color(255, 109, 61)); // Badlands Plateau
        BIOME_COLORS.put(166, new Color(176, 151, 101)); // Modified Badlands Plateau
        BIOME_COLORS.put(167, new Color(202, 140, 101)); // Eroded Badlands
        
        // Savanna
        BIOME_COLORS.put(35, new Color(189, 178, 95)); // Savanna
        BIOME_COLORS.put(163, new Color(229, 218, 135)); // Savanna Plateau
        BIOME_COLORS.put(36, new Color(167, 157, 100)); // Shattered Savanna
        BIOME_COLORS.put(164, new Color(207, 197, 140)); // Shattered Savanna Plateau
        
        // Dark Forest
        BIOME_COLORS.put(29, new Color(64, 81, 26)); // Dark Forest
        BIOME_COLORS.put(157, new Color(104, 121, 66)); // Dark Forest Hills
        
        // River and beach
        BIOME_COLORS.put(7, new Color(0, 0, 255)); // River
        BIOME_COLORS.put(11, new Color(255, 255, 170)); // Beach
        BIOME_COLORS.put(25, new Color(162, 162, 132)); // Stone Shore
        BIOME_COLORS.put(26, new Color(250, 240, 192)); // Snowy Beach
        
        // Mushroom Fields
        BIOME_COLORS.put(14, new Color(255, 0, 255)); // Mushroom Fields
        BIOME_COLORS.put(15, new Color(160, 0, 255)); // Mushroom Field Shore
        
        // Nether
        BIOME_COLORS.put(8, new Color(255, 0, 0)); // Nether Wastes
        BIOME_COLORS.put(170, new Color(73, 144, 123)); // Soul Sand Valley
        BIOME_COLORS.put(171, new Color(92, 25, 29)); // Crimson Forest
        BIOME_COLORS.put(172, new Color(22, 126, 134)); // Warped Forest
        BIOME_COLORS.put(173, new Color(94, 116, 105)); // Basalt Deltas
        
        // End
        BIOME_COLORS.put(9, new Color(128, 128, 255)); // The End
        BIOME_COLORS.put(40, new Color(155, 155, 255)); // Small End Islands
        BIOME_COLORS.put(41, new Color(128, 128, 200)); // End Midlands
        BIOME_COLORS.put(42, new Color(100, 100, 155)); // End Highlands
        BIOME_COLORS.put(43, new Color(180, 180, 220)); // End Barrens
        
        // Cave biomes (1.18+)
        BIOME_COLORS.put(174, new Color(56, 61, 58)); // Dripstone Caves
        BIOME_COLORS.put(175, new Color(45, 95, 106)); // Lush Caves
        BIOME_COLORS.put(176, new Color(22, 17, 32)); // Deep Dark
    }
    
    /**
     * Initialize structure color mappings
     */
    private static void initializeStructureColors() {
        STRUCTURE_COLORS.put("village", new Color(139, 69, 19));        // Brown
        STRUCTURE_COLORS.put("mansion", new Color(64, 64, 64));         // Dark Gray
        STRUCTURE_COLORS.put("monument", new Color(0, 255, 255));       // Cyan
        STRUCTURE_COLORS.put("stronghold", new Color(128, 0, 128));     // Purple
        STRUCTURE_COLORS.put("fortress", new Color(255, 0, 0));         // Red
        STRUCTURE_COLORS.put("bastion", new Color(255, 69, 0));         // Orange Red
        STRUCTURE_COLORS.put("end_city", new Color(255, 0, 255));       // Magenta
        STRUCTURE_COLORS.put("shipwreck", new Color(101, 67, 33));      // Saddle Brown
        STRUCTURE_COLORS.put("ruined_portal", new Color(75, 0, 130));   // Indigo
        STRUCTURE_COLORS.put("desert_pyramid", new Color(255, 215, 0)); // Gold
        STRUCTURE_COLORS.put("jungle_pyramid", new Color(34, 139, 34)); // Forest Green
        STRUCTURE_COLORS.put("igloo", new Color(176, 224, 230));        // Powder Blue
        STRUCTURE_COLORS.put("swamp_hut", new Color(85, 107, 47));      // Dark Olive Green
        STRUCTURE_COLORS.put("outpost", new Color(105, 105, 105));      // Dim Gray
        STRUCTURE_COLORS.put("ancient_city", new Color(25, 25, 112));   // Midnight Blue
    }
    
    /**
     * Get color for a biome ID
     * @param biomeId Biome ID
     * @return Color for the biome
     */
    public Color getBiomeColor(int biomeId) {
        return BIOME_COLORS.getOrDefault(biomeId, DEFAULT_BIOME_COLOR);
    }
    
    /**
     * Get color for a structure type
     * @param structureType Structure type name
     * @return Color for the structure
     */
    public Color getStructureColor(String structureType) {
        return STRUCTURE_COLORS.getOrDefault(structureType.toLowerCase(), DEFAULT_STRUCTURE_COLOR);
    }
    
    /**
     * Get ocean color for default background
     * @return Ocean color
     */
    public Color getOceanColor() {
        return OCEAN_COLOR;
    }
    
    /**
     * Get default biome color
     * @return Default biome color
     */
    public Color getDefaultBiomeColor() {
        return DEFAULT_BIOME_COLOR;
    }
    
    /**
     * Get default structure color
     * @return Default structure color
     */
    public Color getDefaultStructureColor() {
        return DEFAULT_STRUCTURE_COLOR;
    }
    
    /**
     * Check if a biome color is defined
     * @param biomeId Biome ID
     * @return True if color is defined
     */
    public boolean hasBiomeColor(int biomeId) {
        return BIOME_COLORS.containsKey(biomeId);
    }
    
    /**
     * Check if a structure color is defined
     * @param structureType Structure type
     * @return True if color is defined
     */
    public boolean hasStructureColor(String structureType) {
        return STRUCTURE_COLORS.containsKey(structureType.toLowerCase());
    }
    
    /**
     * Add or update a biome color
     * @param biomeId Biome ID
     * @param color Color for the biome
     */
    public void setBiomeColor(int biomeId, Color color) {
        BIOME_COLORS.put(biomeId, color);
    }
    
    /**
     * Add or update a structure color
     * @param structureType Structure type
     * @param color Color for the structure
     */
    public void setStructureColor(String structureType, Color color) {
        STRUCTURE_COLORS.put(structureType.toLowerCase(), color);
    }
    
    /**
     * Get all defined biome colors
     * @return Map of biome ID to color
     */
    public Map<Integer, Color> getAllBiomeColors() {
        return new HashMap<>(BIOME_COLORS);
    }
    
    /**
     * Get all defined structure colors
     * @return Map of structure type to color
     */
    public Map<String, Color> getAllStructureColors() {
        return new HashMap<>(STRUCTURE_COLORS);
    }
}
