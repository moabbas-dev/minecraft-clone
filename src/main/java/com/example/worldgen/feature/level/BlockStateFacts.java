package com.example.worldgen.feature.level;

import com.example.worldgen.feature.Direction;
import com.example.worldgen.level.StateRef;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The block-state physics the ported feature code asks vanilla's {@code BlockBehaviour.BlockStateBase} (26.3) for, per block
 * id, read from the client jar:
 * <ul>
 *     <li>{@code isSolid()} = {@code legacySolid} = {@code calculateSolid()}: {@code forceSolidOn} gives true,
 *     {@code forceSolidOff} false, a null shape cache ({@code Properties.dynamicShape()}) false, an empty collision shape
 *     false, else true when the collision bounds' {@code getSize()} ({@code (xs + ys + zs) / 3}) is at least
 *     {@code 0.7291666666666666} or their y size at least 1;</li>
 *     <li>{@code getFaceOcclusionShape(dir)}: {@code initCache} sets the occlusion shape to
 *     {@code canOcclude ? block.getOcclusionShape(state) : Shapes.empty()}; an empty one gives {@code EMPTY_OCCLUSION_SHAPES}
 *     (every face {@code Shapes.empty()}), a full block ({@code Block.isShapeFullBlock}) gives
 *     {@code FULL_BLOCK_OCCLUSION_SHAPES} (every face {@code Shapes.block()}); {@link #faceOcclusionIsFullBlock} answers
 *     {@code faceShape != Shapes.empty() && Block.isShapeFullBlock(faceShape)} for those two cases;</li>
 *     <li>{@code isFaceSturdy(level, pos, dir)} ({@code SupportType.FULL}) and {@code isFaceSturdy(..., SupportType.CENTER)}
 *     over {@code getBlockSupportShape} (by default the collision shape for {@code CollisionContext.empty()});
 *     {@code Block.canSupportCenter(level, pos, dir)} is the CENTER test;</li>
 *     <li>{@code Block.isFaceFull(shape, dir)} of the support shape and of the collision shape
 *     ({@code MultifaceBlock.canAttachTo}); {@code getCollisionShape(level, pos).getFaceShape(UP).isEmpty()}
 *     ({@code SeaPickleBlock.mayPlaceOn});</li>
 *     <li>{@code canBeReplaced()} = {@code Properties.replaceable()}; {@code liquid()} = {@code Properties.liquid()}.</li>
 * </ul>
 * {@code BlockBehaviour} defaults (bytecode): {@code getShape} = {@code Shapes.block()}; {@code getCollisionShape} =
 * {@code hasCollision ? getShape : empty}; {@code getOcclusionShape} = {@code getShape(EMPTY, ZERO)};
 * {@code getBlockSupportShape} = the collision shape. {@code Properties.noCollision()} sets {@code hasCollision = false} and
 * {@code canOcclude = false}; {@code noOcclusion()} only {@code canOcclude = false}. A block not listed here throws: its
 * facts have not been read.
 */
public final class BlockStateFacts {

    /** The shape classes of the listed blocks. */
    enum Kind {
        /**
         * Default shapes, collision and occlusion (no {@code noCollision}, {@code noOcclusion}, {@code dynamicShape},
         * {@code forceSolid*}, no shape override): solid, every face occluding, sturdy and full, not replaceable.
         */
        FULL(true, true, true, false, true, false, false),
        /** {@code IceBlock} with {@code Properties.noOcclusion()}: full collision (solid, sturdy), empty occlusion. */
        NO_OCCLUSION(true, false, true, false, true, false, false),
        /**
         * {@code MudBlock}: collision {@code Block.column(16, 0, 14)} (bounds size (1 + 0.875 + 1) / 3 &gt;= 0.729, so solid;
         * its UP face is empty, the other faces of the collision box are not full on UP), default full {@code getShape}
         * (occluding), {@code getBlockSupportShape} = {@code Shapes.block()} (sturdy and full on every face).
         */
        MUD(true, true, true, true, true, false, false),
        /**
         * {@code AirBlock} ({@code getShape} empty, {@code noCollision}, {@code replaceable}) and {@code LiquidBlock}
         * ({@code getShape} empty; {@code getCollisionShape} with the empty context: {@code alwaysCollideWithFluid()} false,
         * so empty; {@code noCollision}, {@code replaceable}, liquid for water and lava only): not solid, nothing occludes,
         * not sturdy, replaceable.
         */
        EMPTY_REPLACEABLE(false, false, false, true, false, true, false),
        /** {@code LiquidBlock} water / lava: as {@link #EMPTY_REPLACEABLE}, plus {@code Properties.liquid()}. */
        LIQUID(false, false, false, true, false, true, true),
        /**
         * A block with {@code Properties.noCollision()} whose class overrides neither {@code getCollisionShape} nor
         * {@code getBlockSupportShape}: empty collision and support shapes (not solid, not sturdy, no full face), no occlusion,
         * not replaceable.
         */
        NO_COLLISION(false, false, false, true, false, false, false),
        /** {@link #NO_COLLISION} with {@code Properties.replaceable()}. */
        NO_COLLISION_REPLACEABLE(false, false, false, true, false, true, false),
        /**
         * {@code BambooStalkBlock}: {@code forceSolidOn()} (solid), {@code noOcclusion()}, collision (and so support) shape
         * {@code SHAPE_COLLISION = Block.column(3, 0, 16)} moved by the XZ offset: never a full face, so not sturdy (FULL),
         * its UP face is not empty; the CENTER support test is not derived ({@link #isFaceSturdyCenter} throws).
         */
        BAMBOO(true, false, false, false, false, false, false),
        /**
         * {@code CactusBlock}: {@code getShape} = {@code Block.column(14, 0, 16)} (not a full block: no full occlusion face),
         * {@code getCollisionShape} = {@code SHAPE_COLLISION = Block.column(14, 0, 15)}: bounds size (0.875 + 0.9375 + 0.875) / 3
         * &gt;= 0.729 (solid), no full face (not sturdy), UP face empty; the CENTER support test is not derived.
         */
        CACTUS(true, false, false, true, false, false, false),
        /**
         * {@code CarpetBlock} (MOSS_CARPET; Properties of, mapColor, strength, sound, pushReaction): shape and collision
         * {@code SHAPE = Block.column(16, 0, 1)}: bounds size (1 + 0.0625 + 1) / 3 &lt; 0.729 and y size &lt; 1 (not solid);
         * only the DOWN face is full (sturdy for FULL and CENTER, full support / collision face), the UP face is empty. The
         * per-direction answers are in the accessors; the face occlusion test is not derived (throws).
         */
        CARPET(false, false, false, true, false, false, false),
        /**
         * {@code SeaPickleBlock} ({@code noOcclusion()}, collision = {@code getShape}: {@code Block.column(4, 0, 6)},
         * {@code column(10, 0, 6)}, {@code column(12, 0, 6)}, {@code column(12, 0, 7)} for 1..4 pickles): the largest bounds
         * size (0.75 + 0.4375 + 0.75) / 3 &lt; 0.729 (not solid), no full face, UP face empty; CENTER not derived.
         */
        SEA_PICKLE(false, false, false, true, false, false, false),
        /**
         * Only {@code isSolid()} (false through {@code Properties.forceSolidOff()}), {@code canBeReplaced()} (false) and
         * {@code liquid()} (false) are derived; every shape query throws: BIG_DRIPLEAF (BigDripleafBlock, collision depends on
         * TILT), AZALEA and FLOWERING_AZALEA (AzaleaBlock).
         */
        SOLID_OFF(false, false, false, false, false, false, false),
        /**
         * {@code BigDripleafBlock}: {@code forceSolidOff()} (not solid); {@code getCollisionShape} = {@code SHAPE_LEAF[tilt]}:
         * {@code Block.column(16, 11, 15)} (NONE, UNSTABLE), {@code column(16, 11, 13)} (PARTIAL), {@code Shapes.empty()}
         * (FULL): never touching y 0 or 1, so the UP and DOWN faces are empty and no face is full (not sturdy, no CENTER support
         * on UP / DOWN); the horizontal CENTER test and the face occlusion are not derived (throw).
         */
        BIG_DRIPLEAF(false, false, false, true, false, false, false);

        final boolean solid;
        final boolean occludesFaces;
        final boolean sturdyFaces;
        final boolean collisionUpFaceEmpty;
        final boolean supportFacesFull;
        final boolean replaceable;
        final boolean liquid;

        Kind(boolean solid, boolean occludesFaces, boolean sturdyFaces, boolean collisionUpFaceEmpty, boolean supportFacesFull,
             boolean replaceable, boolean liquid) {
            this.solid = solid;
            this.occludesFaces = occludesFaces;
            this.sturdyFaces = sturdyFaces;
            this.collisionUpFaceEmpty = collisionUpFaceEmpty;
            this.supportFacesFull = supportFacesFull;
            this.replaceable = replaceable;
            this.liquid = liquid;
        }
    }

    private static final Map<String, Kind> KINDS = new HashMap<>();

    static {
        // Blocks.<clinit>: plain Block / DropExperienceBlock / SandBlock / ColoredFallingBlock / SnowyBlock (GrassBlock,
        // MyceliumBlock) / RotatedPillarBlock / InfestedBlock / InfestedRotatedPillarBlock / RedStoneOreBlock / MagmaBlock /
        // dyed terracotta (ColorCollection lambda: new Block(Properties.of()...)); none of these classes overrides a shape
        // method and their Properties chains call no noCollision / noOcclusion / dynamicShape / forceSolidOn/Off.
        // Added for the vegetation features: MELON (plain Block: of, mapColor, strength, sound, pushReaction), PUMPKIN
        // (PumpkinBlock, no shape override; of, mapColor, instrument, strength, sound, pushReaction).
        for (String id : Set.of("stone", "granite", "diorite", "andesite", "deepslate", "tuff", "calcite", "dirt", "coarse_dirt",
                "grass_block", "podzol", "mycelium", "clay", "sand", "red_sand", "gravel", "sandstone", "red_sandstone",
                "snow_block", "packed_ice", "bedrock", "terracotta", "white_terracotta", "orange_terracotta",
                "yellow_terracotta", "brown_terracotta", "red_terracotta", "light_gray_terracotta", "coal_ore",
                "deepslate_coal_ore", "iron_ore", "deepslate_iron_ore", "copper_ore", "deepslate_copper_ore", "gold_ore",
                "deepslate_gold_ore", "redstone_ore", "deepslate_redstone_ore", "lapis_ore", "deepslate_lapis_ore",
                "diamond_ore", "deepslate_diamond_ore", "emerald_ore", "deepslate_emerald_ore", "raw_copper_block",
                "raw_iron_block", "infested_stone", "infested_deepslate", "magma_block", "sulfur", "cinnabar", "melon",
                "pumpkin")) {
            KINDS.put("minecraft:" + id, Kind.FULL);
        }
        // Coral: CoralBlock (of, mapColor, instrument, requiresCorrectToolForDrops, strength, sound; no shape override) is
        // FULL; CoralPlantBlock / CoralFanBlock / CoralWallFanBlock have noCollision() and override only getShape.
        for (String c : Set.of("tube", "brain", "bubble", "fire", "horn")) {
            KINDS.put("minecraft:" + c + "_coral_block", Kind.FULL);
            KINDS.put("minecraft:" + c + "_coral", Kind.NO_COLLISION);
            KINDS.put("minecraft:" + c + "_coral_fan", Kind.NO_COLLISION);
            KINDS.put("minecraft:" + c + "_coral_wall_fan", Kind.NO_COLLISION);
        }
        // ICE: new IceBlock(Properties.of()...noOcclusion()...)
        KINDS.put("minecraft:ice", Kind.NO_OCCLUSION);
        // MUD: new MudBlock(Properties.ofLegacyCopy(DIRT)...)
        KINDS.put("minecraft:mud", Kind.MUD);
        // AIR, CAVE_AIR, VOID_AIR: new AirBlock(Properties.of().replaceable().noCollision()...air());
        for (String id : Set.of("air", "cave_air", "void_air")) {
            KINDS.put("minecraft:" + id, Kind.EMPTY_REPLACEABLE);
        }
        // WATER, LAVA: new LiquidBlock(..., Properties.of()...replaceable().noCollision()...liquid())
        KINDS.put("minecraft:water", Kind.LIQUID);
        KINDS.put("minecraft:lava", Kind.LIQUID);
        // Blocks.<clinit> Properties chains with noCollision() and without replaceable(); none of their classes
        // (FlowerBlock, EyeblossomBlock, MushroomBlock, SaplingBlock, MangrovePropaguleBlock, TallFlowerBlock, FlowerBedBlock,
        // CactusFlowerBlock, FireflyBushBlock, SugarCaneBlock, SweetBerryBushBlock, SporeBlossomBlock, KelpBlock,
        // KelpPlantBlock, CaveVinesBlock, CaveVinesPlantBlock, BigDripleafStemBlock, SmallDripleafBlock) overrides
        // getCollisionShape or getBlockSupportShape (javap of each class chain).
        for (String id : Set.of("dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
                "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "closed_eyeblossom",
                "brown_mushroom", "red_mushroom", "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling",
                "acacia_sapling", "cherry_sapling", "dark_oak_sapling", "pale_oak_sapling", "poplar_sapling",
                "mangrove_propagule", "sunflower", "lilac", "rose_bush", "peony", "pink_petals", "wildflowers", "cactus_flower",
                "firefly_bush", "sugar_cane", "sweet_berry_bush", "spore_blossom", "kelp", "kelp_plant", "cave_vines",
                "cave_vines_plant", "big_dripleaf_stem", "small_dripleaf")) {
            KINDS.put("minecraft:" + id, Kind.NO_COLLISION);
        }
        // noCollision() and replaceable(): BUSH, RED_SHRUB (BushBlock), DEAD_BUSH (DryVegetationBlock), SHORT_GRASS, FERN
        // (TallGrassBlock), SHORT_DRY_GRASS, TALL_DRY_GRASS, TALL_GRASS, LARGE_FERN (DoublePlantBlock), SEAGRASS
        // (SeagrassBlock), TALL_SEAGRASS (TallSeagrassBlock), LEAF_LITTER (LeafLitterBlock), VINE (VineBlock).
        for (String id : Set.of("bush", "red_shrub", "dead_bush", "short_grass", "fern", "short_dry_grass", "tall_dry_grass",
                "tall_grass", "large_fern", "seagrass", "tall_seagrass", "leaf_litter", "vine")) {
            KINDS.put("minecraft:" + id, Kind.NO_COLLISION_REPLACEABLE);
        }
        KINDS.put("minecraft:bamboo", Kind.BAMBOO);
        KINDS.put("minecraft:cactus", Kind.CACTUS);
        KINDS.put("minecraft:moss_carpet", Kind.CARPET);
        KINDS.put("minecraft:sea_pickle", Kind.SEA_PICKLE);
        for (String id : Set.of("azalea", "flowering_azalea")) KINDS.put("minecraft:" + id, Kind.SOLID_OFF);
        KINDS.put("minecraft:big_dripleaf", Kind.BIG_DRIPLEAF);
    }

    private BlockStateFacts() {
    }

    private static Kind kind(StateRef state) {
        Kind k = KINDS.get(state.id());
        if (k == null) throw new IllegalArgumentException("block shape facts not read from the jar for " + state.id());
        return k;
    }

    /** The kind for a shape query: SOLID_OFF blocks have no derived shape facts. */
    private static Kind shapeKind(StateRef state) {
        Kind k = kind(state);
        if (k == Kind.SOLID_OFF) throw new IllegalArgumentException("shape facts of " + state.id() + " not derived from the jar");
        return k;
    }

    /** Whether the shape facts of a block have been read. */
    public static boolean isKnown(String id) {
        return KINDS.containsKey(id);
    }

    /** {@code BlockState.isSolid()}. */
    public static boolean isSolid(StateRef state) {
        return kind(state).solid;
    }

    /** {@code faceOcclusionShape != Shapes.empty() && Block.isShapeFullBlock(state.getFaceOcclusionShape(direction))}. */
    public static boolean faceOcclusionIsFullBlock(StateRef state, Direction direction) {
        Kind k = shapeKind(state);
        if (k == Kind.CARPET || k == Kind.BIG_DRIPLEAF) throw new IllegalArgumentException("face occlusion of " + state.id() + " not derived from the jar");
        return k.occludesFaces;
    }

    /** {@code BlockState.isFaceSturdy(level, pos, direction)} ({@code SupportType.FULL}). */
    public static boolean isFaceSturdy(StateRef state, Direction direction) {
        Kind k = shapeKind(state);
        if (k == Kind.CARPET) return direction == Direction.DOWN;
        return k.sturdyFaces;
    }

    /**
     * {@code BlockState.isFaceSturdy(level, pos, direction, SupportType.CENTER)} (= {@code Block.canSupportCenter}): for the
     * listed shape classes the support shape is either a full block (every face supports the centre) or empty.
     */
    public static boolean isFaceSturdyCenter(StateRef state, Direction direction) {
        Kind k = shapeKind(state);
        if (k == Kind.CARPET) return direction == Direction.DOWN;
        if (k == Kind.BIG_DRIPLEAF && !direction.isHorizontal()) return false;
        if (k == Kind.BIG_DRIPLEAF || k == Kind.BAMBOO || k == Kind.CACTUS || k == Kind.SEA_PICKLE) throw new IllegalArgumentException("CENTER support of " + state.id() + " not derived from the jar");
        return k.sturdyFaces;
    }

    /** {@code Block.isFaceFull(state.getBlockSupportShape(level, pos), direction)}. */
    public static boolean supportFaceFull(StateRef state, Direction direction) {
        Kind k = shapeKind(state);
        if (k == Kind.CARPET) return direction == Direction.DOWN;
        return k.supportFacesFull;
    }

    /** {@code Block.isFaceFull(state.getCollisionShape(level, pos), direction)}. */
    public static boolean collisionFaceFull(StateRef state, Direction direction) {
        Kind k = shapeKind(state);
        // MudBlock collision Block.column(16, 0, 14): only its DOWN face covers the whole block face
        if (k == Kind.MUD || k == Kind.CARPET) return direction == Direction.DOWN;
        return k.supportFacesFull;
    }

    /** {@code state.getCollisionShape(level, pos).getFaceShape(Direction.UP).isEmpty()}. */
    public static boolean collisionUpFaceEmpty(StateRef state) {
        return shapeKind(state).collisionUpFaceEmpty;
    }

    /** {@code BlockState.canBeReplaced()}. */
    public static boolean canBeReplaced(StateRef state) {
        return kind(state).replaceable;
    }

    /** {@code BlockState.liquid()}. */
    public static boolean liquid(StateRef state) {
        return kind(state).liquid;
    }

    /**
     * {@code state.getFluidState().getType()} as a registry id: {@code LiquidBlock.getFluidState} returns
     * {@code stateCache.get(min(level, 8))}, built in its constructor as {@code [fluid.getSource(false),
     * fluid.getFlowing(8 - i, false) for i in 1..7, fluid.getFlowing(8, true)]}, so level 0 is the source fluid
     * ({@code minecraft:water} / {@code minecraft:lava}) and every other level the flowing one; a {@code waterlogged=true}
     * state and the blocks that always hold water ({@code bubble_column}, {@code kelp}, {@code kelp_plant},
     * {@code seagrass}, {@code tall_seagrass}: {@code Fluids.WATER.getSource(false)}) give {@code minecraft:water};
     * everything else {@code minecraft:empty}.
     */
    public static String fluidType(StateRef state) {
        String id = state.id();
        if (id.equals("minecraft:water") || id.equals("minecraft:lava")) {
            String level = state.properties().get("level");
            boolean source = level == null || level.equals("0");
            if (id.equals("minecraft:water")) return source ? "minecraft:water" : "minecraft:flowing_water";
            return source ? "minecraft:lava" : "minecraft:flowing_lava";
        }
        if ("true".equals(state.properties().get("waterlogged"))) return "minecraft:water";
        if (id.equals("minecraft:bubble_column") || id.equals("minecraft:kelp") || id.equals("minecraft:kelp_plant")
                || id.equals("minecraft:seagrass") || id.equals("minecraft:tall_seagrass")) {
            return "minecraft:water";
        }
        return "minecraft:empty";
    }

    /**
     * The identity of {@code state.getFluidState()} ({@code FluidState} objects are the canonical states of a fluid's state
     * definition, so {@code Objects.equals} compares type, {@code LEVEL} and {@code FALLING}): type, amount and falling flag
     * ({@code LiquidBlock} level 8 and above is {@code getFlowing(8, true)}).
     */
    public static String fluidStateKey(StateRef state) {
        String type = fluidType(state);
        if (!type.startsWith("minecraft:flowing_")) return type;
        int level = Integer.parseInt(state.properties().get("level"));
        return type + "[" + fluidAmount(state) + "," + (level >= 8) + "]";
    }

    /**
     * {@code state.getFluidState().getAmount()}: a source has 8; the flowing states of {@code LiquidBlock}'s cache have
     * {@code 8 - level} for levels 1..7 and 8 (falling) for level 8 and above ({@code getFlowing(amount, falling)} sets
     * {@code LEVEL = amount}); the empty fluid 0.
     */
    public static int fluidAmount(StateRef state) {
        String type = fluidType(state);
        if (type.equals("minecraft:empty")) return 0;
        if (type.equals("minecraft:water") || type.equals("minecraft:lava")) return 8;
        int level = Integer.parseInt(state.properties().get("level"));
        return level >= 8 ? 8 : 8 - level;
    }
}
