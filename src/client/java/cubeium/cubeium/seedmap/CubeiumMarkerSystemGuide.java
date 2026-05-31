package cubeium.cubeium.seedmap;

/**
 * Configuration and setup guide for the Marker system.
 * 
 * MARKER ICONS SETUP:
 * ===================
 * Place map marker icon PNG files in:
 *   src/main/resources/assets/cubeium/textures/gui/markers/
 * 
 * Required files:
 *   - origin_icon.png      (16x16 or 32x32, shown at world coordinate 0,0)
 *   - player_head_icon.png (16x16 or 32x32, fallback for player marker)
 *   - village_icon.png     (16x16 or 32x32, for future village markers)
 *   - stronghold_icon.png  (16x16 or 32x32, for future stronghold markers)
 * 
 * ICON RECOMMENDATIONS:
 * - Format: PNG with transparency (alpha channel)
 * - Size: 16x16 or 32x32 pixels (CubeiumMarkerRenderer will scale to display size)
 * - Colors: Bright, distinct colors for visibility on map
 * - Origin: Crosshair or star icon, neutral color (white/gray)
 * - Player: Simplified player head or player-shaped icon, bright color
 * - Village: House or settlement icon, brown/tan
 * - Stronghold: Fortress or portal icon, dark/purple
 * 
 * ADDING NEW MARKER TYPES:
 * 1. Add icon PNG file to markers/ folder
 * 2. Add MarkerType enum value in CubeiumMapMarker.java
 * 3. Add Identifier and texture path in CubeiumMarkerRenderer.initializeIconTextures()
 * 4. Use: session.markers.add(new CubeiumMapMarker(MarkerType.YOURTYPE, x, z, "Label"))
 * 
 * PLAYER HEAD RENDERING:
 * The system attempts to render the player's actual skin head on the player marker.
 * If skin loading fails (no internet, texture not ready), it falls back to 
 * the player_head_icon.png placeholder. This is handled automatically and gracefully.
 * 
 * SESSION PERSISTENCE:
 * Markers are stored in SeedMapSession.markers (CopyOnWriteArrayList) and persist
 * across screen close/reopen like seed and pan/zoom. This allows:
 * - Origin marker to always be visible
 * - Player marker to update each tick while screen is open
 * - Easy addition of future marker persistence to disk
 * 
 * PERFORMANCE:
 * - Marker rendering is lightweight (< 1ms per frame for typical use)
 * - Player head texture is cached for 5 seconds to avoid repeated lookups
 * - Markers are culled if outside visible map area
 * - Uses same coordinate math as hover tooltips for consistency
 */
public class CubeiumMarkerSystemGuide {
    // This is a documentation class with no runtime code.
    // See CubeiumMarkerRenderer.java for implementation details.
}
