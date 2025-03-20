package com.seedmap.world;

import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

public class BiomeGenerator {
    private final Level level;
    private final long seed;
    private final RandomState randomState;
    private final NoiseBasedChunkGenerator chunkGenerator;

    public BiomeGenerator(Level level) {
        this.level = level;
        this.seed = level.getSeed();
        this.chunkGenerator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        this.randomState = RandomState.create(chunkGenerator.getBiomeSource(), seed);
    }

    public ResourceKey<Biome> getBiomeAt(int x, int z) {
        // Get the biome using the noise generator
        return chunkGenerator.getBiomeSource()
            .getNoiseBiome(x >> 2, 64 >> 2, z >> 2, randomState)
            .unwrapKey()
            .orElse(Biomes.PLAINS);
    }

    public int getBiomeColor(ResourceKey<Biome> biome) {
        String path = biome.location().getPath();
        return switch (path) {
            // Overworld Biomes
            case "plains" -> 0x91BD59;      // Light Green
            case "desert" -> 0xF4A460;      // Sandy
            case "mountains" -> 0x8B4513;   // Brown
            case "forest" -> 0x056621;      // Dark Green
            case "taiga" -> 0x0B6659;       // Dark Teal
            case "swamp" -> 0x07F9B2;       // Light Green
            case "river" -> 0x3030AF;       // Blue
            case "nether_wastes" -> 0xBF0000; // Red
            case "the_end" -> 0x808080;     // Gray
            case "snowy_plains" -> 0xFFFFFF; // White
            case "snowy_taiga" -> 0xCCCCCC; // Light Gray
            case "snowy_beach" -> 0xF0F0F0; // Off-White
            case "mushroom_fields" -> 0xFF00FF; // Purple
            case "beach" -> 0xFADE55;       // Yellow
            case "jungle" -> 0x537B09;      // Dark Green
            case "sparse_jungle" -> 0x628B17; // Medium Green
            case "bamboo_jungle" -> 0x1F9E0E; // Light Green
            case "badlands" -> 0xD94515;    // Orange-Red
            case "wooded_badlands" -> 0xB09765; // Brown
            case "eroded_badlands" -> 0x8B3F2D; // Dark Red
            case "savanna" -> 0xBDB25F;     // Yellow-Green
            case "savanna_plateau" -> 0xA79D64; // Light Yellow-Green
            case "windswept_savanna" -> 0xE5DA87; // Light Yellow
            case "windswept_hills" -> 0x8D8D8D; // Gray
            case "windswept_forest" -> 0x6B8C5D; // Green-Gray
            case "windswept_gravelly_hills" -> 0x909090; // Light Gray
            case "meadow" -> 0x67C240;      // Bright Green
            case "grove" -> 0x42B883;       // Emerald
            case "snowy_slopes" -> 0xE8E8E8; // Light Gray
            case "frozen_peaks" -> 0xE0E0E0; // Gray
            case "jagged_peaks" -> 0xC0C0C0; // Silver
            case "stony_peaks" -> 0x808080; // Dark Gray
            case "cherry_grove" -> 0xFFB7C5; // Pink
            
            // Nether Biomes
            case "nether_wastes" -> 0xBF0000; // Red
            case "warped_forest" -> 0x167E7E; // Cyan
            case "crimson_forest" -> 0xDC143C; // Crimson
            case "soul_sand_valley" -> 0x4A4A4A; // Dark Gray
            case "basalt_deltas" -> 0x424242; // Charcoal
            
            // End Biomes
            case "the_end" -> 0x808080;     // Gray
            case "end_highlands" -> 0xE0E0E0; // Light Gray
            case "end_midlands" -> 0xE6E6FA; // Lavender
            case "small_end_islands" -> 0xFFF0F5; // Lavender Blush
            case "end_barrens" -> 0x2F4F4F; // Dark Slate
            
            default -> 0xFFFFFF; // White for unknown
        };
    }
} 