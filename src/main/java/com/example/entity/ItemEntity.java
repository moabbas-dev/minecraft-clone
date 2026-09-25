package com.example.entity;

import com.example.world.BlockType;
import lombok.Getter;
import org.joml.Vector3f;

/**
 * A dropped stack lying in the world. Independent of any inventory until picked up - the
 * {@link ItemEntityManager} is the only thing that moves items between the two. Position is the
 * bottom-center of the item's small bounding box (same convention as {@code Player.position}).
 */
public class ItemEntity {
    @Getter
    private final BlockType type;
    @Getter
    private int count;
    public final Vector3f position = new Vector3f();
    public final Vector3f velocity = new Vector3f();
    /** Seconds since spawn; only advances while the item's chunk is loaded. */
    @Getter
    float age = 0f;
    /** Seconds after spawn before the item can be picked up. */
    @Getter
    final float pickupDelay;
    @Getter
    boolean alive = true;
    @Getter
    boolean onGround = false;

    ItemEntity(BlockType type, int count, float pickupDelay) {
        this.type = type;
        this.count = count;
        this.pickupDelay = pickupDelay;
    }

    void addCount(int amount) {
        count += amount;
    }

    /** Removes {@code amount} from the stack; the entity dies the moment it reaches zero. */
    void removeCount(int amount) {
        count -= amount;
        if (count <= 0) {
            count = 0;
            alive = false;
        }
    }

    void kill() {
        alive = false;
    }

    public boolean canBePickedUp() {
        return alive && age >= pickupDelay;
    }
}
