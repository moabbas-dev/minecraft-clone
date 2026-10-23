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
 *     <li>{@code isFaceSturdy(level, pos, dir)} ({@code SupportType.FULL} over {@code getBlockSupportShape}, by default the
 *     collision shape for {@code CollisionContext.empty()});</li>
 *     <li>{@code canBeReplaced()} = {@code Properties.replaceable()}.</li>
 * </ul>
 * {@code BlockBehaviour} defaults (bytecode): {@code getShape} = {@code Shapes.block()}; {@code getCollisionShape} =
 * {@code hasCollision ? getShape : empty}; {@code getOcclusionShape} = {@code getShape(EMPTY, ZERO)};
 * {@code getBlockSupportShape} = the collision shape. A block not listed here throws: its facts have not been read.
 */
public final class BlockStateFacts {

    /** The shape classes of the listed blocks. */
    enum Kind {
        /**
         * Default shapes, collision and occlusion (no {@code noCollision}, {@code noOcclusion}, {@code dynamicShape},
         * {@code forceSolid*}, no shape override): solid, every face occluding and sturdy, not replaceable.
         */
        FULL(true, true, true, false),
        /** {@code IceBlock} with {@code Properties.noOcclusion()}: full collision (solid, sturdy), empty occlusion. */
        NO_OCCLUSION(true, false, true, false),
        /**
         * {@code MudBlock}: collision {@code Block.column(16, 0, 14)} (bounds size (1 + 0.875 + 1) / 3 &gt;= 0.729, so solid),
         * default full {@code getShape} (occluding), {@code getBlockSupportShape} = {@code Shapes.block()} (sturdy).
         */
        MUD(true, true, true, false),
        /**
         * {@code AirBlock} ({@code getShape} empty, {@code noCollision}, {@code replaceable}) and {@code LiquidBlock}
         * ({@code getShape} empty; {@code getCollisionShape} with the empty context: {@code alwaysCollideWithFluid()} false,
         * so empty; {@code noCollision}, {@code replaceable}): not solid, nothing occludes, not sturdy, replaceable.
         */
        EMPTY_REPLACEABLE(false, false, false, true);

        final boolean solid;
        final boolean occludesFaces;
        final boolean sturdyFaces;
        final boolean replaceable;

        Kind(boolean solid, boolean occludesFaces, boolean sturdyFaces, boolean replaceable) {
            this.solid = solid;
            this.occludesFaces = occludesFaces;
            this.sturdyFaces = sturdyFaces;
            this.replaceable = replaceable;
        }
    }

    private static final Map<String, Kind> KINDS = new HashMap<>();

    static {
        // Blocks.<clinit>: plain Block / DropExperienceBlock / SandBlock / ColoredFallingBlock / SnowyBlock (GrassBlock,
        // MyceliumBlock) / RotatedPillarBlock / InfestedBlock / InfestedRotatedPillarBlock / RedStoneOreBlock / MagmaBlock /
        // dyed terracotta (ColorCollection lambda: new Block(Properties.of()...)); none of these classes overrides a shape
        // method and their Properties chains call no noCollision / noOcclusion / dynamicShape / forceSolidOn/Off.
        for (String id : Set.of("stone", "granite", "diorite", "andesite", "deepslate", "tuff", "calcite", "dirt", "coarse_dirt",
                "grass_block", "podzol", "mycelium", "clay", "sand", "red_sand", "gravel", "sandstone", "red_sandstone",
                "snow_block", "packed_ice", "bedrock", "terracotta", "white_terracotta", "orange_terracotta",
                "yellow_terracotta", "brown_terracotta", "red_terracotta", "light_gray_terracotta", "coal_ore",
                "deepslate_coal_ore", "iron_ore", "deepslate_iron_ore", "copper_ore", "deepslate_copper_ore", "gold_ore",
                "deepslate_gold_ore", "redstone_ore", "deepslate_redstone_ore", "lapis_ore", "deepslate_lapis_ore",
                "diamond_ore", "deepslate_diamond_ore", "emerald_ore", "deepslate_emerald_ore", "raw_copper_block",
                "raw_iron_block", "infested_stone", "infested_deepslate", "magma_block", "sulfur", "cinnabar")) {
            KINDS.put("minecraft:" + id, Kind.FULL);
        }
        // ICE: new IceBlock(Properties.of()...noOcclusion()...)
        KINDS.put("minecraft:ice", Kind.NO_OCCLUSION);
        // MUD: new MudBlock(Properties.ofLegacyCopy(DIRT)...)
        KINDS.put("minecraft:mud", Kind.MUD);
        // AIR, CAVE_AIR, VOID_AIR: new AirBlock(Properties.of().replaceable().noCollision()...air());
        // WATER, LAVA: new LiquidBlock(..., Properties.of()...replaceable().noCollision()...liquid())
        for (String id : Set.of("air", "cave_air", "void_air", "water", "lava")) {
            KINDS.put("minecraft:" + id, Kind.EMPTY_REPLACEABLE);
        }
    }

    private BlockStateFacts() {
    }

    private static Kind kind(StateRef state) {
        Kind k = KINDS.get(state.id());
        if (k == null) throw new IllegalArgumentException("block shape facts not read from the jar for " + state.id());
        return k;
    }

    /** {@code BlockState.isSolid()}. */
    public static boolean isSolid(StateRef state) {
        return kind(state).solid;
    }

    /** {@code faceOcclusionShape != Shapes.empty() && Block.isShapeFullBlock(state.getFaceOcclusionShape(direction))}. */
    public static boolean faceOcclusionIsFullBlock(StateRef state, Direction direction) {
        return kind(state).occludesFaces;
    }

    /** {@code BlockState.isFaceSturdy(level, pos, direction)} ({@code SupportType.FULL}). */
    public static boolean isFaceSturdy(StateRef state, Direction direction) {
        return kind(state).sturdyFaces;
    }

    /** {@code BlockState.canBeReplaced()}. */
    public static boolean canBeReplaced(StateRef state) {
        return kind(state).replaceable;
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
}
