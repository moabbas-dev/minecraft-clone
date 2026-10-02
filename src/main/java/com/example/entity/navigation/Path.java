package com.example.entity.navigation;

import org.joml.Vector3f;

import java.util.List;

/**
 * An ordered list of world-space waypoints (block-column centers) a {@link Navigator} walks
 * through one at a time. Deliberately dumb - all the "is this still a good path" logic lives in
 * the navigator, not here; this is just the sequence plus a cursor.
 */
public final class Path {
    private final List<Vector3f> waypoints;
    private int index = 0;

    public Path(List<Vector3f> waypoints) {
        this.waypoints = waypoints;
    }

    public boolean isComplete() {
        return index >= waypoints.size();
    }

    public Vector3f currentWaypoint() {
        return waypoints.get(index);
    }

    public void advance() {
        index++;
    }

    public int remaining() {
        return waypoints.size() - index;
    }

    public int length() {
        return waypoints.size();
    }
}
