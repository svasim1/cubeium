package cubeium.cubeium.seedmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cubeium.cubeium.world.generation.BiomeGenerator;

public final class CubeiumBiomeFilterCatalog {
    private static volatile List<BiomeGroup> cachedGroups;

    private CubeiumBiomeFilterCatalog() {
    }

    public static List<BiomeGroup> getCategorizedBiomes(BiomeGenerator biomeGenerator) {
        List<BiomeGroup> current = cachedGroups;
        if (current != null) {
            return current;
        }

        synchronized (CubeiumBiomeFilterCatalog.class) {
            if (cachedGroups != null) {
                return cachedGroups;
            }

            Map<String, List<BiomeEntry>> grouped = new LinkedHashMap<>();
            for (String category : List.of(
                "BEACH", "DESERT", "FOREST", "ICE", "JUNGLE", "MESA",
                "MOUNTAINS", "MUSHROOM", "OCEAN", "PLAINS", "RIVER", "SAVANNA",
                "SWAMP", "TAIGA", "OTHER"
            )) {
                grouped.put(category, new ArrayList<>());
            }

            // Biome ids are below 256; list exactly the biomes this version's Overworld generates.
            // Cave biomes are left out: the map samples above the terrain, where they never occur.
            for (int biomeId = 0; biomeId < 256; biomeId++) {
                if (!biomeGenerator.isOverworldBiome(biomeId)) {
                    continue;
                }
                String biomeName = biomeGenerator.getBiomeName(biomeId);
                if (isCaveBiome(biomeName)) {
                    continue;
                }
                BiomeEntry entry = new BiomeEntry(biomeId, biomeName, BiomeGenerator.getBiomeColor(biomeId));
                grouped.get(classifyBiome(biomeName)).add(entry);
            }

            List<BiomeGroup> groups = new ArrayList<>();
            for (Map.Entry<String, List<BiomeEntry>> group : grouped.entrySet()) {
                List<BiomeEntry> entries = group.getValue();
                entries.sort(Comparator.comparing(BiomeEntry::name));
                if (!entries.isEmpty()) {
                    groups.add(new BiomeGroup(group.getKey(), List.copyOf(entries)));
                }
            }

            cachedGroups = List.copyOf(groups);
            return cachedGroups;
        }
    }

    public static List<BiomeEntry> flatten(List<BiomeGroup> groups) {
        List<BiomeEntry> entries = new ArrayList<>();
        for (BiomeGroup group : groups) {
            entries.addAll(group.entries());
        }
        return entries;
    }

    private static String classifyBiome(String biomeName) {
        String normalized = biomeName.toLowerCase(Locale.ROOT);
        if (normalized.contains("beach") || normalized.contains("shore")) return "BEACH";
        if (normalized.contains("desert")) return "DESERT";
        if (normalized.contains("swamp")) return "SWAMP";
        if (normalized.contains("forest") || normalized.contains("grove") || normalized.contains("cherry") || normalized.contains("pale garden")) return "FOREST";
        if (normalized.contains("frozen") || normalized.contains("ice spikes") || normalized.contains("snowy slopes")) return "ICE";
        if (normalized.contains("jungle") || normalized.contains("bamboo")) return "JUNGLE";
        if (normalized.contains("badlands") || normalized.contains("wooded badlands") || normalized.contains("eroded")) return "MESA";
        if (normalized.contains("peak") || normalized.contains("hill") || normalized.contains("meadow") || normalized.contains("slopes") || normalized.contains("windswept")) return "MOUNTAINS";
        if (normalized.contains("mushroom")) return "MUSHROOM";
        if (normalized.contains("ocean")) return "OCEAN";
        if (normalized.contains("plain")) return "PLAINS";
        if (normalized.contains("river")) return "RIVER";
        if (normalized.contains("savanna")) return "SAVANNA";
        if (normalized.contains("taiga")) return "TAIGA";
        return "OTHER";
    }

    private static boolean isCaveBiome(String biomeName) {
        String normalized = biomeName.toLowerCase(Locale.ROOT);
        return normalized.contains("cave") || normalized.contains("deep dark");
    }

    public record BiomeEntry(int id, String name, int color) {
    }

    public record BiomeGroup(String title, List<BiomeEntry> entries) {
    }
}