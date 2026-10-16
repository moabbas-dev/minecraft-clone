package com.example.entity.navigation;

import com.example.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Bounded A* over a coarse 2.5D column grid - not real vanilla's full 3D voxel pathfinder (the
 * request explicitly says not to attempt that). A "node" is one (x, z) column at whatever Y
 * counts as its walkable surface there; edges are the 8 neighboring columns, allowed only when
 * the height change is a single step up, any step down (falling), or level, and - for diagonal
 * moves - both orthogonal neighbors are also open (the standard grid-pathfinding rule against
 * cutting through a solid corner).
 *
 * <p>Bounding, per the request's own "do not search the entire world" rule: at most
 * {@link #MAX_EXPANDED_NODES} nodes are ever expanded, and if the real target is farther than
 * {@link #SEARCH_RADIUS_COLUMNS} away, the search aims at the point that far along the straight
 * line toward it instead - the mob paths a real, obstacle-aware bounded leg of the journey, then
 * {@link GroundNavigator} naturally asks for the next leg once it arrives, rather than ever
 * trying to solve the whole route in one search.
 */
final class Pathfinder {
    static final int SEARCH_RADIUS_COLUMNS = 16;
    private static final int MAX_EXPANDED_NODES = 600;
    /** How far below a reference Y a walkable surface may be found (a generous, bounded fall -
     *  mobs take no fall damage in this milestone, so there's no cost model reason to limit this
     *  tightly; it only exists so the vertical scan itself stays bounded). */
    private static final int GROUND_SEARCH_DOWN = 10;
    private static final int GROUND_SEARCH_UP = 4;
    /** Only a single block of step-up is allowed between adjacent columns (the request's own
     *  "basic one-block elevation changes" scope) - a bigger rise is simply not a valid edge. */
    private static final int MAX_STEP_UP = 1;

    private Pathfinder() {
    }

    private record Node(int x, int z, int y) {
    }

    private record Entry(Node node, float f) {
    }

    /** Returns null when no walkable route exists from the start column (e.g. the mob itself
     *  isn't standing somewhere valid) or no reachable column improves on the start within the
     *  search bound - {@link GroundNavigator} treats that as "walk straight at it" so a mob is
     *  never left doing nothing. */
    static Path findPath(World world, Vector3f startPos, Vector3f goalPos, float mobHeight) {
        int mobHeightBlocks = Math.max(1, (int) Math.ceil(mobHeight));
        int sx = (int) Math.floor(startPos.x), sz = (int) Math.floor(startPos.z);
        Integer startY = findGroundY(world, sx, sz, (int) Math.floor(startPos.y), mobHeightBlocks);
        if (startY == null) return null;

        float dx = goalPos.x - startPos.x, dz = goalPos.z - startPos.z;
        float totalDist = (float) Math.sqrt(dx * dx + dz * dz);
        float aimX = goalPos.x, aimZ = goalPos.z;
        if (totalDist > SEARCH_RADIUS_COLUMNS) {
            float scale = SEARCH_RADIUS_COLUMNS / totalDist;
            aimX = startPos.x + dx * scale;
            aimZ = startPos.z + dz * scale;
        }
        int gx = (int) Math.floor(aimX), gz = (int) Math.floor(aimZ);
        Integer goalY = findGroundY(world, gx, gz, startY, mobHeightBlocks);
        if (goalY == null) {
            // Try the immediate neighbors of the intended goal column - tolerates aiming at a
            // slightly-off column (e.g. right at a ledge) without failing the whole search.
            outer:
            for (int r = 1; r <= 2 && goalY == null; r++) {
                for (int ddx = -r; ddx <= r; ddx++) {
                    for (int ddz = -r; ddz <= r; ddz++) {
                        Integer y = findGroundY(world, gx + ddx, gz + ddz, startY, mobHeightBlocks);
                        if (y != null) {
                            gx += ddx;
                            gz += ddz;
                            goalY = y;
                            break outer;
                        }
                    }
                }
            }
            if (goalY == null) return null;
        }

        Node start = new Node(sx, sz, startY);
        Node goal = new Node(gx, gz, goalY);
        if (start.x() == goal.x() && start.z() == goal.z()) return null; // already there

        Map<Node, Float> gScore = new HashMap<>();
        Map<Node, Node> cameFrom = new HashMap<>();
        PriorityQueue<Entry> open = new PriorityQueue<>((a, b) -> Float.compare(a.f(), b.f()));
        gScore.put(start, 0f);
        open.add(new Entry(start, heuristic(start, goal)));

        Node best = start;
        float bestH = heuristic(start, goal);
        int expanded = 0;

        while (!open.isEmpty() && expanded < MAX_EXPANDED_NODES) {
            Node current = open.poll().node();
            float currentG = gScore.getOrDefault(current, Float.MAX_VALUE);
            expanded++;

            float h = heuristic(current, goal);
            if (h < bestH) {
                bestH = h;
                best = current;
            }
            if (current.x() == goal.x() && current.z() == goal.z()) {
                best = current;
                break;
            }

            for (int[] d : NEIGHBOR_OFFSETS) {
                int ddx = d[0], ddz = d[1];
                if (ddx != 0 && ddz != 0) {
                    // Diagonal: forbid cutting a solid corner - both orthogonal neighbors must
                    // also be open at a compatible height, not just the diagonal destination.
                    if (findGroundY(world, current.x() + ddx, current.z(), current.y(), mobHeightBlocks) == null) continue;
                    if (findGroundY(world, current.x(), current.z() + ddz, current.y(), mobHeightBlocks) == null) continue;
                }
                Integer ny = findGroundY(world, current.x() + ddx, current.z() + ddz, current.y(), mobHeightBlocks);
                if (ny == null) continue;
                int rise = ny - current.y();
                if (rise > MAX_STEP_UP) continue;

                Node neighbor = new Node(current.x() + ddx, current.z() + ddz, ny);
                float stepCost = (ddx != 0 && ddz != 0) ? 1.414f : 1f;
                stepCost += Math.abs(rise) * 0.1f;
                float tentativeG = currentG + stepCost;
                if (tentativeG < gScore.getOrDefault(neighbor, Float.MAX_VALUE)) {
                    gScore.put(neighbor, tentativeG);
                    cameFrom.put(neighbor, current);
                    open.add(new Entry(neighbor, tentativeG + heuristic(neighbor, goal)));
                }
            }
        }

        if (best.x() == start.x() && best.z() == start.z()) return null; // never made any progress
        return reconstruct(cameFrom, start, best);
    }

    private static final int[][] NEIGHBOR_OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
    };

    private static float heuristic(Node a, Node b) {
        float dx = a.x() - b.x(), dz = a.z() - b.z();
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private static Path reconstruct(Map<Node, Node> cameFrom, Node start, Node end) {
        List<Node> nodes = new ArrayList<>();
        Node cur = end;
        while (cur != null && !cur.equals(start)) {
            nodes.add(cur);
            cur = cameFrom.get(cur);
        }
        java.util.Collections.reverse(nodes);
        List<Vector3f> waypoints = new ArrayList<>(nodes.size());
        for (Node n : nodes) waypoints.add(new Vector3f(n.x() + 0.5f, n.y(), n.z() + 0.5f));
        return waypoints.isEmpty() ? null : new Path(waypoints);
    }

    /** The walkable surface Y closest to {@code referenceY} at this column, or null if none
     *  exists within the bounded vertical scan - solid ground directly beneath, and clear
     *  headroom for the mob's own height above it. */
    private static Integer findGroundY(World world, int x, int z, int referenceY, int mobHeightBlocks) {
        int minY = Math.max(com.example.world.Chunk.MIN_Y, referenceY - GROUND_SEARCH_DOWN);
        int maxY = referenceY + GROUND_SEARCH_UP;
        Integer best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int y = minY; y <= maxY; y++) {
            if (isWalkableStand(world, x, y, z, mobHeightBlocks)) {
                int dist = Math.abs(y - referenceY);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = y;
                }
            }
        }
        return best;
    }

    private static boolean isWalkableStand(World world, int x, int y, int z, int mobHeightBlocks) {
        if (!world.isSolidCollision(x, y - 1, z)) return false;
        for (int dy = 0; dy < mobHeightBlocks; dy++) {
            if (world.isSolidCollision(x, y + dy, z)) return false;
        }
        return true;
    }
}
