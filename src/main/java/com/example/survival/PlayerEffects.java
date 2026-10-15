package com.example.survival;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The player's active status effects. Adding an effect that is already active keeps the stronger one (the higher amplifier, or the
 * longer duration at the same amplifier), as MobEffectInstance.update does. Time is counted in game ticks and advanced by
 * {@link #tick}, which calls back into {@link PlayerSurvival} for what each effect does.
 */
public final class PlayerEffects {
    /** One active effect: {@code duration} ticks left, {@code amplifier} 0 for level I. */
    public record Instance(StatusEffect type, int duration, int amplifier) {
    }

    private final Map<StatusEffect, Instance> active = new EnumMap<>(StatusEffect.class);

    /** Adds the effect; returns true when it is new or replaced a weaker one (so its start effect should run). */
    public boolean add(StatusEffect type, int durationTicks, int amplifier) {
        Instance old = active.get(type);
        if (old != null && (old.amplifier() > amplifier || (old.amplifier() == amplifier && old.duration() >= durationTicks))) return false;
        active.put(type, new Instance(type, durationTicks, amplifier));
        return true;
    }

    public boolean has(StatusEffect type) {
        return active.containsKey(type);
    }

    public Instance get(StatusEffect type) {
        return active.get(type);
    }

    public boolean isEmpty() {
        return active.isEmpty();
    }

    /** The active effects, beneficial ones first as the HUD lists them. */
    public List<Instance> all() {
        return new ArrayList<>(active.values());
    }

    public void clear() {
        active.clear();
    }

    /** One game tick: every effect does its tick work, then loses a tick; finished ones are removed and reported. */
    void tick(PlayerSurvival survival) {
        List<Instance> expired = new ArrayList<>();
        for (Instance e : new ArrayList<>(active.values())) {
            if (e.type().appliesThisTick(e.duration(), e.amplifier())) survival.applyEffectTick(e.type(), e.amplifier());
            if (e.duration() - 1 <= 0) {
                active.remove(e.type());
                expired.add(e);
            } else {
                active.put(e.type(), new Instance(e.type(), e.duration() - 1, e.amplifier()));
            }
        }
        for (Instance e : expired) survival.onEffectEnded(e.type(), e.amplifier());
    }
}
