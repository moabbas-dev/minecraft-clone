package com.example.game;

import com.example.combat.EquipmentSlot;
import org.joml.Matrix4f;

/**
 * The player model for the inventory preview: vanilla's wide ("Steve") player in the 64x64 skin layout, its outer skin
 * layers (hat, jacket, sleeves, pants) and the humanoid armor layers (64x32 textures, boxes grown by 1 px for helmet,
 * chestplate and boots and by 0.5 px for leggings). Pure geometry on top of {@link MobModelBuilder}'s box writer; the
 * model is built around the feet at the origin, facing -Z at body yaw 0, 1.8 blocks tall.
 */
final class PlayerModelBuilder {
    private PlayerModelBuilder() {
    }

    static final int SKIN_W = 64, SKIN_H = 64, ARMOR_W = 64, ARMOR_H = 32;
    /** Vanilla's undyed leather armor colour. */
    static final float[] LEATHER_TINT = {160 / 255f, 101 / 255f, 64 / 255f};
    private static final float[] WHITE = {1f, 1f, 1f};
    /** The player renderer scales the 32-pixel model by 15/16, giving the 1.8-block player (rounded). */
    static final float MODEL_SCALE = 0.9375f;
    /** The neck pivot in blocks above the feet (model pixel y = 0 on the 24-pixel frame). */
    private static final float NECK_Y = 1.5f * MODEL_SCALE;

    /** The pose the preview shows: the body turned by {@code bodyYaw}, the head by {@code headYaw} more and tilted by {@code headPitch} (radians). */
    record Pose(float bodyYaw, float headYaw, float headPitch) {
    }

    private static Matrix4f headTurn(Pose pose) {
        return new Matrix4f().translate(0f, NECK_Y, 0f).rotateY(pose.headYaw()).rotateX(pose.headPitch()).translate(0f, -NECK_Y, 0f);
    }

    /** The skin's base layer (head, body, arms, legs) or its outer layer (hat, jacket, sleeves, pants). */
    static int emitSkin(float[] out, int n, Pose pose, boolean outerLayer, float sky) {
        MobModelBuilder.Writer w = MobModelBuilder.writer(out, n, pose.bodyYaw(), sky, WHITE, SKIN_W, SKIN_H);
        w.scale = MODEL_SCALE;
        w.inflate = outerLayer ? 0.5f : 0f;
        w.part = headTurn(pose);
        w.box(0, -4, 0, 8, 8, 8, outerLayer ? 32 : 0, 0, 0, false);                 // head / hat
        w.part = null;
        w.inflate = outerLayer ? 0.25f : 0f;
        w.box(0, 6, 0, 8, 12, 4, 16, outerLayer ? 32 : 16, 0, false);                // body / jacket
        w.box(-6, 6, 0, 4, 12, 4, 40, outerLayer ? 32 : 16, 0, false);               // right arm / sleeve
        w.box(6, 6, 0, 4, 12, 4, outerLayer ? 48 : 32, 48, 0, false);                // left arm / sleeve
        w.box(-1.9f, 18, 0, 4, 12, 4, 0, outerLayer ? 32 : 16, 0, false);            // right leg / pants
        w.box(1.9f, 18, 0, 4, 12, 4, outerLayer ? 0 : 16, 48, 0, false);             // left leg / pants
        return w.n;
    }

    /**
     * One worn armor piece. Helmet, chestplate and boots use the outer armor texture (boxes +1 px); leggings use the inner
     * one (+0.5 px). Armor textures have a single arm and leg rectangle; the left limb mirrors it.
     */
    static int emitArmor(float[] out, int n, Pose pose, EquipmentSlot slot, float sky) {
        MobModelBuilder.Writer w = MobModelBuilder.writer(out, n, pose.bodyYaw(), sky, WHITE, ARMOR_W, ARMOR_H);
        w.scale = MODEL_SCALE;
        switch (slot) {
            case HEAD -> {
                w.inflate = 1f;
                w.part = headTurn(pose);
                w.box(0, -4, 0, 8, 8, 8, 0, 0, 0, false);
            }
            case CHEST -> {
                w.inflate = 1f;
                w.box(0, 6, 0, 8, 12, 4, 16, 16, 0, false);
                w.box(-6, 6, 0, 4, 12, 4, 40, 16, 0, false);
                w.box(6, 6, 0, 4, 12, 4, 40, 16, 0, true);
            }
            case LEGS -> {
                w.inflate = 0.5f;
                w.box(0, 6, 0, 8, 12, 4, 16, 16, 0, false);
                w.box(-1.9f, 18, 0, 4, 12, 4, 0, 16, 0, false);
                w.box(1.9f, 18, 0, 4, 12, 4, 0, 16, 0, true);
            }
            case FEET -> {
                w.inflate = 1f;
                w.box(-1.9f, 18, 0, 4, 12, 4, 0, 16, 0, false);
                w.box(1.9f, 18, 0, 4, 12, 4, 0, 16, 0, true);
            }
        }
        return w.n;
    }
}
