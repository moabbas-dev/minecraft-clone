package com.example.world;

import com.example.inventory.Inventory;
import com.example.inventory.ItemStack;
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

    /** Changes only the display name stored in world.dat (the folder name stays, so saves are
     *  never moved); every other byte - position, inventory, survival state - is copied through
     *  untouched. Returns false (nothing changed) if the file is missing or unreadable. */
    public static boolean renameWorld(String folderName, String newName) {
        return renameWorld(worldDir(folderName), newName);
    }

    public static boolean renameWorld(File dir, String newName) {
        File file = new File(dir, "world.dat");
        if (!file.isFile() || newName == null || newName.isBlank()) return false;
        try {
            byte[] all = java.nio.file.Files.readAllBytes(file.toPath());
            DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(all));
            long seed = in.readLong();
            int type = in.readInt();
            in.readUTF();
            byte[] rest = in.readAllBytes();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeLong(seed);
            out.writeInt(type);
            out.writeUTF(newName.trim());
            out.write(rest);
            out.flush();
            java.nio.file.Files.write(file.toPath(), bytes.toByteArray());
            return true;
        } catch (IOException e) {
            System.err.println("Failed to rename world in " + dir + ": " + e.getMessage());
            return false;
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

    /** Plain snapshot of one placed cooking station - see {@code com.example.cooking.
     *  CookingStationManager.Snapshot}, which this mirrors field-for-field. {@code stationTypeName}
     *  is a String (not the enum) for the same reason {@link SurvivalSnapshot} avoids the
     *  {@code survival} package: {@code world} never depends on {@code cooking}. */
    public record CookingStationSnapshot(int x, int y, int z, String stationTypeName, ItemStack input,
                                          ItemStack fuel, ItemStack output, int burnTimeRemainingTicks,
                                          int totalBurnTimeTicks, int cookProgressTicks, float storedExperience) {
    }

    private static void writeStack(DataOutputStream out, ItemStack stack) throws IOException {
        out.writeInt(stack.isEmpty() ? 0 : stack.type().id);
        out.writeInt(stack.count());
    }

    private static ItemStack readStack(DataInputStream in) throws IOException {
        int id = in.readInt();
        int count = in.readInt();
        return new ItemStack(BlockType.byId(id), count); // ItemStack's own ctor normalizes AIR/count<=0 to EMPTY
    }

    /** One file per world (not per-chunk) - every cooking station a world has ever had, however
     *  many chunks they're spread across. Simpler than slicing snapshots per chunk, and correct:
     *  {@code CookingStationManager} only ever skips *ticking* a station in an unloaded chunk
     *  (see its own docs), it never needs its data evicted from memory at this project's scale. */
    public static void saveCookingStations(File dir, List<CookingStationSnapshot> stations) {
        dir.mkdirs();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(dir, "cooking_stations.dat"))))) {
            out.writeInt(stations.size());
            for (CookingStationSnapshot s : stations) {
                out.writeInt(s.x());
                out.writeInt(s.y());
                out.writeInt(s.z());
                out.writeUTF(s.stationTypeName());
                writeStack(out, s.input());
                writeStack(out, s.fuel());
                writeStack(out, s.output());
                out.writeInt(s.burnTimeRemainingTicks());
                out.writeInt(s.totalBurnTimeTicks());
                out.writeInt(s.cookProgressTicks());
                out.writeFloat(s.storedExperience());
            }
        } catch (IOException e) {
            System.err.println("Failed to save cooking stations: " + e.getMessage());
        }
    }

    /** Empty list if the world has no cooking-stations file yet (a save from before this
     *  milestone, or a world that never had one placed) - never null, never throws. */
    public static List<CookingStationSnapshot> loadCookingStations(File dir) {
        File file = new File(dir, "cooking_stations.dat");
        List<CookingStationSnapshot> result = new ArrayList<>();
        if (!file.isFile()) return result;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int x = in.readInt(), y = in.readInt(), z = in.readInt();
                String typeName = in.readUTF();
                ItemStack input = readStack(in);
                ItemStack fuel = readStack(in);
                ItemStack output = readStack(in);
                int burn = in.readInt(), totalBurn = in.readInt(), progress = in.readInt();
                float xp = in.readFloat();
                result.add(new CookingStationSnapshot(x, y, z, typeName, input, fuel, output, burn, totalBurn, progress, xp));
            }
        } catch (IOException e) {
            System.err.println("Failed to load cooking stations from " + dir + ": " + e.getMessage());
        }
        return result;
    }

    /** One saved persistent mob (passive animals; hostile mobs are never saved). */
    public record MobSnapshot(String type, float x, float y, float z, float yaw, float health, float extra,
                              float vx, float vy, float vz) {
    }

    public static void saveMobs(File dir, List<MobSnapshot> mobs) {
        dir.mkdirs();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(dir, "mobs.dat"))))) {
            out.writeInt(mobs.size());
            for (MobSnapshot m : mobs) {
                out.writeUTF(m.type());
                out.writeFloat(m.x());
                out.writeFloat(m.y());
                out.writeFloat(m.z());
                out.writeFloat(m.yaw());
                out.writeFloat(m.health());
                out.writeFloat(m.extra());
                out.writeFloat(m.vx());
                out.writeFloat(m.vy());
                out.writeFloat(m.vz());
            }
        } catch (IOException e) {
            System.err.println("Failed to save mobs: " + e.getMessage());
        }
    }

    /** Empty list if the world has no mobs file (a save from before animals existed) or it is unreadable. */
    public static List<MobSnapshot> loadMobs(File dir) {
        File file = new File(dir, "mobs.dat");
        List<MobSnapshot> result = new ArrayList<>();
        if (!file.isFile()) return result;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            int count = in.readInt();
            for (int i = 0; i < count && i < 4096; i++) {
                result.add(new MobSnapshot(in.readUTF(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(),
                        in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat()));
            }
        } catch (IOException e) {
            System.err.println("Failed to load mobs from " + dir + ": " + e.getMessage());
        }
        return result;
    }

    public static void saveArmor(File dir, com.example.inventory.PlayerArmor armor) {
        dir.mkdirs();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(dir, "armor.dat"))))) {
            armor.write(out);
        } catch (IOException e) {
            System.err.println("Failed to save armor: " + e.getMessage());
        }
    }

    public static void loadArmor(File dir, com.example.inventory.PlayerArmor into) {
        File file = new File(dir, "armor.dat");
        if (!file.isFile()) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            into.read(in);
        } catch (IOException e) {
            System.err.println("Failed to load armor from " + dir + ": " + e.getMessage());
        }
    }

    /** Player extras that live beside the survival snapshot: XP, this life's score and the difficulty lock. */
    public record Extras(float experience, float score, boolean difficultyLocked) {
    }

    public static void saveExtras(File dir, Extras e) {
        dir.mkdirs();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(dir, "extras.dat"))))) {
            out.writeFloat(e.experience());
            out.writeFloat(e.score());
            out.writeBoolean(e.difficultyLocked());
        } catch (IOException ex) {
            System.err.println("Failed to save extras: " + ex.getMessage());
        }
    }

    /** Defaults (no XP, unlocked) when the world has no extras file yet. */
    public static Extras loadExtras(File dir) {
        File file = new File(dir, "extras.dat");
        if (!file.isFile()) return new Extras(0f, 0f, false);
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            return new Extras(in.readFloat(), in.readFloat(), in.readBoolean());
        } catch (IOException ex) {
            return new Extras(0f, 0f, false);
        }
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
