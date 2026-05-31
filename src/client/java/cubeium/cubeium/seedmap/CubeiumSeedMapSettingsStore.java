package cubeium.cubeium.seedmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cubeium.cubeium.Cubeium;
import net.fabricmc.loader.api.FabricLoader;

public final class CubeiumSeedMapSettingsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SETTINGS_FILE_NAME = "cubeium-client-settings.json";

    private CubeiumSeedMapSettingsStore() {
    }

    public static void savePersistentSettings(CubeiumSeedMapScreen.SeedMapSession session) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("showPerformanceInfo", session.isShowPerformanceInfo());
            root.addProperty("showFloatingTooltip", session.isShowFloatingTooltip());
            root.addProperty("enableTeleportInContextMenu", session.isEnableTeleportInContextMenu());
            root.addProperty("showMarkerLabels", session.isShowMarkerLabels());
            root.addProperty("preservePanOnOpen", session.isPreservePanOnOpen());
            root.addProperty("biomeFilteringEnabled", session.isBiomeFilteringEnabled());

            JsonArray selectedBiomeIds = new JsonArray();
            for (Integer biomeId : session.selectedBiomeIds) {
                selectedBiomeIds.add(biomeId);
            }
            root.add("selectedBiomeIds", selectedBiomeIds);

            Path settingsPath = getSettingsPath();
            Files.createDirectories(settingsPath.getParent());
            Files.writeString(settingsPath, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Cubeium.LOGGER.warn("[CubeiumSeedMapSettingsStore] Failed to save settings", e);
        }
    }

    private static Path getSettingsPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(SETTINGS_FILE_NAME);
    }
}
