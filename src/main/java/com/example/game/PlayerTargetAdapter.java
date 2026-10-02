package com.example.game;

import com.example.entity.Targetable;
import com.example.physics.Player;
import com.example.survival.DamageSource;
import com.example.survival.PlayerSurvival;
import org.joml.Vector3f;

/** Lets mob AI target the (deliberately non-Entity) player through the generic
 *  {@link Targetable} contract; damage goes through {@link PlayerSurvival#damage}, the same
 *  centralized entry point fall/starvation damage already use. Game re-points it on world change. */
final class PlayerTargetAdapter implements Targetable {
    private Player player;
    private PlayerSurvival survival;

    void bind(Player player, PlayerSurvival survival) {
        this.player = player;
        this.survival = survival;
    }

    @Override
    public Vector3f getPosition() {
        return new Vector3f(player.position.x, player.position.y + 0.9f, player.position.z);
    }

    @Override
    public boolean isAlive() {
        return player != null && survival.getHealth().isAlive();
    }

    @Override
    public boolean damage(float amount, DamageSource source) {
        return survival.damage(amount, source);
    }
}
