package com.example.worldgen.biome;

import java.util.function.Consumer;

/**
 * Port of {@code net.minecraft.world.level.biome.OverworldBiomeBuilder} (Minecraft Java Edition 26.3).
 *
 * <p>Produces the overworld multi-noise parameter table ({@code addBiomes}). Every table, threshold
 * and method body was read from the 26.3 client jar (javap plus a Fernflower decompile cross-checked
 * against javap constants), and the produced list is compared entry by entry with the output of the
 * real vanilla class run as an oracle ({@code OverworldBiomeBuilderTest}).
 *
 * <p>Not ported here, because they belong to the density-function engine:
 * <ul>
 *   <li>{@code addDebugBiomes}, only reached when {@code SharedConstants.debugGenerateSquareTerrainWithoutNoise}
 *       is set (false in a normal game); it needs {@code TerrainProvider} splines.</li>
 *   <li>{@code spawnTarget}, which builds {@code SpawnTargetPoint}s keyed by density-function holders:
 *       temperature/vegetation/erosion = {@code FULL_RANGE}, continents = {@code span(inland, FULL_RANGE)},
 *       weirdness {@code span(-1.0F, -0.16F)} for the first point and {@code span(0.16F, 1.0F)} for the second.</li>
 *   <li>{@code deepDarkRegion(erosion, depth)}, which returns the density function graph
 *       {@code min(sub(constant(-0.225F), erosion), max(sub(depth, constant(0.9F)), constant(0.0F)))}
 *       built from {@code BinaryFunction} SUB/MIN/MAX nodes.</li>
 * </ul>
 */
public final class OverworldBiomeBuilder {
    private static final float VALLEY_SIZE = 0.05F;
    private static final float LOW_START = 0.26666668F;
    public static final float HIGH_START = 0.4F;
    private static final float HIGH_END = 0.93333334F;
    private static final float PEAK_SIZE = 0.1F;
    public static final float PEAK_START = 0.56666666F;
    private static final float PEAK_END = 0.7666667F;
    public static final float NEAR_INLAND_START = -0.11F;
    public static final float MID_INLAND_START = 0.03F;
    public static final float FAR_INLAND_START = 0.3F;
    public static final float EROSION_INDEX_1_START = -0.78F;
    public static final float EROSION_INDEX_2_START = -0.375F;
    private static final float EROSION_DEEP_DARK_DRYNESS_THRESHOLD = -0.225F;
    private static final float DEPTH_DEEP_DARK_DRYNESS_THRESHOLD = 0.9F;

    private final Climate.Parameter FULL_RANGE = Climate.Parameter.span(-1.0F, 1.0F);
    private final Climate.Parameter[] temperatures = new Climate.Parameter[]{
        Climate.Parameter.span(-1.0F, -0.45F), Climate.Parameter.span(-0.45F, -0.15F),
        Climate.Parameter.span(-0.15F, 0.2F), Climate.Parameter.span(0.2F, 0.55F),
        Climate.Parameter.span(0.55F, 1.0F)};
    private final Climate.Parameter[] humidities = new Climate.Parameter[]{
        Climate.Parameter.span(-1.0F, -0.35F), Climate.Parameter.span(-0.35F, -0.1F),
        Climate.Parameter.span(-0.1F, 0.1F), Climate.Parameter.span(0.1F, 0.3F),
        Climate.Parameter.span(0.3F, 1.0F)};
    private final Climate.Parameter[] erosions = new Climate.Parameter[]{
        Climate.Parameter.span(-1.0F, -0.78F), Climate.Parameter.span(-0.78F, -0.375F),
        Climate.Parameter.span(-0.375F, -0.2225F), Climate.Parameter.span(-0.2225F, 0.05F),
        Climate.Parameter.span(0.05F, 0.45F), Climate.Parameter.span(0.45F, 0.55F),
        Climate.Parameter.span(0.55F, 1.0F)};
    private final Climate.Parameter FROZEN_RANGE;
    private final Climate.Parameter UNFROZEN_RANGE;
    private final Climate.Parameter mushroomFieldsContinentalness;
    private final Climate.Parameter deepOceanContinentalness;
    private final Climate.Parameter oceanContinentalness;
    private final Climate.Parameter coastContinentalness;
    private final Climate.Parameter inlandContinentalness;
    private final Climate.Parameter nearInlandContinentalness;
    private final Climate.Parameter midInlandContinentalness;
    private final Climate.Parameter farInlandContinentalness;
    private final String[][] OCEANS;
    private final String[][] MIDDLE_BIOMES;
    private final String[][] MIDDLE_BIOMES_VARIANT;
    private final String[][] PLATEAU_BIOMES;
    private final String[][] PLATEAU_BIOMES_VARIANT;
    private final String[][] SHATTERED_BIOMES;

    public OverworldBiomeBuilder() {
        this.FROZEN_RANGE = this.temperatures[0];
        this.UNFROZEN_RANGE = Climate.Parameter.span(this.temperatures[1], this.temperatures[4]);
        this.mushroomFieldsContinentalness = Climate.Parameter.span(-1.2F, -1.05F);
        this.deepOceanContinentalness = Climate.Parameter.span(-1.05F, -0.455F);
        this.oceanContinentalness = Climate.Parameter.span(-0.455F, -0.19F);
        this.coastContinentalness = Climate.Parameter.span(-0.19F, -0.11F);
        this.inlandContinentalness = Climate.Parameter.span(-0.11F, 0.55F);
        this.nearInlandContinentalness = Climate.Parameter.span(-0.11F, 0.03F);
        this.midInlandContinentalness = Climate.Parameter.span(0.03F, 0.3F);
        this.farInlandContinentalness = Climate.Parameter.span(0.3F, 1.0F);
        this.OCEANS = new String[][]{
            {Biomes.DEEP_FROZEN_OCEAN, Biomes.DEEP_COLD_OCEAN, Biomes.DEEP_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN,
                Biomes.WARM_OCEAN},
            {Biomes.FROZEN_OCEAN, Biomes.COLD_OCEAN, Biomes.OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.WARM_OCEAN}};
        this.MIDDLE_BIOMES = new String[][]{
            {Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.TAIGA},
            {Biomes.PLAINS, Biomes.PLAINS, Biomes.FOREST, Biomes.TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA},
            {Biomes.FLOWER_FOREST, Biomes.PLAINS, Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.DARK_FOREST},
            {Biomes.SAVANNA, Biomes.SAVANNA, Biomes.FOREST, Biomes.JUNGLE, Biomes.JUNGLE},
            {Biomes.DESERT, Biomes.DESERT, Biomes.DESERT, Biomes.DESERT, Biomes.DESERT}};
        this.MIDDLE_BIOMES_VARIANT = new String[][]{
            {Biomes.ICE_SPIKES, null, Biomes.SNOWY_TAIGA, null, null},
            {Biomes.DAPPLED_FOREST, null, null, null, Biomes.OLD_GROWTH_PINE_TAIGA},
            {Biomes.SUNFLOWER_PLAINS, null, null, Biomes.OLD_GROWTH_BIRCH_FOREST, null},
            {null, null, Biomes.PLAINS, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE},
            {null, null, null, null, null}};
        this.PLATEAU_BIOMES = new String[][]{
            {Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.SNOWY_TAIGA},
            {Biomes.MEADOW, Biomes.MEADOW, Biomes.FOREST, Biomes.TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA},
            {Biomes.MEADOW, Biomes.MEADOW, Biomes.MEADOW, Biomes.MEADOW, Biomes.PALE_GARDEN},
            {Biomes.SAVANNA_PLATEAU, Biomes.SAVANNA_PLATEAU, Biomes.FOREST, Biomes.FOREST, Biomes.JUNGLE},
            {Biomes.BADLANDS, Biomes.BADLANDS, Biomes.BADLANDS, Biomes.WOODED_BADLANDS, Biomes.WOODED_BADLANDS}};
        this.PLATEAU_BIOMES_VARIANT = new String[][]{
            {Biomes.ICE_SPIKES, null, null, null, null},
            {Biomes.CHERRY_GROVE, null, Biomes.MEADOW, Biomes.MEADOW, Biomes.OLD_GROWTH_PINE_TAIGA},
            {Biomes.CHERRY_GROVE, Biomes.CHERRY_GROVE, Biomes.FOREST, Biomes.BIRCH_FOREST, null},
            {null, null, null, null, null},
            {Biomes.ERODED_BADLANDS, Biomes.ERODED_BADLANDS, null, null, null}};
        this.SHATTERED_BIOMES = new String[][]{
            {Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_HILLS,
                Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_FOREST},
            {Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_HILLS,
                Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_FOREST},
            {Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST,
                Biomes.WINDSWEPT_FOREST},
            {null, null, null, null, null},
            {null, null, null, null, null}};
    }

    /** Vanilla {@code addBiomes} (release path: off-coast, inland, underground). */
    public void addBiomes(Consumer<Climate.Entry<String>> biomes) {
        this.addOffCoastBiomes(biomes);
        this.addInlandBiomes(biomes);
        this.addUndergroundBiomes(biomes);
    }

    private void addOffCoastBiomes(Consumer<Climate.Entry<String>> biomes) {
        this.addSurfaceBiome(biomes, this.FULL_RANGE, this.FULL_RANGE, this.mushroomFieldsContinentalness,
            this.FULL_RANGE, this.FULL_RANGE, 0.0F, Biomes.MUSHROOM_FIELDS);
        for (int temperatureIndex = 0; temperatureIndex < this.temperatures.length; ++temperatureIndex) {
            Climate.Parameter temperature = this.temperatures[temperatureIndex];
            this.addSurfaceBiome(biomes, temperature, this.FULL_RANGE, this.deepOceanContinentalness,
                this.FULL_RANGE, this.FULL_RANGE, 0.0F, this.OCEANS[0][temperatureIndex]);
            this.addSurfaceBiome(biomes, temperature, this.FULL_RANGE, this.oceanContinentalness,
                this.FULL_RANGE, this.FULL_RANGE, 0.0F, this.OCEANS[1][temperatureIndex]);
        }
    }

    private void addInlandBiomes(Consumer<Climate.Entry<String>> biomes) {
        this.addMidSlice(biomes, Climate.Parameter.span(-1.0F, -0.93333334F));
        this.addHighSlice(biomes, Climate.Parameter.span(-0.93333334F, -0.7666667F));
        this.addPeaks(biomes, Climate.Parameter.span(-0.7666667F, -0.56666666F));
        this.addHighSlice(biomes, Climate.Parameter.span(-0.56666666F, -0.4F));
        this.addMidSlice(biomes, Climate.Parameter.span(-0.4F, -0.26666668F));
        this.addLowSlice(biomes, Climate.Parameter.span(-0.26666668F, -0.05F));
        this.addValleys(biomes, Climate.Parameter.span(-0.05F, 0.05F));
        this.addLowSlice(biomes, Climate.Parameter.span(0.05F, 0.26666668F));
        this.addMidSlice(biomes, Climate.Parameter.span(0.26666668F, 0.4F));
        this.addHighSlice(biomes, Climate.Parameter.span(0.4F, 0.56666666F));
        this.addPeaks(biomes, Climate.Parameter.span(0.56666666F, 0.7666667F));
        this.addHighSlice(biomes, Climate.Parameter.span(0.7666667F, 0.93333334F));
        this.addMidSlice(biomes, Climate.Parameter.span(0.93333334F, 1.0F));
    }

    private void addPeaks(Consumer<Climate.Entry<String>> biomes, Climate.Parameter weirdness) {
        for (int temperatureIndex = 0; temperatureIndex < this.temperatures.length; ++temperatureIndex) {
            Climate.Parameter temperature = this.temperatures[temperatureIndex];
            for (int humidityIndex = 0; humidityIndex < this.humidities.length; ++humidityIndex) {
                Climate.Parameter humidity = this.humidities[humidityIndex];
                String middleBiome = this.pickMiddleBiome(temperatureIndex, humidityIndex, weirdness);
                String middleBiomeOrBadlandsIfHot =
                    this.pickMiddleBiomeOrBadlandsIfHot(temperatureIndex, humidityIndex, weirdness);
                String middleBiomeOrBadlandsIfHotOrSlopeIfCold =
                    this.pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(temperatureIndex, humidityIndex, weirdness);
                String plateauBiome = this.pickPlateauBiome(temperatureIndex, humidityIndex, weirdness);
                String shatteredBiome = this.pickShatteredBiome(temperatureIndex, humidityIndex, weirdness);
                String shatteredBiomeOrWindsweptSavanna =
                    this.maybePickWindsweptSavannaBiome(temperatureIndex, humidityIndex, weirdness, shatteredBiome);
                String peakBiome = this.pickPeakBiome(temperatureIndex, humidityIndex, weirdness);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
                    this.erosions[0], weirdness, 0.0F, peakBiome);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness),
                    this.erosions[1], weirdness, 0.0F, middleBiomeOrBadlandsIfHotOrSlopeIfCold);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[1], weirdness, 0.0F, peakBiome);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness),
                    Climate.Parameter.span(this.erosions[2], this.erosions[3]), weirdness, 0.0F, middleBiome);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[2], weirdness, 0.0F, plateauBiome);
                this.addSurfaceBiome(biomes, temperature, humidity, this.midInlandContinentalness,
                    this.erosions[3], weirdness, 0.0F, middleBiomeOrBadlandsIfHot);
                this.addSurfaceBiome(biomes, temperature, humidity, this.farInlandContinentalness,
                    this.erosions[3], weirdness, 0.0F, plateauBiome);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
                    this.erosions[4], weirdness, 0.0F, middleBiome);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness),
                    this.erosions[5], weirdness, 0.0F, shatteredBiomeOrWindsweptSavanna);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[5], weirdness, 0.0F, shatteredBiome);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
                    this.erosions[6], weirdness, 0.0F, middleBiome);
            }
        }
    }

    private void addHighSlice(Consumer<Climate.Entry<String>> biomes, Climate.Parameter weirdness) {
        for (int temperatureIndex = 0; temperatureIndex < this.temperatures.length; ++temperatureIndex) {
            Climate.Parameter temperature = this.temperatures[temperatureIndex];
            for (int humidityIndex = 0; humidityIndex < this.humidities.length; ++humidityIndex) {
                Climate.Parameter humidity = this.humidities[humidityIndex];
                String middleBiome = this.pickMiddleBiome(temperatureIndex, humidityIndex, weirdness);
                String middleBiomeOrBadlandsIfHot =
                    this.pickMiddleBiomeOrBadlandsIfHot(temperatureIndex, humidityIndex, weirdness);
                String middleBiomeOrBadlandsIfHotOrSlopeIfCold =
                    this.pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(temperatureIndex, humidityIndex, weirdness);
                String plateauBiome = this.pickPlateauBiome(temperatureIndex, humidityIndex, weirdness);
                String shatteredBiome = this.pickShatteredBiome(temperatureIndex, humidityIndex, weirdness);
                String middleBiomeOrWindsweptSavanna =
                    this.maybePickWindsweptSavannaBiome(temperatureIndex, humidityIndex, weirdness, middleBiome);
                String slopeBiome = this.pickSlopeBiome(temperatureIndex, humidityIndex, weirdness);
                String peakBiome = this.pickPeakBiome(temperatureIndex, humidityIndex, weirdness);
                this.addSurfaceBiome(biomes, temperature, humidity, this.coastContinentalness,
                    Climate.Parameter.span(this.erosions[0], this.erosions[1]), weirdness, 0.0F, middleBiome);
                this.addSurfaceBiome(biomes, temperature, humidity, this.nearInlandContinentalness,
                    this.erosions[0], weirdness, 0.0F, slopeBiome);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[0], weirdness, 0.0F, peakBiome);
                this.addSurfaceBiome(biomes, temperature, humidity, this.nearInlandContinentalness,
                    this.erosions[1], weirdness, 0.0F, middleBiomeOrBadlandsIfHotOrSlopeIfCold);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[1], weirdness, 0.0F, slopeBiome);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness),
                    Climate.Parameter.span(this.erosions[2], this.erosions[3]), weirdness, 0.0F, middleBiome);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[2], weirdness, 0.0F, plateauBiome);
                this.addSurfaceBiome(biomes, temperature, humidity, this.midInlandContinentalness,
                    this.erosions[3], weirdness, 0.0F, middleBiomeOrBadlandsIfHot);
                this.addSurfaceBiome(biomes, temperature, humidity, this.farInlandContinentalness,
                    this.erosions[3], weirdness, 0.0F, plateauBiome);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
                    this.erosions[4], weirdness, 0.0F, middleBiome);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness),
                    this.erosions[5], weirdness, 0.0F, middleBiomeOrWindsweptSavanna);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[5], weirdness, 0.0F, shatteredBiome);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
                    this.erosions[6], weirdness, 0.0F, middleBiome);
            }
        }
    }

    private void addMidSlice(Consumer<Climate.Entry<String>> biomes, Climate.Parameter weirdness) {
        this.addSurfaceBiome(biomes, this.FULL_RANGE, this.FULL_RANGE, this.coastContinentalness,
            Climate.Parameter.span(this.erosions[0], this.erosions[2]), weirdness, 0.0F, Biomes.STONY_SHORE);
        this.addSurfaceBiome(biomes, Climate.Parameter.span(this.temperatures[1], this.temperatures[2]),
            this.FULL_RANGE, Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
            this.erosions[6], weirdness, 0.0F, Biomes.SWAMP);
        this.addSurfaceBiome(biomes, Climate.Parameter.span(this.temperatures[3], this.temperatures[4]),
            this.FULL_RANGE, Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
            this.erosions[6], weirdness, 0.0F, Biomes.MANGROVE_SWAMP);
        for (int temperatureIndex = 0; temperatureIndex < this.temperatures.length; ++temperatureIndex) {
            Climate.Parameter temperature = this.temperatures[temperatureIndex];
            for (int humidityIndex = 0; humidityIndex < this.humidities.length; ++humidityIndex) {
                Climate.Parameter humidity = this.humidities[humidityIndex];
                String middleBiome = this.pickMiddleBiome(temperatureIndex, humidityIndex, weirdness);
                String middleBiomeOrBadlandsIfHot =
                    this.pickMiddleBiomeOrBadlandsIfHot(temperatureIndex, humidityIndex, weirdness);
                String middleBiomeOrBadlandsIfHotOrSlopeIfCold =
                    this.pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(temperatureIndex, humidityIndex, weirdness);
                String shatteredBiome = this.pickShatteredBiome(temperatureIndex, humidityIndex, weirdness);
                String plateauBiome = this.pickPlateauBiome(temperatureIndex, humidityIndex, weirdness);
                String beachBiome = this.pickBeachBiome(temperatureIndex, humidityIndex);
                String middleBiomeOrWindsweptSavanna =
                    this.maybePickWindsweptSavannaBiome(temperatureIndex, humidityIndex, weirdness, middleBiome);
                String shatteredCoastBiome = this.pickShatteredCoastBiome(temperatureIndex, humidityIndex, weirdness);
                String slopeBiome = this.pickSlopeBiome(temperatureIndex, humidityIndex, weirdness);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[0], weirdness, 0.0F, slopeBiome);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.nearInlandContinentalness, this.midInlandContinentalness),
                    this.erosions[1], weirdness, 0.0F, middleBiomeOrBadlandsIfHotOrSlopeIfCold);
                this.addSurfaceBiome(biomes, temperature, humidity, this.farInlandContinentalness,
                    this.erosions[1], weirdness, 0.0F, temperatureIndex == 0 ? slopeBiome : plateauBiome);
                this.addSurfaceBiome(biomes, temperature, humidity, this.nearInlandContinentalness,
                    this.erosions[2], weirdness, 0.0F, middleBiome);
                this.addSurfaceBiome(biomes, temperature, humidity, this.midInlandContinentalness,
                    this.erosions[2], weirdness, 0.0F, middleBiomeOrBadlandsIfHot);
                this.addSurfaceBiome(biomes, temperature, humidity, this.farInlandContinentalness,
                    this.erosions[2], weirdness, 0.0F, plateauBiome);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness),
                    this.erosions[3], weirdness, 0.0F, middleBiome);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[3], weirdness, 0.0F, middleBiomeOrBadlandsIfHot);
                if (weirdness.max() < 0L) {
                    this.addSurfaceBiome(biomes, temperature, humidity, this.coastContinentalness,
                        this.erosions[4], weirdness, 0.0F, beachBiome);
                    this.addSurfaceBiome(biomes, temperature, humidity,
                        Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
                        this.erosions[4], weirdness, 0.0F, middleBiome);
                } else {
                    this.addSurfaceBiome(biomes, temperature, humidity,
                        Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
                        this.erosions[4], weirdness, 0.0F, middleBiome);
                }
                this.addSurfaceBiome(biomes, temperature, humidity, this.coastContinentalness,
                    this.erosions[5], weirdness, 0.0F, shatteredCoastBiome);
                this.addSurfaceBiome(biomes, temperature, humidity, this.nearInlandContinentalness,
                    this.erosions[5], weirdness, 0.0F, middleBiomeOrWindsweptSavanna);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[5], weirdness, 0.0F, shatteredBiome);
                if (weirdness.max() < 0L) {
                    this.addSurfaceBiome(biomes, temperature, humidity, this.coastContinentalness,
                        this.erosions[6], weirdness, 0.0F, beachBiome);
                } else {
                    this.addSurfaceBiome(biomes, temperature, humidity, this.coastContinentalness,
                        this.erosions[6], weirdness, 0.0F, middleBiome);
                }
                if (temperatureIndex == 0) {
                    this.addSurfaceBiome(biomes, temperature, humidity,
                        Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
                        this.erosions[6], weirdness, 0.0F, middleBiome);
                }
            }
        }
    }

    private void addLowSlice(Consumer<Climate.Entry<String>> biomes, Climate.Parameter weirdness) {
        this.addSurfaceBiome(biomes, this.FULL_RANGE, this.FULL_RANGE, this.coastContinentalness,
            Climate.Parameter.span(this.erosions[0], this.erosions[2]), weirdness, 0.0F, Biomes.STONY_SHORE);
        this.addSurfaceBiome(biomes, Climate.Parameter.span(this.temperatures[1], this.temperatures[2]),
            this.FULL_RANGE, Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
            this.erosions[6], weirdness, 0.0F, Biomes.SWAMP);
        this.addSurfaceBiome(biomes, Climate.Parameter.span(this.temperatures[3], this.temperatures[4]),
            this.FULL_RANGE, Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
            this.erosions[6], weirdness, 0.0F, Biomes.MANGROVE_SWAMP);
        for (int temperatureIndex = 0; temperatureIndex < this.temperatures.length; ++temperatureIndex) {
            Climate.Parameter temperature = this.temperatures[temperatureIndex];
            for (int humidityIndex = 0; humidityIndex < this.humidities.length; ++humidityIndex) {
                Climate.Parameter humidity = this.humidities[humidityIndex];
                String middleBiome = this.pickMiddleBiome(temperatureIndex, humidityIndex, weirdness);
                String middleBiomeOrBadlandsIfHot =
                    this.pickMiddleBiomeOrBadlandsIfHot(temperatureIndex, humidityIndex, weirdness);
                String middleBiomeOrBadlandsIfHotOrSlopeIfCold =
                    this.pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(temperatureIndex, humidityIndex, weirdness);
                String beachBiome = this.pickBeachBiome(temperatureIndex, humidityIndex);
                String middleBiomeOrWindsweptSavanna =
                    this.maybePickWindsweptSavannaBiome(temperatureIndex, humidityIndex, weirdness, middleBiome);
                String shatteredCoastBiome = this.pickShatteredCoastBiome(temperatureIndex, humidityIndex, weirdness);
                this.addSurfaceBiome(biomes, temperature, humidity, this.nearInlandContinentalness,
                    Climate.Parameter.span(this.erosions[0], this.erosions[1]), weirdness, 0.0F,
                    middleBiomeOrBadlandsIfHot);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    Climate.Parameter.span(this.erosions[0], this.erosions[1]), weirdness, 0.0F,
                    middleBiomeOrBadlandsIfHotOrSlopeIfCold);
                this.addSurfaceBiome(biomes, temperature, humidity, this.nearInlandContinentalness,
                    Climate.Parameter.span(this.erosions[2], this.erosions[3]), weirdness, 0.0F, middleBiome);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    Climate.Parameter.span(this.erosions[2], this.erosions[3]), weirdness, 0.0F,
                    middleBiomeOrBadlandsIfHot);
                this.addSurfaceBiome(biomes, temperature, humidity, this.coastContinentalness,
                    Climate.Parameter.span(this.erosions[3], this.erosions[4]), weirdness, 0.0F, beachBiome);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[4], weirdness, 0.0F, middleBiome);
                this.addSurfaceBiome(biomes, temperature, humidity, this.coastContinentalness,
                    this.erosions[5], weirdness, 0.0F, shatteredCoastBiome);
                this.addSurfaceBiome(biomes, temperature, humidity, this.nearInlandContinentalness,
                    this.erosions[5], weirdness, 0.0F, middleBiomeOrWindsweptSavanna);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[5], weirdness, 0.0F, middleBiome);
                this.addSurfaceBiome(biomes, temperature, humidity, this.coastContinentalness,
                    this.erosions[6], weirdness, 0.0F, beachBiome);
                if (temperatureIndex == 0) {
                    this.addSurfaceBiome(biomes, temperature, humidity,
                        Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
                        this.erosions[6], weirdness, 0.0F, middleBiome);
                }
            }
        }
    }

    private void addValleys(Consumer<Climate.Entry<String>> biomes, Climate.Parameter weirdness) {
        this.addSurfaceBiome(biomes, this.FROZEN_RANGE, this.FULL_RANGE, this.coastContinentalness,
            Climate.Parameter.span(this.erosions[0], this.erosions[1]), weirdness, 0.0F,
            weirdness.max() < 0L ? Biomes.STONY_SHORE : Biomes.FROZEN_RIVER);
        this.addSurfaceBiome(biomes, this.UNFROZEN_RANGE, this.FULL_RANGE, this.coastContinentalness,
            Climate.Parameter.span(this.erosions[0], this.erosions[1]), weirdness, 0.0F,
            weirdness.max() < 0L ? Biomes.STONY_SHORE : Biomes.RIVER);
        this.addSurfaceBiome(biomes, this.FROZEN_RANGE, this.FULL_RANGE, this.nearInlandContinentalness,
            Climate.Parameter.span(this.erosions[0], this.erosions[1]), weirdness, 0.0F, Biomes.FROZEN_RIVER);
        this.addSurfaceBiome(biomes, this.UNFROZEN_RANGE, this.FULL_RANGE, this.nearInlandContinentalness,
            Climate.Parameter.span(this.erosions[0], this.erosions[1]), weirdness, 0.0F, Biomes.RIVER);
        this.addSurfaceBiome(biomes, this.FROZEN_RANGE, this.FULL_RANGE,
            Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
            Climate.Parameter.span(this.erosions[2], this.erosions[5]), weirdness, 0.0F, Biomes.FROZEN_RIVER);
        this.addSurfaceBiome(biomes, this.UNFROZEN_RANGE, this.FULL_RANGE,
            Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
            Climate.Parameter.span(this.erosions[2], this.erosions[5]), weirdness, 0.0F, Biomes.RIVER);
        this.addSurfaceBiome(biomes, this.FROZEN_RANGE, this.FULL_RANGE, this.coastContinentalness,
            this.erosions[6], weirdness, 0.0F, Biomes.FROZEN_RIVER);
        this.addSurfaceBiome(biomes, this.UNFROZEN_RANGE, this.FULL_RANGE, this.coastContinentalness,
            this.erosions[6], weirdness, 0.0F, Biomes.RIVER);
        this.addSurfaceBiome(biomes, Climate.Parameter.span(this.temperatures[1], this.temperatures[2]),
            this.FULL_RANGE, Climate.Parameter.span(this.inlandContinentalness, this.farInlandContinentalness),
            this.erosions[6], weirdness, 0.0F, Biomes.SWAMP);
        this.addSurfaceBiome(biomes, Climate.Parameter.span(this.temperatures[3], this.temperatures[4]),
            this.FULL_RANGE, Climate.Parameter.span(this.inlandContinentalness, this.farInlandContinentalness),
            this.erosions[6], weirdness, 0.0F, Biomes.MANGROVE_SWAMP);
        this.addSurfaceBiome(biomes, this.FROZEN_RANGE, this.FULL_RANGE,
            Climate.Parameter.span(this.inlandContinentalness, this.farInlandContinentalness),
            this.erosions[6], weirdness, 0.0F, Biomes.FROZEN_RIVER);
        for (int temperatureIndex = 0; temperatureIndex < this.temperatures.length; ++temperatureIndex) {
            Climate.Parameter temperature = this.temperatures[temperatureIndex];
            for (int humidityIndex = 0; humidityIndex < this.humidities.length; ++humidityIndex) {
                Climate.Parameter humidity = this.humidities[humidityIndex];
                String middleBiomeOrBadlandsIfHot =
                    this.pickMiddleBiomeOrBadlandsIfHot(temperatureIndex, humidityIndex, weirdness);
                this.addSurfaceBiome(biomes, temperature, humidity,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    Climate.Parameter.span(this.erosions[0], this.erosions[1]), weirdness, 0.0F,
                    middleBiomeOrBadlandsIfHot);
            }
        }
    }

    private void addUndergroundBiomes(Consumer<Climate.Entry<String>> biomes) {
        this.addUndergroundBiome(biomes, this.FULL_RANGE, this.FULL_RANGE, Climate.Parameter.span(0.8F, 1.0F),
            this.FULL_RANGE, this.FULL_RANGE, 0.0F, Biomes.DRIPSTONE_CAVES);
        this.addUndergroundBiome(biomes, this.FULL_RANGE, Climate.Parameter.span(0.7F, 1.0F), this.FULL_RANGE,
            this.FULL_RANGE, this.FULL_RANGE, 0.0F, Biomes.LUSH_CAVES);
        this.addUndergroundBiome(biomes, this.FULL_RANGE, this.FULL_RANGE,
            Climate.Parameter.span(this.coastContinentalness, this.inlandContinentalness),
            Climate.Parameter.span(this.erosions[5], this.erosions[6]), Climate.Parameter.span(-1.1F, -0.85F), 0.0F,
            Biomes.SULFUR_CAVES);
        this.addBottomBiome(biomes, this.FULL_RANGE, this.FULL_RANGE, this.FULL_RANGE,
            Climate.Parameter.span(this.erosions[0], this.erosions[1]), this.FULL_RANGE, 0.0F, Biomes.DEEP_DARK);
    }

    private String pickMiddleBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
        if (weirdness.max() < 0L) {
            return this.MIDDLE_BIOMES[temperatureIndex][humidityIndex];
        }
        String variant = this.MIDDLE_BIOMES_VARIANT[temperatureIndex][humidityIndex];
        return variant == null ? this.MIDDLE_BIOMES[temperatureIndex][humidityIndex] : variant;
    }

    private String pickMiddleBiomeOrBadlandsIfHot(int temperatureIndex, int humidityIndex,
                                                  Climate.Parameter weirdness) {
        return temperatureIndex == 4 ? this.pickBadlandsBiome(humidityIndex, weirdness)
            : this.pickMiddleBiome(temperatureIndex, humidityIndex, weirdness);
    }

    private String pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(int temperatureIndex, int humidityIndex,
                                                               Climate.Parameter weirdness) {
        return temperatureIndex == 0 ? this.pickSlopeBiome(temperatureIndex, humidityIndex, weirdness)
            : this.pickMiddleBiomeOrBadlandsIfHot(temperatureIndex, humidityIndex, weirdness);
    }

    private String maybePickWindsweptSavannaBiome(int temperatureIndex, int humidityIndex,
                                                  Climate.Parameter weirdness, String underlyingBiome) {
        return temperatureIndex > 1 && humidityIndex < 4 && weirdness.max() >= 0L
            ? Biomes.WINDSWEPT_SAVANNA : underlyingBiome;
    }

    private String pickShatteredCoastBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
        String beachOrMiddleBiome = weirdness.max() >= 0L
            ? this.pickMiddleBiome(temperatureIndex, humidityIndex, weirdness)
            : this.pickBeachBiome(temperatureIndex, humidityIndex);
        return this.maybePickWindsweptSavannaBiome(temperatureIndex, humidityIndex, weirdness, beachOrMiddleBiome);
    }

    private String pickBeachBiome(int temperatureIndex, int humidityIndex) {
        if (temperatureIndex == 0) {
            return Biomes.SNOWY_BEACH;
        }
        return temperatureIndex == 4 ? Biomes.DESERT : Biomes.BEACH;
    }

    private String pickBadlandsBiome(int humidityIndex, Climate.Parameter weirdness) {
        if (humidityIndex < 2) {
            return weirdness.max() < 0L ? Biomes.BADLANDS : Biomes.ERODED_BADLANDS;
        }
        return humidityIndex < 3 ? Biomes.BADLANDS : Biomes.WOODED_BADLANDS;
    }

    private String pickPlateauBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
        if (weirdness.max() >= 0L) {
            String variant = this.PLATEAU_BIOMES_VARIANT[temperatureIndex][humidityIndex];
            if (variant != null) {
                return variant;
            }
        }
        return this.PLATEAU_BIOMES[temperatureIndex][humidityIndex];
    }

    private String pickPeakBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
        if (temperatureIndex <= 2) {
            return weirdness.max() < 0L ? Biomes.JAGGED_PEAKS : Biomes.FROZEN_PEAKS;
        }
        return temperatureIndex == 3 ? Biomes.STONY_PEAKS : this.pickBadlandsBiome(humidityIndex, weirdness);
    }

    private String pickSlopeBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
        if (temperatureIndex >= 3) {
            return this.pickPlateauBiome(temperatureIndex, humidityIndex, weirdness);
        }
        return humidityIndex <= 1 ? Biomes.SNOWY_SLOPES : Biomes.GROVE;
    }

    private String pickShatteredBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
        String biome = this.SHATTERED_BIOMES[temperatureIndex][humidityIndex];
        return biome == null ? this.pickMiddleBiome(temperatureIndex, humidityIndex, weirdness) : biome;
    }

    /** Vanilla {@code addSurfaceBiome}: two entries, depth point 0.0 and depth point 1.0. */
    private void addSurfaceBiome(Consumer<Climate.Entry<String>> biomes, Climate.Parameter temperature,
                                 Climate.Parameter humidity, Climate.Parameter continentalness,
                                 Climate.Parameter erosion, Climate.Parameter weirdness, float offset, String biome) {
        biomes.accept(new Climate.Entry<>(Climate.parameters(temperature, humidity, continentalness, erosion,
            Climate.Parameter.point(0.0F), weirdness, offset), biome));
        biomes.accept(new Climate.Entry<>(Climate.parameters(temperature, humidity, continentalness, erosion,
            Climate.Parameter.point(1.0F), weirdness, offset), biome));
    }

    /** Vanilla {@code addUndergroundBiome}: depth span 0.2..0.9. */
    private void addUndergroundBiome(Consumer<Climate.Entry<String>> biomes, Climate.Parameter temperature,
                                     Climate.Parameter humidity, Climate.Parameter continentalness,
                                     Climate.Parameter erosion, Climate.Parameter weirdness, float offset,
                                     String biome) {
        biomes.accept(new Climate.Entry<>(Climate.parameters(temperature, humidity, continentalness, erosion,
            Climate.Parameter.span(0.2F, 0.9F), weirdness, offset), biome));
    }

    /** Vanilla {@code addBottomBiome}: depth point 1.1. */
    private void addBottomBiome(Consumer<Climate.Entry<String>> biomes, Climate.Parameter temperature,
                                Climate.Parameter humidity, Climate.Parameter continentalness,
                                Climate.Parameter erosion, Climate.Parameter weirdness, float offset, String biome) {
        biomes.accept(new Climate.Entry<>(Climate.parameters(temperature, humidity, continentalness, erosion,
            Climate.Parameter.point(1.1F), weirdness, offset), biome));
    }

    /**
     * Vanilla {@code TerrainProvider.peaksAndValleys(float)} (called through {@code NoiseRouterData}):
     * {@code -(|(|w| - 0.6666667F)| - 0.33333334F) * 3.0F}, all in float.
     */
    public static float peaksAndValleys(float weirdness) {
        return -(Math.abs(Math.abs(weirdness) - 0.6666667F) - 0.33333334F) * 3.0F;
    }

    public static String getDebugStringForPeaksAndValleys(double peaksAndValleys) {
        if (peaksAndValleys < (double) peaksAndValleys(0.05F)) {
            return "Valley";
        } else if (peaksAndValleys < (double) peaksAndValleys(0.26666668F)) {
            return "Low";
        } else if (peaksAndValleys < (double) peaksAndValleys(0.4F)) {
            return "Mid";
        }
        return peaksAndValleys < (double) peaksAndValleys(0.56666666F) ? "High" : "Peak";
    }

    public String getDebugStringForContinentalness(double continentalness) {
        double quantized = (double) Climate.quantizeCoord((float) continentalness);
        if (quantized < (double) this.mushroomFieldsContinentalness.max()) {
            return "Mushroom fields";
        } else if (quantized < (double) this.deepOceanContinentalness.max()) {
            return "Deep ocean";
        } else if (quantized < (double) this.oceanContinentalness.max()) {
            return "Ocean";
        } else if (quantized < (double) this.coastContinentalness.max()) {
            return "Coast";
        } else if (quantized < (double) this.nearInlandContinentalness.max()) {
            return "Near inland";
        }
        return quantized < (double) this.midInlandContinentalness.max() ? "Mid inland" : "Far inland";
    }

    public String getDebugStringForErosion(double erosion) {
        return getDebugStringForNoiseValue(erosion, this.erosions);
    }

    public String getDebugStringForTemperature(double temperature) {
        return getDebugStringForNoiseValue(temperature, this.temperatures);
    }

    public String getDebugStringForHumidity(double humidity) {
        return getDebugStringForNoiseValue(humidity, this.humidities);
    }

    private static String getDebugStringForNoiseValue(double noiseValue, Climate.Parameter[] array) {
        double quantized = (double) Climate.quantizeCoord((float) noiseValue);
        for (int i = 0; i < array.length; ++i) {
            if (quantized < (double) array[i].max()) {
                return "" + i;
            }
        }
        return "?";
    }

    public Climate.Parameter[] getTemperatureThresholds() {
        return this.temperatures;
    }

    public Climate.Parameter[] getHumidityThresholds() {
        return this.humidities;
    }

    public Climate.Parameter[] getErosionThresholds() {
        return this.erosions;
    }

    public Climate.Parameter[] getContinentalnessThresholds() {
        return new Climate.Parameter[]{this.mushroomFieldsContinentalness, this.deepOceanContinentalness,
            this.oceanContinentalness, this.coastContinentalness, this.nearInlandContinentalness,
            this.midInlandContinentalness, this.farInlandContinentalness};
    }

    public Climate.Parameter[] getPeaksAndValleysThresholds() {
        return new Climate.Parameter[]{
            Climate.Parameter.span(-2.0F, peaksAndValleys(0.05F)),
            Climate.Parameter.span(peaksAndValleys(0.05F), peaksAndValleys(0.26666668F)),
            Climate.Parameter.span(peaksAndValleys(0.26666668F), peaksAndValleys(0.4F)),
            Climate.Parameter.span(peaksAndValleys(0.4F), peaksAndValleys(0.56666666F)),
            Climate.Parameter.span(peaksAndValleys(0.56666666F), 2.0F)};
    }

    public Climate.Parameter[] getWeirdnessThresholds() {
        return new Climate.Parameter[]{Climate.Parameter.span(-2.0F, 0.0F), Climate.Parameter.span(0.0F, 2.0F)};
    }
}
