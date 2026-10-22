package com.example.world;

import com.example.worldgen.biome.BiomeDefinition;
import com.example.worldgen.chunk.OverworldGenerator;
import com.example.worldgen.chunk.OverworldSpawn;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.random.RandomSource;
import com.example.worldgen.terrain.TerrainChunk;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The engine side of the vanilla 26.3 overworld generator ({@link OverworldGenerator}): every chunk is generated through the BIOMES
 * and TERRAIN statuses (noise fill, aquifers, surface rules, ore veins, carvers and the carving mask) and copied into the engine
 * {@link Chunk}; features (ores, vegetation, trees), structures and mobs are not generated yet.
 * <ul>
 *     <li>blocks: each distinct vanilla state of a chunk's palette is converted once through {@link VanillaBlocks#byVanilla}; a state
 *     the engine has no block for stops generation with {@link VanillaBlocks.UnsupportedBlockStateException} (never substituted);</li>
 *     <li>biomes: the chunk's quart biome table, recomputed from the seed for chunks loaded from disk;</li>
 *     <li>post-processing: the positions the aquifer and the surface rules marked are handed to {@link World} (fluid ticks once the
 *     chunk's neighbours exist, see {@code World.runPostProcessingIfReady});</li>
 *     <li>tint: vanilla's block tint ({@code ClientLevel.calculateBlockTint}) per column, see {@link #computeTint};</li>
 *     <li>spawn: {@link #findSpawn} ({@link OverworldSpawn}).</li>
 * </ul>
 * One instance per world, used from the game thread.
 */
public final class VanillaChunkGenerator implements WorldGenerator {

    /** The worldgen data folder (gitignored, restored from the user's client jar by {@code scripts/restore-worldgen-data.py}). */
    public static final Path DATA_ROOT = Path.of("data");
    private static final File GRASS_COLORMAP = new File("textures/colormap/grass.png");
    private static final File FOLIAGE_COLORMAP = new File("textures/colormap/foliage.png");

    private final OverworldGenerator overworld;
    /** Generated terrain chunks not copied into the engine yet (the spawn search generates a few before the world streams them). */
    private final Map<Long, TerrainChunk> generatedAhead = new HashMap<>();
    private final Map<Long, List<int[]>> postProcessing = new HashMap<>();
    private final Map<StateRef, BlockType> blockTypes = new HashMap<>();
    private final Map<String, BiomeTint> biomeTints = new HashMap<>();
    private int[] grassPixels;
    private int[] foliagePixels;

    public VanillaChunkGenerator(long seed) {
        this(DATA_ROOT, seed);
    }

    /** @throws OverworldGenerator.MissingWorldgenDataException when the data folder is missing (the message names the restore script) */
    public VanillaChunkGenerator(Path dataRoot, long seed) {
        this.overworld = new OverworldGenerator(dataRoot, seed);
    }

    public OverworldGenerator overworld() {
        return overworld;
    }

    /** {@code noise_settings.sea_level} of the overworld settings (63). */
    public int seaLevel() {
        return overworld.seaLevel();
    }

    // ------------------------------------------------------------------ blocks

    private static long key(int cx, int cz) {
        return Chunk.key(cx, cz);
    }

    private TerrainChunk terrain(int cx, int cz) {
        TerrainChunk t = generatedAhead.get(key(cx, cz));
        return t != null ? t : generatedAhead.computeIfAbsent(key(cx, cz), k -> overworld.generate(cx, cz));
    }

    @Override
    public void generate(Chunk chunk, World world) {
        TerrainChunk terrain = generatedAhead.remove(key(chunk.chunkX, chunk.chunkZ));
        if (terrain == null) terrain = overworld.generate(chunk.chunkX, chunk.chunkZ);
        copyBlocks(terrain, chunk);
        chunk.setQuartBiomes(terrain.quartBiomes());
        List<int[]> marks = terrain.postProcessingPositions();
        if (!marks.isEmpty()) postProcessing.put(key(chunk.chunkX, chunk.chunkZ), marks);
    }

    private void copyBlocks(TerrainChunk terrain, Chunk chunk) {
        BlockType[] byIndex = new BlockType[terrain.paletteSize()];
        for (int y = Chunk.MIN_Y; y <= Chunk.MAX_Y; y++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                for (int x = 0; x < Chunk.SIZE_X; x++) {
                    int index = terrain.paletteIndexLocal(x, y, z);
                    BlockType type = byIndex[index];
                    if (type == null) {
                        type = engineBlock(terrain.paletteState(index), chunk);
                        byIndex[index] = type;
                    }
                    if (!type.isAir()) chunk.setLocal(x, y, z, type);
                }
            }
        }
    }

    private BlockType engineBlock(StateRef state, Chunk chunk) {
        BlockType type = blockTypes.get(state);
        if (type != null) return type;
        try {
            type = VanillaBlocks.byVanilla(state.id(), state.properties());
        } catch (VanillaBlocks.UnsupportedBlockStateException e) {
            throw new VanillaBlocks.UnsupportedBlockStateException(state.id(), state.properties(),
                    "generated in chunk " + chunk.chunkX + "," + chunk.chunkZ + " and the engine has no block for it (" + e.getMessage() + ")");
        }
        blockTypes.put(state, type);
        return type;
    }

    @Override
    public List<int[]> takePostProcessing(Chunk chunk) {
        List<int[]> marks = postProcessing.remove(key(chunk.chunkX, chunk.chunkZ));
        return marks == null ? List.of() : marks;
    }

    // ------------------------------------------------------------------ biomes

    @Override
    public boolean hasBiomes() {
        return true;
    }

    @Override
    public void computeBiomes(Chunk chunk) {
        if (!chunk.hasBiomes()) chunk.setQuartBiomes(overworld.biomeTable(chunk.chunkX, chunk.chunkZ).clone());
    }

    @Override
    public String noiseBiome(int quartX, int quartY, int quartZ) {
        return overworld.noiseBiome(quartX, quartY, quartZ);
    }

    @Override
    public long biomeZoomSeed() {
        return overworld.biomeZoomSeed();
    }

    // ------------------------------------------------------------------ tint

    /** The three colour resolvers of a biome ({@code BiomeColors.GRASS/FOLIAGE/WATER_COLOR_RESOLVER}) with their constant parts. */
    private record BiomeTint(BiomeDefinition biome, int grassBase, boolean grassVaries, int foliage, int water) {
    }

    private BiomeTint colors(String biomeId) {
        BiomeTint c = biomeTints.get(biomeId);
        if (c != null) return c;
        if (grassPixels == null) {
            grassPixels = colormapPixels(GRASS_COLORMAP);
            foliagePixels = colormapPixels(FOLIAGE_COLORMAP);
        }
        BiomeDefinition biome = overworld.biome(biomeId);
        boolean varies = biome.effects().grassColorModifier() == BiomeDefinition.GrassColorModifier.SWAMP;
        int grass = biome.getGrassColor(0.0, 0.0, grassPixels, overworld.biomeNoises());
        c = new BiomeTint(biome, grass, varies, biome.getFoliageColor(foliagePixels), biome.getWaterColor());
        biomeTints.put(biomeId, c);
        return c;
    }

    /** {@code LegacyStuffWrapper.getPixels}: the colormap image's ARGB pixels, row by row ({@code NativeImage.makePixelArray}). */
    private static int[] colormapPixels(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) throw new IOException("not an image");
            return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the vanilla colormap " + file.getAbsolutePath()
                    + " (copy textures/colormap from your 26.3 client jar, see docs/ASSETS.md)", e);
        }
    }

    /**
     * {@code ClientLevel.calculateBlockTint(pos, resolver)} for grass, foliage and water, evaluated once per column at the column's
     * top block (the engine keeps one tint per column, not per block): with blend radius {@code r = 0} the resolver at the block's
     * own biome; otherwise the integer average of the red, green and blue of the resolver over the {@code (2r + 1)^2} blocks
     * {@code x - r .. x + r, z - r .. z + r} at the same y ({@code Cursor3D}), each biome taken from the level's
     * {@code BiomeManager} ({@link World#getBiome}: loaded chunks' stored biomes, else the generator's BIOMES table). The resolvers
     * are {@code Biome.getGrassColor(x, z)}, {@code getFoliageColor()} and {@code getWaterColor()}.
     */
    @Override
    public void computeTint(Chunk chunk, World world) {
        int r = world.getBiomeBlendRadius();
        int cells = (r * 2 + 1) * (r * 2 + 1);
        for (int lx = 0; lx < Chunk.SIZE_X; lx++) {
            for (int lz = 0; lz < Chunk.SIZE_Z; lz++) {
                int x = chunk.worldOriginX() + lx;
                int z = chunk.worldOriginZ() + lz;
                int y = Math.max(Chunk.MIN_Y, chunk.getColumnHeight(lx, lz));
                int[] grass = new int[3];
                int[] foliage = new int[3];
                int[] water = new int[3];
                for (int bx = x - r; bx <= x + r; bx++) {
                    for (int bz = z - r; bz <= z + r; bz++) {
                        BiomeTint c = colors(world.getBiome(bx, y, bz));
                        int g = c.grassVaries() ? c.biome().getGrassColor(bx, bz, grassPixels, overworld.biomeNoises()) : c.grassBase();
                        add(grass, g);
                        add(foliage, c.foliage());
                        add(water, c.water());
                    }
                }
                chunk.setGrassTint(lx, lz, grass[0] / cells / 255f, grass[1] / cells / 255f, grass[2] / cells / 255f);
                chunk.setFoliageTint(lx, lz, foliage[0] / cells / 255f, foliage[1] / cells / 255f, foliage[2] / cells / 255f);
                chunk.setWaterTint(lx, lz, water[0] / cells / 255f, water[1] / cells / 255f, water[2] / cells / 255f);
            }
        }
    }

    /** {@code ARGB.red/green/blue}. */
    private static void add(int[] sum, int argb) {
        sum[0] += argb >> 16 & 0xFF;
        sum[1] += argb >> 8 & 0xFF;
        sum[2] += argb & 0xFF;
    }

    // ------------------------------------------------------------------ spawn

    /**
     * The spawn of a new world ({@link OverworldSpawn}): the world spawn ({@code MinecraftServer.setInitialSpawn}), then where the
     * player appears around it ({@code PlayerSpawnFinder.findSpawn} with the default {@code respawn_radius} 10 and vanilla's unseeded
     * thread-local random), as {@code {x, y, z}} of the player's feet. The terrain chunks it generates are kept for the world to use.
     */
    public double[] findSpawn() {
        OverworldSpawn.Chunks chunks = this::terrain;
        OverworldSpawn.BlockPos worldSpawn = OverworldSpawn.worldSpawn(overworld, chunks);
        double[] player = OverworldSpawn.playerSpawn(overworld, chunks, worldSpawn, OverworldSpawn.RESPAWN_RADIUS,
                RandomSource.createThreadLocalInstance());
        // Keep only what the world will load right away (the spawn's neighbourhood); a far spiral search must not pin chunks.
        int pcx = Math.floorDiv((int) Math.floor(player[0]), 16);
        int pcz = Math.floorDiv((int) Math.floor(player[2]), 16);
        generatedAhead.keySet().removeIf(k -> {
            int cx = (int) k.longValue();
            int cz = (int) (k >> 32);
            return Math.abs(cx - pcx) > 2 || Math.abs(cz - pcz) > 2;
        });
        return player;
    }
}
