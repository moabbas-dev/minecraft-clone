package com.example.entity;

import com.example.survival.DamageSource;
import com.example.world.BlockDrops;
import com.example.world.BlockType;
import com.example.world.Difficulty;
import com.example.world.Raycast;
import com.example.world.World;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Vanilla explosion (research: docs/research/hostile-mob-models.md): blocks are destroyed by rays cast through the
 * surface of a 16x16x16 grid, entities take damage that falls off linearly out to twice the power.
 */
public final class Explosions {
    private Explosions() {
    }

    /** Blast resistance by block (vanilla values for the blocks that exist here; hardness-like for the rest). */
    public static float blastResistance(BlockType t) {
        return switch (t) {
            case BEDROCK -> 3_600_000f;
            case OBSIDIAN -> 1200f;
            case WATER -> 100f;
            case STONE, COBBLESTONE, MOSSY_COBBLESTONE, SMOOTH_STONE, BRICKS, IRON_BLOCK, GOLD_BLOCK, DIAMOND_BLOCK -> 6f;
            case TERRACOTTA, WHITE_TERRACOTTA, ORANGE_TERRACOTTA, YELLOW_TERRACOTTA, BROWN_TERRACOTTA, RED_TERRACOTTA,
                    LIGHT_GRAY_TERRACOTTA -> 4.2f;
            // G28 (26.3 Blocks static initializer, strength(destroyTime, resistance)); the rest of them equal their hardness
            case CINNABAR, DEEPSLATE, COBBLED_DEEPSLATE, GRANITE, RAW_COPPER_BLOCK, RAW_IRON_BLOCK, SULFUR, TUFF -> 6f;
            case DEEPSLATE_IRON_ORE, DEEPSLATE_COAL_ORE, DEEPSLATE_COPPER_ORE, DEEPSLATE_DIAMOND_ORE, DEEPSLATE_EMERALD_ORE,
                    DEEPSLATE_GOLD_ORE, DEEPSLATE_LAPIS_ORE, DEEPSLATE_REDSTONE_ORE -> 3f;
            // C1: andesite and diorite strength(1.5, 6); InfestedBlock explosionResistance(0.75)
            case ANDESITE, DIORITE -> 6f;
            case INFESTED_STONE, INFESTED_DEEPSLATE -> 0.75f;
            case OAK_PLANKS -> 3f;
            case SANDSTONE -> 0.8f;
            case ICE -> 0.5f;
            case TNT -> 0f;
            default -> Math.max(0f, t.hardness);
        };
    }

    /** Vanilla entity damage for an explosion of {@code power}: {@code floor((i^2 + i) / 2 * 7 * 2p + 1)}, 0 beyond 2p. */
    public static float entityDamage(float distance, float power, float exposure) {
        float reach = power * 2f;
        if (distance >= reach) return 0f;
        float impact = (1f - distance / reach) * exposure;
        if (impact <= 0f) return 0f;
        return (float) Math.floor((impact * impact + impact) / 2f * 7f * reach + 1f);
    }

    /** Difficulty scaling vanilla applies to explosion damage dealt to players. */
    public static float scaleForDifficulty(float damage, Difficulty d) {
        return switch (d) {
            case PEACEFUL -> 0f;
            case EASY -> Math.min(damage / 2f + 1f, damage);
            case NORMAL -> damage;
            case HARD -> damage * 1.5f;
        };
    }

    /** Positions of every block a blast would destroy (without changing the world), for tests and for detonate. */
    public static Set<Long> blastBlocks(World world, Vector3f center, float power, Random random) {
        Set<Long> out = new HashSet<>();
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                for (int k = 0; k < 16; k++) {
                    if (i != 0 && i != 15 && j != 0 && j != 15 && k != 0 && k != 15) continue; // surface of the grid only
                    float dx = i / 15f * 2f - 1f, dy = j / 15f * 2f - 1f, dz = k / 15f * 2f - 1f;
                    float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    dx /= len;
                    dy /= len;
                    dz /= len;
                    float x = center.x, y = center.y, z = center.z;
                    float strength = power * (0.7f + random.nextFloat() * 0.6f);
                    while (strength > 0f) {
                        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
                        BlockType b = world.getBlock(bx, by, bz);
                        if (b != BlockType.AIR) {
                            strength -= (blastResistance(b) + 0.3f) * 0.3f;
                            if (strength > 0f && b != BlockType.BEDROCK && !b.isFluid()) out.add(pack(bx, by, bz));
                        }
                        strength -= 0.225f;
                        x += dx * 0.3f;
                        y += dy * 0.3f;
                        z += dz * 0.3f;
                    }
                }
            }
        }
        return out;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | ((y - com.example.world.Chunk.MIN_Y) & 0xFFF);
    }

    private static int unpackX(long p) {
        return (int) (p >> 38) << 6 >> 6;
    }

    private static int unpackZ(long p) {
        return (int) ((p >> 12) & 0x3FFFFFF) << 6 >> 6;
    }

    private static int unpackY(long p) {
        return (int) (p & 0xFFF) + com.example.world.Chunk.MIN_Y;
    }

    /** Runs the whole explosion: damages and pushes {@code victims}, destroys blocks and drops 1 in {@code power} of them. */
    public static void detonate(World world, Vector3f center, float power, List<Targetable> victims,
                                ItemEntityManager items, Random random, Difficulty difficulty) {
        for (Targetable t : victims) {
            if (!t.isAlive()) continue;
            Vector3f p = t.getPosition();
            float distance = p.distance(center);
            float exposure = exposure(world, center, p);
            float dmg = entityDamage(distance, power, exposure);
            if (dmg <= 0f) continue;
            if (!(t instanceof LivingEntity)) dmg = scaleForDifficulty(dmg, difficulty);
            if (dmg <= 0f) continue;
            float impact = (1f - distance / (power * 2f)) * exposure;
            t.hurtBy(dmg, DamageSource.EXPLOSION, center, Math.max(0.4f, impact * 2f));
        }
        for (long packed : blastBlocks(world, center, power, random)) {
            int x = unpackX(packed), y = unpackY(packed), z = unpackZ(packed);
            BlockType b = world.getBlock(x, y, z);
            if (b == BlockType.AIR) continue;
            world.setBlock(x, y, z, BlockType.AIR);
            if (random.nextFloat() < 1f / power) {
                for (BlockDrops.Resolved d : BlockDrops.resolve(b, random, BlockType.IRON_PICKAXE)) {
                    items.spawn(d.item(), d.count(), new Vector3f(x + 0.5f, y + 0.5f, z + 0.5f), random);
                }
            }
        }
    }

    /** Fraction of probe points (feet, middle, head) of a victim the blast can see; blocks in between shield it. */
    static float exposure(World world, Vector3f from, Vector3f victimCenter) {
        float seen = 0f;
        float[] heights = {-0.8f, 0f, 0.7f};
        for (float h : heights) {
            Vector3f to = new Vector3f(victimCenter.x, victimCenter.y + h, victimCenter.z);
            Vector3f dir = new Vector3f(to).sub(from);
            float len = dir.length();
            if (len < 1e-3f || Raycast.cast(world, from, dir, len) == null) seen += 1f;
        }
        return seen / heights.length;
    }
}
