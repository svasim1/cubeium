package cubeium.cubeium.world;

import dev.xpple.cubiomes.Cubiomes;

/** The three dimensions the map can show, with their cubiomes ids. */
public enum Dimension {
    OVERWORLD("overworld"),
    NETHER("the_nether"),
    END("the_end");

    private final String key;

    Dimension(String key) {
        this.key = key;
    }

    /** Vanilla dimension id path, e.g. "the_nether"; also used for translation keys. */
    public String key() {
        return key;
    }

    /** Converts coordinates from this dimension to {@code to}, as portals do; NaN when they don't correspond (the End). */
    public double scaleTo(Dimension to) {
        if (this == to) return 1;
        if (this == OVERWORLD && to == NETHER) return 1 / 8.0;
        if (this == NETHER && to == OVERWORLD) return 8;
        return Double.NaN;
    }

    public int cubiomesId() {
        return switch (this) {
            case OVERWORLD -> Cubiomes.DIM_OVERWORLD();
            case NETHER -> Cubiomes.DIM_NETHER();
            case END -> Cubiomes.DIM_END();
        };
    }
}
