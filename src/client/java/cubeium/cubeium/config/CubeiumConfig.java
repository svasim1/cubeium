package cubeium.cubeium.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import cubeium.cubeium.Cubeium;
import cubeium.cubeium.world.StructureKind;
import net.fabricmc.loader.api.FabricLoader;

/** Persistent client settings, stored as config/cubeium.json. */
public final class CubeiumConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("cubeium.json");
    private static CubeiumConfig instance;

    public boolean darkMode = false;
    public boolean floatingTooltip = false;
    public boolean markerLabels = false;
    public boolean teleportInMenu = false;
    public boolean keepMapPosition = false;
    public boolean renderMetrics = false;
    /** GUI scale of the map screens; 0 = automatic. */
    public int mapUiScale = 0;

    public boolean regionGrid = false;
    public boolean coordinateAxes = true;
    public boolean panelCollapsed = false;
    public boolean slimeChunks = false;

    /** Enabled structures, by {@link StructureKind#key()}. */
    public Set<String> structures = new LinkedHashSet<>(defaultStructures());

    public boolean highlightBiomes = false;
    public Set<Integer> highlightedBiomes = new LinkedHashSet<>();

    /** Seed text per world ("sp:<save name>" or "mp:<server address>"), so each world keeps its own. */
    public Map<String, String> seeds = new LinkedHashMap<>();

    /** Waypoints per world, keyed like {@link #seeds}. */
    public Map<String, List<Waypoint>> waypoints = new LinkedHashMap<>();

    public static CubeiumConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public List<Waypoint> waypoints(String worldKey) {
        return waypoints.computeIfAbsent(worldKey, key -> new ArrayList<>());
    }

    public Set<StructureKind> enabledStructures() {
        Set<StructureKind> kinds = EnumSet.noneOf(StructureKind.class);
        for (StructureKind kind : StructureKind.values()) {
            if (structures.contains(kind.key())) {
                kinds.add(kind);
            }
        }
        return kinds;
    }

    public void setStructureEnabled(StructureKind kind, boolean enabled) {
        if (enabled) {
            structures.add(kind.key());
        } else {
            structures.remove(kind.key());
        }
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Cubeium.LOGGER.warn("Failed to save {}", FILE, e);
        }
    }

    private static CubeiumConfig load() {
        if (Files.exists(FILE)) {
            try {
                CubeiumConfig config = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), CubeiumConfig.class);
                if (config != null) {
                    config.repairNulls();
                    return config;
                }
            } catch (IOException | JsonParseException e) {
                Cubeium.LOGGER.warn("Failed to read {}, using defaults", FILE, e);
            }
        }
        return new CubeiumConfig();
    }

    /** Gson leaves fields missing from an older file null. */
    private void repairNulls() {
        if (structures == null) structures = new LinkedHashSet<>(defaultStructures());
        if (highlightedBiomes == null) highlightedBiomes = new LinkedHashSet<>();
        if (seeds == null) seeds = new LinkedHashMap<>();
        if (waypoints == null) waypoints = new LinkedHashMap<>();
    }

    private static Set<String> defaultStructures() {
        Set<String> keys = new LinkedHashSet<>();
        for (StructureKind kind : StructureKind.values()) {
            // Off by default: so common they would bury the map in icons.
            boolean noisy = kind == StructureKind.MINESHAFT || kind == StructureKind.BURIED_TREASURE || kind == StructureKind.RUINED_PORTAL || kind == StructureKind.RUINED_PORTAL_NETHER
                    || kind == StructureKind.SHIPWRECK || kind == StructureKind.OCEAN_RUIN || kind == StructureKind.TRIAL_CHAMBERS;
            if (!noisy) {
                keys.add(kind.key());
            }
        }
        return keys;
    }
}
