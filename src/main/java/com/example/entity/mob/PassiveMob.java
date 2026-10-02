package com.example.entity.mob;

import com.example.entity.Mob;
import com.example.entity.ai.FloatGoal;
import com.example.entity.ai.LookAtPlayerGoal;
import com.example.entity.ai.PanicGoal;
import com.example.entity.ai.RandomLookAroundGoal;
import com.example.entity.ai.RandomStrollGoal;
import com.example.entity.navigation.GroundNavigator;
import com.example.world.BlockDrops;
import com.example.world.BlockType;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Shared shape of the farm animals (research: docs/research/passive-mobs.md): float, panic, stroll,
 * look at player, random look - the vanilla goal list with its priorities - persist forever, 1-3 XP.
 * Subclasses add the stats, panic speed and drops.
 */
public abstract class PassiveMob extends Mob {
    protected PassiveMob(Vector3f spawnPosition, float yaw, float maxHealth, float panicMultiplier) {
        super(spawnPosition, yaw, maxHealth);
        setNavigator(new GroundNavigator());
        getGoals()
                .add(0, new FloatGoal())
                .add(1, new PanicGoal(panicMultiplier))
                .add(5, new RandomStrollGoal())
                .add(6, new LookAtPlayerGoal())
                .add(7, new RandomLookAroundGoal());
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public int xpReward(Random random) {
        return 1 + random.nextInt(3);
    }

    /** One drop line: {@code min..max} uniformly (each count equally likely, ignoring Looting). */
    protected static void roll(List<BlockDrops.Resolved> out, Random random, BlockType item, int min, int max) {
        int n = min + random.nextInt(max - min + 1);
        if (n > 0) out.add(new BlockDrops.Resolved(item, n));
    }

    protected static List<BlockDrops.Resolved> newDrops() {
        return new ArrayList<>(2);
    }
}
