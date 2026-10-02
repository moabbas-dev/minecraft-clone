package com.example.entity.mob;

import com.example.entity.Mob;
import com.example.world.Difficulty;
import org.joml.Vector3f;

import java.util.function.Supplier;

/** Creates a mob from its saved type id (see {@link Mob#typeId()}); unknown ids give null and are skipped on load. */
public final class MobTypes {
    private MobTypes() {
    }

    public static Mob create(String typeId, Vector3f position, float yaw, Supplier<Difficulty> difficulty) {
        return switch (typeId) {
            case "zombie" -> new Zombie(position, yaw, difficulty);
            case "pig" -> new Pig(position, yaw);
            case "cow" -> new Cow(position, yaw);
            case "chicken" -> new Chicken(position, yaw);
            default -> null;
        };
    }
}
