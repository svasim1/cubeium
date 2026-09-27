package cubeium.cubeium.gametest;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;

import cubeium.cubeium.Cubeium;
import cubeium.cubeium.config.CubeiumConfig;
import cubeium.cubeium.screen.SeedMapScreen;
import cubeium.cubeium.world.Biomes;
import cubeium.cubeium.world.Dimension;
import cubeium.cubeium.world.StructureFinder;
import cubeium.cubeium.world.StructureKind;
import cubeium.cubeium.world.WorldGenerator;
import dev.xpple.cubiomes.Cubiomes;
import dev.xpple.cubiomes.Pos;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * Runs inside the real client. Compares Cubeium's biomes and structures with Minecraft's own world
 * generation for the same seed, then screenshots the UI (run/screenshots/).
 */
public class CubeiumClientGameTest implements FabricClientGameTest {
    private static final String SEED = "Cubeium";
    private static final int BIOME_SAMPLES = 4000;
    private static final int BIOME_RANGE = 30_000;
    private static final int MC = Cubiomes.MC_26_3();

    @Override
    public void runTest(ClientGameTestContext context) {
        // Consistent settings would create a superflat world; real terrain generation is needed.
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(state -> state.setSeed(SEED))
                .create()) {
            singleplayer.getConnection().waitForChunksRender();

            List<String> failures = new ArrayList<>();
            failures.addAll(singleplayer.getServer().computeOnServer(CubeiumClientGameTest::compareBiomes));
            failures.addAll(singleplayer.getServer().computeOnServer(CubeiumClientGameTest::compareStructures));
            if (!failures.isEmpty()) {
                throw new AssertionError(String.join("\n", failures));
            }

            screenshots(context);
        }
    }

    // ---- biomes ----

    private static List<String> compareBiomes(MinecraftServer server) {
        long seed = server.overworld().getSeed();
        if (seed != WorldOptions.parseSeed(SEED).orElseThrow()) {
            return List.of("world seed " + seed + " differs from the parsed seed text");
        }
        List<String> failures = new ArrayList<>();
        compareBiomes(server.overworld(), new WorldGenerator(MC, seed, Dimension.OVERWORLD), new int[] {WorldGenerator.MAP_BIOME_Y, 64, -20}, failures);
        compareBiomes(server.getLevel(Level.NETHER), new WorldGenerator(MC, seed, Dimension.NETHER), new int[] {64}, failures);
        compareBiomes(server.getLevel(Level.END), new WorldGenerator(MC, seed, Dimension.END), new int[] {64}, failures);
        return failures;
    }

    private static void compareBiomes(ServerLevel level, WorldGenerator world, int[] heights, List<String> failures) {
        Random random = new Random(1);
        Set<String> seen = new HashSet<>();
        List<String> surface = new ArrayList<>(), underground = new ArrayList<>();
        int compared = 0;
        for (int i = 0; i < BIOME_SAMPLES; i++) {
            int x = random.nextInt(2 * BIOME_RANGE) - BIOME_RANGE, z = random.nextInt(2 * BIOME_RANGE) - BIOME_RANGE;
            for (int y : heights) {
                String expected = level.getUncachedNoiseBiome(x >> 2, y >> 2, z >> 2).unwrapKey().orElseThrow().identifier().getPath();
                String actual = Biomes.name(MC, Cubiomes.getBiomeAt(generatorOf(world), 4, x >> 2, y >> 2, z >> 2));
                seen.add(expected);
                compared++;
                if (!expected.equals(actual)) {
                    (y < 0 ? underground : surface).add("(" + x + ", " + y + ", " + z + ") minecraft=" + expected + " cubiomes=" + actual);
                }
            }
            // The map pipeline itself.
            String expected = level.getUncachedNoiseBiome(x >> 2, WorldGenerator.MAP_BIOME_Y >> 2, z >> 2).unwrapKey().orElseThrow().identifier().getPath();
            String actual = Biomes.name(MC, world.biomeAt(x, z));
            if (!expected.equals(actual)) {
                surface.add("map (" + x + ", " + z + ") minecraft=" + expected + " cubeium=" + actual);
            }
        }
        Cubeium.LOGGER.info("[GameTest] {} biomes: {} samples, {} surface mismatches, {} underground mismatches {}, {} distinct biomes",
                world.dimension(), compared, surface.size(), underground.size(), underground, seen.size());
        if (!surface.isEmpty()) {
            failures.add(world.dimension() + ": " + surface.size() + " biome mismatches, first " + surface.subList(0, Math.min(5, surface.size())));
        }
        // cubiomes has rare edge cases in underground cave biomes; tolerate up to 0.1%.
        if (underground.size() > BIOME_SAMPLES / 1000) {
            failures.add(world.dimension() + ": " + underground.size() + " underground mismatches " + underground.subList(0, 5));
        }
        int minBiomes = world.dimension() == Dimension.OVERWORLD ? 30 : 3;
        if (seen.size() < minBiomes) {
            failures.add(world.dimension() + ": only " + seen.size() + " distinct biomes - not a default world?");
        }
    }

    /** The per-thread native generator, via a throwaway biome call's generator. */
    private static MemorySegment generatorOf(WorldGenerator world) {
        return GENERATORS.computeIfAbsent(world, w -> {
            MemorySegment g = dev.xpple.cubiomes.Generator.allocate(Arena.ofAuto());
            Cubiomes.setupGenerator(g, w.mcVersion(), 0);
            Cubiomes.applySeed(g, w.dimension().cubiomesId(), w.seed());
            return g;
        });
    }

    private static final Map<WorldGenerator, MemorySegment> GENERATORS = new LinkedHashMap<>();

    // ---- structures ----

    private static List<String> compareStructures(MinecraftServer server) {
        long seed = server.overworld().getSeed();
        List<String> failures = new ArrayList<>();
        for (Dimension dimension : Dimension.values()) {
            ServerLevel level = switch (dimension) {
                case OVERWORLD -> server.overworld();
                case NETHER -> server.getLevel(Level.NETHER);
                case END -> server.getLevel(Level.END);
            };
            StructureFinder finder = new StructureFinder(new WorldGenerator(MC, seed, dimension));
            for (StructureKind kind : StructureKind.in(dimension)) {
                if (kind == StructureKind.STRONGHOLD) {
                    compareStrongholds(level, finder, failures);
                } else {
                    compareStructure(level, finder, kind, failures);
                }
            }
        }
        return failures;
    }

    private static void compareStructure(ServerLevel level, StructureFinder finder, StructureKind kind, List<String> failures) {
        List<Holder<Structure>> structures = vanillaStructures(level, kind);
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        StructurePlacement placement = state.getPlacementsForStructure(structures.getFirst()).getFirst();
        int radius = kind == StructureKind.MINESHAFT ? 400 : kind == StructureKind.BURIED_TREASURE ? 2500 : 4000;
        int size = finder.regionBlocks(kind);

        int agree = 0, onlyCubeium = 0, onlyMinecraft = 0, present = 0;
        List<String> examples = new ArrayList<>();
        for (int rx = Math.floorDiv(-radius, size); rx <= Math.floorDiv(radius, size); rx++) {
            for (int rz = Math.floorDiv(-radius, size); rz <= Math.floorDiv(radius, size); rz++) {
                int[] attempt = attempt(kind, finder.world().seed(), rx, rz);
                if (attempt == null) {
                    continue;
                }
                boolean cubeium = !finder.find(kind, attempt[0], attempt[1], attempt[0] + 1, attempt[1] + 1).isEmpty();
                ChunkPos chunk = ChunkPos.containing(new BlockPos(attempt[0], 0, attempt[1]));
                // For chunks that are not generated yet Minecraft answers CHUNK_LOAD_NEEDED when a
                // start is possible (it never reports START_PRESENT without generating), and
                // START_NOT_PRESENT when it is not. Any variant being possible counts.
                // isStructureChunk adds what the presence check leaves out: exclusion zones such as
                // "no pillager outpost within 10 chunks of a village".
                boolean minecraft = false;
                for (Holder<Structure> structure : placement.isStructureChunk(state, chunk.x(), chunk.z()) ? structures : List.<Holder<Structure>>of()) {
                    StructureCheckResult result = level.structureManager().checkStructurePresence(chunk, structure.value(), placement, false);
                    if (result != StructureCheckResult.START_NOT_PRESENT) {
                        minecraft = true;
                        break;
                    }
                }
                if (minecraft && (kind == StructureKind.END_CITY || kind == StructureKind.END_CITY_SHIP)) {
                    Boolean ship = generatedShip(level, structures.getFirst(), chunk);
                    minecraft = ship != null && ship == (kind == StructureKind.END_CITY_SHIP);
                }
                if (minecraft) {
                    present++;
                }
                if (minecraft == cubeium) {
                    agree++;
                } else {
                    if (cubeium) onlyCubeium++; else onlyMinecraft++;
                    if (examples.size() < 3) examples.add((cubeium ? "cubeium-only " : "minecraft-only ") + attempt[0] + "," + attempt[1]);
                }
            }
        }
        int total = agree + onlyCubeium + onlyMinecraft;
        double agreement = total == 0 ? 1 : agree / (double) total;
        Cubeium.LOGGER.info("[GameTest] {}: {}/{} attempts agree ({} structures in Minecraft; {} cubeium-only, {} minecraft-only) {}",
                kind, agree, total, present, onlyCubeium, onlyMinecraft, examples);
        // Fortress/bastion share one structure set whose weighted choice Minecraft's presence check skips.
        boolean sharedSet = kind == StructureKind.NETHER_FORTRESS || kind == StructureKind.BASTION_REMNANT;
        if (!sharedSet && agreement < 0.95) {
            failures.add(kind + ": only " + Math.round(agreement * 100) + "% of " + total + " attempts agree " + examples);
        }
    }

    private static void compareStrongholds(ServerLevel level, StructureFinder finder, List<String> failures) {
        Holder<Structure> stronghold = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.STRONGHOLD);
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        ConcentricRingsStructurePlacement rings = (ConcentricRingsStructurePlacement) state.getPlacementsForStructure(stronghold).getFirst();
        Set<ChunkPos> minecraft = new HashSet<>(state.getRingPositionsFor(rings));
        int[] ours = finder.strongholds();
        int matches = 0;
        for (int i = 0; i < ours.length; i += 2) {
            if (minecraft.contains(ChunkPos.containing(new BlockPos(ours[i], 0, ours[i + 1])))) {
                matches++;
            }
        }
        Cubeium.LOGGER.info("[GameTest] STRONGHOLD: {}/{} positions match Minecraft's rings", matches, minecraft.size());
        if (matches != minecraft.size() || ours.length / 2 != minecraft.size()) {
            failures.add("STRONGHOLD: " + matches + "/" + minecraft.size() + " match");
        }
    }

    /**
     * Lets the server generate the chunk up to structure starts, exactly as in normal play, and
     * reports whether its end city has a ship (null: no end city starts there).
     */
    private static Boolean generatedShip(ServerLevel level, Holder<Structure> endCity, ChunkPos chunk) {
        ChunkAccess access = level.getChunk(chunk.x(), chunk.z(), ChunkStatus.STRUCTURE_STARTS, true);
        StructureStart start = access == null ? null : access.getStartForStructure(endCity.value());
        if (start == null || !start.isValid()) {
            return null;
        }
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof TemplateStructurePiece template && "ship".equals(templateName(template))) {
                return true;
            }
        }
        return false;
    }

    private static String templateName(TemplateStructurePiece piece) {
        try {
            Field field = TemplateStructurePiece.class.getDeclaredField("templateName");
            field.setAccessible(true);
            return (String) field.get(piece);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The generation attempt in a region straight from cubiomes, before any biome check. */
    private static int[] attempt(StructureKind kind, long seed, int regionX, int regionZ) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pos = Pos.allocate(arena);
            if (Cubiomes.getStructurePos(kind.cubiomesType(), MC, seed, regionX, regionZ, pos) == 0) {
                return null;
            }
            return new int[] {Pos.x(pos), Pos.z(pos)};
        }
    }

    /** The vanilla structures a kind stands for (all variants). */
    private static List<Holder<Structure>> vanillaStructures(ServerLevel level, StructureKind kind) {
        HolderGetter<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        if (kind == StructureKind.VILLAGE) {
            List<Holder<Structure>> villages = new ArrayList<>();
            registry.getOrThrow(StructureTags.VILLAGE).forEach(villages::add);
            return villages;
        }
        String prefix = switch (kind) {
            case PILLAGER_OUTPOST -> "PILLAGER_OUTPOST";
            case OCEAN_MONUMENT -> "OCEAN_MONUMENT";
            case WOODLAND_MANSION -> "WOODLAND_MANSION";
            case RUINED_PORTAL -> "RUINED_PORTAL_";
            case DESERT_PYRAMID -> "DESERT_PYRAMID";
            case JUNGLE_TEMPLE -> "JUNGLE_TEMPLE";
            case SWAMP_HUT -> "SWAMP_HUT";
            case IGLOO -> "IGLOO";
            case SHIPWRECK -> "SHIPWRECK";
            case OCEAN_RUIN -> "OCEAN_RUIN_";
            case BURIED_TREASURE -> "BURIED_TREASURE";
            case ANCIENT_CITY -> "ANCIENT_CITY";
            case TRAIL_RUINS -> "TRAIL_RUINS";
            case TRIAL_CHAMBERS -> "TRIAL_CHAMBERS";
            case ABANDONED_CAMP -> "ABANDONED_CAMP_";
            case MINESHAFT -> "MINESHAFT";
            case NETHER_FORTRESS -> "FORTRESS";
            case BASTION_REMNANT -> "BASTION_REMNANT";
            case RUINED_PORTAL_NETHER -> "RUINED_PORTAL_NETHER";
            case END_CITY, END_CITY_SHIP -> "END_CITY";
            default -> throw new IllegalArgumentException(kind.toString());
        };
        List<Holder<Structure>> holders = new ArrayList<>();
        for (Field field : BuiltinStructures.class.getFields()) {
            String name = field.getName();
            boolean matches = prefix.endsWith("_") ? name.startsWith(prefix) : name.equals(prefix) || name.startsWith(prefix + "_");
            if (kind == StructureKind.RUINED_PORTAL && name.equals("RUINED_PORTAL_NETHER")) {
                matches = false;
            }
            if (matches && Modifier.isStatic(field.getModifiers())) {
                try {
                    @SuppressWarnings("unchecked")
                    ResourceKey<Structure> key = (ResourceKey<Structure>) field.get(null);
                    holders.add(registry.getOrThrow(key));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        if (holders.isEmpty()) {
            throw new IllegalStateException("no vanilla structure for " + kind);
        }
        return holders;
    }

    // ---- UI ----

    /** Clicks the widget whose label uses a translation key, wherever it sits in the screen (tabs too). */
    private static void click(ClientGameTestContext context, String translationKey) {
        double[] position = context.computeOnClient(client -> {
            AbstractWidget widget = find(client.gui.screen().children(), translationKey);
            if (widget == null) {
                throw new AssertionError("no widget labelled " + translationKey);
            }
            double scale = client.getWindow().getGuiScale();
            return new double[] {(widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
        });
        context.getInput().setCursorPos(position[0], position[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }

    private static AbstractWidget find(List<? extends GuiEventListener> children, String translationKey) {
        for (GuiEventListener child : children) {
            if (child instanceof AbstractWidget widget && mentions(widget.getMessage(), translationKey)) {
                return widget;
            }
            if (child instanceof ContainerEventHandler container) {
                AbstractWidget found = find(container.children(), translationKey);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean mentions(Component component, String translationKey) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            if (translatable.getKey().equals(translationKey)) {
                return true;
            }
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component nested && mentions(nested, translationKey)) {
                    return true;
                }
            }
        }
        return component.getSiblings().stream().anyMatch(sibling -> mentions(sibling, translationKey));
    }

    private static void screenshots(ClientGameTestContext context) {
        context.setScreen(SeedMapScreen::new);
        context.waitTicks(80);
        context.takeScreenshot("cubeium-map");

        // Zoom out around the map center with the mouse wheel.
        double[] windowSize = context.computeOnClient(client -> new double[] {client.getWindow().getWidth(), client.getWindow().getHeight()});
        context.getInput().setCursorPos(windowSize[0] / 2, windowSize[1] / 3);
        context.getInput().scroll(-4);
        context.waitTicks(80);
        context.takeScreenshot("cubeium-map-zoomed-out");

        click(context, "cubeium.tab.structures");
        context.waitTicks(40);
        context.takeScreenshot("cubeium-structures-tab");
        click(context, "cubeium.tab.biomes");
        context.waitTicks(5);
        context.takeScreenshot("cubeium-biomes-tab");
        // Highlight one biome: the rest of the map dims and a banner offers "Show all".
        click(context, "cubeium.biomes.highlight");
        click(context, "biome.minecraft.badlands");
        context.waitTicks(40);
        context.takeScreenshot("cubeium-highlight");
        click(context, "cubeium.biomes.show_all");
        context.waitTicks(20);
        if (context.computeOnClient(client -> CubeiumConfig.get().highlightBiomes)) {
            throw new AssertionError("\"Show all\" did not turn the highlight off");
        }

        click(context, "cubeium.dimension.overworld");
        context.waitTicks(80);
        context.takeScreenshot("cubeium-nether");
        click(context, "cubeium.dimension.the_nether");
        click(context, "cubeium.dimension.the_end");
        context.waitTicks(40);

        click(context, "cubeium.settings.title");
        context.waitTicks(5);
        context.takeScreenshot("cubeium-settings");
        click(context, "cubeium.settings.dark_mode");
        click(context, "cubeium.settings.marker_labels");
        click(context, "gui.done");
        context.waitTicks(40);

        // Right-click menu in the middle of the map, with dark mode and marker labels on.
        double[] center = context.computeOnClient(client -> new double[] {client.getWindow().getWidth() / 2.0, client.getWindow().getHeight() / 3.0});
        context.getInput().setCursorPos(center[0], center[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(5);
        context.takeScreenshot("cubeium-dark-labels-menu");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(5);
        context.setScreen(() -> null);
    }
}
