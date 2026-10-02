package com.example.world;

import com.example.engine.BlockTextureMap;
import com.example.engine.TextureAtlas;

/**
 * Builds flat vertex-attribute arrays (position, uv, skyShade, blockLight) for a chunk's
 * visible faces. Only faces adjacent to a non-occluding neighbor are emitted (simple face
 * culling, not full greedy meshing) which is enough to keep vertex counts reasonable.
 *
 * Sky light and block light are baked in as two separate channels (rather than one combined
 * brightness) so the fragment shader can blend them live against a day-night uniform without
 * needing to re-mesh every chunk every frame: skyShade already has the old per-face directional
 * tint (top/side/bottom) folded in and fades with uDaylightFactor, while blockLight (torches,
 * glowstone, ...) stays constant regardless of time of day, matching vanilla's actual model.
 *
 * Neighbor sampling checks the LOCAL chunk first and only falls through to {@link World}
 * (a HashMap&lt;Long,Chunk&gt; lookup, with Long autoboxing) when a face's neighbor actually
 * crosses into an adjacent chunk. The overwhelming majority of a chunk's blocks have all 6
 * neighbors inside the same chunk, so this avoids millions of avoidable HashMap lookups and
 * boxed-Long allocations per chunk mesh build - previously every single neighbor check went
 * through World regardless, which was a real contributor to per-frame cost during streaming.
 */
public class ChunkMeshBuilder {

    public record MeshData(float[] opaque, float[] transparent) {
    }

    private static final float SHADE_TOP = 1.0f;
    private static final float SHADE_BOTTOM = 0.5f;
    private static final float SHADE_NORTH_SOUTH = 0.8f;
    private static final float SHADE_EAST_WEST = 0.6f;
    /** Water's surface sits slightly below a full block, matching Minecraft's water visual height. */
    private static final float WATER_TOP_HEIGHT = 14f / 16f;
    /** No-op tint multiplier for every face except grass-top/leaves - reused to avoid allocating per face. */
    private static final float[] NEUTRAL_TINT = {1f, 1f, 1f};
    /** Real vanilla spruce foliage is a fixed color, not colormap-sampled like oak/birch/jungle/
     *  acacia - a genuine, long-standing vanilla special case (see TreeSpecies), not an invented one. */
    private static final float[] SPRUCE_TINT = {0.38f, 0.50f, 0.38f};
    /** Real vanilla cherry foliage is also a fixed color (pink), not colormap-sampled - same
     *  special case as spruce, and the request's own explicit requirement ("do not incorrectly
     *  replace cherry pink foliage with a generic biome color"). Adapted/artistic approximation
     *  of cherry blossom pink - not independently verified against vanilla's exact source value
     *  this session, same honesty convention as every other adapted color in this codebase. */
    private static final float[] CHERRY_TINT = {0.97f, 0.75f, 0.84f};
    /** Slightly under a full block, matching a real young sapling's actual visual scale. */
    private static final float CROSS_PLANT_HEIGHT = 0.8f;
    /** Per-vertex wind-displacement weight for leaf faces (0=rigid, 1=full sway) - this project's
     *  own tuning (see Shaders.CHUNK_VERTEX's wind formula), not an official Minecraft value.
     *  Every non-leaf, non-plant face gets 0.0. */
    private static final float LEAF_WIND_WEIGHT = 0.7f;
    /** The same "no colormap file available" fallback constants BiomeColors already passes to
     *  Colormap.load - reused here (not reinvented) as the representative tint for hotbar icons,
     *  which have no world column to sample a real per-biome blend from. */
    private static final float[] DEFAULT_GRASS_TINT = unpackHex(0x7CBD6B);
    private static final float[] DEFAULT_FOLIAGE_TINT = unpackHex(0x59AE30);

    private static float[] unpackHex(int rgb) {
        return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};
    }

    private static boolean isBiomeTintedLeaves(BlockType type) {
        return type == BlockType.OAK_LEAVES || type == BlockType.BIRCH_LEAVES
                || type == BlockType.JUNGLE_LEAVES || type == BlockType.ACACIA_LEAVES
                || type == BlockType.MANGROVE_LEAVES || type == BlockType.DARK_OAK_LEAVES;
    }

    /** The tint a leaf-family block's icon should use, or {@code null} if {@code type} isn't
     *  one - a representative default (see DEFAULT_FOLIAGE_TINT) for the biome-blended species,
     *  since an icon has no world column to blend a real one from; the exact fixed tint for
     *  spruce/cherry, since those never blend with biome color in the first place. */
    public static float[] leafIconTint(BlockType type) {
        if (type == BlockType.SPRUCE_LEAVES) return SPRUCE_TINT;
        if (type == BlockType.CHERRY_LEAVES) return CHERRY_TINT;
        if (isBiomeTintedLeaves(type)) return DEFAULT_FOLIAGE_TINT;
        return null;
    }

    /** The representative grass-top tint for icons - see DEFAULT_GRASS_TINT. */
    public static float[] grassIconTint() {
        return DEFAULT_GRASS_TINT;
    }

    /** True for any of the 8 leaf species, regardless of which tint rule (biome-blended vs.
     *  spruce/cherry's fixed tint) applies to it - package-private so
     *  ChunkMeshBuilderFaceCullingTest-style pure-logic tests can exercise it directly. */
    static boolean isAnyLeaves(BlockType type) {
        return isBiomeTintedLeaves(type) || type == BlockType.SPRUCE_LEAVES || type == BlockType.CHERRY_LEAVES;
    }

    /** Wind-displacement weight for a cube-face block - LEAF_WIND_WEIGHT for any of the 8 leaf
     *  species (every one gets the same flat weight; the shader adds its own free per-vertex
     *  height gradient via fract(aPos.y), so no CPU-side per-face variation is needed here), 0.0
     *  for everything else. Trunks/branches are deliberately excluded - see the class docs on
     *  DARK_OAK's shared trunk/branch block type for why they can't be told apart at this level
     *  without new per-face metadata this round doesn't otherwise need. */
    static float windWeightFor(BlockType type) {
        return isAnyLeaves(type) ? LEAF_WIND_WEIGHT : 0f;
    }

    private final TextureAtlas atlas;
    private final BlockTextureMap textureMap;

    public ChunkMeshBuilder(TextureAtlas atlas, BlockTextureMap textureMap) {
        this.atlas = atlas;
        this.textureMap = textureMap;
    }

    public MeshData build(Chunk chunk, World world) {
        FloatBuffer opaque = new FloatBuffer();
        FloatBuffer transparent = new FloatBuffer();

        for (int lx = 0; lx < Chunk.SIZE_X; lx++) {
            for (int lz = 0; lz < Chunk.SIZE_Z; lz++) {
                int maxY = Math.min(Chunk.SIZE_Y - 1, chunk.getColumnHeight(lx, lz) + 12);
                for (int ly = 0; ly <= maxY; ly++) {
                    BlockType type = chunk.getLocal(lx, ly, lz);
                    if (type.isAir()) continue;

                    if (isCrossShapedPlant(type)) {
                        // A real plant model (two crossing diagonal quads), not a cube - see
                        // emitCrossPlant. Unconditional: a plant never occludes anything and is
                        // never occluded by a neighboring cube face the way a real cube face is,
                        // so none of the up/down/north/south/east/west culling below applies to it.
                        emitCrossPlant(transparent, chunk, world, lx, ly, lz, atlas.getUv(textureMap.facesFor(type).side()));
                        continue;
                    }

                    BlockTextureMap.Faces faces = textureMap.facesFor(type);
                    FloatBuffer target = type.isTransparentRender() ? transparent : opaque;

                    BlockType up = sampleBlock(chunk, world, lx, ly + 1, lz);
                    BlockType down = sampleBlock(chunk, world, lx, ly - 1, lz);
                    BlockType north = sampleBlock(chunk, world, lx, ly, lz - 1);
                    BlockType south = sampleBlock(chunk, world, lx, ly, lz + 1);
                    BlockType east = sampleBlock(chunk, world, lx + 1, ly, lz);
                    BlockType west = sampleBlock(chunk, world, lx - 1, ly, lz);

                    boolean isWater = type == BlockType.WATER;
                    float topY = isWater ? WATER_TOP_HEIGHT : 1f;
                    // Leaves are tinted on every face; grass only on its top face (the side/bottom
                    // textures are real, already-colored images - see TextureAtlas); water is
                    // tinted on every face, same as leaves, from the biome-blended color baked
                    // per column in TerrainGenerator.computeTint (see Chunk.waterTint). Spruce is
                    // the one species that does NOT use the biome-blended tint - see SPRUCE_TINT.
                    // Looked up once per block, not per face; everything else reuses NEUTRAL_TINT.
                    boolean isBiomeTintedLeaves = isBiomeTintedLeaves(type);
                    boolean isSpruceLeaves = type == BlockType.SPRUCE_LEAVES;
                    boolean isCherryLeaves = type == BlockType.CHERRY_LEAVES;
                    float[] sideTint = isBiomeTintedLeaves ? chunk.getFoliageTint(lx, lz)
                            : isSpruceLeaves ? SPRUCE_TINT
                            : isCherryLeaves ? CHERRY_TINT
                            : isWater ? chunk.getWaterTint(lx, lz) : NEUTRAL_TINT;
                    float[] topTint = isBiomeTintedLeaves || isWater || isSpruceLeaves || isCherryLeaves ? sideTint
                            : type == BlockType.GRASS_BLOCK ? chunk.getGrassTint(lx, lz) : NEUTRAL_TINT;
                    float windWeight = windWeightFor(type);
                    // Grass: the side's tintable fringe overlay (biome-colored, same tint as the top face).
                    float[] overlayUv = faces.sideOverlay() != null ? atlas.getUv(faces.sideOverlay()) : null;

                    if (shouldDrawFace(type, up))
                        emitFace(target, Face.TOP, lx, ly, lz, atlas.getUv(faces.top()), SHADE_TOP, chunk, world, lx, ly + 1, lz, topY, topTint, windWeight);
                    if (shouldDrawFace(type, down))
                        emitFace(target, Face.BOTTOM, lx, ly, lz, atlas.getUv(faces.bottom()), SHADE_BOTTOM, chunk, world, lx, ly - 1, lz, topY, sideTint, windWeight);
                    if (shouldDrawFace(type, north)) {
                        emitFace(target, Face.NORTH, lx, ly, lz, atlas.getUv(faces.side()), SHADE_NORTH_SOUTH, chunk, world, lx, ly, lz - 1, topY, sideTint, windWeight);
                        if (overlayUv != null) {
                            emitFace(target, Face.NORTH, lx, ly, lz, overlayUv, SHADE_NORTH_SOUTH, chunk, world, lx, ly, lz - 1, topY, topTint, windWeight, OVERLAY_OFFSET);
                        }
                    }
                    if (shouldDrawFace(type, south)) {
                        emitFace(target, Face.SOUTH, lx, ly, lz, atlas.getUv(faces.side()), SHADE_NORTH_SOUTH, chunk, world, lx, ly, lz + 1, topY, sideTint, windWeight);
                        if (overlayUv != null) {
                            emitFace(target, Face.SOUTH, lx, ly, lz, overlayUv, SHADE_NORTH_SOUTH, chunk, world, lx, ly, lz + 1, topY, topTint, windWeight, OVERLAY_OFFSET);
                        }
                    }
                    if (shouldDrawFace(type, east)) {
                        emitFace(target, Face.EAST, lx, ly, lz, atlas.getUv(faces.side()), SHADE_EAST_WEST, chunk, world, lx + 1, ly, lz, topY, sideTint, windWeight);
                        if (overlayUv != null) {
                            emitFace(target, Face.EAST, lx, ly, lz, overlayUv, SHADE_EAST_WEST, chunk, world, lx + 1, ly, lz, topY, topTint, windWeight, OVERLAY_OFFSET);
                        }
                    }
                    if (shouldDrawFace(type, west)) {
                        emitFace(target, Face.WEST, lx, ly, lz, atlas.getUv(faces.side()), SHADE_EAST_WEST, chunk, world, lx - 1, ly, lz, topY, sideTint, windWeight);
                        if (overlayUv != null) {
                            emitFace(target, Face.WEST, lx, ly, lz, overlayUv, SHADE_EAST_WEST, chunk, world, lx - 1, ly, lz, topY, topTint, windWeight, OVERLAY_OFFSET);
                        }
                    }
                }
            }
        }

        return new MeshData(opaque.toArray(), transparent.toArray());
    }

    private static boolean inChunk(int lx, int lz) {
        return lx >= 0 && lx < Chunk.SIZE_X && lz >= 0 && lz < Chunk.SIZE_Z;
    }

    private BlockType sampleBlock(Chunk chunk, World world, int lx, int ly, int lz) {
        if (inChunk(lx, lz)) return chunk.getLocal(lx, ly, lz);
        return world.getBlock(chunk.worldOriginX() + lx, ly, chunk.worldOriginZ() + lz);
    }

    private int sampleSkyLight(Chunk chunk, World world, int lx, int ly, int lz) {
        if (inChunk(lx, lz)) return chunk.getSkyLight(lx, ly, lz);
        return world.getSkyLight(chunk.worldOriginX() + lx, ly, chunk.worldOriginZ() + lz);
    }

    private int sampleBlockLight(Chunk chunk, World world, int lx, int ly, int lz) {
        if (inChunk(lx, lz)) return chunk.getBlockLight(lx, ly, lz);
        return world.getBlockLight(chunk.worldOriginX() + lx, ly, chunk.worldOriginZ() + lz);
    }

    /**
     * A face is worth drawing only where the neighbor doesn't fully occlude it - true whether
     * "current" is opaque or transparent (water/glass/ice). Previously the transparent branch
     * unconditionally returned true regardless of the neighbor, so e.g. a water face against an
     * opaque stone neighbor was drawn even though stone already draws its own face at that exact
     * same plane - two coincident, coplanar quads that z-fight (flicker between the two
     * textures depending on floating-point depth rounding). Since every underwater block has
     * water touching most of its faces, this was visible on essentially all submerged terrain,
     * and on any opaque block (e.g. a placed light source) touching water.
     */
    // Package-private (not private) so ChunkMeshBuilderFaceCullingTest can exercise this pure
    // logic directly - it has no atlas/texture dependency, unlike the rest of this class.
    static boolean shouldDrawFace(BlockType current, BlockType neighbor) {
        if (isAnyLeaves(current) || isAnyLeaves(neighbor)) {
            // Leaves are flagged isOpaqueCube()=true (their base shape is a full cube, and
            // that flag also drives light propagation - see LightEngine), but their real
            // textures bake in genuine per-pixel transparent cutout holes (the leafy silhouette
            // look), so neither direction of this relationship reliably occludes anything.
            // Culling here caused visible holes wherever a leaf touched anything solid - most
            // commonly another leaf of any species (every interior face inside an ordinary tree
            // canopy), or the leaf's own tree trunk - the reported "I can see through a placed
            // leaf block". Real vanilla itself never culls a face against a leaf neighbor for
            // exactly this reason; this now matches that instead of treating leaves like an
            // ordinary opaque cube for culling purposes.
            return true;
        }
        if (neighbor == current) return false;
        return !neighbor.isOpaqueCube();
    }

    /** Blocks rendered as a real cross/plant model (see {@link #emitCrossPlant}) instead of a
     *  cube - public so Game's hotbar-icon/held-item rendering can also branch on it (see
     *  drawBlockIcon/buildHeldItemVerts), not just this class's own mesh-building pass. */
    public static boolean isCrossShapedPlant(BlockType type) {
        return type == BlockType.OAK_SAPLING_STAGE_0 || type == BlockType.OAK_SAPLING_STAGE_1
                || type == BlockType.OAK_SAPLING_STAGE_2 || type == BlockType.OAK_SAPLING_STAGE_3;
    }

    /** Every block that should render as a single flat icon/cross rather than a solid cube, for
     *  the hotbar/inventory icon, the held-item view model, and a dropped item-entity's mesh (see
     *  Game's drawBlockIcon/buildHeldItemVerts/itemModelFor) - real in-world cross-shaped plants
     *  (see {@link #isCrossShapedPlant}) plus every tool (see {@link ToolEffectiveness}), which
     *  is never placed in the world at all so has no in-world model of its own to speak of.
     *  Deliberately a separate flag from {@link #isCrossShapedPlant}: that one also gates this
     *  class's own real world-mesh pass, which a tool should never reach in normal play. */
    /** Smelting-output/raw-material items with no other category to claim them (not a tool, not
     *  food) - see BlockType's own docs on the cooking/smelting milestone. */
    private static final java.util.Set<BlockType> SMELTING_ITEMS = java.util.EnumSet.of(
            BlockType.COAL, BlockType.CHARCOAL, BlockType.IRON_INGOT, BlockType.GOLD_INGOT, BlockType.COPPER_INGOT,
            BlockType.IRON_NUGGET, BlockType.BRICK, BlockType.RAW_IRON, BlockType.RAW_GOLD, BlockType.RAW_COPPER,
            BlockType.LEATHER, BlockType.FEATHER, BlockType.EGG, BlockType.LEATHER_HELMET, BlockType.LEATHER_CHESTPLATE, BlockType.LEATHER_LEGGINGS, BlockType.LEATHER_BOOTS, BlockType.IRON_HELMET, BlockType.IRON_CHESTPLATE, BlockType.IRON_LEGGINGS, BlockType.IRON_BOOTS, BlockType.GOLD_HELMET, BlockType.GOLD_CHESTPLATE, BlockType.GOLD_LEGGINGS, BlockType.GOLD_BOOTS,
            BlockType.DIAMOND, BlockType.DIAMOND_SWORD, BlockType.DIAMOND_AXE, BlockType.DIAMOND_PICKAXE, BlockType.DIAMOND_SHOVEL, BlockType.DIAMOND_HELMET, BlockType.DIAMOND_CHESTPLATE, BlockType.DIAMOND_LEGGINGS, BlockType.DIAMOND_BOOTS);

    public static boolean isFlatIconItem(BlockType type) {
        return isCrossShapedPlant(type) || ToolEffectiveness.isTool(type) || FoodRegistry.isFood(type)
                || SMELTING_ITEMS.contains(type);
    }

    /**
     * Real vanilla's actual sapling/flower/grass model: two quads set diagonally across the
     * block's footprint, crossing in an X when viewed from above, instead of 6 axis-aligned cube
     * faces. Each plane is a single quad, not the double-sided pair real vanilla needs - this
     * project never enables {@code GL_CULL_FACE} anywhere (confirmed), so one quad is already
     * visible from both sides here. Lit from the block's own light level directly (no per-face
     * directional shade or neighbor-face sampling - a plant isn't a face being occluded by a
     * neighbor the way a cube face is, so that whole model doesn't apply to it).
     */
    private void emitCrossPlant(FloatBuffer buf, Chunk chunk, World world, int lx, int ly, int lz, float[] uv) {
        float skyShade = sampleSkyLight(chunk, world, lx, ly, lz) / 15f;
        float blockLight = sampleBlockLight(chunk, world, lx, ly, lz) / 15f;
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float h = CROSS_PLANT_HEIGHT;

        // Base vertices (dy=0) get wind weight 0.0 - anchored to the ground, never translating
        // as a rigid object; top vertices (dy=h) get 1.0 - a real per-vertex bend-from-the-base
        // gradient, not a flat per-block weight the way leaf cube faces use.
        float[][] planeA = {
                {0, 0, 0, u0, v1, 0}, {1, 0, 1, u1, v1, 0}, {1, h, 1, u1, v0, 1},
                {0, 0, 0, u0, v1, 0}, {1, h, 1, u1, v0, 1}, {0, h, 0, u0, v0, 1}
        };
        float[][] planeB = {
                {1, 0, 0, u0, v1, 0}, {0, 0, 1, u1, v1, 0}, {0, h, 1, u1, v0, 1},
                {1, 0, 0, u0, v1, 0}, {0, h, 1, u1, v0, 1}, {1, h, 0, u0, v0, 1}
        };
        for (float[][] plane : new float[][][]{planeA, planeB}) {
            for (float[] c : plane) {
                buf.add(lx + c[0], ly + c[1], lz + c[2], c[3], c[4], skyShade, blockLight, 1f, 1f, 1f, c[5]);
            }
        }
    }

    private enum Face {TOP, BOTTOM, NORTH, SOUTH, EAST, WEST}

    /** How far a side overlay sits outside the block face. It is opaque only where the base is
     *  transparent, so it can never visibly overlap; the offset just makes it win the depth test
     *  at mip distances where the base's edge texels average to a partly-opaque value. */
    private static final float OVERLAY_OFFSET = 0.002f;

    private void emitFace(FloatBuffer buf, Face face, int x, int y, int z, float[] uv, float directionalShade,
                           Chunk chunk, World world, int neighborLx, int neighborLy, int neighborLz, float topY,
                           float[] tint, float windWeight) {
        emitFace(buf, face, x, y, z, uv, directionalShade, chunk, world, neighborLx, neighborLy, neighborLz, topY, tint, windWeight, 0f);
    }

    private void emitFace(FloatBuffer buf, Face face, int x, int y, int z, float[] uv, float directionalShade,
                           Chunk chunk, World world, int neighborLx, int neighborLy, int neighborLz, float topY,
                           float[] tint, float windWeight, float outwardOffset) {
        float skyShade = (sampleSkyLight(chunk, world, neighborLx, neighborLy, neighborLz) / 15f) * directionalShade;
        float blockLight = sampleBlockLight(chunk, world, neighborLx, neighborLy, neighborLz) / 15f;

        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        // For the four side faces, texture V increases downward within a tile (artwork top = v0,
        // artwork bottom = v1), so the block's bottom edge (dy=0) must sample v1 and its top edge
        // (dy=topY) must sample v0 - otherwise vertically-asymmetric textures like grass render
        // upside down (dirt on top, grass on the bottom).
        float[][] corners; // each entry: {dx,dy,dz,u,v}
        switch (face) {
            case TOP -> corners = new float[][]{
                    {0, topY, 0, u0, v0}, {0, topY, 1, u0, v1}, {1, topY, 1, u1, v1},
                    {0, topY, 0, u0, v0}, {1, topY, 1, u1, v1}, {1, topY, 0, u1, v0}
            };
            case BOTTOM -> corners = new float[][]{
                    {0, 0, 0, u0, v0}, {1, 0, 0, u1, v0}, {1, 0, 1, u1, v1},
                    {0, 0, 0, u0, v0}, {1, 0, 1, u1, v1}, {0, 0, 1, u0, v1}
            };
            case NORTH -> corners = new float[][]{
                    {0, 0, 0, u0, v1}, {0, topY, 0, u0, v0}, {1, topY, 0, u1, v0},
                    {0, 0, 0, u0, v1}, {1, topY, 0, u1, v0}, {1, 0, 0, u1, v1}
            };
            case SOUTH -> corners = new float[][]{
                    {0, 0, 1, u0, v1}, {1, 0, 1, u1, v1}, {1, topY, 1, u1, v0},
                    {0, 0, 1, u0, v1}, {1, topY, 1, u1, v0}, {0, topY, 1, u0, v0}
            };
            case WEST -> corners = new float[][]{
                    {0, 0, 0, u0, v1}, {0, 0, 1, u1, v1}, {0, topY, 1, u1, v0},
                    {0, 0, 0, u0, v1}, {0, topY, 1, u1, v0}, {0, topY, 0, u0, v0}
            };
            case EAST -> corners = new float[][]{
                    {1, 0, 0, u0, v1}, {1, topY, 0, u0, v0}, {1, topY, 1, u1, v0},
                    {1, 0, 0, u0, v1}, {1, topY, 1, u1, v0}, {1, 0, 1, u1, v1}
            };
            default -> throw new IllegalStateException();
        }
        float ox = 0f, oy = 0f, oz = 0f;
        switch (face) {
            case TOP -> oy = outwardOffset;
            case BOTTOM -> oy = -outwardOffset;
            case NORTH -> oz = -outwardOffset;
            case SOUTH -> oz = outwardOffset;
            case EAST -> ox = outwardOffset;
            case WEST -> ox = -outwardOffset;
        }
        for (float[] c : corners) {
            buf.add(x + c[0] + ox, y + c[1] + oy, z + c[2] + oz, c[3], c[4], skyShade, blockLight, tint[0], tint[1], tint[2], windWeight);
        }
    }

    /** Minimal growable float buffer to avoid boxing overhead from List<Float>. */
    private static final class FloatBuffer {
        private float[] data = new float[1024];
        private int size = 0;

        void add(float... values) {
            ensure(size + values.length);
            System.arraycopy(values, 0, data, size, values.length);
            size += values.length;
        }

        private void ensure(int needed) {
            if (needed <= data.length) return;
            int newCap = Math.max(data.length * 2, needed);
            float[] bigger = new float[newCap];
            System.arraycopy(data, 0, bigger, 0, size);
            data = bigger;
        }

        float[] toArray() {
            float[] result = new float[size];
            System.arraycopy(data, 0, result, 0, size);
            return result;
        }
    }
}
