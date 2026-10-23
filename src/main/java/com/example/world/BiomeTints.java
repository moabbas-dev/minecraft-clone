package com.example.world;

import com.example.worldgen.biome.BiomeDefinition;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Vanilla's block tint ({@code ClientLevel.calculateBlockTint} with {@code BiomeColors.GRASS/FOLIAGE/WATER_COLOR_RESOLVER}) for a
 * generator with biomes, written into a chunk's per-column tint cache; shared by the overworld and the flat generator.
 */
final class BiomeTints {
    private static final File GRASS_COLORMAP = new File("textures/colormap/grass.png");
    private static final File FOLIAGE_COLORMAP = new File("textures/colormap/foliage.png");

    private final Function<String, BiomeDefinition> definitions;
    private final BiomeDefinition.Noises noises;
    private final Map<String, BiomeTint> biomeTints = new HashMap<>();
    private int[] grassPixels;
    private int[] foliagePixels;

    BiomeTints(Function<String, BiomeDefinition> definitions, BiomeDefinition.Noises noises) {
        this.definitions = definitions;
        this.noises = noises;
    }

    /** The three colour resolvers of a biome with their constant parts. */
    private record BiomeTint(BiomeDefinition biome, int grassBase, boolean grassVaries, int foliage, int water) {
    }

    private BiomeTint colors(String biomeId) {
        BiomeTint c = biomeTints.get(biomeId);
        if (c != null) return c;
        if (grassPixels == null) {
            grassPixels = colormapPixels(GRASS_COLORMAP);
            foliagePixels = colormapPixels(FOLIAGE_COLORMAP);
        }
        BiomeDefinition biome = definitions.apply(biomeId);
        boolean varies = biome.effects().grassColorModifier() == BiomeDefinition.GrassColorModifier.SWAMP;
        int grass = biome.getGrassColor(0.0, 0.0, grassPixels, noises);
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
     * {@code BiomeManager} ({@link World#getBiome}). The resolvers are {@code Biome.getGrassColor(x, z)}, {@code getFoliageColor()}
     * and {@code getWaterColor()}.
     */
    void computeTint(Chunk chunk, World world) {
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
                        int g = c.grassVaries() ? c.biome().getGrassColor(bx, bz, grassPixels, noises) : c.grassBase();
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
}
