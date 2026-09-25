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

    /** {@code inventory} is null for saves written before inventories existed (they load as empty). */
    public record LoadedMeta(long seed, WorldSettings settings, Vector3f playerPos, float dayTime, int selectedIndex,
                             Inventory inventory) {
        public LoadedMeta(long seed, WorldSettings settings, Vector3f playerPos, float dayTime, int selectedIndex) {
            this(seed, settings, playerPos, dayTime, selectedIndex, null);
        }
    }

    /** Marks the optional inventory section appended after the original world.dat fields. */
    private static final int INVENTORY_MAGIC = 0x494E5601;

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
        saveMeta(dir, settings, seed, px, py, pz, dayTime, selectedIndex, null);
    }

    public static void saveMeta(File dir, WorldSettings settings, long seed, float px, float py, float pz, float dayTime,
                                int selectedIndex, Inventory inventory) {
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
            try { // absent in saves from before inventories existed - just end-of-file, not an error
                if (in.readInt() == INVENTORY_MAGIC) inventory = Inventory.read(in);
            } catch (EOFException ignored) {
                // old-format save: no inventory section
            }
            return new LoadedMeta(seed, new WorldSettings(name, type), new Vector3f(px, py, pz), dayTime, selectedIndex, inventory);
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
