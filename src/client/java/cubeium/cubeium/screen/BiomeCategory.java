package cubeium.cubeium.screen;

import java.util.Set;

import net.minecraft.network.chat.Component;

/** Groups for the Biomes tab, by vanilla biome id. */
enum BiomeCategory {
    FORESTS("forests", Set.of("forest", "flower_forest", "birch_forest", "old_growth_birch_forest", "dark_forest", "pale_garden",
            "cherry_grove", "dappled_forest", "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "windswept_forest")),
    PLAINS("plains", Set.of("plains", "sunflower_plains", "meadow", "savanna", "savanna_plateau", "windswept_savanna")),
    SNOWY("snowy", Set.of("snowy_plains", "ice_spikes", "snowy_taiga", "grove", "snowy_slopes", "frozen_peaks", "snowy_beach")),
    MOUNTAINS("mountains", Set.of("windswept_hills", "windswept_gravelly_hills", "jagged_peaks", "stony_peaks")),
    DESERTS("deserts", Set.of("desert", "badlands", "eroded_badlands", "wooded_badlands")),
    JUNGLES("jungles", Set.of("jungle", "sparse_jungle", "bamboo_jungle")),
    SWAMPS("swamps", Set.of("swamp", "mangrove_swamp")),
    WATER("water", Set.of("ocean", "deep_ocean", "cold_ocean", "deep_cold_ocean", "lukewarm_ocean", "deep_lukewarm_ocean",
            "warm_ocean", "frozen_ocean", "deep_frozen_ocean", "river", "frozen_river", "beach", "stony_shore", "mushroom_fields")),
    NETHER("nether", Set.of("nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas")),
    END("end", Set.of("the_end", "small_end_islands", "end_midlands", "end_highlands", "end_barrens")),
    OTHER("other", Set.of());

    private final String key;
    private final Set<String> biomes;

    BiomeCategory(String key, Set<String> biomes) {
        this.key = key;
        this.biomes = biomes;
    }

    Component title() {
        return Component.translatable("cubeium.biomes.category." + key);
    }

    static BiomeCategory of(String biomeId) {
        for (BiomeCategory category : values()) {
            if (category.biomes.contains(biomeId)) {
                return category;
            }
        }
        return OTHER;
    }
}
