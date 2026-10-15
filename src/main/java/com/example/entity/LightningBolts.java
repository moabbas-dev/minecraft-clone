package com.example.entity;

import com.example.world.Chunk;
import com.example.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Lightning bolts as the 26.3 client's {@code LightningBolt} lives them: a bolt has a life of 2 ticks, flashes once and then 1 to 3 more times. Its first
 * tick is the thunder; every tick it has a life of 0 or more it lights the sky and strikes whatever is within 3 blocks
 * horizontally (and from 3 below to 6 above); when the life runs out, the next flash starts after a random 0-9 tick pause (a new
 * look for the bolt), until none are left. In a thunderstorm every loaded chunk near the player has a 1 in 100000 chance per tick
 * of being struck (ServerLevel.tickChunk).
 */
public final class LightningBolts {
    public static final class Bolt {
        public final float x, y, z;
        int life = 2;
        int flashes;
        /** The bolt's shape comes from this seed and changes with each flash. */
        public long seed;

        Bolt(float x, float y, float z, int flashes, long seed) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.flashes = flashes;
            this.seed = seed;
        }

        /** True while the bolt is lit (life 0 or more). */
        public boolean lit() {
            return life >= 0;
        }
    }

    /** What happens around a bolt, supplied by the game. */
    public interface Listener {
        /** The bolt's first tick: thunder and (on Normal and Hard) fire. */
        void struck(Bolt bolt);

        /** Every tick the bolt is lit: light the sky and hit what is near. */
        void lit(Bolt bolt);
    }

    public static final float CHUNK_STRIKE_CHANCE = 1f / 100000f;
    private final List<Bolt> bolts = new ArrayList<>();

    public List<Bolt> bolts() {
        return bolts;
    }

    public void clear() {
        bolts.clear();
    }

    public Bolt spawn(float x, float y, float z, Random random) {
        Bolt b = new Bolt(x, y, z, 1 + random.nextInt(3), random.nextLong());
        bolts.add(b);
        return b;
    }

    /** One game tick (LightningBolt.tick). */
    public void tick(Random random, Listener listener) {
        for (int i = bolts.size() - 1; i >= 0; i--) {
            Bolt b = bolts.get(i);
            if (b.life == 2) listener.struck(b);
            b.life--;
            if (b.life < 0) {
                if (b.flashes == 0) {
                    bolts.remove(i);
                    continue;
                }
                if (b.life < -random.nextInt(10)) {
                    b.flashes--;
                    b.life = 1;
                    b.seed = random.nextLong();
                }
            }
            if (b.life >= 0) listener.lit(b);
        }
    }

    /**
     * The thunderstorm's random strikes for one tick: every chunk within {@code radiusChunks} of the player's rolls a 1 in 100000.
     * A struck chunk picks a random column and strikes its top block, unless a living thing stands within 3 blocks of it, in which
     * case the bolt goes to a random one of those (ServerLevel.findLightningTargetAround). Returns the strike positions.
     *
     * @param livingAt positions (x, y, z) of mobs and the player that can be struck
     */
    public static List<float[]> randomStrikes(World world, Random random, int cx, int cz, int radiusChunks, List<float[]> livingAt) {
        List<float[]> out = new ArrayList<>();
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                if (dx * dx + dz * dz > radiusChunks * radiusChunks) continue;
                if (random.nextFloat() >= CHUNK_STRIKE_CHANCE) continue;
                Chunk chunk = world.getChunk(cx + dx, cz + dz);
                if (chunk == null || !chunk.isGenerated()) continue;
                int x = (cx + dx) * Chunk.SIZE_X + random.nextInt(Chunk.SIZE_X), z = (cz + dz) * Chunk.SIZE_Z + random.nextInt(Chunk.SIZE_Z);
                float y = world.getColumnHeightAt(x, z) + 1;
                List<float[]> near = new ArrayList<>();
                for (float[] p : livingAt) {
                    float ddx = p[0] - (x + 0.5f), ddz = p[2] - (z + 0.5f);
                    if (ddx * ddx + ddz * ddz <= 9f && p[1] >= y - 3f && p[1] <= y + 6f && world.getSkyLight((int) Math.floor(p[0]), (int) Math.floor(p[1]), (int) Math.floor(p[2])) >= 15) {
                        near.add(p);
                    }
                }
                if (!near.isEmpty()) {
                    float[] p = near.get(random.nextInt(near.size()));
                    out.add(new float[]{(float) Math.floor(p[0]) + 0.5f, (float) Math.floor(p[1]), (float) Math.floor(p[2]) + 0.5f});
                } else {
                    out.add(new float[]{x + 0.5f, y, z + 0.5f});
                }
            }
        }
        return out;
    }
}
