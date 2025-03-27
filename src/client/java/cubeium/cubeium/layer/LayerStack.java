package cubeium.cubeium.layer;

public class LayerStack {
    private Layer topLayer;
    private final long seed;
    private final boolean largeBiomes;

    public LayerStack(long seed, boolean largeBiomes) {
        this.seed = seed;
        this.largeBiomes = largeBiomes;
        initializeLayers();
    }

    private void initializeLayers() {
        // Create the layer hierarchy exactly as in Cubiomes
        Layer continent = new ContinentLayer();

        // Zoom layers for initial continent shape
        Layer zoom1 = new ZoomLayer(continent);
        Layer zoom2 = new ZoomLayer(zoom1);

        // Basic terrain layers
        Layer land = new LandLayer(zoom2);
        Layer island = new IslandLayer(land);
        Layer snow = new SnowLayer(island);
        Layer cool = new CoolLayer(snow);
        Layer heat = new HeatLayer(cool);
        Layer special = new SpecialLayer(heat);
        Layer mushroom = new MushroomLayer(special);
        Layer deepOcean = new DeepOceanLayer(mushroom);

        // More zoom layers for biome detail
        Layer zoom3 = new ZoomLayer(deepOcean);
        Layer zoom4 = new ZoomLayer(zoom3);

        // Biome layers
        Layer biome = new BiomeLayer(zoom4);
        Layer noise = new NoiseLayer(biome);
        Layer edge = new BiomeEdgeLayer(noise);

        // Final detail layers
        Layer zoom5 = new ZoomLayer(edge);
        Layer river = new RiverLayer(zoom5);
        Layer shore = new ShoreLayer(river);
        Layer oceanTemp = new OceanTempLayer(shore);
        Layer oceanMix = new OceanMixLayer(shore, oceanTemp);

        // Final smoothing
        Layer voronoi = new VoronoiLayer(oceanMix);

        // Set up parent-child relationships
        continent.child = zoom1;
        zoom1.child = zoom2;
        zoom2.child = land;
        land.child = island;
        island.child = snow;
        snow.child = cool;
        cool.child = heat;
        heat.child = special;
        special.child = mushroom;
        mushroom.child = deepOcean;
        deepOcean.child = zoom3;
        zoom3.child = zoom4;
        zoom4.child = biome;
        biome.child = noise;
        noise.child = edge;
        edge.child = zoom5;
        zoom5.child = river;
        river.child = shore;
        shore.child = oceanTemp;
        oceanTemp.child = oceanMix;
        oceanMix.child = voronoi;

        // Set up parent references
        zoom1.parent = continent;
        zoom2.parent = zoom1;
        land.parent = zoom2;
        island.parent = land;
        snow.parent = island;
        cool.parent = snow;
        heat.parent = cool;
        special.parent = heat;
        mushroom.parent = special;
        deepOcean.parent = mushroom;
        zoom3.parent = deepOcean;
        zoom4.parent = zoom3;
        biome.parent = zoom4;
        noise.parent = biome;
        edge.parent = noise;
        zoom5.parent = edge;
        river.parent = zoom5;
        shore.parent = river;
        oceanTemp.parent = shore;
        oceanMix.parent = shore;
        voronoi.parent = oceanMix;

        // Set the top layer
        topLayer = voronoi;

        // Initialize all layers with the seed
        continent.setSeed(seed);
    }

    public int[] getBiomeMap(int x, int z, int width, int height) {
        int[] out = new int[width * height];
        topLayer.getMap(out, x, z, width, height);
        return out;
    }
}