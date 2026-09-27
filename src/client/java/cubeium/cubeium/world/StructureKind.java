package cubeium.cubeium.world;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntSupplier;

import dev.xpple.cubiomes.Cubiomes;

/** Structures the map can show, with their cubiomes type and dimension. */
public enum StructureKind {
    VILLAGE("village", Dimension.OVERWORLD, Cubiomes::Village),
    STRONGHOLD("stronghold", Dimension.OVERWORLD, Cubiomes::Stronghold),
    PILLAGER_OUTPOST("pillager_outpost", Dimension.OVERWORLD, Cubiomes::Outpost),
    OCEAN_MONUMENT("ocean_monument", Dimension.OVERWORLD, Cubiomes::Monument),
    WOODLAND_MANSION("woodland_mansion", Dimension.OVERWORLD, Cubiomes::Mansion),
    RUINED_PORTAL("ruined_portal", Dimension.OVERWORLD, Cubiomes::Ruined_Portal),
    DESERT_PYRAMID("desert_pyramid", Dimension.OVERWORLD, Cubiomes::Desert_Pyramid),
    JUNGLE_TEMPLE("jungle_temple", Dimension.OVERWORLD, Cubiomes::Jungle_Temple),
    SWAMP_HUT("swamp_hut", Dimension.OVERWORLD, Cubiomes::Swamp_Hut),
    IGLOO("igloo", Dimension.OVERWORLD, Cubiomes::Igloo),
    SHIPWRECK("shipwreck", Dimension.OVERWORLD, Cubiomes::Shipwreck),
    OCEAN_RUIN("ocean_ruin", Dimension.OVERWORLD, Cubiomes::Ocean_Ruin),
    BURIED_TREASURE("buried_treasure", Dimension.OVERWORLD, Cubiomes::Treasure),
    ANCIENT_CITY("ancient_city", Dimension.OVERWORLD, Cubiomes::Ancient_City),
    TRAIL_RUINS("trail_ruins", Dimension.OVERWORLD, Cubiomes::Trail_Ruins),
    TRIAL_CHAMBERS("trial_chambers", Dimension.OVERWORLD, Cubiomes::Trial_Chambers),
    ABANDONED_CAMP("abandoned_camp", Dimension.OVERWORLD, Cubiomes::Abandoned_Camp),
    MINESHAFT("mineshaft", Dimension.OVERWORLD, Cubiomes::Mineshaft),
    NETHER_FORTRESS("nether_fortress", Dimension.NETHER, Cubiomes::Fortress),
    BASTION_REMNANT("bastion_remnant", Dimension.NETHER, Cubiomes::Bastion),
    RUINED_PORTAL_NETHER("ruined_portal_nether", Dimension.NETHER, Cubiomes::Ruined_Portal_N),
    END_CITY("end_city", Dimension.END, Cubiomes::End_City),
    /** End cities that include a ship (elytra); the plain kind lists the ones without. */
    END_CITY_SHIP("end_city_ship", Dimension.END, Cubiomes::End_City),
    /** The small return gateways on the outer End islands (End Highlands). */
    END_GATEWAY("end_gateway", Dimension.END, Cubiomes::End_Gateway);

    private final String key;
    private final Dimension dimension;
    private final IntSupplier cubiomesType;

    StructureKind(String key, Dimension dimension, IntSupplier cubiomesType) {
        this.key = key;
        this.dimension = dimension;
        this.cubiomesType = cubiomesType;
    }

    /** Stable id used in settings and translation keys. */
    public String key() {
        return key;
    }

    public Dimension dimension() {
        return dimension;
    }

    public int cubiomesType() {
        return cubiomesType.getAsInt();
    }

    /** Placed per chunk rather than in large regions, so only searched down to a certain zoom. */
    public boolean isDense() {
        return this == MINESHAFT || this == BURIED_TREASURE || this == END_GATEWAY;
    }

    public static List<StructureKind> in(Dimension dimension) {
        return Arrays.stream(values()).filter(kind -> kind.dimension == dimension).toList();
    }
}
