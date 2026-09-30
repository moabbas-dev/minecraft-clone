package com.example.world;

import com.example.inventory.Inventory;
import org.joml.Vector3f;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Disk persistence for worlds: one folder per world under saves/, a small world.dat
 * metadata file, and one raw block file per generated chunk under chunks/.
 */
public class WorldIO {
    public static final File SAVES_ROOT = new File("saves");

    public record SavedWorld(String folderName, String displayName, WorldSettings.WorldType type, long lastPlayed) {
    }

    /** {@code inventory}/{@code survival} are null for saves written before each existed (an
     *  absent inventory loads as empty; an absent survival snapshot means the caller should
     *  start a fresh {@code PlayerSurvival} instead of restoring one - this class deliberately
     *  doesn't depend on the {@code survival} package, so it hands back plain numbers, not a
     *  {@code PlayerSurvival}/{@code HealthComponent}/{@code HungerComponent} instance). */
    public record LoadedMeta(long seed, WorldSettings settings, Vector3f playerPos, float dayTime, int selectedIndex,
                             Inventory inventory, SurvivalSnapshot survival) {
        public LoadedMeta(long seed, WorldSettings settings, Vector3f playerPos, float dayTime, int selectedIndex) {
            this(seed, settings, playerPos, dayTime, selectedIndex, null, null);
        }
    }

    /** Plain numeric snapshot of a {@code PlayerSurvival} - health, food/hunger, saturation,
     *  exhaustion - written/read by {@code Game}, never interpreted here. */
    public record SurvivalSnapshot(float health, float foodLevel, float saturation, float exhaustion) {
    }

    /** Marks the optional inventory section appended after the original world.dat fields. */
    private static final int INVENTORY_MAGIC = 0x494E5601;
    /** Marks the optional survival (difficulty + health/hunger) section appended after that. */
    private static final int SURVIVAL_MAGIC = 0x53555256;

    public static String sanitizeFolderName(String name) {
        String cleaned = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return cleaned.isEmpty() ? "World" : cleaned;
    }

    public static File worldDir(String folderName) {
        return new File(SAVES_ROOT, folderName);
    }

    public static List<SavedWorld> listSavedWorlds() {
        List<SavedWorld> result = new ArrayList<>();
        File[] dirs = SAVES_ROOT.listFiles(File::isDirectory);
        if (dirs == null) return result;
        for (File dir : dirs) {
            LoadedMeta meta = loadMeta(dir);
            if (meta == null) continue;
            result.add(new SavedWorld(dir.getName(), meta.settings().name(), meta.settings().type(), dir.lastModified()));
        }
        result.sort((a, b) -> Long.compare(b.lastPlayed(), a.lastPlayed()));
        return result;
    }

    public static void saveMeta(File dir, WorldSettings settings, long seed, float px, float py, float pz, float dayTime, int selectedIndex) {
        saveMeta(dir, settings, seed, px, py, pz, dayTime, selectedIndex, null, null);
    }

    public static void saveMeta(File dir, WorldSettings settings, long seed, float px, float py, float pz, float dayTime,
                                int selectedIndex, Inventory inventory) {
        saveMeta(dir, settings, seed, px, py, pz, dayTime, selectedIndex, inventory, null);
    }

    /** {@code survival} is null exactly when there's nothing meaningful to persist yet (e.g. no
     *  active player) - the difficulty in {@code settings} is written whenever a survival
     *  snapshot is, since both live in the one new trailing section (see SURVIVAL_MAGIC) rather
     *  than each getting their own ad-hoc appended field. */
    public static void saveMeta(File dir, WorldSettings settings, long seed, float px, float py, float pz, float dayTime,
                                int selectedIndex, Inventory inventory, SurvivalSnapshot survival) {
        dir.mkdirs();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(dir, "world.dat"))))) {
            out.writeLong(seed);
            out.writeInt(settings.type().ordinal());
            out.writeUTF(settings.name());
            out.writeFloat(px);
            out.writeFloat(py);
            out.writeFloat(pz);
            out.writeFloat(dayTime);
            out.writeInt(selectedIndex);
            if (inventory != null) {
                out.writeInt(INVENTORY_MAGIC);
                inventory.write(out);
            }
            if (survival != null) {
                out.writeInt(SURVIVAL_MAGIC);
                out.writeInt(settings.difficulty().ordinal());
                out.writeFloat(survival.health());
                out.writeFloat(survival.foodLevel());
                out.writeFloat(survival.saturation());
                out.writeFloat(survival.exhaustion());
            }
        } catch (IOException e) {
            System.err.println("Failed to save world metadata: " + e.getMessage());
        }
    }

    public static LoadedMeta loadMeta(File dir) {
        File file = new File(dir, "world.dat");
        if (!file.isFile()) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            long seed = in.readLong();
            WorldSettings.WorldType type = WorldSettings.WorldType.values()[in.readInt()];
            String name = in.readUTF();
            float px = in.readFloat(), py = in.readFloat(), pz = in.readFloat();
            float dayTime = in.readFloat();
            int selectedIndex = in.readInt();
            Inventory inventory = null;
            Difficulty difficulty = Difficulty.NORMAL;
            SurvivalSnapshot survival = null;
            try { // absent in saves from before inventories existed - just end-of-file, not an error
                if (in.readInt() == INVENTORY_MAGIC) inventory = Inventory.read(in);
            } catch (EOFException ignored) {
                // old-format save: no inventory section
            }
            try { // absent in saves from before health/hunger existed - same "just EOF" tolerance
                if (in.readInt() == SURVIVAL_MAGIC) {
                    difficulty = Difficulty.values()[in.readInt()];
                    survival = new SurvivalSnapshot(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
                }
            } catch (EOFException ignored) {
                // old-format save: no survival section
            }
            return new LoadedMeta(seed, new WorldSettings(name, type, difficulty), new Vector3f(px, py, pz), dayTime, selectedIndex, inventory, survival);
        } catch (IOException e) {
            System.err.println("Failed to load world metadata from " + dir + ": " + e.getMessage());
            return null;
        }
    }

    public static void saveChunk(File dir, Chunk chunk) {
        File chunksDir = new File(dir, "chunks");
        chunksDir.mkdirs();
        File file = new File(chunksDir, chunk.chunkX + "_" + chunk.chunkZ + ".chunk");
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            out.write(chunk.exportBlocks());
        } catch (IOException e) {
            System.err.println("Failed to save chunk " + chunk.chunkX + "," + chunk.chunkZ + ": " + e.getMessage());
        }
    }

    public static boolean chunkExists(File dir, int cx, int cz) {
        return chunkFile(dir, cx, cz).isFile();
    }

    public static byte[] loadChunk(File dir, int cx, int cz) {
        try {
            return Files.readAllBytes(chunkFile(dir, cx, cz).toPath());
        } catch (IOException e) {
            return null;
        }
    }

    private static File chunkFile(File dir, int cx, int cz) {
        return new File(new File(dir, "chunks"), cx + "_" + cz + ".chunk");
    }

    public static void deleteWorld(String folderName) {
        deleteRecursively(worldDir(folderName));
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }
}
