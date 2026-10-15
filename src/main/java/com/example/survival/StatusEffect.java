package com.example.survival;

/** The status effects the player can have here (MobEffects of the 26.3 client), with the icon file under {@code textures/mob_effect/}. */
public enum StatusEffect {
    /** Adds 0.005 x (level + 1) exhaustion every tick (HungerMobEffect). */
    HUNGER("hunger", false),
    /** 1 magic damage every 25 >> amplifier ticks, never taking the last half heart (PoisonMobEffect). */
    POISON("poison", false),
    /** Heals 1 every 50 >> amplifier ticks (RegenerationMobEffect). */
    REGENERATION("regeneration", true),
    /** 4 x (level + 1) extra health points that are lost first (AbsorptionMobEffect). */
    ABSORPTION("absorption", true);

    public final String icon;
    public final boolean beneficial;

    StatusEffect(String icon, boolean beneficial) {
        this.icon = icon;
        this.beneficial = beneficial;
    }

    /** MobEffect.shouldApplyEffectTickThisTick: whether the effect does its work on this tick of its remaining {@code duration}. */
    public boolean appliesThisTick(int duration, int amplifier) {
        return switch (this) {
            case POISON -> {
                int interval = 25 >> amplifier;
                yield interval <= 0 || duration % interval == 0;
            }
            case REGENERATION -> {
                int interval = 50 >> amplifier;
                yield interval <= 0 || duration % interval == 0;
            }
            case HUNGER -> true;
            case ABSORPTION -> false;
        };
    }
}
