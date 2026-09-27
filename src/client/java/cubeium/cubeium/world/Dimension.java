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

    public int cubiomesId() {
        return switch (this) {
            case OVERWORLD -> Cubiomes.DIM_OVERWORLD();
            case NETHER -> Cubiomes.DIM_NETHER();
            case END -> Cubiomes.DIM_END();
        };
    }
}
