package com.example.entity.mob;

import com.example.entity.Mob;
import com.example.world.Difficulty;
import org.joml.Vector3f;

import java.util.function.Supplier;

/** Creates a mob from its saved type id (see {@link Mob#typeId()}); unknown ids give null and are skipped on load. */
public final class MobTypes {
    private MobTypes() {
    }

    /** {@code pig_baby}, {@code cow_baby} and {@code chicken_baby} spawn the baby variant (the saved id stays the species). */
    public static Mob create(String typeId, Vector3f position, float yaw, Supplier<Difficulty> difficulty) {
        if (typeId.endsWith("_baby")) {
            Mob m = create(typeId.substring(0, typeId.length() - "_baby".length()), position, yaw, difficulty);
            if (m instanceof PassiveMob animal) animal.makeBaby();
            return m instanceof PassiveMob ? m : null;
        }
        return switch (typeId) {
            case "zombie" -> new Zombie(position, yaw, difficulty);
            case "creeper" -> new Creeper(position, yaw, difficulty);
            case "skeleton" -> new Skeleton(position, yaw, difficulty);
            case "spider" -> new Spider(position, yaw, difficulty);
            case "pig" -> new Pig(position, yaw);
            case "cow" -> new Cow(position, yaw);
            case "chicken" -> new Chicken(position, yaw);
            default -> null;
        };
    }
}
