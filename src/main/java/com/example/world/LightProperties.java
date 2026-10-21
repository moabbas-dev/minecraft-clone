package com.example.world;

/**
 * What a block does to light, as the 26.3 client defines it: how much of it a block swallows (the light dampening,
 * BlockBehaviour.getLightDampening: 15 for a block that is solid to rendering, 1 for leaves, ice, water and the like that
 * let light through but dim it, 0 for air, glass and plants), how much it emits, whether light can reach a corner past it
 * (BlockState.isLightPermeable), and how dark it makes the corners it touches in smooth lighting (Block.getShadeBrightness:
 * 0.2 for a block with a full collision shape, 1.0 for the rest and for glass and mud).
 */
public final class LightProperties {
    private LightProperties() {
    }

    private static final byte[] DAMPENING = new byte[BlockType.values().length];
    private static final float[] SHADE = new float[BlockType.values().length];

    static {
        for (BlockType t : BlockType.values()) {
            int d;
            if (t == BlockType.AIR) d = 0;
            // powder snow: noOcclusion (not solid render), but its outline shape is a full block, so it does not propagate skylight: 1
            else if (t.isFluid() || t == BlockType.ICE || t == BlockType.MANGROVE_ROOTS || t == BlockType.POWDER_SNOW
                    || ChunkMeshBuilder.isAnyLeaves(t)) d = 1;
            else if (t == BlockType.CHEST) d = 0; // not a full block: light passes
            else if (BlockSupport.isFarmland(t)) d = 1; // not solid to rendering and no skylight passage: 1
            else d = t.isOpaqueCube() ? 15 : 0;
            DAMPENING[t.ordinal()] = (byte) d;
            boolean fullCollision = t.isSolidCollision() && t != BlockType.CHEST;
            boolean notFull = BlockSupport.isFarmland(t); // 15/16 high, so not a full collision shape
            // powder snow's collision shape without an entity is empty (PowderSnowBlock.getCollisionShape), so 1.0
            SHADE[t.ordinal()] = !fullCollision || notFull || t == BlockType.GLASS || t == BlockType.MUD || t == BlockType.POWDER_SNOW ? 1.0f : 0.2f;
        }
    }

    /** Light lost entering this block, 0-15 (a block that is opaque to rendering is 15). */
    public static int dampening(BlockType t) {
        return DAMPENING[t.ordinal()];
    }

    /** The block light level a block gives off (the furnaces' lit state is added by the world on top of this). */
    public static int emission(BlockType t) {
        return (int) t.lightEmission;
    }

    /** True when the block is solid to rendering and stops light (vanilla's isSolidRender with a dampening). */
    public static boolean isSolidRender(BlockType t) {
        return DAMPENING[t.ordinal()] >= 15;
    }

    /** BlockState.isLightPermeable: everything but a block that is solid to rendering. */
    public static boolean isLightPermeable(BlockType t) {
        return !isSolidRender(t);
    }

    /** The smooth-lighting darkening of a corner next to this block: 0.2 for a full collision block, else 1.0. */
    public static float shadeBrightness(BlockType t) {
        return SHADE[t.ordinal()];
    }
}
