package cubeium.cubeium.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import cubeium.cubeium.generator.BiomeGenerator;
import cubeium.cubeium.noise.BiomeNoise;

public class BiomeCommand {
        private static String getBiomeName(int biomeId) {
                switch (biomeId) {
                        case 0:
                                return "The Void";
                        case 1:
                                return "Plains";
                        case 2:
                                return "Sunflower Plains";
                        case 3:
                                return "Snowy Plains";
                        case 4:
                                return "Ice Spikes";
                        case 5:
                                return "Desert";
                        case 6:
                                return "Swamp";
                        case 7:
                                return "Mangrove Swamp";
                        case 8:
                                return "Forest";
                        case 9:
                                return "Flower Forest";
                        case 10:
                                return "Birch Forest";
                        case 11:
                                return "Dark Forest";
                        case 12:
                                return "Old Growth Birch Forest";
                        case 13:
                                return "Old Growth Pine Taiga";
                        case 14:
                                return "Old Growth Spruce Taiga";
                        case 15:
                                return "Taiga";
                        case 16:
                                return "Snowy Taiga";
                        case 17:
                                return "Savanna";
                        case 18:
                                return "Savanna Plateau";
                        case 19:
                                return "Windswept Hills";
                        case 20:
                                return "Windswept Gravelly Hills";
                        case 21:
                                return "Windswept Forest";
                        case 22:
                                return "Windswept Savanna";
                        case 23:
                                return "Jungle";
                        case 24:
                                return "Sparse Jungle";
                        case 25:
                                return "Bamboo Jungle";
                        case 26:
                                return "Badlands";
                        case 27:
                                return "Eroded Badlands";
                        case 28:
                                return "Wooded Badlands";
                        case 29:
                                return "Meadow";
                        case 30:
                                return "Cherry Grove";
                        case 31:
                                return "Grove";
                        case 32:
                                return "Snowy Slopes";
                        case 33:
                                return "Frozen Peaks";
                        case 34:
                                return "Jagged Peaks";
                        case 35:
                                return "Stony Peaks";
                        case 36:
                                return "River";
                        case 37:
                                return "Frozen River";
                        case 38:
                                return "Beach";
                        case 39:
                                return "Snowy Beach";
                        case 40:
                                return "Stony Shore";
                        case 41:
                                return "Warm Ocean";
                        case 42:
                                return "Lukewarm Ocean";
                        case 43:
                                return "Deep Lukewarm Ocean";
                        case 44:
                                return "Ocean";
                        case 45:
                                return "Deep Ocean";
                        case 46:
                                return "Cold Ocean";
                        case 47:
                                return "Deep Cold Ocean";
                        case 48:
                                return "Frozen Ocean";
                        case 49:
                                return "Deep Frozen Ocean";
                        case 50:
                                return "Mushroom Fields";
                        case 51:
                                return "Dripstone Caves";
                        case 52:
                                return "Lush Caves";
                        case 53:
                                return "Deep Dark";
                        case 54:
                                return "Nether Wastes";
                        case 55:
                                return "Warped Forest";
                        case 56:
                                return "Crimson Forest";
                        case 57:
                                return "Soul Sand Valley";
                        case 58:
                                return "Basalt Deltas";
                        case 59:
                                return "The End";
                        case 60:
                                return "End Highlands";
                        case 61:
                                return "End Midlands";
                        case 62:
                                return "Small End Islands";
                        case 63:
                                return "End Barrens";
                        default:
                                return "Unknown Biome (" + biomeId + ")";
                }
        }

        public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
                dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("testbiome")
                                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
                                                .argument("x", IntegerArgumentType.integer())
                                                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
                                                                .argument("y", IntegerArgumentType.integer())
                                                                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
                                                                                .argument("z", IntegerArgumentType
                                                                                                .integer())
                                                                                .executes(context -> {
                                                                                        int x = IntegerArgumentType
                                                                                                        .getInteger(context,
                                                                                                                        "x");
                                                                                        int y = IntegerArgumentType
                                                                                                        .getInteger(context,
                                                                                                                        "y");
                                                                                        int z = IntegerArgumentType
                                                                                                        .getInteger(context,
                                                                                                                        "z");

                                                                                        // Get the world seed from the
                                                                                        // client
                                                                                        long worldSeed = 3227429997367034446L; // Hardcoded
                                                                                                                               // test
                                                                                                                               // seed

                                                                                        // Create a new biome generator
                                                                                        BiomeGenerator generator = new BiomeGenerator(
                                                                                                        worldSeed,
                                                                                                        false);

                                                                                        // Get the biome ID
                                                                                        int biomeId = generator
                                                                                                        .getBiome(x, y, z);

                                                                                        // Get the climate parameters
                                                                                        // for debugging
                                                                                        BiomeNoise biomeNoise = new BiomeNoise(
                                                                                                        worldSeed,
                                                                                                        false);
                                                                                        double[] params = biomeNoise
                                                                                                        .getClimateParameters(
                                                                                                                        x,
                                                                                                                        y,
                                                                                                                        z);
                                                                                        double temperature = params[0];
                                                                                        double humidity = params[1];
                                                                                        double continentalness = params[2];
                                                                                        double erosion = params[3];
                                                                                        double weirdness = params[4];
                                                                                        double depth = params[5];

                                                                                        // Send the result back to the
                                                                                        // player
                                                                                        context.getSource()
                                                                                                        .sendFeedback(Text
                                                                                                                        .literal(String.format(
                                                                                                                                        "Biome at (%d, %d, %d): %s\n"
                                                                                                                                                        +
                                                                                                                                                        "Climate Parameters:\n"
                                                                                                                                                        +
                                                                                                                                                        "Temperature: %.4f\n"
                                                                                                                                                        +
                                                                                                                                                        "Humidity: %.4f\n"
                                                                                                                                                        +
                                                                                                                                                        "Continentalness: %.4f\n"
                                                                                                                                                        +
                                                                                                                                                        "Erosion: %.4f\n"
                                                                                                                                                        +
                                                                                                                                                        "Weirdness: %.4f\n"
                                                                                                                                                        +
                                                                                                                                                        "Depth: %.4f",
                                                                                                                                        x,
                                                                                                                                        y,
                                                                                                                                        z,
                                                                                                                                        getBiomeName(biomeId),
                                                                                                                                        temperature,
                                                                                                                                        humidity,
                                                                                                                                        continentalness,
                                                                                                                                        erosion,
                                                                                                                                        weirdness,
                                                                                                                                        depth)));

                                                                                        return 1;
                                                                                })))));
        }
}