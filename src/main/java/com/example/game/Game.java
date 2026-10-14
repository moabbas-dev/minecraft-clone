package com.example.game;

import com.example.audio.GameAudio;
import com.example.cooking.CookingMenu;
import com.example.cooking.CookingStationManager;
import com.example.cooking.CookingStationState;
import com.example.cooking.CookingStationType;
import com.example.entity.ItemEntity;
import com.example.entity.Mob;
import com.example.entity.MobManager;
import com.example.entity.MobSpawner;
import com.example.entity.Targetable;
import com.example.entity.mob.Zombie;
import com.example.entity.ItemEntityManager;
import com.example.fx.ParticleSystem;
import com.example.inventory.Inventory;
import com.example.inventory.InventoryMenu;
import com.example.inventory.ItemStack;
import com.example.engine.*;
import com.example.physics.Player;
import com.example.survival.DamageSource;
import com.example.survival.PlayerSurvival;
import com.example.testctl.GameAccess;
import com.example.testctl.GameCommandQueue;
import com.example.testctl.TestController;
import com.example.ui.CookingLayout;
import com.example.ui.DeathScreen;
import com.example.ui.GameOptions;
import com.example.ui.GuiEnv;
import com.example.ui.McScreen;
import com.example.ui.OptionsScreens;
import com.example.ui.GuiPainter;
import com.example.ui.InventoryLayout;
import com.example.ui.PauseScreen;
import com.example.ui.SelectWorldScreen;
import com.example.ui.TitleScreen;
import com.example.ui.WorldCreateScreen;
import com.example.world.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

@Slf4j
public class Game implements GameAccess {
    /** Chunk radius currently in effect (Video Settings > Render Distance). */
    private int renderDistance = 6;
    // Chunk streaming used to be budgeted by a fixed COUNT of chunks/meshes per frame. That
    // doesn't bound wall-clock time: per-chunk cost varies a lot (an exposed-sky column near
    // a cave mouth lights very differently than solid stone), so a run of expensive chunks
    // could still spike a single frame well past its budget. This is the actual root cause of
    // the reported movement freezes (see updateChunkStreaming): a synchronous burst of
    // generation + lighting + meshing work on the single game-loop thread, triggered whenever
    // the player moves into unexplored territory. Budgeting by TIME instead bounds the spike
    // directly, spreading the same total work over more frames instead of front-loading it.
    private static final long STREAMING_TIME_BUDGET_NANOS = 4_000_000L; // 4ms/frame cap
    private static final long FRAME_WARN_THRESHOLD_NANOS = 20_000_000L; // flag frames slower than 20ms
    private static final float REACH_DISTANCE = 5.0f;
    private static final float SKY_R = 0.53f, SKY_G = 0.81f, SKY_B = 0.92f;
    /** Full day/night cycle length: vanilla's 1200 s (20 min). */
    private static final float DAY_LENGTH_SECONDS = 1200f; // vanilla: 24000 ticks = 20 minutes
    private static final float CELESTIAL_DISTANCE = 180f;
    /** Vanilla's quads are 60 (sun) and 40 (moon) blocks wide at a distance of 100; these keep the same angular size at {@link #CELESTIAL_DISTANCE}. */
    private static final float SUN_HALF_SIZE = 30f * CELESTIAL_DISTANCE / 100f;
    private static final float MOON_HALF_SIZE = 20f * CELESTIAL_DISTANCE / 100f;
    /** The moon sprite per phase index 0..7 (vanilla order). */
    private static final String[] MOON_PHASE_TEXTURES = {"full_moon", "waning_gibbous", "third_quarter", "waning_crescent",
            "new_moon", "waxing_crescent", "first_quarter", "waxing_gibbous"};
    /** Vanilla draws rain in the (2 x radius + 1)^2 block columns around the camera: 10 on Fancy graphics. */
    private static final int RAIN_COLUMN_RADIUS = 10;
    private static final float SPRINT_FOV_BOOST = 5f;
    private static final float FOV_LERP_RATE = 9f;
    private static final float SWING_DURATION = 0.3f; // vanilla: 6 ticks
    /** Seconds between a mined block's hit sounds (vanilla: every 4 ticks). */
    private static final float MINE_HIT_INTERVAL = 0.2f;
    private float mineHitClock = 0f;

    // Biome atmosphere: how strongly the biome's own fog tint nudges the time-of-day sky color
    // (see BiomeColors - these are deliberately subtle mood tints, not a sky repaint), how far
    // underwater fog reaches (vanilla's underwater visibility is short), and how fast the
    // surface<->underwater fog blend eases when crossing the water plane (avoids a snap).
    private static final float BIOME_FOG_BLEND_STRENGTH = 0.4f;
    /** Same idea as BIOME_FOG_BLEND_STRENGTH but for the sky color itself - kept slightly
     *  weaker than fog's nudge so the day/night gradient stays clearly the dominant driver of
     *  sky color, with biome only ever shifting the mood, never overriding the time of day. */
    private static final float SKY_TINT_BLEND_STRENGTH = 0.3f;
    private static final float UNDERWATER_FOG_START = 0f;
    private static final float UNDERWATER_FOG_END = 12f;
    private static final float UNDERWATER_TRANSITION_RATE = 3f;
    private static final Vector3f DEFAULT_UNDERWATER_FOG = new Vector3f(0.02f, 0.08f, 0.20f);
    /** Global wind-sway multiplier (see Shaders.CHUNK_VERTEX) - the one debug/tuning knob for
     *  this feature: set to 0f to disable wind entirely with no other code changes, per the
     *  request's own "simple configuration constant is acceptable" guidance. This project's own
     *  tuning, not an official Minecraft value. */
    private static final float WIND_STRENGTH = 1f;

    private enum GameState {TITLE, SELECT_WORLD, WORLD_CREATE, IN_GAME, PAUSED, INVENTORY, COOKING, OPTIONS, DEATH}

    private Window window;
    private Input input;
    private GameState state = GameState.TITLE;

    private World world;
    private Player player;
    private TextureAtlas atlas;
    private BlockTextureMap textureMap;
    private ChunkMeshBuilder meshBuilder;
    private Shader chunkShader;
    private UiRenderer uiRenderer;
    private Font font;

    private TitleScreen titleScreen;
    private SelectWorldScreen selectWorldScreen;
    private WorldCreateScreen worldCreateScreen;
    private PauseScreen pauseScreen;
    private GameAudio gameAudio;

    private final Map<Long, ChunkRenderData> renderData = new ConcurrentHashMap<>();

    private String currentWorldFolder;
    private WorldSettings currentSettings;

    /** The single source of truth for item counts and the selected hotbar slot - the hotbar UI,
     *  held-item view model, and block placement all read it; nothing keeps a second copy. */
    private Inventory inventory = new Inventory();
    /** Non-null only while the inventory/crafting screen is open (state == INVENTORY). */
    private InventoryMenu menu;
    /** Non-null only while a Furnace/Blast Furnace/Smoker screen is open (state == COOKING). */
    private CookingMenu cookingMenu;
    /** Every placed cooking station in the current world - ticked from the fixed-step
     *  accumulator below, completely independent of whether cookingMenu is open. */
    private CookingStationManager cookingStations = new CookingStationManager();
    private float tickAccumulator = 0f;
    /** Real vanilla's own rate - 20 simulated ticks per second, independent of render FPS (see
     *  updateInGame's accumulator loop and CookingStationManager/CookingStationState). */
    private static final float TICK_SECONDS = 1f / 20f;
    /** Health/hunger/damage state - see resetGameplayState for when a fresh one replaces this. */
    private PlayerSurvival survival = new PlayerSurvival(Difficulty.NORMAL);
    private final Random gameplayRandom = new Random();
    private final MiningController mining = new MiningController(gameplayRandom);
    private final ItemEntityManager itemEntities = new ItemEntityManager(Inventory.DEFAULT_MAX_STACK);
    private final ParticleSystem particles = new ParticleSystem();
    private CrackOverlay crackOverlay;
    private Mesh itemMesh;
    private Mesh particleMesh;
    /** Reused every frame - dropped items and particles never allocate per-frame vertex arrays. */
    private final float[] itemScratch = new float[ItemEntityManager.MAX_ENTITIES * ItemModelBuilder.MAX_FLOATS_PER_ITEM];
    private final float[] particleScratch = new float[ParticleSystem.maxVertexFloats()];
    private final Map<BlockType, ItemModelBuilder.Model> itemModels = new EnumMap<>(BlockType.class);
    private static final Matrix4f IDENTITY = new Matrix4f();
    /** Test-controller-only input overrides (see GameAccess.holdAttack/useItem) - the controller
     *  has no synthetic keyboard/mouse, so these let it drive mining and placement through the
     *  exact same code path real input takes. Both are only ever touched on the main thread. */
    private boolean forcedAttackHeld = false;
    private boolean forcedUseHeld = false;
    private int forcedUseRequests = 0;
    /** Seconds left to show the selected-block name label above the hotbar - reset to
     *  HOTBAR_LABEL_DURATION whenever selectedIndex actually changes (see
     *  updateHotbarSelection), counted down every frame, drawn only while > 0. */
    private float hotbarLabelTimer = 0f;
    private static final float HOTBAR_LABEL_DURATION = 1.5f;
    /** The label fades out over this many seconds at the tail of HOTBAR_LABEL_DURATION, rather
     *  than popping off abruptly. */
    private static final float HOTBAR_LABEL_FADE_SECONDS = 0.4f;
    /** Elapsed seconds fed to the wind shader as uWindTime - only accumulates during real
     *  gameplay (updateInGame), never while paused/on a menu, so wind visibly freezes when the
     *  game is paused rather than continuing to animate an unattended world. */
    private float windTime = 0f;

    private double titleTimer = 0;
    /** 0=midnight, 0.25=sunrise, 0.5=noon, 0.75=sunset. */
    private float dayTime = 0.5f;
    /** Whole days elapsed since the world began (the moon phase cycles every 8 of them). */
    private long dayCount;
    private final GameOptions options = GameOptions.load(GameOptions.DEFAULT_FILE);
    private GuiEnv guiEnv;
    private McScreen optionsScreen;
    private GameState optionsParent = GameState.TITLE;
    private DeathScreen deathScreen;
    private HudTextures hudTextures;
    private final Vector3f spawnPoint = new Vector3f();
    private float currentFov = 70f;
    private float swingTimer = 0f;
    private final MobManager mobs = new MobManager();
    private final MobSpawner mobSpawner = new MobSpawner();
    private final com.example.entity.PassiveSpawner passiveSpawner = new com.example.entity.PassiveSpawner();
    private final PlayerTargetAdapter playerTarget = new PlayerTargetAdapter();
    private final java.util.List<Targetable> mobTargets = java.util.List.of(playerTarget);
    private Mesh mobMesh;
    private float lastHurtCooldown;
    private final com.example.entity.XpOrbManager xpOrbs = new com.example.entity.XpOrbManager();
    private final com.example.entity.FallingBlockManager fallingBlocks = new com.example.entity.FallingBlockManager();
    private final com.example.entity.ArrowManager arrows = new com.example.entity.ArrowManager();
    private boolean hideHud;
    private boolean difficultyLocked;
    /** Use time of the food being eaten in ticks (0 when not eating) and which hand holds it, for the hand animation. */
    private float eatUseTicks;
    private boolean eatingOffhand;
    private float eatTimer, eatSoundTimer;
    private int lastLevel;
    private final java.util.Map<String, com.example.engine.EntityTexture> mobSkins = new java.util.LinkedHashMap<>();
    private final float[] mobScratch = new float[MobManager.MAX_MOBS * MobModelBuilder.FLOATS_PER_MOB];
    /** Vanilla survival reach for hitting entities (blocks use REACH_DISTANCE). */
    private static final float ENTITY_REACH = 3.0f;
    /** Seconds since the last attack swing or held-item change (drives the 1.9+ attack charge). */
    private float attackTimer = 10f;
    private BlockType lastHeldType;
    private int forcedAttackPresses;
    private boolean debugOverlay = false;
    /** 0=surface fog, 1=fully underwater fog - eased, see renderWorld. */
    private float underwaterBlend = 0f;
    private Mesh sunMoonMesh;
    private Mesh heldItemMesh;

    private Weather weather;
    private SaplingGrowth saplingGrowth;

    // Opt-in live-controller test harness - see com.example.testctl. Off by default; only
    // started (init()) when -Dtest.controller=true is passed, so normal play never creates a
    // GameCommandQueue/TestController at all, let alone opens a socket.
    private GameCommandQueue testCommandQueue;
    private TestController testController;

    private Mesh rainMesh;
    /** Seconds of rain animation (vanilla counts ticks: 20 per second). */
    private float rainTime = 0f;
    /** The item shown in the first-person hand (null = bare hand); it lags the selection while the hand lowers and raises. */
    private BlockType shownHeld;
    /** 0 = hand fully lowered, 1 = fully raised; drives the equip animation. */
    private float equipRaised;
    private final java.util.Set<String> missingTextures = new java.util.HashSet<>();
    /** Real texture files loaded on demand by {@link #realTexture} (kept apart from the mob skins, which load as a set). */
    private final java.util.Map<String, com.example.engine.EntityTexture> realTextures = new java.util.HashMap<>();
    private final com.example.fx.SpriteParticles critParticles =
            new com.example.fx.SpriteParticles(com.example.fx.SpriteParticles.Kind.CRIT, new java.util.Random());
    private final com.example.fx.SpriteParticles damageParticles =
            new com.example.fx.SpriteParticles(com.example.fx.SpriteParticles.Kind.DAMAGE_INDICATOR, new java.util.Random());
    private int forcedDropRequests;
    private boolean forcedDropWholeStack;
    private int forcedSwapRequests;
    /** Items flying into the player after being collected (vanilla's ItemPickupParticle: 3 ticks, accelerating). */
    private static final float PICKUP_SECONDS = 0.15f;

    private static final class Pickup {
        final BlockType type;
        final int count;
        final float x, y, z, age;
        float t;

        Pickup(ItemEntityManager.Collected c) {
            type = c.type();
            count = c.count();
            x = c.x();
            y = c.y();
            z = c.z();
            age = c.age();
        }
    }

    private final java.util.List<Pickup> pickups = new java.util.ArrayList<>();
    /** The item shown in the left hand (null = nothing) and its equip progress (0 = lowered, 1 = raised). */
    private BlockType shownOffhand;
    private float equipRaisedOff;

    // 11 floats/vertex everywhere: position(3), uv(2), skyShade(1), blockLight(1), tint(3),
    // windWeight(1) - one shared layout across every mesh that uses chunkShader (chunk terrain,
    // held item, rain, sun/moon; see Shaders.CHUNK_VERTEX's aWindWeight). Only leaf faces and
    // sapling cross-plants ever write a nonzero value; everything else writes a constant 0f.
    private static final int[] CHUNK_VERTEX_LAYOUT = {3, 2, 1, 1, 3, 1};

    @RequiredArgsConstructor
    private static final class ChunkRenderData {
        final int chunkX, chunkZ;
        final Mesh opaqueMesh = new Mesh(CHUNK_VERTEX_LAYOUT);
        final Mesh transparentMesh = new Mesh(CHUNK_VERTEX_LAYOUT);

        void destroy() {
            opaqueMesh.destroy();
            transparentMesh.destroy();
        }
    }

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        window = new Window(1280, 720, "Minecraft 1.8 Clone");
        input = new Input(window.getHandle());
        window.releaseMouse(); // start on the title screen with a free cursor

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glClearColor(SKY_R, SKY_G, SKY_B, 1f);

        atlas = new TextureAtlas();
        textureMap = new BlockTextureMap();
        meshBuilder = new ChunkMeshBuilder(atlas, textureMap);
        chunkShader = new Shader(Shaders.CHUNK_VERTEX, Shaders.CHUNK_FRAGMENT);
        uiRenderer = new UiRenderer();
        font = new Font();
        hudTextures = HudTextures.loadOrNull();
        sunMoonMesh = new Mesh(CHUNK_VERTEX_LAYOUT);
        rainMesh = new Mesh(CHUNK_VERTEX_LAYOUT);
        heldItemMesh = new Mesh(CHUNK_VERTEX_LAYOUT);
        itemMesh = new Mesh(CHUNK_VERTEX_LAYOUT);
        mobMesh = new Mesh(CHUNK_VERTEX_LAYOUT);
        particleMesh = new Mesh(CHUNK_VERTEX_LAYOUT);
        crackOverlay = new CrackOverlay(CHUNK_VERTEX_LAYOUT);

        gameAudio = new GameAudio();
        mobs.setXpOrbs(xpOrbs);
        mobs.setProjectileSink(r -> arrows.fire(r.origin(), r.velocity(), r.damage()));
        mobs.setSoundSink(e -> {
            if (player != null && player.position.distanceSquared(e.x(), e.y(), e.z()) < 32f * 32f) {
                gameAudio.play(e.id(), new Vector3f(e.x(), e.y(), e.z()));
            }
        });
        gameAudio.init(); // never throws - degrades to silent no-ops if no device/OpenAL fails

        guiEnv = new GuiEnv(options, new com.example.ui.UiSprites());
        applyOptions();

        titleScreen = new TitleScreen(guiEnv, new TitleScreen.Listener() {
            @Override
            public void onPlay() {
                selectWorldScreen.refresh();
                state = GameState.SELECT_WORLD;
            }

            @Override
            public void onOptions() {
                openOptions(GameState.TITLE);
            }

            @Override
            public void onQuit() {
                window.close();
            }
        });

        selectWorldScreen = new SelectWorldScreen(guiEnv, new SelectWorldScreen.Listener() {
            @Override
            public void onLoadWorld(WorldIO.SavedWorld saved) {
                loadWorld(saved);
            }

            @Override
            public void onCreateNew() {
                worldCreateScreen.reset();
                state = GameState.WORLD_CREATE;
            }

            @Override
            public void onBack() {
                titleScreen.invalidate();
                state = GameState.TITLE;
            }
        });

        worldCreateScreen = new WorldCreateScreen(guiEnv, new WorldCreateScreen.Listener() {
            @Override
            public void onCreateWorld(WorldSettings settings, Long seed) {
                startNewWorld(settings, seed != null ? seed : System.currentTimeMillis());
            }

            @Override
            public void onBack() {
                selectWorldScreen.refresh();
                state = GameState.SELECT_WORLD;
            }
        });

        pauseScreen = new PauseScreen(guiEnv, new PauseScreen.Listener() {
            @Override
            public void onBackToGame() {
                resumeGame();
            }

            @Override
            public void onOptions() {
                openOptions(GameState.PAUSED);
            }

            @Override
            public void onQuitToTitle() {
                quitToTitle();
            }
        });

        deathScreen = new DeathScreen(guiEnv, new DeathScreen.Listener() {
            @Override
            public void onRespawn() {
                respawnPlayer();
            }

            @Override
            public void onTitleScreen() {
                respawnPlayer(); // a fresh life for whenever this save is loaded again
                quitToTitle();
            }
        });

        glfwPollEvents();
        System.out.println("Controls: WASD move, mouse look, Space jump / swim up / double-tap fly, "
                + "Shift sneak / swim down (fly), Ctrl sprint, Left click break, Right click place, "
                + "1-9 or scroll to select block, Esc for menu.");

        testCommandQueue = new GameCommandQueue();
        if ("true".equals(System.getProperty("test.controller"))) {
            testController = new TestController(this, testCommandQueue);
            try {
                testController.start();
            } catch (IOException e) {
                log.error("Failed to start test controller", e);
                testController = null;
            }
        }
    }

    private void startNewWorld(WorldSettings settings) {
        startNewWorld(settings, System.currentTimeMillis());
    }

    /** Seed-overridable variant used by the normal creation flow (random seed) and by the
     *  test controller's newTestWorld command (fixed seed, for deterministic scenarios). */
    private void startNewWorld(WorldSettings settings, long seed) {
        for (ChunkRenderData rd : renderData.values()) rd.destroy();
        renderData.clear();

        WorldGenerator generator = settings.type() == WorldSettings.WorldType.FLAT
                ? new FlatWorldGenerator()
                : new TerrainGenerator(seed);
        world = new World(seed, generator);

        currentWorldFolder = uniqueFolderName(WorldIO.sanitizeFolderName(settings.name()));
        currentSettings = settings;
        world.setSaveDir(WorldIO.worldDir(currentWorldFolder));
        dayTime = 0.3f; // fresh worlds start shortly after sunrise
        dayCount = 0L;

        saplingGrowth = new SaplingGrowth(seed);
        cookingStations = new CookingStationManager(); // fresh world: nothing to restore
        Chunk spawnChunk = world.getOrCreateChunk(0, 0);
        world.generateChunk(spawnChunk);
        saplingGrowth.registerChunk(spawnChunk); // a no-op for a fresh world, kept for consistency with loadWorld

        int spawnLocalX = 8, spawnLocalZ = 8;
        int spawnY;
        if (settings.type() == WorldSettings.WorldType.FLAT) {
            spawnY = spawnChunk.getColumnHeight(spawnLocalX, spawnLocalZ) + 2;
        } else {
            int bestHeight = -1;
            for (int x = 2; x < Chunk.SIZE_X - 2; x++) {
                for (int z = 2; z < Chunk.SIZE_Z - 2; z++) {
                    int h = spawnChunk.getColumnHeight(x, z);
                    if (h > TerrainGenerator.SEA_LEVEL && h > bestHeight) {
                        bestHeight = h;
                        spawnLocalX = x;
                        spawnLocalZ = z;
                    }
                }
            }
            spawnY = (bestHeight > 0 ? bestHeight : spawnChunk.getColumnHeight(spawnLocalX, spawnLocalZ)) + 2;
        }
        player = new Player(new Vector3f(spawnLocalX + 0.5f, spawnY, spawnLocalZ + 0.5f));
        spawnPoint.set(player.position);
        player.setViewBobbing(options.viewBobbing);

        resetGameplayState(new Inventory(), settings.difficulty());
        weather = new Weather(seed);

        window.setTitle("Minecraft 1.8 Clone - " + settings.name());
        window.captureMouse();
        input.resyncMousePosition();
        state = GameState.IN_GAME;

        saveWorld(); // write it to disk immediately so it shows up in the world list right away
    }

    private void loadWorld(WorldIO.SavedWorld saved) {
        File dir = WorldIO.worldDir(saved.folderName());
        WorldIO.LoadedMeta meta = WorldIO.loadMeta(dir);
        if (meta == null) {
            selectWorldScreen.refresh();
            state = GameState.SELECT_WORLD;
            return;
        }

        for (ChunkRenderData rd : renderData.values()) rd.destroy();
        renderData.clear();

        WorldGenerator generator = meta.settings().type() == WorldSettings.WorldType.FLAT
                ? new FlatWorldGenerator()
                : new TerrainGenerator(meta.seed());
        world = new World(meta.seed(), generator);
        world.setSaveDir(dir);
        currentWorldFolder = saved.folderName();
        currentSettings = meta.settings();
        dayTime = meta.dayTime();
        dayCount = meta.dayCount();

        saplingGrowth = new SaplingGrowth(meta.seed());
        cookingStations = new CookingStationManager();
        cookingStations.restoreAll(toManagerSnapshots(WorldIO.loadCookingStations(dir)));
        int spawnCx = World.worldToChunk((int) Math.floor(meta.playerPos().x));
        int spawnCz = World.worldToChunk((int) Math.floor(meta.playerPos().z));
        Chunk spawnChunk = world.getOrCreateChunk(spawnCx, spawnCz);
        world.generateChunk(spawnChunk); // restores from disk since it was already saved
        saplingGrowth.registerChunk(spawnChunk); // rescans for any saplings that were saved mid-growth

        player = new Player(new Vector3f(meta.playerPos()));
        spawnPoint.set(player.position);
        player.setViewBobbing(options.viewBobbing);
        resetGameplayState(meta.inventory() != null ? meta.inventory() : new Inventory(), meta.settings().difficulty());
        if (meta.survival() != null) {
            WorldIO.SurvivalSnapshot s = meta.survival();
            survival.loadState(s.health(), s.foodLevel(), s.saturation(), s.exhaustion());
        }
        WorldIO.loadArmor(dir, survival.getArmor());
        WorldIO.Extras extras = WorldIO.loadExtras(dir);
        survival.loadExperience(extras.experience(), extras.score());
        difficultyLocked = extras.difficultyLocked();
        lastLevel = survival.getLevel();
        for (WorldIO.MobSnapshot ms : WorldIO.loadMobs(dir)) {
            Mob m = com.example.entity.mob.MobTypes.create(ms.type(), new Vector3f(ms.x(), ms.y(), ms.z()), ms.yaw(),
                    () -> survival.getDifficulty());
            if (m == null) continue;
            m.getHealth().setHealth(ms.health());
            m.restoreExtraState(ms.extra());
            m.velocity.set(ms.vx(), ms.vy(), ms.vz());
            mobs.spawn(m);
        }
        weather = new Weather(meta.seed());

        window.setTitle("Minecraft 1.8 Clone - " + meta.settings().name());
        window.captureMouse();
        input.resyncMousePosition();
        state = GameState.IN_GAME;
    }

    private String uniqueFolderName(String base) {
        String candidate = base;
        int suffix = 2;
        while (WorldIO.worldDir(candidate).exists()) {
            candidate = base + " (" + suffix + ")";
            suffix++;
        }
        return candidate;
    }

    /** Writes world metadata and every currently-loaded chunk to disk. Safe to call with no active world. */
    private void saveWorld() {
        if (world == null || currentWorldFolder == null) return;
        fallingBlocks.settleAll(world, itemEntities, gameplayRandom); // nothing in mid-air is lost to a save
        File dir = WorldIO.worldDir(currentWorldFolder);
        WorldIO.SurvivalSnapshot survivalSnapshot = new WorldIO.SurvivalSnapshot(
                survival.getHealth().getCurrentHealth(), survival.getHunger().getFoodLevel(),
                survival.getHunger().getSaturation(), survival.getHunger().getExhaustion());
        WorldIO.saveMeta(dir, currentSettings, world.seed, player.position.x, player.position.y, player.position.z,
                dayTime, inventory.getSelectedSlot(), inventory, survivalSnapshot, dayCount);
        for (Chunk c : world.chunkMap().values()) {
            if (c.isGenerated()) WorldIO.saveChunk(dir, c);
        }
        WorldIO.saveCookingStations(dir, toWorldIoSnapshots(cookingStations.snapshotAll()));
        List<WorldIO.MobSnapshot> savedMobs = new java.util.ArrayList<>();
        for (Mob m : mobs.mobs()) {
            if (!m.isPersistent() || !m.isAlive()) continue;
            savedMobs.add(new WorldIO.MobSnapshot(m.typeId(), m.position.x, m.position.y, m.position.z, m.getYaw(),
                    m.getHealth().getCurrentHealth(), m.extraState(), m.velocity.x, m.velocity.y, m.velocity.z));
        }
        WorldIO.saveMobs(dir, savedMobs);
        WorldIO.saveArmor(dir, survival.getArmor());
        WorldIO.saveExtras(dir, new WorldIO.Extras(survival.getExperience(), survival.getScore(), difficultyLocked));
    }

    /** These two conversions exist purely because {@code world.WorldIO} deliberately doesn't
     *  depend on the {@code cooking} package (same reasoning as SurvivalSnapshot) - the two
     *  Snapshot records have identical fields, Game is just the one place that already depends
     *  on both packages and can freely convert between them. */
    private static List<WorldIO.CookingStationSnapshot> toWorldIoSnapshots(List<CookingStationManager.Snapshot> in) {
        List<WorldIO.CookingStationSnapshot> out = new java.util.ArrayList<>(in.size());
        for (CookingStationManager.Snapshot s : in) {
            out.add(new WorldIO.CookingStationSnapshot(s.x(), s.y(), s.z(), s.stationTypeName(), s.input(), s.fuel(),
                    s.output(), s.burnTimeRemainingTicks(), s.totalBurnTimeTicks(), s.cookProgressTicks(), s.storedExperience()));
        }
        return out;
    }

    private static List<CookingStationManager.Snapshot> toManagerSnapshots(List<WorldIO.CookingStationSnapshot> in) {
        List<CookingStationManager.Snapshot> out = new java.util.ArrayList<>(in.size());
        for (WorldIO.CookingStationSnapshot s : in) {
            out.add(new CookingStationManager.Snapshot(s.x(), s.y(), s.z(), s.stationTypeName(), s.input(), s.fuel(),
                    s.output(), s.burnTimeRemainingTicks(), s.totalBurnTimeTicks(), s.cookProgressTicks(), s.storedExperience()));
        }
        return out;
    }

    private void openOptions(GameState parent) {
        optionsParent = parent;
        boolean inWorld = parent == GameState.PAUSED && world != null;
        optionsScreen = OptionsScreens.create(newOptionsContext(inWorld));
        state = GameState.OPTIONS;
    }

    private OptionsScreens.Context newOptionsContext(boolean inWorld) {
        OptionsScreens.Context ctx = new OptionsScreens.Context(guiEnv, this::applyOptions, this::closeOptions,
                inWorld ? () -> survival.getDifficulty() : null,
                inWorld ? d -> {
                    if (difficultyLocked) return;
                    survival.setDifficulty(d);
                    currentSettings = new WorldSettings(currentSettings.name(), currentSettings.type(), d);
                } : null);
        if (inWorld) {
            ctx.isLocked = () -> difficultyLocked;
            ctx.lock = () -> difficultyLocked = true;
        }
        return ctx;
    }

    private void closeOptions() {
        options.save(GameOptions.DEFAULT_FILE);
        optionsScreen = null;
        if (optionsParent == GameState.PAUSED) pauseScreen.invalidate();
        else titleScreen.invalidate();
        state = optionsParent;
    }

    /** Pushes the current {@link GameOptions} into the engine; cheap and idempotent. */
    private void applyOptions() {
        options.clamp();
        renderDistance = options.renderDistance;
        window.setVsync(options.vsync);
        window.setFullscreen(options.fullscreen);
        if (player != null) player.setViewBobbing(options.viewBobbing);
        gameAudio.setMasterVolume(options.masterVolume);
        gameAudio.setCategoryVolume(com.example.audio.AudioCategory.BLOCKS, options.blocksVolume);
        gameAudio.setCategoryVolume(com.example.audio.AudioCategory.PLAYER, options.playersVolume);
        gameAudio.setCategoryVolume(com.example.audio.AudioCategory.AMBIENT, options.ambientVolume);
        gameAudio.setCategoryVolume(com.example.audio.AudioCategory.WEATHER, options.weatherVolume);
        gameAudio.setCategoryVolume(com.example.audio.AudioCategory.HOSTILE, options.hostileVolume);
        gameAudio.setCategoryVolume(com.example.audio.AudioCategory.NEUTRAL, options.neutralVolume);
        applyPlayerControls();
    }

    /** Pushes the rebindable controls and the sprint/sneak/auto-jump options into the player. */
    private void applyPlayerControls() {
        if (player == null) return;
        com.example.ui.KeyBindings k = options.keys;
        player.setControls(k.get(com.example.ui.KeyBindings.Bind.FORWARD), k.get(com.example.ui.KeyBindings.Bind.BACK),
                k.get(com.example.ui.KeyBindings.Bind.LEFT), k.get(com.example.ui.KeyBindings.Bind.RIGHT),
                k.get(com.example.ui.KeyBindings.Bind.JUMP), k.get(com.example.ui.KeyBindings.Bind.SNEAK),
                k.get(com.example.ui.KeyBindings.Bind.SPRINT));
        player.setControlOptions(options.toggleSprint, options.toggleCrouch, options.autoJump);
    }

    private int keyCode(com.example.ui.KeyBindings.Bind b) {
        return options.keys.get(b);
    }

    private boolean pressed(com.example.ui.KeyBindings.Bind b) {
        return input.consumeKeyPressed(keyCode(b));
    }

    private boolean held(com.example.ui.KeyBindings.Bind b) {
        return input.isKeyDown(keyCode(b));
    }

    /** Back to life at the world's spawn point with full health and hunger. */
    private void respawnPlayer() {
        player.teleport(spawnPoint.x, spawnPoint.y, spawnPoint.z);
        survival.reset(player.position);
        if (state == GameState.DEATH) {
            window.captureMouse();
            input.resyncMousePosition();
            state = GameState.IN_GAME;
        }
    }

    private void pauseGame() {
        pauseScreen.invalidate();
        window.releaseMouse();
        input.resyncMousePosition();
        state = GameState.PAUSED;
    }

    private void resumeGame() {
        window.captureMouse();
        input.resyncMousePosition();
        state = GameState.IN_GAME;
    }

    /** Fresh per-world gameplay state: the given inventory, no in-progress break, no dropped
     *  items or particles carried over from the previous world, and fresh health/hunger at the
     *  given world's difficulty. {@code player} may be null (quitting to the title screen, where
     *  there's nothing to anchor the survival system's position tracking to yet). */
    private void resetGameplayState(Inventory startingInventory, Difficulty difficulty) {
        inventory = startingInventory;
        mining.reset();
        itemEntities.clear();
        particles.clear();
        pickups.clear();
        critParticles.clear();
        damageParticles.clear();
        shownHeld = inventory.selected().isEmpty() ? null : inventory.selected().type();
        equipRaised = 0f; // the hand rises when the world opens, like a freshly equipped item
        shownOffhand = inventory.getOffhand().isEmpty() ? null : inventory.getOffhand().type();
        equipRaisedOff = 0f;
        mobs.clear();
        xpOrbs.clear();
        fallingBlocks.clear();
        arrows.clear();
        difficultyLocked = false;
        eatTimer = 0f;
        survival = new PlayerSurvival(difficulty);
        lastLevel = 0;
        if (player != null) survival.reset(player.position);
        playerTarget.bind(player, survival);
    }

    private void quitToTitle() {
        saveWorld();
        for (ChunkRenderData rd : renderData.values()) rd.destroy();
        renderData.clear();
        world = null;
        player = null;
        weather = null;
        saplingGrowth = null;
        cookingStations = new CookingStationManager();
        resetGameplayState(new Inventory(), Difficulty.NORMAL);
        forcedAttackHeld = false;
        forcedUseHeld = false;
        forcedUseRequests = 0;
        currentWorldFolder = null;
        currentSettings = null;
        window.releaseMouse();
        input.resyncMousePosition();
        state = GameState.TITLE;
    }

    private void loop() {
        double lastTime = glfwGetTime();
        while (!window.shouldClose()) {
            double now = glfwGetTime();
            float dt = (float) Math.min(0.05, now - lastTime);
            lastTime = now;

            long frameStartNanos = System.nanoTime();
            update(dt, now);
            testCommandQueue.drain(); // run any pending controller commands before this frame renders
            render(dt);
            window.update();
            limitFrameRate(frameStartNanos);

            // update(), render() and the buffer swap all run sequentially on this one thread -
            // there is no separate render thread here, so a stall in any of them freezes input
            // handling AND the displayed frame together, not just movement. This log is how to
            // empirically confirm that: if frames stay under the threshold, the freeze is gone;
            // if one still fires, the timestamp pins exactly when it happened.
            long frameNanos = System.nanoTime() - frameStartNanos;
            if (frameNanos > FRAME_WARN_THRESHOLD_NANOS) {
                log.info("Frame took {} ms (update+render+swap combined) - target is under 16.7ms for 60fps",
                        String.format("%.1f", frameNanos / 1_000_000.0));
            }
        }
    }

    private void update(float dt, double now) {
        switch (state) {
            case TITLE -> titleScreen.update(dt, input, window.getWidth(), window.getHeight());
            case SELECT_WORLD -> selectWorldScreen.update(dt, input, window.getWidth(), window.getHeight());
            case WORLD_CREATE -> worldCreateScreen.update(dt, input, window.getWidth(), window.getHeight());
            case IN_GAME -> updateInGame(dt, now);
            case INVENTORY -> {
                tickCookingStations(dt); // a GUI screen isn't the pause menu - real vanilla keeps furnaces (and everything else) ticking while any non-pause screen is open
                updateInventoryScreen();
            }
            case COOKING -> {
                tickCookingStations(dt);
                updateCookingScreen();
            }
            case PAUSED -> pauseScreen.update(dt, input, window.getWidth(), window.getHeight());
            case OPTIONS -> optionsScreen.update(dt, input, window.getWidth(), window.getHeight());
            case DEATH -> deathScreen.update(dt, input, window.getWidth(), window.getHeight());
        }
        input.endFrame();
    }

    private void updateInGame(float dt, double now) {
        if (input.consumeKeyPressed(GLFW_KEY_ESCAPE)) {
            pauseGame();
            return;
        }
        if (input.consumeKeyPressed(GLFW_KEY_F3)) {
            debugOverlay = !debugOverlay;
        }
        if (input.consumeKeyPressed(GLFW_KEY_F1)) hideHud = !hideHud;
        if (pressed(com.example.ui.KeyBindings.Bind.SCREENSHOT)) takeUserScreenshot();
        if (pressed(com.example.ui.KeyBindings.Bind.FULLSCREEN)) {
            options.fullscreen = !options.fullscreen;
            applyOptions();
            options.save(GameOptions.DEFAULT_FILE);
        }
        if (pressed(com.example.ui.KeyBindings.Bind.INVENTORY)) {
            openInventory(2);
            return;
        }
        applyPlayerControls();
        if (pressed(com.example.ui.KeyBindings.Bind.DROP)) {
            dropHeldItem(input.isKeyDown(GLFW_KEY_LEFT_CONTROL) || input.isKeyDown(GLFW_KEY_RIGHT_CONTROL));
        }
        if (forcedDropRequests > 0) {
            forcedDropRequests--;
            dropHeldItem(forcedDropWholeStack);
        }
        boolean swapKey = pressed(com.example.ui.KeyBindings.Bind.SWAP_OFFHAND);
        if (forcedSwapRequests > 0) {
            forcedSwapRequests--;
            swapKey = true;
        }
        if (swapKey) swapSelectedWithOffhand();

        float mouseSpeed = options.mouseSpeedMultiplier();
        player.look(input.getMouseDeltaX() * mouseSpeed, input.getMouseDeltaY() * mouseSpeed * (options.invertMouse ? -1f : 1f));
        player.update(dt, input, world, now, survival.getHunger().canSprint());
        survival.update(dt, player);
        if (survival.getHealth().isDead()) {
            dropEverythingOnDeath();
            deathScreen.invalidate();
            window.releaseMouse();
            input.resyncMousePosition();
            state = GameState.DEATH;
            return;
        }

        float targetFov = options.fov * options.sprintFovFactor(player.isSprinting());
        currentFov += (targetFov - currentFov) * Math.min(1f, FOV_LERP_RATE * dt);
        if (swingTimer > 0f) swingTimer = Math.max(0f, swingTimer - dt);
        attackTimer += dt;
        BlockType heldNow = inventory.selected().isEmpty() ? null : inventory.selected().type();
        if (heldNow != lastHeldType) {
            lastHeldType = heldNow;
            attackTimer = 0f; // switching items restarts the attack cooldown, as in vanilla
        }
        // Equip animation: the old item is lowered, then the new one is raised (vanilla's reequip bob).
        if (heldNow != shownHeld) {
            equipRaised -= dt / HeldItemModel.EQUIP_SECONDS;
            if (equipRaised <= 0f) {
                equipRaised = 0f;
                shownHeld = heldNow;
            }
        } else {
            equipRaised = Math.min(1f, equipRaised + dt / HeldItemModel.EQUIP_SECONDS);
        }
        // Same for the offhand (no attack-strength factor); an empty offhand has nothing to lower, so a new item rises at once.
        BlockType offNow = inventory.getOffhand().isEmpty() ? null : inventory.getOffhand().type();
        if (offNow != shownOffhand) {
            if (shownOffhand == null) {
                shownOffhand = offNow;
                equipRaisedOff = 0f;
            } else {
                equipRaisedOff -= dt / HeldItemModel.EQUIP_SECONDS;
                if (equipRaisedOff <= 0f) {
                    equipRaisedOff = 0f;
                    shownOffhand = offNow;
                }
            }
        } else {
            equipRaisedOff = Math.min(1f, equipRaisedOff + dt / HeldItemModel.EQUIP_SECONDS);
        }

        dayTime += dt / DAY_LENGTH_SECONDS;
        float wholeDays = (float) Math.floor(dayTime);
        dayCount += (long) wholeDays;
        dayTime -= wholeDays;
        weather.update(dt);
        rainTime += dt;
        saplingGrowth.update(world, dt);
        tickCookingStations(dt);
        windTime += dt;
        gameAudio.update(dt, player, world, weather);

        updateHotbarSelection();
        if (hotbarLabelTimer > 0f) hotbarLabelTimer = Math.max(0f, hotbarLabelTimer - dt);
        updateChunkStreaming();
        handleInteraction(dt);
        updateItemsAndEffects(dt);

        titleTimer += dt;
        if (titleTimer > 0.25) {
            titleTimer = 0;
            ItemStack sel = inventory.selected();
            window.setTitle(String.format("Minecraft 1.8 Clone - Selected: %s (%.0f,%.0f,%.0f)%s",
                    sel.isEmpty() ? "-" : sel.type().displayName, player.position.x, player.position.y, player.position.z,
                    player.isFlying() ? " [flying]" : ""));
        }
    }

    private void updateHotbarSelection() {
        int previous = inventory.getSelectedSlot();
        double scroll = input.getScrollDelta();
        if (scroll > 0) inventory.cycleSelection(-1);
        else if (scroll < 0) inventory.cycleSelection(1);

        com.example.ui.KeyBindings.Bind[] hotbarBinds = {
                com.example.ui.KeyBindings.Bind.HOTBAR_1, com.example.ui.KeyBindings.Bind.HOTBAR_2, com.example.ui.KeyBindings.Bind.HOTBAR_3, com.example.ui.KeyBindings.Bind.HOTBAR_4, com.example.ui.KeyBindings.Bind.HOTBAR_5,
                com.example.ui.KeyBindings.Bind.HOTBAR_6, com.example.ui.KeyBindings.Bind.HOTBAR_7, com.example.ui.KeyBindings.Bind.HOTBAR_8, com.example.ui.KeyBindings.Bind.HOTBAR_9};
        for (int i = 0; i < hotbarBinds.length && i < inventory.getHotbarSize(); i++) {
            if (pressed(hotbarBinds[i])) inventory.selectSlot(i);
        }
        if (inventory.getSelectedSlot() != previous) {
            hotbarLabelTimer = HOTBAR_LABEL_DURATION;
        }
    }

    private void updateChunkStreaming() {
        long deadline = System.nanoTime() + STREAMING_TIME_BUDGET_NANOS;
        int pcx = World.worldToChunk((int) Math.floor(player.position.x));
        int pcz = World.worldToChunk((int) Math.floor(player.position.z));

        int generated = 0;
        generation:
        for (int dx = -renderDistance; dx <= renderDistance; dx++) {
            for (int dz = -renderDistance; dz <= renderDistance; dz++) {
                if (dx * dx + dz * dz > renderDistance * renderDistance) continue;
                int cx = pcx + dx, cz = pcz + dz;
                if (!world.hasChunk(cx, cz)) {
                    Chunk c = world.getOrCreateChunk(cx, cz);
                    world.generateChunk(c); // biome climate sampling + height noise + block fill
                    saplingGrowth.registerChunk(c); // picks up any saplings this chunk had saved
                    if (c.isFreshlyGenerated() && mobSpawner.isEnabled()) {
                        com.example.entity.PassiveSpawner.spawnForNewChunk(world, mobs, c, gameplayRandom);
                    }
                    generated++;
                    // Check the deadline only AFTER doing a chunk of work, never before, so a
                    // frame is always guaranteed to make forward progress even if a single
                    // chunk happens to cost more than the whole budget by itself.
                    if (System.nanoTime() >= deadline) break generation;
                }
            }
        }

        int unloaded = 0;
        int unloadRadius = renderDistance + 2;
        Iterator<Map.Entry<Long, Chunk>> it = world.chunkMap().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Chunk> entry = it.next();
            Chunk c = entry.getValue();
            int ddx = c.chunkX - pcx, ddz = c.chunkZ - pcz;
            if (ddx * ddx + ddz * ddz > unloadRadius * unloadRadius) {
                it.remove();
                // Persist this chunk before dropping it from memory, otherwise edits made here
                // are lost forever if the player wanders back after it's already unloaded.
                if (c.isGenerated() && world.getSaveDir() != null) {
                    WorldIO.saveChunk(world.getSaveDir(), c); // synchronous disk write
                }
                saplingGrowth.unregisterChunk(c.chunkX, c.chunkZ); // its saplings pause until reload
                ChunkRenderData rd = renderData.remove(entry.getKey());
                if (rd != null) rd.destroy();
                unloaded++;
                // Same reasoning as generation: this loop also does real (I/O) work per chunk,
                // so it gets the same time cap rather than draining unboundedly in one frame.
                if (System.nanoTime() >= deadline) break;
            }
        }

        int meshed = 0;
        if (System.nanoTime() < deadline) {
            for (Chunk c : world.chunkMap().values()) {
                if (c.isGenerated() && c.isMeshDirty()) {
                    rebuildMesh(c); // relights (BFS) + rebuilds geometry for this chunk
                    meshed++;
                    if (System.nanoTime() >= deadline) break;
                }
            }
        }
        // Chunks generated but not reached by the mesh loop this frame simply stay marked
        // dirty and get picked up on a later frame - no work is lost, it's just spread out.

        if (log.isDebugEnabled() && (generated > 0 || unloaded > 0 || meshed > 0)) {
            long tookMicros = (System.nanoTime() - (deadline - STREAMING_TIME_BUDGET_NANOS)) / 1000;
            log.debug("Streaming: generated={}, unloaded={}, remeshed={}, took={}us", generated, unloaded, meshed, tookMicros);
        }
    }

    private void rebuildMesh(Chunk chunk) {
        long key = Chunk.key(chunk.chunkX, chunk.chunkZ);
        // LightEngine now imports light across borders from loaded neighbors itself, and marks
        // those neighbors dirty again if this chunk's own edge values changed as a result - see
        // LightEngine.compute for why that subsumes the old "first light computed" special case.
        LightEngine.compute(chunk, world);
        ChunkMeshBuilder.MeshData data = meshBuilder.build(chunk, world);
        ChunkRenderData rd = renderData.computeIfAbsent(key, k -> new ChunkRenderData(chunk.chunkX, chunk.chunkZ));
        rd.opaqueMesh.upload(data.opaque());
        rd.transparentMesh.upload(data.transparent());
        chunk.clearMeshDirty();
    }

    private void handleInteraction(float dt) {
        // Dead: no mining, placing, or eating - "prevent additional normal actions" until a
        // future respawn (see testctl's respawn command). Movement/camera/pause still work -
        // this project has no death screen to explain a full input freeze, so freezing only the
        // world-interacting actions is the smallest honest stop-gap (documented limitation).
        if (survival.getHealth().isDead()) return;

        boolean leftPressed = pressed(com.example.ui.KeyBindings.Bind.ATTACK);
        if (forcedAttackPresses > 0) {
            forcedAttackPresses--;
            leftPressed = true;
        }
        if (leftPressed) {
            startSwing(); // swings even on a whiff, matching vanilla's air-punch
        }

        Vector3f eye = player.eyePosition();
        Vector3f dir = player.frontVector();
        Raycast.Hit hit = Raycast.cast(world, eye, dir, REACH_DISTANCE);

        boolean attackHeld = held(com.example.ui.KeyBindings.Bind.ATTACK) || forcedAttackHeld;

        // Entity targeting: a mob nearer than any block hit is what the click is aimed at -
        // attack it (cooldown-gated, via the shared damage pipeline) instead of mining.
        float blockDist = hit == null ? Float.MAX_VALUE : MobManager.rayBoxDistance(eye, dir,
                new float[]{hit.x(), hit.y(), hit.z()}, new float[]{hit.x() + 1f, hit.y() + 1f, hit.z() + 1f});
        float[] mobDist = new float[1];
        Mob targetedMob = mobs.raycast(eye, dir, ENTITY_REACH, mobDist);
        // A strictly nearer entity wins the crosshair; at equal distance the block keeps it.
        boolean aimingAtMob = targetedMob != null && mobDist[0] < blockDist;
        if (aimingAtMob) {
            mining.cancel();
            attackHeld = false;
        }
        // A click on a mob or on empty air is an attack swing (and restarts the cooldown); a click on a
        // block is mining and leaves the cooldown alone.
        if (leftPressed && (aimingAtMob || hit == null)) performPlayerAttack(aimingAtMob ? targetedMob : null);
        float miningSpeedFactor = (player.isOnGround() || player.isFlying() ? 1f : 0.2f) * (player.isHeadSubmerged() ? 0.2f : 1f);
        MiningController.Result mined = mining.tick(world, hit, attackHeld, dt, inventory.selected().type(), miningSpeedFactor);
        if (mined.mining()) {
            // vanilla calls swing every tick while a block is being mined (a swing only restarts once it is past its half), and
            // plays the block's hit sound every 4 ticks, starting with the first
            startSwing();
            mineHitClock -= dt;
            if (mineHitClock <= 0f) {
                mineHitClock += MINE_HIT_INTERVAL;
                gameAudio.playDig(hit.type(), new Vector3f(hit.x() + 0.5f, hit.y() + 0.5f, hit.z() + 0.5f));
            }
        } else {
            mineHitClock = 0f;
        }
        if (mined.broken()) {
            onBlockBroken(mined);
            fallingBlocks.checkColumn(world, mined.x(), mined.y() + 1, mined.z());
        }

        if (pressed(com.example.ui.KeyBindings.Bind.PICK_ITEM) && hit != null) pickBlock(hit.type());

        // Eating: hold use on a food item (vanilla 32 ticks, dried kelp 16); releasing or switching cancels.
        ItemStack mainHeld = inventory.selected(), offHeld = inventory.getOffhand();
        FoodProperties mainFood = mainHeld.isEmpty() ? null : FoodRegistry.propertiesFor(mainHeld.type());
        FoodProperties offFood = offHeld.isEmpty() ? null : FoodRegistry.propertiesFor(offHeld.type());
        boolean eatOff = mainFood == null && offFood != null && !mainHandTakesUse(mainHeld, hit);
        ItemStack heldForEat = eatOff ? offHeld : mainHeld;
        FoodProperties eatFood = eatOff ? offFood : mainFood;
        if (eatFood != null && (held(com.example.ui.KeyBindings.Bind.USE) || forcedUseHeld) && survival.getHunger().canEat(eatFood)) {
            float duration = heldForEat.type() == BlockType.DRIED_KELP ? 0.8f : 1.6f;
            eatUseTicks = duration * 20f;
            eatingOffhand = eatOff;
            eatTimer += dt;
            eatSoundTimer += dt;
            if (eatSoundTimer >= 0.2f) { // vanilla: an eating sound every 4 ticks
                eatSoundTimer = 0f;
                gameAudio.play("eat", null);
            }
            if (eatTimer >= duration) {
                eatTimer = 0f;
                tryEat(eatOff, heldForEat.type());
                gameAudio.play("burp", null);
            }
        } else {
            eatTimer = 0f;
            eatSoundTimer = 0f;
            eatUseTicks = 0f;
        }

        boolean use = pressed(com.example.ui.KeyBindings.Bind.USE);
        boolean forcedUse = false;
        if (forcedUseRequests > 0) {
            forcedUseRequests--;
            use = true;
            forcedUse = true;
        }
        if (use && !useHand(false, hit, forcedUse)) useHand(true, hit, forcedUse);
    }

    /** Whether the main hand would take a right click (so the offhand is not tried): armor to wear, a block to place or open. */
    private boolean mainHandTakesUse(ItemStack held, Raycast.Hit hit) {
        boolean opensScreen = hit != null && !player.isSneaking()
                && (hit.type() == BlockType.CRAFTING_TABLE || cookingStationTypeOf(hit.type()) != null);
        if (held.isEmpty()) return opensScreen;
        return com.example.inventory.PlayerArmor.canEquip(held) || opensScreen || (hit != null && BlockPlacer.isPlaceable(held.type()));
    }

    /**
     * One right click offered to a hand (vanilla tries the main hand and then the offhand): armor is worn, food is eaten,
     * a crafting table or cooking station opens, a block is placed. Returns true when this hand took the click and false
     * when it had nothing to do with it, so the other hand gets its turn.
     */
    private boolean useHand(boolean offhand, Raycast.Hit hit, boolean forcedUse) {
        ItemStack held = offhand ? inventory.getOffhand() : inventory.selected();
        FoodProperties food = held.isEmpty() ? null : FoodRegistry.propertiesFor(held.type());
        if (com.example.inventory.PlayerArmor.canEquip(held)) {
            // Right-click wears armor from the hand, swapping with whatever is worn in that slot.
            ItemStack replaced = survival.getArmor().equip(held);
            if (offhand) inventory.setOffhand(replaced);
            else inventory.set(inventory.getSelectedSlot(), replaced);
            gameAudio.play("armor.equip." + armorMaterial(held.type()), null);
            return true;
        }
        if (food != null) {
            if (forcedUse) tryEat(offhand, held.type()); // the test controller eats instantly; real input holds use
            return true;
        }
        if (hit == null) return false;
        // Using a crafting table opens the 3x3 crafting screen instead of placing a block (sneaking still places, like vanilla).
        if (hit.type() == BlockType.CRAFTING_TABLE && !player.isSneaking()) {
            openInventory(3);
            return true;
        }
        CookingStationType stationType = cookingStationTypeOf(hit.type());
        if (stationType != null && !player.isSneaking()) {
            openCookingScreenAt(hit.x(), hit.y(), hit.z());
            return true;
        }
        BlockPlacer.Result placed = BlockPlacer.tryPlace(world, inventory, offhand, hit, this::blockIntersectsPlayer, saplingGrowth);
        if (placed.placed()) {
            fallingBlocks.checkColumn(world, placed.x(), placed.y(), placed.z());
            startSwing();
            gameAudio.playPlace(placed.type(), new Vector3f(placed.x() + 0.5f, placed.y() + 0.5f, placed.z() + 0.5f));
            CookingStationType placedStation = cookingStationTypeOf(placed.type());
            if (placedStation != null) cookingStations.create(placed.x(), placed.y(), placed.z(), placedStation);
            return true;
        }
        return placed.outcome() == BlockPlacer.Outcome.INVALID_TARGET; // a refused placement ends the click; no item means pass
    }

    /** F: the selected hotbar stack and the offhand stack trade places. */
    private void swapSelectedWithOffhand() {
        ItemStack main = inventory.selected(), off = inventory.getOffhand();
        inventory.set(inventory.getSelectedSlot(), off);
        inventory.setOffhand(main);
    }

    /** One player melee swing: charge, crit/sprint/sweep rules, damage through the shared pipeline,
     *  knockback along the player's facing, and durability. {@code target} is null for an air swing. */
    private void performPlayerAttack(Mob target) {
        ItemStack held = inventory.selected();
        BlockType heldType = held.isEmpty() ? null : held.type();
        float charge = com.example.combat.CombatMath.charge(attackTimer * 20f, com.example.combat.WeaponStats.periodTicks(heldType));
        attackTimer = 0f;
        startSwing();
        if (target == null) return;
        boolean sprintHit = com.example.combat.CombatMath.isSprintHit(charge, player.isSprinting());
        boolean crit = com.example.combat.CombatMath.isCritical(charge, player.isOnGround(), player.getVerticalVelocity(),
                player.isSwimming(), player.isFlying(), sprintHit);
        float damage = com.example.combat.CombatMath.attackDamage(heldType, charge, crit);
        Vector3f front = player.frontVector();
        // Vanilla pushes the victim along the attacker's facing, i.e. away from a point behind the target.
        Vector3f pushFrom = new Vector3f(target.position.x - front.x, 0f, target.position.z - front.z);
        float healthBefore = target.getHealth().getCurrentHealth();
        boolean applied = target.hurtBy(damage, com.example.survival.DamageSource.PLAYER, pushFrom,
                com.example.combat.CombatMath.knockbackStrength(sprintHit));
        if (!applied) return; // a hit swallowed by the hurt window spends no durability
        if (crit) critParticles.track(trackedBox(target));
        float dealt = healthBefore - target.getHealth().getCurrentHealth();
        if (dealt > 2f) { // vanilla: damage indicator hearts when more than one heart was taken, (int)(damage / 2) of them
            damageParticles.spawnGaussian(target.position.x, target.position.y + target.getHeight() * 0.5f, target.position.z,
                    0.1f, 0f, 0.1f, 0.2f, (int) (dealt * 0.5f));
        }
        damageSelectedItem(com.example.combat.Durability.attackCost(heldType));
        gameAudio.play(crit ? "attack.crit" : charge >= 0.9f ? "attack.strong" : "attack.weak", null);
        if (sprintHit) gameAudio.play("attack.knockback", null);
        if (com.example.combat.CombatMath.isSweep(heldType, charge, player.isOnGround(), crit, sprintHit)) {
            gameAudio.play("attack.sweep", null);
            for (Mob other : mobs.sweepTargets(target, player.position)) {
                other.hurtBy(com.example.combat.CombatMath.SWEEP_DAMAGE, com.example.survival.DamageSource.PLAYER, pushFrom,
                        com.example.combat.CombatMath.BASE_KNOCKBACK * com.example.combat.CombatMath.SWEEP_KNOCKBACK_FACTOR);
            }
        }
    }

    /** A live view of a mob's bounding box for the critical-hit particle emitter. */
    private static com.example.fx.SpriteParticles.Tracked trackedBox(Mob m) {
        return new com.example.fx.SpriteParticles.Tracked() {
            public float x() { return m.position.x; }
            public float y() { return m.position.y; }
            public float z() { return m.position.z; }
            public float width() { return m.getWidth(); }
            public float height() { return m.getHeight(); }
        };
    }

    /** Spends durability on the selected hotbar item; a broken item disappears. */
    private void damageSelectedItem(int uses) {
        if (uses <= 0) return;
        int slot = inventory.getSelectedSlot();
        ItemStack s = inventory.get(slot);
        if (s.isEmpty()) return;
        ItemStack after = s.damaged(uses);
        inventory.set(slot, after);
        if (after.isEmpty()) gameAudio.play("item.break", null);
    }

    private static String armorMaterial(BlockType t) {
        String n = t.name();
        if (n.startsWith("LEATHER")) return "leather";
        if (n.startsWith("IRON")) return "iron";
        if (n.startsWith("GOLD")) return "gold";
        if (n.startsWith("DIAMOND")) return "diamond";
        return "generic";
    }

    /** Eats one unit of the given food type from the selected hotbar slot - instant (no
     *  eating-duration animation/timer exists in this project yet, so a single right-click
     *  consumes it immediately; the existing consumeMousePressed edge-trigger already prevents
     *  eating every frame while the button is merely held). Removes the item only if
     *  {@link PlayerSurvival#eat} actually applied it (blocked at max food for non-overeat food). */
    private void tryEat(boolean offhand, BlockType foodType) {
        if (survival.eat(foodType)) {
            if (offhand) inventory.offhandSlot().removeOne(0);
            else inventory.removeFromSlot(inventory.getSelectedSlot(), 1);
        }
    }

    // ---- inventory / crafting screen ----

    private void openInventory(int gridSize) {
        if (state != GameState.IN_GAME) throw new IllegalStateException("not in game");
        menu = new InventoryMenu(inventory, survival.getArmor(), gridSize);
        mining.cancel();
        state = GameState.INVENTORY;
        window.releaseMouse();
        input.resyncMousePosition();
    }

    private void closeInventory() {
        if (menu == null) throw new IllegalStateException("inventory is not open");
        for (ItemStack leftover : menu.close()) dropIntoWorld(leftover); // never discard what didn't fit
        menu = null;
        inventoryDragging = false;
        state = GameState.IN_GAME;
        window.captureMouse();
        input.resyncMousePosition();
    }

    private void updateInventoryScreen() {
        if (pressed(com.example.ui.KeyBindings.Bind.INVENTORY) || input.consumeKeyPressed(GLFW_KEY_ESCAPE)) {
            closeInventory();
            return;
        }
        float mx = (float) input.getMouseX(), my = (float) input.getMouseY();
        InventoryLayout layout = new InventoryLayout(window.getWidth(), window.getHeight(), menu.getGridSize(),
                com.example.ui.GuiScale.compute(window.getWidth(), window.getHeight(), options.guiScale));
        int hovered = layout.slotAt(mx, my);

        // Number keys swap the hovered slot with that hotbar slot (only while the cursor is empty).
        for (int k = 0; k < Inventory.HOTBAR_SIZE; k++) {
            if (pressed(com.example.ui.KeyBindings.Bind.valueOf("HOTBAR_" + (k + 1))) && hovered >= 0) menu.swapWithHotbar(hovered, k);
        }
        if (pressed(com.example.ui.KeyBindings.Bind.SWAP_OFFHAND) && hovered >= 0) menu.swapWithOffhand(hovered);

        boolean left = input.consumeMousePressed(GLFW_MOUSE_BUTTON_LEFT);
        boolean right = input.consumeMousePressed(GLFW_MOUSE_BUTTON_RIGHT);
        if (left || right) {
            long now = System.nanoTime();
            boolean shift = input.isKeyDown(GLFW_KEY_LEFT_SHIFT) || input.isKeyDown(GLFW_KEY_RIGHT_SHIFT);
            boolean doubleClick = left && hovered >= 0 && hovered == lastInventoryClickSlot
                    && now - lastInventoryClickNanos < 250_000_000L && !menu.getCursor().isEmpty();
            if (shift && hovered >= 0) {
                menu.quickMove(hovered);
                lastInventoryClickNanos = 0;
            } else if (doubleClick) {
                menu.gatherSame(hovered);
                lastInventoryClickNanos = 0;
            } else if (hovered >= 0 && menu.canDragInto(hovered)) {
                // A press while holding a stack may become a drag across slots; it resolves when the button is released.
                inventoryDragging = true;
                inventoryDragRight = right;
                inventoryDragSlots.clear();
                inventoryDragSlots.add(hovered);
                lastInventoryClickSlot = hovered;
                lastInventoryClickNanos = now;
            } else {
                handleInventoryClick(hovered, right, layout.panel().contains(mx, my));
                lastInventoryClickSlot = hovered;
                lastInventoryClickNanos = left ? now : 0;
            }
        }
        if (inventoryDragging) {
            int button = inventoryDragRight ? GLFW_MOUSE_BUTTON_RIGHT : GLFW_MOUSE_BUTTON_LEFT;
            if (input.isMouseDown(button)) {
                if (hovered >= 0 && !inventoryDragSlots.contains(hovered) && menu.canDragInto(hovered)) inventoryDragSlots.add(hovered);
            } else {
                inventoryDragging = false;
                if (!menu.distribute(inventoryDragSlots, inventoryDragRight)) {
                    handleInventoryClick(inventoryDragSlots.get(0), inventoryDragRight, true);
                }
            }
        }
    }

    private boolean inventoryDragging, inventoryDragRight;
    private final java.util.List<Integer> inventoryDragSlots = new java.util.ArrayList<>();
    private int lastInventoryClickSlot = -1;
    private long lastInventoryClickNanos;

    /** One click on the screen: a slot does its slot action; empty space outside the panel throws
     *  the cursor stack (all on left click, one on right click) into the world. */
    private void handleInventoryClick(int slot, boolean right, boolean insidePanel) {
        if (slot >= 0) {
            menu.click(slot, right);
        } else if (!insidePanel) {
            ItemStack thrown = menu.dropFromCursor(right);
            if (!thrown.isEmpty()) dropIntoWorld(thrown);
        }
    }

    /** Spawns a stack as dropped item entities just in front of the player. */
    private void dropIntoWorld(ItemStack stack) {
        Vector3f at = player.eyePosition().add(player.frontVector().mul(0.8f));
        itemEntities.spawn(stack.type(), stack.count(), stack.damage(), at, gameplayRandom);
    }

    private void renderInventoryScreen() {
        if (menu == null) return;
        int w = window.getWidth(), h = window.getHeight();
        InventoryLayout layout = new InventoryLayout(w, h, menu.getGridSize(), com.example.ui.GuiScale.compute(w, h, options.guiScale));
        float s = layout.scale();
        float mx = (float) input.getMouseX(), my = (float) input.getMouseY();
        int hovered = layout.slotAt(mx, my);

        GuiPainter.dim(uiRenderer, w, h);
        InventoryLayout.Rect p = layout.panel();
        com.example.engine.HudTextures.Sprite bg = guiSprite(layout.hasArmorSlots()
                ? "textures/gui/container/inventory.png" : "textures/gui/container/crafting_table.png");
        if (bg != null) GuiPainter.background(uiRenderer, bg, p);
        else GuiPainter.panel(uiRenderer, p.x(), p.y(), p.w(), p.h(), s);
        if (layout.hasArmorSlots()) renderPlayerPreview(layout, mx, my);
        font.draw(uiRenderer, "Crafting", layout.titleX(), layout.titleY(), s, 0x404040, false);
        if (!layout.hasArmorSlots()) { // vanilla's player inventory has no "Inventory" caption, the crafting table does
            font.draw(uiRenderer, "Inventory", layout.inventoryLabelX(), layout.inventoryLabelY(), s, 0x404040, false);
        }

        boolean frames = bg == null; // the real texture already has the slot frames, the grid arrow and the preview box
        for (int slot = 0; slot < Inventory.DEFAULT_SLOT_COUNT; slot++) drawMenuSlot(layout, slot, hovered, frames);
        for (int i = 0; i < menu.getGridSize() * menu.getGridSize(); i++) drawMenuSlot(layout, InventoryMenu.GRID_BASE + i, hovered, frames);
        drawMenuSlot(layout, InventoryMenu.RESULT_SLOT, hovered, frames);
        if (layout.hasArmorSlots()) for (int i = 0; i < 4; i++) drawMenuSlot(layout, InventoryMenu.ARMOR_BASE + i, hovered, frames);
        if (layout.hasArmorSlots()) drawMenuSlot(layout, InventoryMenu.OFFHAND_SLOT, hovered, frames);

        ItemStack held = menu.getCursor();
        if (!held.isEmpty()) {
            drawBlockIcon(held.type(), mx - 8 * s, my - 8 * s, 16 * s);
            if (held.count() > 1) drawStackCount(held.count(), mx - 8 * s, my - 8 * s, 16 * s);
        } else if (hovered >= 0) {
            ItemStack under = menu.stackAt(hovered);
            if (!under.isEmpty()) GuiPainter.tooltip(uiRenderer, font, under.type().displayName, mx, my, s, w, h, guiEnv.sprites);
        }
    }

    /**
     * The inventory's player preview, as vanilla's InventoryScreen.renderEntityInInventoryFollowsMouse draws it: the player
     * (skin, outer layers and worn armor) in the box (26,8)-(75,78) at 30 GUI pixels per block, full bright, turning to
     * face the mouse: body yaw 20 degrees and head yaw 40 degrees per atan((boxCentre - mouse) / 40), head and body pitch 20
     * degrees per atan of the vertical offset.
     */
    private void renderPlayerPreview(InventoryLayout layout, float mx, float my) {
        com.example.engine.EntityTexture skin = realTexture("player.steve", "textures/entity/player/wide/steve.png", false);
        if (skin == null) return;
        float s = layout.scale();
        InventoryLayout.Rect box = layout.previewBox();
        float cx = box.x() + box.w() / 2f, cy = box.y() + box.h() / 2f;
        float xAngle = (float) Math.atan((cx - mx) / (40f * s)), yAngle = (float) Math.atan((cy - my) / (40f * s));
        PlayerModelBuilder.Pose pose = PlayerModelBuilder.followMouse(xAngle, yAngle, (System.nanoTime() / 50_000_000L) % 1_000_000L);

        float k = 30f * s; // pixels per block
        float height = 1.8f;
        Matrix4f model = new Matrix4f().translate(cx, cy, 0f).scale(k, -k, k)
                .rotateX(-yAngle * (float) Math.toRadians(20))
                .translate(0f, -(height / 2f + 0.0625f), 0f);
        Matrix4f projection = new Matrix4f().ortho(0f, window.getWidth(), window.getHeight(), 0f, -1000f, 1000f);

        uiRenderer.end();
        glClear(GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        chunkShader.bind();
        chunkShader.setUniform("uProjection", projection);
        chunkShader.setUniform("uView", new Matrix4f());
        chunkShader.setUniform("uModel", model);
        chunkShader.setUniform("uCameraPos", 0f, 0f, 0f);
        chunkShader.setUniform("uTexture", 0);
        chunkShader.setUniform("uDaylightFactor", 1f);
        chunkShader.setUniform("uGamma", options.gamma);
        chunkShader.setUniform("uWindStrength", 0f);
        chunkShader.setUniform("uAlpha", 1f);
        chunkShader.setUniform("uFogStart", 1_000_000f);
        chunkShader.setUniform("uFogEnd", 2_000_000f);

        drawPreviewLayer(skin, PlayerModelBuilder.emitSkin(mobScratch, 0, pose, false, 1f));
        drawPreviewLayer(skin, PlayerModelBuilder.emitSkin(mobScratch, 0, pose, true, 1f));
        for (com.example.combat.EquipmentSlot slot : com.example.combat.EquipmentSlot.values()) {
            ItemStack worn = survival.getArmor().get(slot);
            if (worn.isEmpty()) continue;
            String material = armorMaterial(worn.type());
            String dir = slot == com.example.combat.EquipmentSlot.LEGS ? "humanoid_leggings" : "humanoid";
            com.example.engine.EntityTexture armor = realTexture("armor." + dir + "." + material,
                    "textures/entity/equipment/" + dir + "/" + material + ".png", false);
            if (armor == null) continue;
            boolean leather = material.equals("leather");
            float[] tint = leather ? PlayerModelBuilder.LEATHER_TINT : new float[]{1f, 1f, 1f};
            int floats = PlayerModelBuilder.emitArmor(mobScratch, 0, pose, slot, 1f);
            if (leather) tintLayer(mobScratch, floats, tint);
            drawPreviewLayer(armor, floats);
            if (leather) { // the undyed leather armor also has an untinted overlay layer
                com.example.engine.EntityTexture overlay = realTexture("armor." + dir + ".leather_overlay",
                        "textures/entity/equipment/" + dir + "/leather_overlay.png", false);
                if (overlay != null) drawPreviewLayer(overlay, PlayerModelBuilder.emitArmor(mobScratch, 0, pose, slot, 1f));
            }
        }
        drawPreviewHeldItem(model, pose, inventory.selected(), false);
        drawPreviewHeldItem(model, pose, inventory.getOffhand(), true);
        glDisable(GL_DEPTH_TEST);
        chunkShader.unbind();
        uiRenderer.begin(window.getWidth(), window.getHeight());
    }

    /** The item in one of the preview player's hands, drawn from the block atlas with the same meshes as the first-person view. */
    private void drawPreviewHeldItem(Matrix4f preview, PlayerModelBuilder.Pose pose, ItemStack stack, boolean left) {
        if (stack.isEmpty()) return;
        BlockType type = stack.type();
        boolean flat = ChunkMeshBuilder.isFlatIconItem(type);
        float[] verts = heldMeshCache.computeIfAbsent(type, h -> flat ? buildFlatItemHeldVerts(h) : buildHeldItemVerts(h));
        chunkShader.setUniform("uModel", new Matrix4f(preview).mul(PlayerModelBuilder.handItemMatrix(pose, left, flat)));
        atlas.bind(0);
        heldItemMesh.upload(verts);
        heldItemMesh.draw();
    }

    private void drawPreviewLayer(com.example.engine.EntityTexture texture, int floats) {
        texture.bind(0);
        mobMesh.upload(mobScratch, floats);
        mobMesh.draw();
    }

    /** Multiplies the tint of every vertex written so far by {@code tint}. */
    private static void tintLayer(float[] verts, int floats, float[] tint) {
        for (int i = 0; i < floats; i += 11) {
            verts[i + 7] = tint[0];
            verts[i + 8] = tint[1];
            verts[i + 9] = tint[2];
        }
    }

    /** The real container texture file as a sprite, loaded once; null (and remembered) when it is not there. */
    private com.example.engine.HudTextures.Sprite guiSprite(String path) {
        if (missingGuiSprites.contains(path)) return null;
        com.example.engine.HudTextures.Sprite s = guiSprites.get(path);
        if (s == null) {
            s = com.example.engine.HudTextures.loadFile(path);
            if (s == null) {
                missingGuiSprites.add(path);
                return null;
            }
            guiSprites.put(path, s);
        }
        return s;
    }

    private final java.util.Map<String, com.example.engine.HudTextures.Sprite> guiSprites = new java.util.HashMap<>();
    private final java.util.Set<String> missingGuiSprites = new java.util.HashSet<>();

    private void drawMenuSlot(InventoryLayout layout, int slot, int hovered, boolean frame) {
        InventoryLayout.Rect r = layout.slotRect(slot);
        ItemStack stack = menu.stackAt(slot);
        if (frame) GuiPainter.slot(uiRenderer, r.x(), r.y(), layout.scale());
        com.example.engine.HudTextures.Sprite hlBack = slot == hovered ? guiSprite("textures/gui/sprites/container/slot_highlight_back.png") : null;
        com.example.engine.HudTextures.Sprite hlFront = slot == hovered ? guiSprite("textures/gui/sprites/container/slot_highlight_front.png") : null;
        if (hlBack != null) GuiPainter.highlightSprite(uiRenderer, hlBack, r, layout.scale());
        if (!stack.isEmpty()) {
            drawBlockIcon(stack.type(), r.x(), r.y(), r.w());
            if (stack.count() > 1) drawStackCount(stack.count(), r.x(), r.y(), r.w());
            drawDurabilityBar(stack, r.x(), r.y(), r.w());
        } else {
            // vanilla draws a grey outline of what belongs in an empty armor or offhand slot
            String outline = slot == InventoryMenu.OFFHAND_SLOT ? "shield"
                    : slot >= InventoryMenu.ARMOR_BASE && slot < InventoryMenu.ARMOR_BASE + 4
                    ? new String[]{"helmet", "chestplate", "leggings", "boots"}[slot - InventoryMenu.ARMOR_BASE] : null;
            com.example.engine.HudTextures.Sprite sprite = outline == null ? null
                    : guiSprite("textures/gui/sprites/container/slot/" + outline + ".png");
            if (sprite != null) com.example.engine.HudTextures.draw(uiRenderer, sprite, r.x(), r.y(), layout.scale());
        }
        if (hlFront != null) GuiPainter.highlightSprite(uiRenderer, hlFront, r, layout.scale());
        else if (slot == hovered && hlBack == null) GuiPainter.highlight(uiRenderer, r);
    }

    // ---- cooking station screen (Furnace / Blast Furnace / Smoker) ----

    /** Fixed-step accumulator: cooking stations advance in real, constant-rate simulated ticks
     *  (20/second, matching real vanilla's own rate and this milestone's own "200 ticks/100
     *  ticks" numbers), never directly by render dt/frame count - the same behavior at 30fps and
     *  144fps. A stalled/slow frame just runs the while loop more times in one call rather than
     *  skipping ticks. Called for IN_GAME, INVENTORY, and COOKING alike (see {@link #update}) -
     *  real vanilla only stops world ticking at the actual pause menu (singleplayer's own pause
     *  behavior), not while a chest/crafting-table/furnace screen is open, so a furnace keeps
     *  smelting whether or not the player is looking at it. */
    private void tickCookingStations(float dt) {
        tickAccumulator += dt;
        while (tickAccumulator >= TICK_SECONDS) {
            tickAccumulator -= TICK_SECONDS;
            cookingStations.tickAll(world);
        }
    }

    /** Null for anything that isn't one of the 3 cooking-station blocks. */
    private static CookingStationType cookingStationTypeOf(BlockType type) {
        if (type == BlockType.FURNACE) return CookingStationType.FURNACE;
        if (type == BlockType.BLAST_FURNACE) return CookingStationType.BLAST_FURNACE;
        if (type == BlockType.SMOKER) return CookingStationType.SMOKER;
        return null;
    }

    /** Opens the screen for the station at (x, y, z) - the block there must be a real, already-
     *  registered cooking station (see onBlockBroken/BlockPlacer's own placement hook). */
    private void openCookingScreenAt(int x, int y, int z) {
        if (state != GameState.IN_GAME) throw new IllegalStateException("not in game");
        CookingStationState station = cookingStations.get(x, y, z);
        if (station == null) throw new IllegalStateException("no cooking station at " + x + "," + y + "," + z);
        cookingMenu = new CookingMenu(inventory, station);
        mining.cancel();
        state = GameState.COOKING;
        window.releaseMouse();
        input.resyncMousePosition();
    }

    private void closeCookingScreen() {
        if (cookingMenu == null) throw new IllegalStateException("no cooking screen is open");
        ItemStack leftover = cookingMenu.close(); // the station's own contents are untouched - see CookingMenu's own docs
        if (!leftover.isEmpty()) dropIntoWorld(leftover);
        cookingMenu = null;
        state = GameState.IN_GAME;
        window.captureMouse();
        input.resyncMousePosition();
    }

    private void updateCookingScreen() {
        if (input.consumeKeyPressed(GLFW_KEY_E) || input.consumeKeyPressed(GLFW_KEY_ESCAPE)) {
            closeCookingScreen();
            return;
        }
        boolean left = input.consumeMousePressed(GLFW_MOUSE_BUTTON_LEFT);
        boolean right = input.consumeMousePressed(GLFW_MOUSE_BUTTON_RIGHT);
        if (left || right) {
            float mx = (float) input.getMouseX(), my = (float) input.getMouseY();
            CookingLayout layout = new CookingLayout(window.getWidth(), window.getHeight(),
                    com.example.ui.GuiScale.compute(window.getWidth(), window.getHeight(), options.guiScale));
            handleCookingClick(layout.slotAt(mx, my), right, layout.panel().contains(mx, my));
        }
    }

    private void handleCookingClick(int slot, boolean right, boolean insidePanel) {
        if (slot >= 0) {
            cookingMenu.click(slot, right);
            float xp = cookingMenu.takeEarnedExperience();
            if (xp > 0f) survival.addExperience(xp); // granted the instant it's taken, never lost if the screen closes right after
        } else if (!insidePanel) {
            ItemStack thrown = cookingMenu.dropFromCursor(right);
            if (!thrown.isEmpty()) dropIntoWorld(thrown);
        }
    }

    private void renderCookingScreen() {
        if (cookingMenu == null) return;
        int w = window.getWidth(), h = window.getHeight();
        CookingLayout layout = new CookingLayout(w, h, com.example.ui.GuiScale.compute(w, h, options.guiScale));
        float s = layout.scale();
        float mx = (float) input.getMouseX(), my = (float) input.getMouseY();
        int hovered = layout.slotAt(mx, my);
        CookingStationState station = cookingMenu.getStation();

        GuiPainter.dim(uiRenderer, w, h);
        InventoryLayout.Rect p = layout.panel();
        String dir = station.getType().name().toLowerCase(java.util.Locale.ROOT);
        com.example.engine.HudTextures.Sprite bg = guiSprite("textures/gui/container/" + dir + ".png");
        if (bg != null) GuiPainter.background(uiRenderer, bg, p);
        else GuiPainter.panel(uiRenderer, p.x(), p.y(), p.w(), p.h(), s);

        String title = station.getType().displayName;
        font.draw(uiRenderer, title, layout.titleX(font.width(title, 1f)), layout.titleY(), s, 0x404040, false);
        font.draw(uiRenderer, "Inventory", layout.inventoryLabelX(), layout.inventoryLabelY(), s, 0x404040, false);

        boolean frames = bg == null;
        for (int slot = 0; slot < Inventory.DEFAULT_SLOT_COUNT; slot++) drawCookingSlot(layout, cookingMenu, slot, hovered, frames);
        drawCookingSlot(layout, cookingMenu, CookingMenu.INPUT_SLOT, hovered, frames);
        drawCookingSlot(layout, cookingMenu, CookingMenu.FUEL_SLOT, hovered, frames);
        drawCookingSlot(layout, cookingMenu, CookingMenu.OUTPUT_SLOT, hovered, frames);

        // The lit flame between input and fuel drains as the fuel burns; the arrow fills as the item cooks (vanilla's sprites).
        com.example.engine.HudTextures.Sprite lit = guiSprite("textures/gui/sprites/container/" + dir + "/lit_progress.png");
        if (lit != null && station.isBurning()) {
            int k = Math.min(14, (int) Math.ceil(station.burnFraction() * 13.0) + 1);
            com.example.engine.HudTextures.drawRegion(uiRenderer, lit, p.x() + 56 * s, p.y() + (36 + 14 - k) * s, 14 * s, k * s,
                    0f, (14f - k) / 14f, 1f, 1f);
        }
        com.example.engine.HudTextures.Sprite burn = guiSprite("textures/gui/sprites/container/" + dir + "/burn_progress.png");
        int filled = (int) Math.ceil(station.cookFraction() * 24.0);
        if (burn != null && filled > 0) {
            com.example.engine.HudTextures.drawRegion(uiRenderer, burn, p.x() + 79 * s, p.y() + 34 * s, filled * s, 16 * s,
                    0f, 0f, filled / 24f, 1f);
        }

        ItemStack held = cookingMenu.getCursor();
        if (!held.isEmpty()) {
            drawBlockIcon(held.type(), mx - 8 * s, my - 8 * s, 16 * s);
            if (held.count() > 1) drawStackCount(held.count(), mx - 8 * s, my - 8 * s, 16 * s);
        } else if (hovered >= 0) {
            ItemStack under = cookingMenu.stackAt(hovered);
            if (!under.isEmpty()) GuiPainter.tooltip(uiRenderer, font, under.type().displayName, mx, my, s, w, h, guiEnv.sprites);
        }
    }

    private void drawCookingSlot(CookingLayout layout, CookingMenu menu, int slot, int hovered, boolean frame) {
        InventoryLayout.Rect r = layout.slotRect(slot);
        ItemStack stack = menu.stackAt(slot);
        if (frame) GuiPainter.slot(uiRenderer, r.x(), r.y(), layout.scale());
        com.example.engine.HudTextures.Sprite hlBack = slot == hovered ? guiSprite("textures/gui/sprites/container/slot_highlight_back.png") : null;
        com.example.engine.HudTextures.Sprite hlFront = slot == hovered ? guiSprite("textures/gui/sprites/container/slot_highlight_front.png") : null;
        if (hlBack != null) GuiPainter.highlightSprite(uiRenderer, hlBack, r, layout.scale());
        if (!stack.isEmpty()) {
            drawBlockIcon(stack.type(), r.x(), r.y(), r.w());
            if (stack.count() > 1) drawStackCount(stack.count(), r.x(), r.y(), r.w());
            drawDurabilityBar(stack, r.x(), r.y(), r.w());
        }
        if (hlFront != null) GuiPainter.highlightSprite(uiRenderer, hlFront, r, layout.scale());
        else if (slot == hovered && hlBack == null) GuiPainter.highlight(uiRenderer, r);
    }

    /** Everything that follows one completed break: the sound, the dropped items, and the
     *  particle burst. MiningController guarantees this runs exactly once per broken block; the
     *  world change and drop roll already happened there, so nothing below can duplicate or
     *  lose them - and the purely cosmetic burst is isolated so a failure there can't affect the rest. */
    private void onBlockBroken(MiningController.Result r) {
        survival.onBlockBroken();
        ItemStack heldTool = inventory.selected();
        if (!heldTool.isEmpty()) damageSelectedItem(com.example.combat.Durability.blockBreakCost(heldTool.type(), r.brokenType().hardness));
        Vector3f center = new Vector3f(r.x() + 0.5f, r.y() + 0.5f, r.z() + 0.5f);
        gameAudio.playDig(r.brokenType(), center);
        for (BlockDrops.Resolved drop : r.drops()) {
            itemEntities.spawn(drop.item(), drop.count(), center, gameplayRandom);
        }
        if (cookingStationTypeOf(r.brokenType()) != null) {
            // Never delete a station's contents - drop input/fuel/output exactly like any other
            // block's own drops, right where it stood.
            CookingStationState broken = cookingStations.remove(r.x(), r.y(), r.z());
            if (broken != null) {
                for (ItemStack contents : broken.contents()) {
                    itemEntities.spawn(contents.type(), contents.count(), contents.damage(), center, gameplayRandom);
                }
            }
        }
        try {
            ItemModelBuilder.Model m = itemModelFor(r.brokenType());
            float[] tile = r.brokenType() == BlockType.GRASS_BLOCK ? m.bottom() : m.side();
            float sky = world.getSkyLight(r.x(), r.y() + 1, r.z()) / 15f;
            float blk = world.getBlockLight(r.x(), r.y(), r.z()) / 15f;
            particles.spawnBurst(center.x, center.y, center.z, tile, m.sideTint(), sky, blk, gameplayRandom);
        } catch (RuntimeException e) {
            log.warn("Break particles failed for {}: {}", r.brokenType(), e.toString());
        }
    }

    private void updateItemsAndEffects(float dt) {
        playerTarget.bind(player, survival);
        mobs.setEnvironment(computeDaylightFactor(sunDirection(dayTime)), weather.isRaining());
        if (survival.getDifficulty() == Difficulty.PEACEFUL) mobs.removeHostile();
        fallingBlocks.update(world, dt, itemEntities, gameplayRandom);
        arrows.update(world, mobTargets, dt, p -> gameAudio.play("arrow.hit", new Vector3f(p)));
        xpOrbs.update(world, player.position, dt, v -> {
            survival.addExperience((float) v);
            gameAudio.play("xp.pickup", null);
        });
        int level = survival.getLevel();
        if (level > lastLevel) gameAudio.play("levelup", null);
        lastLevel = level;
        float cooldown = survival.getHealth().hurtCooldownRemaining();
        if (cooldown > lastHurtCooldown + 1e-4f && survival.getHealth().isAlive()) gameAudio.play("player.hurt", null);
        lastHurtCooldown = cooldown;
        mobSpawner.tick(world, mobs, player.position, computeDaylightFactor(sunDirection(dayTime)),
                () -> survival.getDifficulty(), gameplayRandom, dt);
        passiveSpawner.tick(world, mobs, player.position, computeDaylightFactor(sunDirection(dayTime)), gameplayRandom, dt);
        mobs.tick(world, mobTargets, player.position, itemEntities, gameplayRandom, dt);
        survival.addExperience(mobs.drainExperience());
        itemEntities.update(world, dt);
        if (itemEntities.collect(player.position, inventory) > 0) gameAudio.playPickup();
        for (ItemEntityManager.Collected c : itemEntities.drainCollected()) pickups.add(new Pickup(c));
        for (int i = pickups.size() - 1; i >= 0; i--) {
            Pickup pk = pickups.get(i);
            pk.t += dt;
            if (pk.t >= PICKUP_SECONDS) pickups.remove(i);
        }
        particles.update(dt);
        critParticles.update(dt);
        damageParticles.update(dt);
    }

    /** Cached per-block-type UVs/tints for dropped items and particles (built once, on first use). */
    private ItemModelBuilder.Model itemModelFor(BlockType type) {
        return itemModels.computeIfAbsent(type, t -> {
            BlockTextureMap.Faces faces = textureMap.facesFor(t);
            float[] leafTint = ChunkMeshBuilder.leafIconTint(t);
            float[] topTint = leafTint != null ? leafTint : t == BlockType.GRASS_BLOCK ? ChunkMeshBuilder.grassIconTint() : NEUTRAL_ICON_TINT;
            float[] sideTint = leafTint != null ? leafTint : NEUTRAL_ICON_TINT;
            return new ItemModelBuilder.Model(atlas.getUv(faces.top()), atlas.getUv(faces.bottom()), atlas.getUv(faces.side()),
                    topTint, sideTint, ChunkMeshBuilder.isFlatIconItem(t),
                    faces.sideOverlay() != null ? atlas.getUv(faces.sideOverlay()) : null,
                    faces.sideOverlay() != null ? topTint : null);
        });
    }

    /** Pure geometric half of the sapling-planting rule (see BlockPlacer), package-private so it's directly testable without
     *  a Player/GL context (unlike blockIntersectsPlayer, which no block type's placement has
     *  ever needed to unit-test in this codebase). */
    static boolean isDirectlyAboveGround(Raycast.Hit hit) {
        return hit.placeX() == hit.x() && hit.placeZ() == hit.z() && hit.placeY() == hit.y() + 1;
    }

    /**
     * Is the camera's logical eye position (not the view-bob-adjusted render position, so
     * crossing the water surface doesn't flicker in and out with the bob) inside a water block.
     * Reuses the same single-point World.getBlock pattern Player's own inWater check already
     * uses, just at eye height instead of chest height.
     */
    private boolean isEyeSubmerged() {
        return isSubmerged(world, player.eyePosition());
    }

    /** Pure, exercised directly by tests in this package - see the wrapper above for real use. */
    static boolean isSubmerged(World world, Vector3f eyePos) {
        return world.getBlock((int) Math.floor(eyePos.x), (int) Math.floor(eyePos.y), (int) Math.floor(eyePos.z)) == BlockType.WATER;
    }

    private boolean blockIntersectsPlayer(int bx, int by, int bz) {
        float half = Player.WIDTH / 2f;
        float px0 = player.position.x - half, px1 = player.position.x + half;
        float py0 = player.position.y, py1 = player.position.y + player.currentHitboxHeight();
        float pz0 = player.position.z - half, pz1 = player.position.z + half;
        return bx + 1 > px0 && bx < px1 && by + 1 > py0 && by < py1 && bz + 1 > pz0 && bz < pz1;
    }

    private void render(float dt) {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (world != null && (state == GameState.IN_GAME || state == GameState.PAUSED || state == GameState.INVENTORY || state == GameState.COOKING
                || state == GameState.DEATH || (state == GameState.OPTIONS && optionsParent == GameState.PAUSED))) {
            renderWorld(dt);
        }

        glDisable(GL_DEPTH_TEST);
        uiRenderer.begin(window.getWidth(), window.getHeight());
        switch (state) {
            case TITLE -> titleScreen.render(uiRenderer, font, window.getWidth(), window.getHeight());
            case SELECT_WORLD -> selectWorldScreen.render(uiRenderer, font, window.getWidth(), window.getHeight());
            case WORLD_CREATE -> worldCreateScreen.render(uiRenderer, font, window.getWidth(), window.getHeight());
            case IN_GAME -> renderHud();
            case INVENTORY -> renderInventoryScreen();
            case COOKING -> renderCookingScreen();
            case PAUSED -> {
                renderHud();
                pauseScreen.render(uiRenderer, font, window.getWidth(), window.getHeight());
            }
            case OPTIONS -> optionsScreen.render(uiRenderer, font, window.getWidth(), window.getHeight());
            case DEATH -> deathScreen.render(uiRenderer, font, window.getWidth(), window.getHeight());
        }
        uiRenderer.end();
        glEnable(GL_DEPTH_TEST);
    }

    private void renderWorld(float dt) {
        float surfaceFogEnd = renderDistance * Chunk.SIZE_X * 0.9f;
        float surfaceFogStart = surfaceFogEnd * 0.6f;

        Vector3f skyColor = computeSkyColor(dayTime);
        Vector3f sunDir = sunDirection(dayTime);
        float daylightFactor = computeDaylightFactor(sunDir);

        // One climate sample per frame at the player's column - the same call the F3 overlay
        // already makes every frame with no observed cost, reused here instead of a second
        // biome/weight computation just for fog. Null for generators with no climate model
        // (flat worlds), in which case both sky and fog fall back to the plain time-of-day color.
        ClimateSample climate = world.climateAt((int) Math.floor(player.position.x), (int) Math.floor(player.position.z));

        // Sky: the time-of-day gradient, nudged toward the biome's own sky tint (subtle by
        // design, same convention as the fog tint below - real vanilla overworld sky color is
        // uniform, so this is this project's own adapted mood-shift, not a reproduction of a
        // vanilla mechanic). Every downstream use of "the sky color" (fog's own base, the clear
        // color) reads this already-biome-tinted value instead of the raw time-of-day one, so
        // sky and fog read as one consistent atmosphere rather than two independently-tinted
        // things.
        Vector3f biomeSkyColor = climate != null
                ? new Vector3f(skyColor).lerp(new Vector3f(climate.skyTint()), SKY_TINT_BLEND_STRENGTH)
                : new Vector3f(skyColor);

        // Surface fog: the (already biome-sky-tinted) base color, nudged further toward the
        // biome's own fog tint, then storms blend on top using the eased stormIntensity (0..1)
        // instead of the old instant boolean snap - this is what actually fixes the abrupt-
        // transition issue, since the storm now fades in/out over several seconds like
        // everything else in this codebase that's eased (sprint speed, FOV, eye height).
        Vector3f surfaceFogColor = climate != null
                ? new Vector3f(biomeSkyColor).lerp(new Vector3f(climate.fogColor()), BIOME_FOG_BLEND_STRENGTH)
                : new Vector3f(biomeSkyColor);
        float stormFactor = weather.getStormIntensity();
        if (stormFactor > 0f) {
            Vector3f stormColor = new Vector3f(0.35f, 0.37f, 0.40f);
            float strength = stormFactor * (weather.isThundering() ? 0.75f : 0.55f);
            surfaceFogColor = new Vector3f(surfaceFogColor).lerp(stormColor, strength);
            daylightFactor *= 1f - stormFactor * (weather.isThundering() ? 0.4f : 0.2f);
        }

        // Underwater fog: biome-blended, does not participate in the storm blend (a storm on
        // the surface doesn't change how murky the water itself is) and uses much shorter fog
        // distances, matching vanilla's short underwater visibility. Eased in/out via
        // underwaterBlend rather than snapping the instant the camera crosses the water plane -
        // "avoid abrupt transitions when entering or leaving water."
        Vector3f underwaterFogColor = climate != null
                ? new Vector3f(climate.underwaterFogColor()) : DEFAULT_UNDERWATER_FOG;
        boolean submerged = isEyeSubmerged();
        underwaterBlend += ((submerged ? 1f : 0f) - underwaterBlend) * Math.min(1f, UNDERWATER_TRANSITION_RATE * dt);

        Vector3f fogColor = new Vector3f(surfaceFogColor).lerp(underwaterFogColor, underwaterBlend);
        float fogStart = surfaceFogStart + (UNDERWATER_FOG_START - surfaceFogStart) * underwaterBlend;
        float fogEnd = surfaceFogEnd + (UNDERWATER_FOG_END - surfaceFogEnd) * underwaterBlend;
        // The background clear color matters wherever fog doesn't fully cover (e.g. above the
        // world's height limit) - blend it the same way so it never mismatches the fog it's
        // supposed to fade into.
        Vector3f clearColor = new Vector3f(biomeSkyColor).lerp(underwaterFogColor, underwaterBlend);
        glClearColor(clearColor.x, clearColor.y, clearColor.z, 1f);

        // The far plane must comfortably exceed CELESTIAL_DISTANCE or the sun/moon quads get
        // clipped and silently vanish (they were previously placed beyond a too-close far plane).
        float farPlane = Math.max(surfaceFogEnd + 32f, CELESTIAL_DISTANCE + 50f);
        Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(currentFov), window.aspectRatio(), 0.1f, farPlane);
        Matrix4f view = player.viewMatrix();
        Vector3f eye = player.renderEyePosition();

        chunkShader.bind();
        chunkShader.setUniform("uProjection", projection);
        chunkShader.setUniform("uView", view);
        chunkShader.setUniform("uCameraPos", eye.x, eye.y, eye.z);
        chunkShader.setUniform("uTexture", 0);
        chunkShader.setUniform("uFogColor", fogColor.x, fogColor.y, fogColor.z);
        chunkShader.setUniform("uFogStart", fogStart);
        chunkShader.setUniform("uFogEnd", fogEnd);
        chunkShader.setUniform("uDaylightFactor", daylightFactor);
        chunkShader.setUniform("uGamma", options.gamma);
        chunkShader.setUniform("uWindTime", windTime);
        chunkShader.setUniform("uWindStrength", WIND_STRENGTH);
        atlas.bind(0);

        chunkShader.setUniform("uAlpha", 1f);
        for (ChunkRenderData rd : renderData.values()) {
            Matrix4f model = new Matrix4f().translation(rd.chunkX * (float) Chunk.SIZE_X, 0, rd.chunkZ * (float) Chunk.SIZE_Z);
            chunkShader.setUniform("uModel", model);
            rd.opaqueMesh.draw();
        }
        chunkShader.setUniform("uAlpha", 0.85f);
        for (ChunkRenderData rd : renderData.values()) {
            Matrix4f model = new Matrix4f().translation(rd.chunkX * (float) Chunk.SIZE_X, 0, rd.chunkZ * (float) Chunk.SIZE_Z);
            chunkShader.setUniform("uModel", model);
            rd.transparentMesh.draw();
        }

        chunkShader.setUniform("uAlpha", 1f);
        renderBlockCrack();
        renderDroppedItems();
        renderMobs();
        renderArrows();
        renderXpOrbs();
        renderFallingBlocks();
        renderParticles();
        renderSpriteParticles();

        renderSunAndMoon(eye, sunDir, stormFactor, weather.isThundering());
        renderRain();
        renderHeldItem();

        chunkShader.unbind();
    }

    /**
     * First-person held-block view model: the currently selected hotbar block, rendered as a
     * small real 3D object (not a flat icon) in its own view-space projection so it never
     * clips into world geometry, with a walk/sprint sway plus a punch-style swing on
     * break/place - matches vanilla's held-item animation rather than an instant/static prop.
     */
    /** The crack overlay on the block currently being mined - drawn only while a valid break is
     *  in progress; MiningController's stage is the only input, so it resets automatically on
     *  cancel, target change, invalidation and completion. */
    private void renderBlockCrack() {
        int stage = mining.crackStage();
        if (stage < 0) return;
        crackOverlay.update(mining.targetX(), mining.targetY(), mining.targetZ(), stage);
        chunkShader.setUniform("uModel", IDENTITY);
        crackOverlay.bind(0);
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(-1f, -1f);
        crackOverlay.draw();
        glDisable(GL_POLYGON_OFFSET_FILL);
        atlas.bind(0);
    }

    /**
     * All dropped items, batched through the chunk shader and atlas. Blocks are small spinning cubes (display scale 0.25);
     * everything else (food, tools, ingots, saplings) is its flat sprite extruded 1/16 thick at vanilla's ground display
     * scale of 0.5, so a dropped steak looks like the steak icon rather than a crate. Items that were just collected keep
     * spinning while they fly to the player (vanilla's pickup animation: 3 ticks, easing in).
     */
    private void renderDroppedItems() {
        java.util.List<ItemEntity> list = itemEntities.entities();
        int n = 0;
        for (int i = 0; i < list.size(); i++) {
            ItemEntity e = list.get(i);
            float half = itemModelFor(e.getType()).cross() ? 0.25f : 0.125f;
            int seed = System.identityHashCode(e);
            float hover = droppedItemHover(e.getAge(), droppedItemPhase(seed));
            n = emitItemVisual(n, e.getType(), e.getCount(), e.position.x, e.position.y + half + hover, e.position.z, e.getAge(), seed);
        }
        for (Pickup pk : pickups) {
            float f = Math.min(1f, pk.t / PICKUP_SECONDS);
            f *= f;
            float half = itemModelFor(pk.type).cross() ? 0.25f : 0.125f;
            Vector3f to = player.position;
            int seed = System.identityHashCode(pk);
            float startY = pk.y + half + droppedItemHover(pk.age, droppedItemPhase(seed));
            n = emitItemVisual(n, pk.type, pk.count, pk.x + (to.x - pk.x) * f, startY + (to.y + 0.9f - startY) * f,
                    pk.z + (to.z - pk.z) * f, pk.age + pk.t, seed);
        }
        flushItems(n);
    }

    /** Writes one dropped stack's model(s) centred on (x, cy, z), spun by its age; returns the new write index. */
    private int emitItemVisual(int n, BlockType type, int count, float x, float cy, float z, float age, int seed) {
        ItemModelBuilder.Model m = itemModelFor(type);
        float spin = droppedItemSpin(age, droppedItemPhase(seed));
        float cos = (float) Math.cos(spin), sin = (float) Math.sin(spin);
        float half = m.cross() ? 0.25f : 0.125f;
        int bx = (int) Math.floor(x), by = (int) Math.floor(cy), bz = (int) Math.floor(z);
        float sky = world.getSkyLight(bx, by, bz) / 15f;
        float blk = world.getBlockLight(bx, by, bz) / 15f;
        int copies = renderCopies(count);
        java.util.Random spread = new java.util.Random(seed);
        for (int c = 0; c < copies; c++) {
            // later copies are nudged by a small fixed random offset so a stack reads as a pile
            float ox = c == 0 ? 0f : (spread.nextFloat() * 2f - 1f) * 0.15f * 0.5f;
            float oy = c == 0 ? 0f : (spread.nextFloat() * 2f - 1f) * 0.15f * 0.25f;
            float oz = c == 0 ? 0f : (spread.nextFloat() * 2f - 1f) * 0.15f * 0.5f;
            if (m.cross()) {
                float[] sprite = heldMeshCache.computeIfAbsent(type, this::buildFlatItemHeldVerts);
                if (n + sprite.length > itemScratch.length) {
                    flushItems(n);
                    n = 0;
                }
                n = ItemModelBuilder.emitSprite(itemScratch, n, sprite, x + ox, cy + oy, z + oz, 0.5f, cos, sin, sky, blk);
            } else {
                if (n + ItemModelBuilder.MAX_FLOATS_PER_ITEM > itemScratch.length) {
                    flushItems(n);
                    n = 0;
                }
                n = ItemModelBuilder.emitCube(itemScratch, n, x + ox, cy + oy, z + oz, half, cos, sin, m, sky, blk);
            }
        }
        return n;
    }

    /** A dropped item's own phase in radians: vanilla gives each item entity a random bobOffs in [0, 2 pi). */
    static float droppedItemPhase(int seed) {
        return new java.util.Random(seed).nextFloat() * (float) Math.PI * 2f;
    }

    /**
     * How far the lowest point of a dropped item floats above its entity position (ItemEntityRenderer: the model's bottom sits
     * 0.0625 up, plus a bob of sin(ageTicks / 10 + phase) * 0.1 + 0.1, so it rises and falls between 0.0625 and 0.2625).
     * Age is in seconds (20 ticks each).
     */
    static float droppedItemHover(float ageSeconds, float phase) {
        return 0.0625f + (float) Math.sin(ageSeconds * 2f + phase) * 0.1f + 0.1f;
    }

    /** The spin about the vertical axis in radians: ItemEntity.getSpin = ageTicks / 20 + phase, i.e. one radian per second. */
    static float droppedItemSpin(float ageSeconds, float phase) {
        return ageSeconds + phase;
    }

    /** How many copies of the model a dropped stack is drawn with (vanilla's ItemEntityRenderer.getRenderAmount). */
    static int renderCopies(int count) {
        if (count <= 1) return 1;
        if (count <= 16) return 2;
        if (count <= 32) return 3;
        if (count <= 48) return 4;
        return 5;
    }

    private void flushItems(int floats) {
        if (floats == 0) return;
        itemMesh.upload(itemScratch, floats);
        chunkShader.setUniform("uModel", IDENTITY);
        itemMesh.draw();
    }

    private void renderFallingBlocks() {
        java.util.List<com.example.entity.FallingBlockManager.Falling> list = fallingBlocks.entities();
        if (list.isEmpty()) return;
        int n = 0;
        for (com.example.entity.FallingBlockManager.Falling f : list) {
            if (n + 6 * 11 * 6 > itemScratch.length) break;
            ItemModelBuilder.Model m = itemModelFor(f.type);
            int bx = (int) Math.floor(f.position.x), by = (int) Math.floor(f.position.y + 0.5f), bz = (int) Math.floor(f.position.z);
            n = ItemModelBuilder.emitCube(itemScratch, n, f.position.x, f.position.y + 0.5f, f.position.z, 0.5f, 1f, 0f, m,
                    world.getSkyLight(bx, by, bz) / 15f, world.getBlockLight(bx, by, bz) / 15f);
        }
        if (n == 0) return;
        itemMesh.upload(itemScratch, n);
        chunkShader.setUniform("uModel", IDENTITY);
        itemMesh.draw();
    }

    private void renderArrows() {
        java.util.List<com.example.entity.ArrowManager.Arrow> list = arrows.arrows();
        if (list.isEmpty()) return;
        com.example.engine.EntityTexture tex = mobSkins.computeIfAbsent("arrow",
                k -> com.example.engine.EntityTexture.load("textures/entity/projectiles/arrow.png", 32, 32, 0x8A7048));
        int n = 0;
        for (com.example.entity.ArrowManager.Arrow a : list) {
            if (n + 12 * 11 > mobScratch.length) break;
            int bx = (int) Math.floor(a.position.x), by = (int) Math.floor(a.position.y), bz = (int) Math.floor(a.position.z);
            n = MobModelBuilder.emitArrow(mobScratch, n, a.position.x, a.position.y, a.position.z, a.facing.x, a.facing.y, a.facing.z,
                    world.getSkyLight(bx, by, bz) / 15f, world.getBlockLight(bx, by, bz) / 15f);
        }
        if (n == 0) return;
        chunkShader.setUniform("uModel", IDENTITY);
        tex.bind(0);
        mobMesh.upload(mobScratch, n);
        mobMesh.draw();
        atlas.bind(0);
    }

    private void renderMobs() {
        java.util.List<Mob> list = mobs.mobs();
        if (list.isEmpty()) return;
        if (mobSkins.isEmpty()) {
            mobSkins.put("zombie", com.example.engine.EntityTexture.load("textures/entity/zombie/zombie.png", 64, 64, 0x5E874C));
            // These skins were renamed twice: 1.21.5 made them the "temperate" variants (temperate_pig.png), 26.x then
            // swapped the word order (pig_temperate.png, checked in the 26.3 client jar). Newest name first, old names as fallback.
            mobSkins.put("pig", com.example.engine.EntityTexture.loadFirst(
                    new String[]{"textures/entity/pig/pig_temperate.png", "textures/entity/pig/temperate_pig.png", "textures/entity/pig/pig.png"},
                    64, 32, 0xECA0A0));
            mobSkins.put("cow", com.example.engine.EntityTexture.loadFirst(
                    new String[]{"textures/entity/cow/cow_temperate.png", "textures/entity/cow/temperate_cow.png", "textures/entity/cow/cow.png"},
                    64, 32, 0x694628));
            mobSkins.put("chicken", com.example.engine.EntityTexture.loadFirst(
                    new String[]{"textures/entity/chicken/chicken_temperate.png", "textures/entity/chicken/temperate_chicken.png", "textures/entity/chicken.png"},
                    64, 32, 0xF0F0F0));
            // 26.x gives the baby pig, cow and chicken their own textures (and models).
            mobSkins.put("pig_baby", com.example.engine.EntityTexture.loadFirst(
                    new String[]{"textures/entity/pig/pig_temperate_baby.png"}, 32, 32, 0xECA0A0));
            mobSkins.put("cow_baby", com.example.engine.EntityTexture.loadFirst(
                    new String[]{"textures/entity/cow/cow_temperate_baby.png"}, 64, 64, 0x694628));
            mobSkins.put("chicken_baby", com.example.engine.EntityTexture.loadFirst(
                    new String[]{"textures/entity/chicken/chicken_temperate_baby.png"}, 16, 16, 0xF0F0F0));
            for (String species : new String[]{"pig", "cow", "chicken", "pig_baby", "cow_baby", "chicken_baby"}) {
                MobModelBuilder.setSkinSize(species, mobSkins.get(species).width(), mobSkins.get(species).height());
            }
            mobSkins.put("creeper", com.example.engine.EntityTexture.load("textures/entity/creeper/creeper.png", 64, 32, 0x4FA13A));
            mobSkins.put("skeleton", com.example.engine.EntityTexture.load("textures/entity/skeleton/skeleton.png", 64, 32, 0xC9C9C9));
            mobSkins.put("spider", com.example.engine.EntityTexture.load("textures/entity/spider/spider.png", 64, 32, 0x3A2F28));
        }
        chunkShader.setUniform("uModel", IDENTITY);
        for (java.util.Map.Entry<String, com.example.engine.EntityTexture> skin : mobSkins.entrySet()) {
            int n = 0;
            for (Mob m : list) {
                if (!skin.getKey().equals(skinKeyOf(m))) continue;
                if (n + MobModelBuilder.FLOATS_PER_MOB > mobScratch.length) break;
                int bx = (int) Math.floor(m.position.x), by = (int) Math.floor(m.position.y + 1f), bz = (int) Math.floor(m.position.z);
                float sky = world.getSkyLight(bx, by, bz) / 15f, blk = world.getBlockLight(bx, by, bz) / 15f;
                float[] tint = m.getHurtTime() > 0f || !m.isAlive() ? new float[]{1.6f, 0.5f, 0.5f}
                        : m.isOnFire() ? new float[]{1.5f, 0.85f, 0.45f} : NEUTRAL_ICON_TINT;
                float tilt = m.isAlive() ? 0f : (float) (Math.PI / 2) * Math.min(1f, m.getDeathTime());
                float yaw = m.getYaw();
                float px = m.position.x, py = m.position.y, pz = m.position.z;
                float walk = m.getWalkPhase(), amt = m.getWalkAmount();
                switch (skin.getKey()) {
                    case "pig" -> n = MobModelBuilder.emitPig(mobScratch, n, px, py, pz, yaw, tilt, walk, amt, sky, blk, tint);
                    case "pig_baby" -> n = MobModelBuilder.emitBabyPig(mobScratch, n, px, py, pz, yaw, tilt, walk, amt, sky, blk, tint);
                    case "cow_baby" -> n = MobModelBuilder.emitBabyCow(mobScratch, n, px, py, pz, yaw, tilt, walk, amt, sky, blk, tint);
                    case "chicken_baby" -> n = MobModelBuilder.emitBabyChicken(mobScratch, n, px, py, pz, yaw, tilt, walk, amt,
                            !m.isOnGround(), sky, blk, tint);
                    case "creeper" -> {
                        float swell = ((com.example.entity.mob.Creeper) m).getSwellFraction();
                        float[] t2 = ((int) Math.floor(swell * 10f) & 1) == 1 && m.isAlive() ? new float[]{2.4f, 2.4f, 2.4f} : tint;
                        n = MobModelBuilder.emitCreeper(mobScratch, n, px, py, pz, yaw, tilt, walk, amt, swell, sky, blk, t2);
                    }
                    case "skeleton" -> n = MobModelBuilder.emitSkeleton(mobScratch, n, px, py, pz, yaw, tilt, walk, amt, sky, blk, tint);
                    case "spider" -> n = MobModelBuilder.emitSpider(mobScratch, n, px, py, pz, yaw, tilt, walk, amt, sky, blk, tint);
                    case "cow" -> n = MobModelBuilder.emitCow(mobScratch, n, px, py, pz, yaw, tilt, walk, amt, sky, blk, tint);
                    case "chicken" -> n = MobModelBuilder.emitChicken(mobScratch, n, px, py, pz, yaw, tilt, walk, amt,
                            !m.isOnGround(), sky, blk, tint);
                    default -> n = MobModelBuilder.emitZombie(mobScratch, n, px, py, pz, yaw, tilt, walk, amt, sky, blk, tint, m.isBaby() ? 0.5f : 1f);
                }
            }
            if (n == 0) continue;
            skin.getValue().bind(0);
            mobMesh.upload(mobScratch, n);
            mobMesh.draw();
        }
        atlas.bind(0); // everything drawn after the mobs expects the block atlas on unit 0
    }

    /** The skin (and model) a mob is drawn with: baby pigs, cows and chickens have their own, other babies are scaled adults. */
    private static String skinKeyOf(Mob m) {
        String id = m.typeId();
        return m.isBaby() && (id.equals("pig") || id.equals("cow") || id.equals("chicken")) ? id + "_baby" : id;
    }

    /** Experience orbs: small camera-facing quads, full bright, pulsing between green and yellow like vanilla. */
    /** Which of the 16 icons of {@code experience_orb.png} an orb of this value uses (vanilla's ExperienceOrbRenderer.getIcon). */
    static int xpOrbIcon(int value) {
        if (value >= 2477) return 10;
        if (value >= 1237) return 9;
        if (value >= 617) return 8;
        if (value >= 307) return 7;
        if (value >= 149) return 6;
        if (value >= 73) return 5;
        if (value >= 37) return 4;
        if (value >= 17) return 3;
        if (value >= 7) return 2;
        if (value >= 3) return 1;
        return 0;
    }

    /** Experience orbs: camera-facing 0.3-block quads of the real orb sheet, full bright, pulsing between green and yellow like vanilla. */
    private void renderXpOrbs() {
        java.util.List<com.example.entity.XpOrbManager.Orb> orbs = xpOrbs.orbs();
        if (orbs.isEmpty()) return;
        com.example.engine.EntityTexture tex = realTexture("entity.experience_orb", "textures/entity/experience/experience_orb.png", false);
        if (tex == null) return;
        Vector3f front = player.frontVector();
        Vector3f right = new Vector3f(-front.z, 0, front.x);
        if (right.lengthSquared() < 1e-6f) right.set(1, 0, 0);
        right.normalize();
        Vector3f up = new Vector3f(right).cross(front).normalize();
        int n = 0;
        for (com.example.entity.XpOrbManager.Orb o : orbs) {
            if (n + 6 * 11 > mobScratch.length) break;
            int icon = xpOrbIcon(o.value);
            float u0 = (icon % 4) * 16f / 64f, v0 = (icon / 4) * 16f / 64f, u1 = u0 + 16f / 64f, v1 = v0 + 16f / 64f;
            float phase = o.age * 20f / 2f; // vanilla: (age + partial) / 2
            float r = ((float) Math.sin(phase) + 1f) * 0.5f, g = 1f, b = ((float) Math.sin(phase + 4.1887903f) + 1f) * 0.1f;
            float half = 0.15f; // the quad is 0.3 blocks wide
            float[][] c = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            int[] order = {0, 1, 2, 0, 2, 3};
            float[][] uvc = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
            for (int k : order) {
                mobScratch[n++] = o.position.x + (right.x * c[k][0] + up.x * c[k][1]) * half;
                mobScratch[n++] = o.position.y + 0.15f + (right.y * c[k][0] + up.y * c[k][1]) * half;
                mobScratch[n++] = o.position.z + (right.z * c[k][0] + up.z * c[k][1]) * half;
                mobScratch[n++] = uvc[k][0];
                mobScratch[n++] = uvc[k][1];
                mobScratch[n++] = 1f;
                mobScratch[n++] = 1f;
                mobScratch[n++] = r;
                mobScratch[n++] = g;
                mobScratch[n++] = b;
                mobScratch[n++] = 0f;
            }
        }
        if (n == 0) return;
        chunkShader.setUniform("uModel", IDENTITY);
        tex.bind(0);
        mobMesh.upload(mobScratch, n);
        mobMesh.draw();
        atlas.bind(0);
    }

    private void drawAirBubbles(HudTextures t, float hx, float y, float s) {
        float air = survival.getAirTicks();
        if (t.air == null || t.airBursting == null) return;
        if (air >= PlayerSurvival.MAX_AIR_TICKS && !player.isHeadSubmerged()) return;
        int full = (int) Math.ceil((air - 2) * 10.0 / PlayerSurvival.MAX_AIR_TICKS);
        int total = (int) Math.ceil(air * 10.0 / PlayerSurvival.MAX_AIR_TICKS);
        for (int i = 0; i < Math.max(total, 0); i++) {
            float x = hx + (182 - 9) * s - i * 8 * s;
            HudTextures.draw(uiRenderer, i < full ? t.air : t.airBursting, x, y, s);
        }
    }

    private void drawExperienceBar(HudTextures t, float hx, int h, float s) {
        if (t.xpBackground == null || t.xpProgress == null) return;
        float y = h - 29 * s;
        HudTextures.draw(uiRenderer, t.xpBackground, hx, y, s);
        HudTextures.drawFractionX(uiRenderer, t.xpProgress, hx, y, s, survival.getLevelProgress());
        int level = survival.getLevel();
        if (level > 0) {
            String text = Integer.toString(level);
            float scale = s * 0.9f;
            float tw = font.measureText(text, scale);
            float tx = hx + 91 * s - tw / 2f, ty = y - 7 * s;
            for (float[] o : new float[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                font.drawText(uiRenderer, text, tx + o[0] * s * 0.5f, ty + o[1] * s * 0.5f, scale, 0f, 0f, 0f, 1f);
            }
            font.drawText(uiRenderer, text, tx, ty, scale, 0.5f, 1f, 0.125f, 1f);
        }
    }

    /** Attack charge 0..1 for the held item (1 = ready), shared by the HUD indicator. */
    private float attackCharge() {
        ItemStack held = inventory.selected();
        BlockType type = held.isEmpty() ? null : held.type();
        return com.example.combat.CombatMath.charge(attackTimer * 20f, com.example.combat.WeaponStats.periodTicks(type));
    }

    private void drawAttackIndicator(HudTextures t, int w, int h, float hx, float hy, float s) {
        if (options.attackIndicator == 0) return;
        float charge = attackCharge();
        if (options.attackIndicator == 1 && t.crosshairIndicatorBg != null && t.crosshairIndicatorProgress != null) {
            float cx = w / 2f, cy = h / 2f;
            if (charge < 1f) {
                float x = cx - 8 * s, y = cy + 9 * s;
                HudTextures.draw(uiRenderer, t.crosshairIndicatorBg, x, y, s);
                HudTextures.drawFractionX(uiRenderer, t.crosshairIndicatorProgress, x, y, s, charge);
            }
        } else if (options.attackIndicator == 2 && t.hotbarIndicatorBg != null && t.hotbarIndicatorProgress != null && charge < 1f) {
            float x = hx + 182 * s + 6 * s, y = h - 20 * s;
            HudTextures.draw(uiRenderer, t.hotbarIndicatorBg, x, y, s);
            HudTextures.drawFractionY(uiRenderer, t.hotbarIndicatorProgress, x, y, s, charge);
        }
    }

    private long lastFrameEndNanos;

    /** Vanilla Max Framerate: sleep out the rest of the frame when below the "Unlimited" setting. */
    private void limitFrameRate(long frameStartNanos) {
        if (options.maxFps >= GameOptions.UNLIMITED_FPS) return;
        long target = 1_000_000_000L / options.maxFps;
        long remaining = target - (System.nanoTime() - frameStartNanos);
        if (remaining > 1_000_000L) {
            try {
                Thread.sleep(remaining / 1_000_000L - 1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        while (System.nanoTime() - frameStartNanos < target) Thread.onSpinWait();
    }

    /** F2: saves the current frame to screenshots/ with a timestamp name. */
    private void takeUserScreenshot() {
        try {
            File dir = new File("screenshots");
            dir.mkdirs();
            String name = new java.text.SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new java.util.Date());
            File saved = new File(captureScreenshotTo(dir, name));
            log.info("Screenshot saved to {}", saved.getPath());
        } catch (IOException | RuntimeException e) {
            log.warn("Screenshot failed: {}", e.toString());
        }
    }

    /** Q drops one item, Ctrl+Q the whole stack, tossed along the view with a 2 s pickup delay. */
    private void dropHeldItem(boolean wholeStack) {
        int slot = inventory.getSelectedSlot();
        ItemStack s = inventory.get(slot);
        if (s.isEmpty()) return;
        int n = wholeStack ? s.count() : 1;
        Vector3f eye = player.eyePosition();
        itemEntities.throwItem(s.type(), n, s.damage(), eye, player.frontVector(), gameplayRandom);
        inventory.removeFromSlot(slot, n);
        startSwing();
    }

    /** Middle click: select the hotbar slot holding the targeted block, or swap it in from the main inventory. */
    private void pickBlock(BlockType type) {
        if (type == null || type == BlockType.AIR) return;
        for (int i = 0; i < inventory.getHotbarSize(); i++) {
            if (!inventory.get(i).isEmpty() && inventory.get(i).type() == type) {
                inventory.selectSlot(i);
                hotbarLabelTimer = HOTBAR_LABEL_DURATION;
                return;
            }
        }
        for (int i = inventory.getHotbarSize(); i < inventory.slotCount(); i++) {
            if (!inventory.get(i).isEmpty() && inventory.get(i).type() == type) {
                int sel = inventory.getSelectedSlot();
                ItemStack a = inventory.get(sel), b = inventory.get(i);
                inventory.set(sel, b);
                inventory.set(i, a);
                hotbarLabelTimer = HOTBAR_LABEL_DURATION;
                return;
            }
        }
    }

    /** Vanilla death: every inventory and armor stack drops where the player died, and the XP spills as orbs. */
    private void dropEverythingOnDeath() {
        Vector3f at = new Vector3f(player.position.x, player.position.y + 0.9f, player.position.z);
        for (int i = 0; i < inventory.slotCount(); i++) {
            ItemStack s = inventory.get(i);
            if (!s.isEmpty()) itemEntities.spawn(s.type(), s.count(), s.damage(), at, gameplayRandom);
            inventory.set(i, ItemStack.EMPTY);
        }
        ItemStack offhandStack = inventory.getOffhand();
        if (!offhandStack.isEmpty()) itemEntities.spawn(offhandStack.type(), offhandStack.count(), offhandStack.damage(), at, gameplayRandom);
        inventory.setOffhand(ItemStack.EMPTY);
        for (com.example.combat.EquipmentSlot slot : com.example.combat.EquipmentSlot.values()) {
            ItemStack s = survival.getArmor().get(slot);
            if (!s.isEmpty()) itemEntities.spawn(s.type(), 1, s.damage(), at, gameplayRandom);
        }
        survival.getArmor().clear();
        xpOrbs.spawn(survival.takeDeathExperience(), at, gameplayRandom);
    }

    private void renderParticles() {
        if (particles.count() == 0) return;
        Vector3f front = player.frontVector();
        Vector3f right = new Vector3f(-front.z, 0, front.x);
        if (right.lengthSquared() < 1e-6f) right.set(1, 0, 0);
        right.normalize();
        Vector3f up = new Vector3f(right).cross(front).normalize();
        int floats = particles.buildVertices(particleScratch, right, up);
        particleMesh.upload(particleScratch, floats);
        chunkShader.setUniform("uModel", IDENTITY);
        particleMesh.draw();
    }

    /** The real texture file under {@code key}, loaded once; null (and remembered) when the file is not there. */
    private com.example.engine.EntityTexture realTexture(String key, String path, boolean repeat) {
        if (missingTextures.contains(key)) return null;
        com.example.engine.EntityTexture tex = realTextures.get(key);
        if (tex == null) {
            tex = com.example.engine.EntityTexture.loadReal(path, repeat);
            if (tex == null) {
                missingTextures.add(key);
                return null;
            }
            realTextures.put(key, tex);
        }
        return tex;
    }

    /** Critical-hit stars and damage-indicator hearts: camera-facing sprites over the real particle textures. */
    private void renderSpriteParticles() {
        drawSpriteParticles(critParticles, "particle.critical_hit", "textures/particle/critical_hit.png");
        drawSpriteParticles(damageParticles, "particle.damage", "textures/particle/damage.png");
    }

    private void drawSpriteParticles(com.example.fx.SpriteParticles ps, String key, String path) {
        if (ps.count() == 0) return;
        com.example.engine.EntityTexture tex = realTexture(key, path, false);
        if (tex == null) return;
        Vector3f front = player.frontVector();
        Vector3f right = new Vector3f(-front.z, 0, front.x);
        if (right.lengthSquared() < 1e-6f) right.set(1, 0, 0);
        right.normalize();
        Vector3f up = new Vector3f(right).cross(front).normalize();
        Vector3f at = player.position;
        int bx = (int) Math.floor(at.x), by = (int) Math.floor(at.y + 1f), bz = (int) Math.floor(at.z);
        int floats = ps.buildVertices(particleScratch, right, up, world.getSkyLight(bx, by, bz) / 15f, world.getBlockLight(bx, by, bz) / 15f);
        particleMesh.upload(particleScratch, floats);
        chunkShader.setUniform("uModel", IDENTITY);
        tex.bind(0);
        particleMesh.draw();
        atlas.bind(0);
    }

    /** The eating motion of the hand that is using food, or identity when that hand is not eating. */
    private Matrix4f eatMotion(boolean offhand) {
        if (eatUseTicks <= 0f || eatingOffhand != offhand) return new Matrix4f();
        return HeldItemModel.eat(eatUseTicks + 1f - eatTimer * 20f, eatUseTicks, 1);
    }

    /**
     * Starts the hand swing as vanilla's SwingState.startIfAble does: only when no swing is running or the running one is past
     * half its duration (3 of its 6 ticks), which is why holding the mine button gives quick half swings.
     */
    private void startSwing() {
        float elapsedTicks = (SWING_DURATION - swingTimer) * 20f;
        if (swingTimer <= 0f || elapsedTicks > 3f || elapsedTicks <= 0f) swingTimer = SWING_DURATION;
    }

    private void renderHeldItem() {
        BlockType shown = shownHeld;
        glClear(GL_DEPTH_BUFFER_BIT);

        // Vanilla draws the hand with the player's field of view; the transforms below are vanilla's ItemInHandRenderer ones.
        Matrix4f vmProjection = new Matrix4f().perspective((float) Math.toRadians(options.fov), window.aspectRatio(), 0.05f, 20f);
        Matrix4f vmView = new Matrix4f();

        float bobPhase = player.getBobPhase();
        float bobAmount = player.getBobAmplitude();
        float bobX = (float) Math.sin(bobPhase) * bobAmount * 0.6f;
        float bobY = (float) Math.abs(Math.sin(bobPhase * 2f)) * bobAmount * 0.5f;

        // 0..1 progress through the swing (vanilla's attackAnim); 0 when idle
        float swing = swingTimer > 0f ? 1f - swingTimer / SWING_DURATION : 0f;
        boolean eatingMain = eatUseTicks > 0f && !eatingOffhand;
        if (eatingMain) swing = 0f; // vanilla applies no attack motion to the hand that is using an item

        float drop = HeldItemModel.equipDrop(equipRaised);
        float[] verts;
        Matrix4f model;
        com.example.engine.EntityTexture armSkin = null;
        if (shown == null) {
            // Nothing in the hand: the bare 4x12x4 px arm of the real player skin, the only time vanilla draws the arm.
            armSkin = realTexture("player.steve", "textures/entity/player/wide/steve.png", false);
            if (armSkin == null) return;
            verts = armVerts();
            model = HeldItemModel.emptyArm(swing, drop);
        } else {
            boolean flat = ChunkMeshBuilder.isFlatIconItem(shown);
            verts = heldMeshCache.computeIfAbsent(shown, h -> flat ? buildFlatItemHeldVerts(h) : buildHeldItemVerts(h));
            model = flat ? HeldItemModel.flatItem(swing, drop) : HeldItemModel.block(swing, drop);
        }
        model = new Matrix4f().translate(bobX * 0.5f, -bobY * 0.5f, 0f).mul(eatMotion(false)).mul(model);

        chunkShader.setUniform("uProjection", vmProjection);
        chunkShader.setUniform("uView", vmView);
        chunkShader.setUniform("uModel", model);
        chunkShader.setUniform("uAlpha", 1f);
        chunkShader.setUniform("uFogStart", 1_000_000f);
        chunkShader.setUniform("uFogEnd", 2_000_000f);

        uploadLitHeld(verts);
        if (armSkin != null) armSkin.bind(0);
        heldItemMesh.draw();
        if (armSkin != null) atlas.bind(0);
        renderOffhandItem();
    }

    /**
     * The item in the left hand: vanilla draws it with the mirrored right-hand transform (side -1), with its own equip
     * animation and no swing (attacking is the main hand); an empty offhand shows nothing, not even an arm.
     */
    private void renderOffhandItem() {
        BlockType shown = shownOffhand;
        if (shown == null) return;
        float bobPhase = player.getBobPhase();
        float bobAmount = player.getBobAmplitude();
        float bobX = (float) Math.sin(bobPhase) * bobAmount * 0.6f;
        float bobY = (float) Math.abs(Math.sin(bobPhase * 2f)) * bobAmount * 0.5f;
        float drop = HeldItemModel.equipDrop(equipRaisedOff);
        boolean flat = ChunkMeshBuilder.isFlatIconItem(shown);
        float[] verts = heldMeshCache.computeIfAbsent(shown, h -> flat ? buildFlatItemHeldVerts(h) : buildHeldItemVerts(h));
        Matrix4f model = new Matrix4f().translate(bobX * 0.5f, -bobY * 0.5f, 0f).scale(-1f, 1f, 1f)
                .mul(eatMotion(true))
                .mul(flat ? HeldItemModel.flatItem(0f, drop) : HeldItemModel.block(0f, drop));
        chunkShader.setUniform("uModel", model);
        uploadLitHeld(verts);
        heldItemMesh.draw();
    }

    private float[] heldLit = new float[0];

    /**
     * Uploads a held-item mesh lit by the world: the sky and block light at the player's eye, with the mesh's own baked
     * per-face brightness on the sky part, so the hands are as bright as everything else - dark at night, dim in a cave,
     * lit by a torch (vanilla lights the first-person hands with the light at the player).
     */
    private void uploadLitHeld(float[] src) {
        Vector3f eye = player.eyePosition();
        int bx = (int) Math.floor(eye.x), by = (int) Math.floor(eye.y), bz = (int) Math.floor(eye.z);
        float sky = world.getSkyLight(bx, by, bz) / 15f, blk = world.getBlockLight(bx, by, bz) / 15f;
        if (heldLit.length < src.length) heldLit = new float[src.length];
        System.arraycopy(src, 0, heldLit, 0, src.length);
        for (int i = 0; i + 6 < src.length; i += 11) {
            float shade = src[i + 6]; // the baked face brightness lives in the block-light slot of a held mesh
            heldLit[i + 5] = shade * sky;
            heldLit[i + 6] = blk;
        }
        heldItemMesh.upload(heldLit, src.length);
    }

    private float[] armVertsCache;

    private final java.util.Map<BlockType, float[]> heldMeshCache = new java.util.EnumMap<>(BlockType.class);

    /** The bare-hand arm: the 4x12x4 px player arm UV-mapped onto the real skin, built once (its motion comes from the model matrix). */
    private float[] armVerts() {
        if (armVertsCache == null) armVertsCache = HeldItemModel.armBox();
        return armVertsCache;
    }

    /** A standalone unit cube (-0.5..0.5) with all 6 faces always emitted - no neighbor culling needed for a held item. */
    private float[] buildHeldItemVerts(BlockType block) {
        BlockTextureMap.Faces faces = textureMap.facesFor(block);
        float[] top = atlas.getUv(faces.top());
        float[] bottom = atlas.getUv(faces.bottom());
        float[] side = atlas.getUv(faces.side());

        // Same tint split as drawBlockIcon: leaf species tint every face, grass tints only the
        // top (its side/bottom textures are already-colored images), everything else is neutral.
        float[] leafTint = ChunkMeshBuilder.leafIconTint(block);
        float[] topTint = leafTint != null ? leafTint : block == BlockType.GRASS_BLOCK ? ChunkMeshBuilder.grassIconTint() : NEUTRAL_ICON_TINT;
        float[] sideTint = leafTint != null ? leafTint : NEUTRAL_ICON_TINT;

        float[] overlay = faces.sideOverlay() != null ? atlas.getUv(faces.sideOverlay()) : null;
        float[] verts = new float[(overlay != null ? 10 : 6) * 6 * 11];
        int idx = 0;
        idx = emitCubeFace(verts, idx, new float[]{-0.5f, 0.5f, -0.5f}, new float[]{-0.5f, 0.5f, 0.5f}, new float[]{0.5f, 0.5f, 0.5f}, new float[]{0.5f, 0.5f, -0.5f}, top, 1.0f, topTint);
        idx = emitCubeFace(verts, idx, new float[]{-0.5f, -0.5f, 0.5f}, new float[]{-0.5f, -0.5f, -0.5f}, new float[]{0.5f, -0.5f, -0.5f}, new float[]{0.5f, -0.5f, 0.5f}, bottom, 0.6f, sideTint);
        idx = emitCubeFace(verts, idx, new float[]{-0.5f, -0.5f, 0.5f}, new float[]{0.5f, -0.5f, 0.5f}, new float[]{0.5f, 0.5f, 0.5f}, new float[]{-0.5f, 0.5f, 0.5f}, side, 0.85f, sideTint);
        idx = emitCubeFace(verts, idx, new float[]{0.5f, -0.5f, -0.5f}, new float[]{-0.5f, -0.5f, -0.5f}, new float[]{-0.5f, 0.5f, -0.5f}, new float[]{0.5f, 0.5f, -0.5f}, side, 0.85f, sideTint);
        idx = emitCubeFace(verts, idx, new float[]{0.5f, -0.5f, 0.5f}, new float[]{0.5f, -0.5f, -0.5f}, new float[]{0.5f, 0.5f, -0.5f}, new float[]{0.5f, 0.5f, 0.5f}, side, 0.7f, sideTint);
        idx = emitCubeFace(verts, idx, new float[]{-0.5f, -0.5f, -0.5f}, new float[]{-0.5f, -0.5f, 0.5f}, new float[]{-0.5f, 0.5f, 0.5f}, new float[]{-0.5f, 0.5f, -0.5f}, side, 0.7f, sideTint);
        if (overlay != null) { // grass: biome-tinted fringe, drawn a hair outside the base faces
            float k = 1.004f;
            idx = emitCubeFace(verts, idx, new float[]{-0.5f * k, -0.5f * k, 0.5f * k}, new float[]{0.5f * k, -0.5f * k, 0.5f * k}, new float[]{0.5f * k, 0.5f * k, 0.5f * k}, new float[]{-0.5f * k, 0.5f * k, 0.5f * k}, overlay, 0.85f, topTint);
            idx = emitCubeFace(verts, idx, new float[]{0.5f * k, -0.5f * k, -0.5f * k}, new float[]{-0.5f * k, -0.5f * k, -0.5f * k}, new float[]{-0.5f * k, 0.5f * k, -0.5f * k}, new float[]{0.5f * k, 0.5f * k, -0.5f * k}, overlay, 0.85f, topTint);
            idx = emitCubeFace(verts, idx, new float[]{0.5f * k, -0.5f * k, 0.5f * k}, new float[]{0.5f * k, -0.5f * k, -0.5f * k}, new float[]{0.5f * k, 0.5f * k, -0.5f * k}, new float[]{0.5f * k, 0.5f * k, 0.5f * k}, overlay, 0.7f, topTint);
            idx = emitCubeFace(verts, idx, new float[]{-0.5f * k, -0.5f * k, -0.5f * k}, new float[]{-0.5f * k, -0.5f * k, 0.5f * k}, new float[]{-0.5f * k, 0.5f * k, 0.5f * k}, new float[]{-0.5f * k, 0.5f * k, -0.5f * k}, overlay, 0.7f, topTint);
        }
        return verts;
    }

    /** Two crossing diagonal planes centered at the origin (-0.5..0.5, matching the cube's own
     *  scale) - the same shape ChunkMeshBuilder.emitCrossPlant uses in-world, just recentered
     *  and reusing emitCubeFace's quad-emission (its p0..p3 corner-to-UV convention already
     *  matches emitCrossPlant's own corner ordering exactly). Saplings are never tinted (not a
     *  leaf, not the grass block), so this always uses the neutral tint. */
    /** A single sprite plane (front + back), U mirrored so a tool's head points away from the hand. */
    private float[] buildFlatItemHeldVerts(BlockType block) {
        Tile tile = textureMap.facesFor(block).side();
        return HeldItemModel.extrudedSprite(atlas.alphaMask(tile), atlas.getUv(tile));
    }

    private int emitCubeFace(float[] arr, int idx, float[] p0, float[] p1, float[] p2, float[] p3, float[] uv, float shade, float[] tint) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        idx = emitHeldVert(arr, idx, p0, u0, v1, shade, tint);
        idx = emitHeldVert(arr, idx, p1, u1, v1, shade, tint);
        idx = emitHeldVert(arr, idx, p2, u1, v0, shade, tint);
        idx = emitHeldVert(arr, idx, p0, u0, v1, shade, tint);
        idx = emitHeldVert(arr, idx, p2, u1, v0, shade, tint);
        idx = emitHeldVert(arr, idx, p3, u0, v0, shade, tint);
        return idx;
    }

    private int emitHeldVert(float[] arr, int idx, float[] p, float u, float v, float shade, float[] tint) {
        arr[idx++] = p[0];
        arr[idx++] = p[1];
        arr[idx++] = p[2];
        arr[idx++] = u;
        arr[idx++] = v;
        arr[idx++] = 0f; // skyShade unused - the held item ignores day/night and world lighting
        arr[idx++] = shade; // baked per-face brightness lives in the blockLight channel instead,
        // since that one isn't multiplied by uDaylightFactor, so it stays constant day or night
        arr[idx++] = tint[0];
        arr[idx++] = tint[1];
        arr[idx++] = tint[2];
        arr[idx++] = 0f; // wind weight - the held item never sways
        return idx;
    }

    /**
     * Vanilla's rain: for every block column within {@link #RAIN_COLUMN_RADIUS} of the camera, one vertical quad (turned to
     * face the camera) from the column's rain-proof surface up to the camera's height plus the radius, textured with the real
     * {@code environment/rain.png}, one block wide with four blocks per texture height, and scrolled downwards at a rate that
     * is randomised per column. Columns under a roof draw nothing because their quad would be empty.
     */
    /** The shader has one alpha per draw, so the columns are drawn in this many alpha steps (nearest the camera first). */
    private static final int RAIN_ALPHA_STEPS = 6;
    private final float[][] rainBuckets = new float[RAIN_ALPHA_STEPS][(2 * RAIN_COLUMN_RADIUS + 1) * (2 * RAIN_COLUMN_RADIUS + 1) * 6 * 11];

    /**
     * Vanilla's alpha for a rain column: ((1 - d^2) * 0.5 + 0.5) times the rain level, with d the column's distance from the
     * camera as a fraction of the rain radius, so the streaks fade out towards the edge of the rain.
     */
    static float rainColumnAlpha(float distanceFraction, float rainLevel) {
        float d = Math.min(1f, distanceFraction);
        return ((1f - d * d) * 0.5f + 0.5f) * rainLevel;
    }

    private void renderRain() {
        float rainLevel = weather.getStormIntensity();
        if (!weather.isRaining() && rainLevel < 0.01f) return;
        com.example.engine.EntityTexture rain = realTexture("environment.rain", "textures/environment/rain.png", true);
        if (rain == null) return;

        Vector3f cam = player.renderEyePosition();
        int cx = (int) Math.floor(cam.x), cy = (int) Math.floor(cam.y), cz = (int) Math.floor(cam.z);
        int ticks = (int) Math.floor(rainTime * 20f);
        float partial = rainTime * 20f - ticks;
        int r = RAIN_COLUMN_RADIUS;

        int[] counts = new int[RAIN_ALPHA_STEPS];
        for (int z = cz - r; z <= cz + r; z++) {
            for (int x = cx - r; x <= cx + r; x++) {
                int surface = world.getColumnHeightAt(x, z) + 1;
                int bottom = Math.max(cy - r, surface), top = Math.max(cy + r, surface);
                if (bottom == top) continue;
                float dx = x - cx, dz = z - cz;
                float len = (float) Math.sqrt(dx * dx + dz * dz);
                float alpha = rainColumnAlpha(len / r, rainLevel);
                int step = Math.min(RAIN_ALPHA_STEPS - 1, (int) (alpha * RAIN_ALPHA_STEPS));
                if (len < 1e-4f) { dx = 0f; dz = 1f; len = 1f; }
                float sx = -dz / len * 0.5f, sz = dx / len * 0.5f; // half the quad's width, sideways to the line of sight
                int seed = x * x * 3121 + x * 45238971 + z * z * 418711 + z * 13761;
                float jitter = new java.util.Random(seed).nextFloat();
                float scroll = -(((ticks + seed) & 31) + partial) / 32f * (3f + jitter);
                int lightY = Math.max(surface, cy);
                float sky = world.getSkyLight(x, lightY, z) / 15f, blk = world.getBlockLight(x, lightY, z) / 15f;
                float x0 = x - cam.x + 0.5f - sx, x1 = x - cam.x + 0.5f + sx;
                float z0 = z - cam.z + 0.5f - sz, z1 = z - cam.z + 0.5f + sz;
                float yTop = top - cam.y, yBot = bottom - cam.y;
                float vTop = bottom * 0.25f + scroll, vBot = top * 0.25f + scroll;
                float[] verts = rainBuckets[step];
                int idx = counts[step];
                idx = rainVertex(verts, idx, x0, yTop, z0, 0f, vTop, sky, blk);
                idx = rainVertex(verts, idx, x1, yTop, z1, 1f, vTop, sky, blk);
                idx = rainVertex(verts, idx, x1, yBot, z1, 1f, vBot, sky, blk);
                idx = rainVertex(verts, idx, x0, yTop, z0, 0f, vTop, sky, blk);
                idx = rainVertex(verts, idx, x1, yBot, z1, 1f, vBot, sky, blk);
                idx = rainVertex(verts, idx, x0, yBot, z0, 0f, vBot, sky, blk);
                counts[step] = idx;
            }
        }
        chunkShader.setUniform("uModel", new Matrix4f().translation(cam.x, cam.y, cam.z));
        chunkShader.setUniform("uFogStart", 1_000_000f);
        chunkShader.setUniform("uFogEnd", 2_000_000f);
        rain.bind(0);
        glDisable(GL_CULL_FACE);
        glDepthMask(false);
        for (int step = 0; step < RAIN_ALPHA_STEPS; step++) {
            if (counts[step] == 0) continue;
            chunkShader.setUniform("uAlpha", (step + 0.5f) / RAIN_ALPHA_STEPS);
            rainMesh.upload(rainBuckets[step], counts[step]);
            rainMesh.draw();
        }
        glDepthMask(true);
        atlas.bind(0);
    }

    private int rainVertex(float[] arr, int idx, float x, float y, float z, float u, float v, float sky, float blk) {
        arr[idx++] = x;
        arr[idx++] = y;
        arr[idx++] = z;
        arr[idx++] = u;
        arr[idx++] = v;
        arr[idx++] = sky;
        arr[idx++] = blk;
        arr[idx++] = 1f; // tint r
        arr[idx++] = 1f; // tint g
        arr[idx++] = 1f; // tint b
        arr[idx++] = 0f; // wind weight - rain never sways
        return idx;
    }

    /**
     * Vanilla doesn't actually remove the sun/moon sprite during rain - the whole atmosphere
     * (sky color, fog, ambient light) darkens together so it reads as washed out behind storm
     * clouds rather than deleted. Previously this engine darkened the sky and terrain lighting
     * for a storm (see renderWorld) but drew the sun/moon at a hardcoded full alpha regardless -
     * a bright, fully-lit disc sitting on top of a dark stormy sky, which is the actual bug: not
     * that the sun was "visible" (it should be), but that it wasn't dimmed with everything else.
     *
     * Uses the eased stormIntensity (not the instant isRaining/isThundering flags) for the same
     * reason the surface fog blend does - so this fades in/out over several seconds along with
     * everything else instead of snapping the instant a storm starts or ends.
     */
    private static final float CELESTIAL_ALPHA_RAIN_TARGET = 0.35f;
    private static final float CELESTIAL_ALPHA_THUNDER_TARGET = 0.12f;

    private void renderSunAndMoon(Vector3f eye, Vector3f sunDir, float stormIntensity, boolean thundering) {
        Vector3f right = new Vector3f(-sunDir.z, 0, sunDir.x);
        if (right.lengthSquared() < 1e-6f) right.set(1, 0, 0);
        right.normalize();
        Vector3f up = new Vector3f(right).cross(sunDir).normalize();

        float celestialAlpha = celestialAlpha(stormIntensity, thundering);

        Matrix4f identity = new Matrix4f();
        chunkShader.setUniform("uModel", identity);
        // Celestial bodies aren't part of the terrain - keep them unfogged (their dimming for
        // weather is handled by uAlpha below instead, so they still blend into the storm sky
        // color rather than the distance-fog color).
        chunkShader.setUniform("uFogStart", 1_000_000f);
        chunkShader.setUniform("uFogEnd", 2_000_000f);
        chunkShader.setUniform("uAlpha", celestialAlpha);

        Vector3f sunCenter = new Vector3f(eye).add(new Vector3f(sunDir).mul(CELESTIAL_DISTANCE));
        renderCelestialBody("environment.sun", "textures/environment/celestial/sun.png", sunCenter, right, up, SUN_HALF_SIZE, false);

        Vector3f moonDir = new Vector3f(sunDir).negate();
        Vector3f moonCenter = new Vector3f(eye).add(new Vector3f(moonDir).mul(CELESTIAL_DISTANCE));
        String phase = MOON_PHASE_TEXTURES[moonPhase(dayCount, dayTime)];
        renderCelestialBody("environment.moon." + phase, "textures/environment/celestial/moon/" + phase + ".png", moonCenter, right, up,
                MOON_HALF_SIZE, true);
    }

    /**
     * Pure formula, exercised directly by tests in this package. stormIntensity is 0..1
     * (Weather.getStormIntensity()); the target alpha at full intensity is the thunder or rain
     * endpoint depending on which is currently happening, eased toward linearly by intensity
     * rather than snapped - at intensity 0 this is always 1 (fully visible) regardless of which
     * state is "current," since there's nothing to fade toward yet.
     */
    static float celestialAlpha(float stormIntensity, boolean thundering) {
        float target = thundering ? CELESTIAL_ALPHA_THUNDER_TARGET : CELESTIAL_ALPHA_RAIN_TARGET;
        float clamped = Math.max(0f, Math.min(1f, stormIntensity));
        return 1f + (target - 1f) * clamped;
    }

    /**
     * The moon phase 0..7 (0 = full moon). Vanilla's phase is the number of the current day, which changes at sunrise
     * (day time 0 is sunrise there), so one night keeps one phase; here the day counter turns over at midnight, so the
     * hours before sunrise still belong to the previous day.
     */
    static int moonPhase(long dayCount, float dayTime) {
        return (int) Math.floorMod(dayCount - (dayTime < 0.25f ? 1L : 0L), 8L);
    }

    private void renderCelestialBody(String key, String path, Vector3f center, Vector3f right, Vector3f up, float half, boolean flipped) {
        com.example.engine.EntityTexture tex = realTexture(key, path, false);
        if (tex == null) return;
        // 26.x draws the moon quad with both texture axes reversed relative to the sun's.
        float u0 = flipped ? 1f : 0f, v0 = flipped ? 1f : 0f, u1 = flipped ? 0f : 1f, v1 = flipped ? 0f : 1f;

        Vector3f rHalf = new Vector3f(right).mul(half);
        Vector3f uHalf = new Vector3f(up).mul(half);
        Vector3f p0 = new Vector3f(center).sub(rHalf).sub(uHalf);
        Vector3f p1 = new Vector3f(center).add(rHalf).sub(uHalf);
        Vector3f p2 = new Vector3f(center).add(rHalf).add(uHalf);
        Vector3f p3 = new Vector3f(center).sub(rHalf).add(uHalf);

        float sky = 1f, block = 1f, t = 1f, wind = 0f; // celestial bodies never sway
        float[] verts = {
                p0.x, p0.y, p0.z, u0, v1, sky, block, t, t, t, wind,
                p1.x, p1.y, p1.z, u1, v1, sky, block, t, t, t, wind,
                p2.x, p2.y, p2.z, u1, v0, sky, block, t, t, t, wind,
                p0.x, p0.y, p0.z, u0, v1, sky, block, t, t, t, wind,
                p2.x, p2.y, p2.z, u1, v0, sky, block, t, t, t, wind,
                p3.x, p3.y, p3.z, u0, v0, sky, block, t, t, t, wind,
        };
        sunMoonMesh.upload(verts);
        tex.bind(0);
        // Vanilla draws the sun and moon additively, so the textures' black surround vanishes into the sky.
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        sunMoonMesh.draw();
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        atlas.bind(0);
    }

    // Package-private (not private): pure functions of primitives/Vector3f with no GL/GLFW
    // dependency, exercised directly by tests in this package (see com.example.game.unit).
    static Vector3f sunDirection(float dayTime) {
        float angle = dayTime * (float) (2 * Math.PI) - (float) (Math.PI / 2);
        return new Vector3f((float) Math.cos(angle), (float) Math.sin(angle), 0f).normalize();
    }

    /**
     * Root cause of "world goes completely black at night": this used to clamp to a hard 0.0
     * floor, and since the fragment shader computes light = max(skyShade * daylightFactor,
     * blockLight), any block with no nearby light source got light = 0 (rescued only by the
     * shader's own tiny 0.04 floor - ~4% brightness, indistinguishable from black on screen).
     * Real moonlight is dim but genuinely visible skylight, not an absence of light, so this
     * now remaps into [MIN_NIGHT_DAYLIGHT, 1.0] instead of [0.0, 1.0] - full moonlit ambient
     * at night, ramping smoothly (same continuous linear sunDir.y curve as before) up to full
     * daylight at noon.
     */
    static final float MIN_NIGHT_DAYLIGHT = 0.2f;

    static float computeDaylightFactor(Vector3f sunDir) {
        float raw = Math.max(0f, Math.min(1f, sunDir.y * 2.5f + 0.35f));
        return MIN_NIGHT_DAYLIGHT + raw * (1f - MIN_NIGHT_DAYLIGHT);
    }

    static Vector3f computeSkyColor(float dayTime) {
        Vector3f night = new Vector3f(0.02f, 0.02f, 0.10f);
        Vector3f dawn = new Vector3f(0.90f, 0.55f, 0.40f);
        Vector3f day = new Vector3f(SKY_R, SKY_G, SKY_B);
        Vector3f dusk = new Vector3f(0.85f, 0.45f, 0.40f);
        if (dayTime < 0.22f) return new Vector3f(night).lerp(dawn, dayTime / 0.22f);
        if (dayTime < 0.30f) return new Vector3f(dawn).lerp(day, (dayTime - 0.22f) / 0.08f);
        if (dayTime < 0.68f) return day;
        if (dayTime < 0.78f) return new Vector3f(day).lerp(dusk, (dayTime - 0.68f) / 0.10f);
        if (dayTime < 0.85f) return new Vector3f(dusk).lerp(night, (dayTime - 0.78f) / 0.07f);
        return night;
    }

    private void renderHud() {
        if (hideHud) return;
        float cx = window.getWidth() / 2f;
        float cy = window.getHeight() / 2f;
        com.example.engine.HudTextures.Sprite crosshair = guiSprite("textures/gui/sprites/hud/crosshair.png");
        if (crosshair != null) {
            // the real 15x15 crosshair, drawn with vanilla's inverting blend so it reads on any background
            float cs = com.example.ui.GuiScale.compute(window.getWidth(), window.getHeight(), options.guiScale);
            glBlendFuncSeparate(GL_ONE_MINUS_DST_COLOR, GL_ONE_MINUS_SRC_COLOR, GL_ONE, GL_ZERO);
            com.example.engine.HudTextures.draw(uiRenderer, crosshair, (float) Math.floor(cx - 7.5f * cs), (float) Math.floor(cy - 7.5f * cs), cs);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        } else {
            uiRenderer.drawColorQuad(cx - 5, cy - 1, 10, 2, 1, 1, 1, 0.9f);
            uiRenderer.drawColorQuad(cx - 1, cy - 5, 2, 10, 1, 1, 1, 0.9f);
        }

        if (hudTextures != null && inventory.getHotbarSize() == 9) {
            renderRealHud();
            float flashReal = weather.getLightningFlash();
            if (flashReal > 0.01f) {
                uiRenderer.drawColorQuad(0, 0, window.getWidth(), window.getHeight(), 1f, 1f, 1f, flashReal * 0.8f);
            }
            renderDebugOverlay();
            return;
        }

        int visibleSlots = inventory.getHotbarSize();
        float slotSize = 44f;
        float margin = 4f;
        float totalWidth = visibleSlots * (slotSize + margin) - margin;
        float startX = (window.getWidth() - totalWidth) / 2f;
        float y = window.getHeight() - slotSize - 12f;

        drawSurvivalHud(startX, startX + totalWidth, y);

        float selectedSlotX = startX;
        for (int i = 0; i < visibleSlots; i++) {
            float x = startX + i * (slotSize + margin);
            boolean selected = i == inventory.getSelectedSlot();
            if (selected) {
                uiRenderer.drawColorQuad(x - 3, y - 3, slotSize + 6, slotSize + 6, 1f, 1f, 0.4f, 0.95f);
                selectedSlotX = x;
            }
            uiRenderer.drawColorQuad(x, y, slotSize, slotSize, 0.1f, 0.1f, 0.1f, 0.55f);

            ItemStack stack = inventory.get(i);
            if (!stack.isEmpty()) {
                drawBlockIcon(stack.type(), x, y, slotSize);
                if (stack.count() > 1) drawStackCount(stack.count(), x, y, slotSize);
            }
        }
        drawHotbarLabel(selectedSlotX, y, slotSize);

        float flash = weather.getLightningFlash();
        if (flash > 0.01f) {
            uiRenderer.drawColorQuad(0, 0, window.getWidth(), window.getHeight(), 1f, 1f, 1f, flash * 0.8f);
        }

        renderDebugOverlay();
    }

    /**
     * Health hearts (left, above the hotbar's left edge) and hunger icons (right, mirrored) -
     * 10 icons each, 2 points per icon, a proportional partial fill for an odd (half-heart-style)
     * value. No heart/drumstick texture exists in this project (only block textures were ever
     * copied in - see TextureAtlas's own docs), so these are plain colored squares over a dark
     * backing, the same "colored quad primitive" convention every other HUD element here
     * already uses (the hotbar slots/selection highlight/crosshair) - not a redesign of the HUD,
     * the smallest layer that actually shows the two new survival stats.
     */
    /** The HUD drawn from the real Minecraft sprites at the menu's GUI scale: 182x22 hotbar with its
     *  selection frame, hearts (8px pitch) left of the bar and hunger icons mirrored on the right,
     *  10 icons each, container + full/half overlay exactly like vanilla. */
    private void renderRealHud() {
        int w = window.getWidth(), h = window.getHeight();
        float s = com.example.ui.GuiScale.compute(w, h, options.guiScale);
        float hx = (float) Math.floor((w - 182 * s) / 2f), hy = h - 22 * s;
        HudTextures t = hudTextures;
        HudTextures.draw(uiRenderer, t.hotbar, hx, hy, s);

        float heartsY = hy - 17 * s;
        drawHealthAndFood(t, hx, heartsY, s);

        int defense = survival.getArmor().totalDefense();
        if (defense > 0 && t.armorEmpty != null) {
            for (int i = 0; i < 10; i++) {
                float x = hx + i * 8 * s, ay = heartsY - 10 * s;
                HudTextures.draw(uiRenderer, t.armorEmpty, x, ay, s);
                int v = defense - i * 2;
                if (v >= 2) HudTextures.draw(uiRenderer, t.armorFull, x, ay, s);
                else if (v == 1) HudTextures.draw(uiRenderer, t.armorHalf, x, ay, s);
            }
        }

        drawAirBubbles(t, hx, heartsY - 10 * s, s);
        drawExperienceBar(t, hx, h, s);
        drawAttackIndicator(t, w, h, hx, hy, s);

        int sel = inventory.getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.get(i);
            if (stack.isEmpty()) continue;
            float ix = hx + (3 + 20 * i) * s, iy = hy + 3 * s;
            drawBlockIcon(stack.type(), ix, iy, 16 * s);
            if (stack.count() > 1) drawStackCount(stack.count(), ix, iy, 16 * s);
            drawDurabilityBar(stack, ix, iy, 16 * s);
        }
        ItemStack off = inventory.getOffhand();
        if (!off.isEmpty()) { // vanilla draws the offhand slot left of the hotbar only while something is in it
            HudTextures.Sprite offSlot = guiSprite("textures/gui/sprites/hud/hotbar_offhand_left.png");
            if (offSlot != null) HudTextures.draw(uiRenderer, offSlot, hx - 29 * s, hy - s, s);
            float ox = hx - 26 * s, oy = hy + 3 * s;
            drawBlockIcon(off.type(), ox, oy, 16 * s);
            if (off.count() > 1) drawStackCount(off.count(), ox, oy, 16 * s);
            drawDurabilityBar(off, ox, oy, 16 * s);
        }
        HudTextures.draw(uiRenderer, t.hotbarSelection, hx - s + 20 * sel * s, hy - s, s);
        drawHotbarLabel(hx + (3 + 20 * sel) * s, heartsY - 4 * s, 16 * s);
    }

    private long hudHealthBlinkEndTick;
    private float hudLastHealth = -1f, hudBlinkFromHealth;

    /**
     * The heart and hunger rows with vanilla's animations (docs/vanilla-rules.md 5.2, rules in
     * {@link com.example.ui.HudAnimation}): health is shown rounded up so any fraction still fills a half heart; after
     * damage or healing the containers flash, with the hearts that were just lost shown in the flash colour; at 4 HP or
     * less each heart jitters 0-1 px downwards; hunger icons jitter by -1..+1 px while saturation is 0. The jitter is
     * seeded per game tick, so it is steady within a tick like vanilla's.
     */
    private void drawHealthAndFood(HudTextures t, float hx, float rowY, float s) {
        long tick = System.nanoTime() / 50_000_000L;
        float healthNow = survival.getHealth().getCurrentHealth();
        if (hudLastHealth < 0f) hudLastHealth = healthNow;
        if (healthNow < hudLastHealth) {
            hudHealthBlinkEndTick = tick + com.example.ui.HudAnimation.DAMAGE_FLASH_TICKS;
            hudBlinkFromHealth = hudLastHealth;
        } else if (healthNow > hudLastHealth) {
            hudHealthBlinkEndTick = tick + com.example.ui.HudAnimation.HEAL_FLASH_TICKS;
            hudBlinkFromHealth = healthNow;
        }
        hudLastHealth = healthNow;
        boolean blink = com.example.ui.HudAnimation.flashOn(hudHealthBlinkEndTick, tick);

        int hp = (int) Math.ceil(healthNow);
        int before = blink ? (int) Math.ceil(hudBlinkFromHealth) : 0;
        int food = (int) Math.ceil(survival.getHunger().getFoodLevel());
        boolean hungerShake = com.example.ui.HudAnimation.hungerShake(tick, food, survival.getHunger().getSaturation());
        java.util.Random jitter = new java.util.Random(tick * 312871L);

        for (int i = 0; i < 10; i++) {
            float x = hx + i * 8 * s;
            float y = com.example.ui.HudAnimation.lowHealthShake(hp) ? rowY + jitter.nextInt(2) * s : rowY;
            HudTextures.Sprite container = blink && t.heartContainerBlink != null ? t.heartContainerBlink : t.heartContainer;
            HudTextures.draw(uiRenderer, container, x, y, s);
            if (blink && t.heartFullBlink != null && t.heartHalfBlink != null) {
                int lost = before - i * 2;
                if (lost >= 2) HudTextures.draw(uiRenderer, t.heartFullBlink, x, y, s);
                else if (lost == 1) HudTextures.draw(uiRenderer, t.heartHalfBlink, x, y, s);
            }
            int v = hp - i * 2;
            if (v >= 2) HudTextures.draw(uiRenderer, t.heartFull, x, y, s);
            else if (v == 1) HudTextures.draw(uiRenderer, t.heartHalf, x, y, s);
        }
        for (int i = 0; i < 10; i++) {
            float fx = hx + (182 - 9) * s - i * 8 * s;
            float fy = hungerShake ? rowY + (jitter.nextInt(3) - 1) * s : rowY;
            HudTextures.draw(uiRenderer, t.foodEmpty, fx, fy, s);
            int v = food - i * 2;
            if (v >= 2) HudTextures.draw(uiRenderer, t.foodFull, fx, fy, s);
            else if (v == 1) HudTextures.draw(uiRenderer, t.foodHalf, fx, fy, s);
        }
    }

    private void drawSurvivalHud(float hotbarLeftX, float hotbarRightX, float hotbarY) {
        float iconSize = 14f, gap = 2f;
        float rowY = hotbarY - iconSize - 6f;

        float health = survival.getHealth().getCurrentHealth();
        for (int i = 0; i < 10; i++) {
            float x = hotbarLeftX + i * (iconSize + gap);
            float value = Math.max(0f, Math.min(2f, health - i * 2f));
            drawSurvivalIcon(x, rowY, iconSize, value, 0.82f, 0.14f, 0.14f, 0.28f, 0.06f, 0.06f);
        }
        float food = survival.getHunger().getFoodLevel();
        for (int i = 0; i < 10; i++) {
            float x = hotbarRightX - iconSize - i * (iconSize + gap);
            float value = Math.max(0f, Math.min(2f, food - i * 2f));
            drawSurvivalIcon(x, rowY, iconSize, value, 0.62f, 0.42f, 0.20f, 0.22f, 0.16f, 0.09f);
        }
    }

    /** One heart/hunger icon: a dark empty backing, then a fill whose width is proportional to
     *  {@code value} (0..2 - 0 empty, 1 half, 2 full), so a half point of health/food reads as a
     *  half-filled icon rather than rounding away. */
    private void drawSurvivalIcon(float x, float y, float size, float value, float fr, float fg, float fb,
                                  float er, float eg, float eb) {
        uiRenderer.drawColorQuad(x, y, size, size, er, eg, eb, 0.92f);
        if (value <= 0f) return;
        float fillWidth = size * (value / 2f);
        uiRenderer.drawColorQuad(x, y, fillWidth, size, fr, fg, fb, 1f);
    }

    /** Vanilla durability bar: 13x2 of a 16-unit icon, green to red, only once the item has been used. */
    private void drawDurabilityBar(ItemStack stack, float slotX, float slotY, float slotSize) {
        if (stack.isEmpty() || stack.damage() <= 0) return;
        int max = com.example.combat.Durability.max(stack.type());
        if (max <= 0) return;
        float ratio = Math.max(0f, Math.min(1f, stack.durabilityLeft() / (float) max));
        float u = slotSize / 16f;
        float bx = slotX + 2 * u, by = slotY + 13 * u;
        // Vanilla: a black 13x2 backing and a 13-px fill whose width is rounded and whose hue runs red -> green.
        int rgb = java.awt.Color.HSBtoRGB(ratio / 3f, 1f, 1f);
        uiRenderer.drawColorQuad(bx, by, 13 * u, 2 * u, 0f, 0f, 0f, 1f);
        uiRenderer.drawColorQuad(bx, by, Math.round(13 * ratio) * u, u,
                ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
    }

    /** Quantity in the slot's bottom-right corner, with a dark drop shadow so it reads over any icon. */
    private void drawStackCount(int count, float slotX, float slotY, float slotSize) {
        String text = Integer.toString(count);
        float px = Math.max(1f, (float) Math.floor(slotSize / 16f)); // one font pixel per slot "icon pixel"
        float x = slotX + slotSize - font.width(text, px) - px * 0.5f;
        float y = slotY + slotSize - 9 * px;
        font.draw(uiRenderer, text, x, y, px, 0xFFFFFF, true);
    }

    /** The selected block's display name, floating just above its hotbar slot for
     *  HOTBAR_LABEL_DURATION seconds after selection actually changes (see
     *  updateHotbarSelection), fading out over the last HOTBAR_LABEL_FADE_SECONDS rather than
     *  disappearing abruptly. */
    private void drawHotbarLabel(float slotX, float slotY, float slotSize) {
        if (hotbarLabelTimer <= 0f) return;
        float alpha = Math.min(1f, hotbarLabelTimer / HOTBAR_LABEL_FADE_SECONDS);
        ItemStack shown = inventory.selected();
        if (shown.isEmpty()) return;
        String name = shown.type().displayName;
        float scale = 1.1f;
        float textWidth = font.measureText(name, scale);
        float x = slotX + slotSize / 2f - textWidth / 2f;
        float y = slotY - font.lineHeight(scale) - 10f;
        uiRenderer.drawColorQuad(x - 6f, y - 3f, textWidth + 12f, font.lineHeight(scale) + 6f, 0f, 0f, 0f, 0.55f * alpha);
        font.drawText(uiRenderer, name, x, y, scale, 1f, 1f, 1f, alpha);
    }

    /**
     * F3-style overlay: the player's current column's raw climate values, every biome's
     * blend weight there, and which one is dominant - lets you watch these change
     * continuously as you cross a biome border instead of jumping, which is the actual
     * thing "smooth transitions" needs to demonstrate.
     */
    private void renderDebugOverlay() {
        if (!debugOverlay) return;

        int wx = (int) Math.floor(player.position.x);
        int wz = (int) Math.floor(player.position.z);
        ClimateSample sample = world.climateAt(wx, wz);

        float scale = 1.0f;
        float lineH = font.lineHeight(scale) + 3f;
        float x = 10f, y = 10f;
        int lineCount = sample == null ? 3 : (12 + Biome.values().length);
        uiRenderer.drawColorQuad(4, 4, 430, lineCount * lineH + 10, 0f, 0f, 0f, 0.6f);

        font.drawText(uiRenderer, "[F3] Climate / Biome Debug", x, y, scale, 1f, 1f, 0.4f, 1f);
        y += lineH * 1.4f;
        font.drawText(uiRenderer, String.format("column: %d, %d", wx, wz), x, y, scale, 0.8f, 0.8f, 0.8f, 1f);
        y += lineH;

        if (sample == null) {
            font.drawText(uiRenderer, "no climate model (flat world)", x, y, scale, 0.8f, 0.8f, 0.8f, 1f);
            return;
        }

        // Local coords make crossing a chunk boundary visible in the overlay itself: the same
        // world column must show identical climate/weights/colors regardless of which chunk it
        // was generated by - watching (chunk, local) roll over while every other line stays
        // continuous is the actual proof of "no seam at chunk borders."
        int cx = World.worldToChunk(wx), cz = World.worldToChunk(wz);
        int lx = World.worldToLocal(wx), lz = World.worldToLocal(wz);
        font.drawText(uiRenderer, String.format("chunk: %d, %d   local: %d, %d", cx, cz, lx, lz), x, y, scale, 0.8f, 0.8f, 0.8f, 1f);
        y += lineH * 1.4f;

        font.drawText(uiRenderer, String.format("temperature:      % .3f", sample.temperature()), x, y, scale, 0.9f, 0.9f, 0.9f, 1f);
        y += lineH;
        font.drawText(uiRenderer, String.format("humidity:         % .3f", sample.humidity()), x, y, scale, 0.9f, 0.9f, 0.9f, 1f);
        y += lineH;
        font.drawText(uiRenderer, String.format("continentalness:  % .3f", sample.continentalness()), x, y, scale, 0.9f, 0.9f, 0.9f, 1f);
        y += lineH;
        font.drawText(uiRenderer, String.format("erosion:          % .3f", sample.erosion()), x, y, scale, 0.9f, 0.9f, 0.9f, 1f);
        y += lineH;
        font.drawText(uiRenderer, String.format("weirdness:        % .3f", sample.weirdness()), x, y, scale, 0.9f, 0.9f, 0.9f, 1f);
        y += lineH;
        // No fabricated value: vanilla's depth axis comes from a 3D density function this
        // heightmap-based engine doesn't have, so it's labeled absent rather than invented.
        font.drawText(uiRenderer, "depth:            n/a (heightmap terrain)", x, y, scale, 0.55f, 0.55f, 0.55f, 1f);
        y += lineH * 1.4f;

        font.drawText(uiRenderer, "dominant: " + sample.dominant().name(), x, y, scale, 0.6f, 1f, 0.6f, 1f);
        y += lineH * 1.4f;

        drawColorSwatchLine(x, y, lineH, "grass color:", sample.grassColor());
        y += lineH;
        drawColorSwatchLine(x, y, lineH, "foliage color:", sample.foliageColor());
        y += lineH * 1.4f;

        font.drawText(uiRenderer, "biome weights (influence on blended height/trees/color):", x, y, scale, 1f, 1f, 0.4f, 1f);
        y += lineH;

        Biome[] all = Biome.values();
        float[] weights = sample.weights();
        Integer[] order = new Integer[all.length];
        for (int i = 0; i < all.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Float.compare(weights[b], weights[a]));
        for (int i : order) {
            boolean isDominant = all[i] == sample.dominant();
            float r = isDominant ? 0.6f : 0.85f, g = isDominant ? 1f : 0.85f, b = isDominant ? 0.6f : 0.85f;
            font.drawText(uiRenderer, String.format("  %-14s %.3f", all[i].name(), weights[i]), x, y, scale, r, g, b, 1f);
            y += lineH;
        }
    }

    /** A small filled swatch plus its hex code, for the F3 overlay's blended grass/foliage rows. */
    private void drawColorSwatchLine(float x, float y, float lineH, String label, float[] rgb) {
        float scale = 1.0f;
        font.drawText(uiRenderer, label, x, y, scale, 0.9f, 0.9f, 0.9f, 1f);
        float swatchX = x + 150f;
        float swatchSize = lineH - 4f;
        uiRenderer.drawColorQuad(swatchX, y, swatchSize, swatchSize, rgb[0], rgb[1], rgb[2], 1f);
        int r = Math.round(rgb[0] * 255f), g = Math.round(rgb[1] * 255f), b = Math.round(rgb[2] * 255f);
        font.drawText(uiRenderer, String.format("#%02X%02X%02X", r, g, b), swatchX + swatchSize + 6f, y, scale, 0.9f, 0.9f, 0.9f, 1f);
    }

    private static final float ISO_COS30 = 0.8660254f;
    private static final float[] NEUTRAL_ICON_TINT = {1f, 1f, 1f};

    /**
     * Draws a block as a proper isometric 3D cube icon (top + 2 side faces), matching how
     * Minecraft actually renders inventory/hotbar items, instead of a single flat top texture.
     * Grass-top and every leaf species were rendering with no color at all before this - their
     * real textures (like the world-rendering ones) are plain colormap-sampled greyish-green
     * base images meant to be tinted at render time, and the icon path never tinted anything.
     * Reuses ChunkMeshBuilder's own tint logic/constants (not a second, possibly-drifting copy)
     * - a representative default tint here, since an icon has no world column to blend a real
     * per-biome one from.
     */
    private void drawBlockIcon(BlockType block, float slotX, float slotY, float slotSize) {
        if (ChunkMeshBuilder.isFlatIconItem(block)) {
            // Saplings/tools render as a single flat icon, never the isometric cube every other
            // block gets - saplings because their real in-world model is a thin flat cross (see
            // ChunkMeshBuilder.emitCrossPlant), tools because they're never a world block at all
            // and a 2D flat icon matches real vanilla's own item-icon convention far better than
            // an isometric cube would.
            drawFlatPlantIcon(block, slotX, slotY, slotSize);
            return;
        }
        BlockTextureMap.Faces faces = textureMap.facesFor(block);
        float[] topUv = atlas.getUv(faces.top());
        float[] sideUv = atlas.getUv(faces.side());

        float[] leafTint = ChunkMeshBuilder.leafIconTint(block);
        // Grass's side/bottom textures are real, already-colored images (dirt with a green
        // overlay strip) - never tinted, same as in real chunk rendering (see ChunkMeshBuilder).
        float[] topTint = leafTint != null ? leafTint : block == BlockType.GRASS_BLOCK ? ChunkMeshBuilder.grassIconTint() : NEUTRAL_ICON_TINT;
        float[] sideTint = leafTint != null ? leafTint : NEUTRAL_ICON_TINT;

        float pad = 3f;
        float scale = Math.min((slotSize - pad * 2) / 1.7320508f, (slotSize - pad * 2) / 2f);
        float cx = slotX + slotSize / 2f;
        float cy = slotY + slotSize / 2f + scale * 0.15f;

        // Unit-cube corners projected with a classic 2:1 isometric transform: sx=(x-z)*cos30, sy=(x+z)*0.5-y.
        int[][] corners = {{0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {1, 1, 0}, {1, 0, 1}, {0, 1, 1}, {1, 1, 1}};
        float[][] pts = new float[8][2];
        for (int i = 0; i < 8; i++) {
            int px = corners[i][0], py = corners[i][1], pz = corners[i][2];
            float sx = (px - pz) * ISO_COS30;
            float sy = (px + pz) * 0.5f - py;
            pts[i][0] = cx + sx * scale;
            pts[i][1] = cy + sy * scale;
        }
        // Indices: 0=000 1=100 2=010 3=001 4=110 5=101 6=011 7=111
        // drawIconFace expects corners in p0=texture-top-left, p1=top-right, p2=bottom-right,
        // p3=bottom-left order (see drawTexturedQuadPoints) - i.e. p0/p1 must be the pair of
        // corners with the HIGHER cube-Y (matching v0, the texture's top row) and p2/p3 the
        // pair with the lower cube-Y (v1). The previous east/south orderings paired a top corner
        // with a bottom corner into p0/p1 instead, which rotated or vertically flipped any
        // vertically-asymmetric side texture (grass_block_side's grass/dirt split, tnt_side's
        // label) - unnoticeable on vertically-symmetric textures like stone, hence easy to miss.
        drawIconFace(pts[2], pts[4], pts[7], pts[6], topUv, 1.0f, topTint);  // top face (y=1)
        drawIconFace(pts[4], pts[7], pts[5], pts[1], sideUv, 0.75f, sideTint); // east face (x=1)
        drawIconFace(pts[6], pts[7], pts[5], pts[3], sideUv, 0.55f, sideTint); // south face (z=1)
        if (faces.sideOverlay() != null) { // grass: the biome-tinted fringe on top of the dirt-only side
            float[] overlayUv = atlas.getUv(faces.sideOverlay());
            drawIconFace(pts[4], pts[7], pts[5], pts[1], overlayUv, 0.75f, topTint);
            drawIconFace(pts[6], pts[7], pts[5], pts[3], overlayUv, 0.55f, topTint);
        }
    }

    /** A single flat front-facing textured quad, centered in the slot - the correct icon shape
     *  for a cross/plant block (see drawBlockIcon), instead of the isometric cube every other
     *  block gets. */
    private void drawFlatPlantIcon(BlockType block, float slotX, float slotY, float slotSize) {
        float[] uv = atlas.getUv(textureMap.facesFor(block).side());
        float pad = 4f;
        float half = (slotSize - pad * 2) / 2f;
        float cx = slotX + slotSize / 2f;
        float cy = slotY + slotSize / 2f;
        drawIconFace(new float[]{cx - half, cy - half}, new float[]{cx + half, cy - half},
                new float[]{cx + half, cy + half}, new float[]{cx - half, cy + half},
                uv, 1.0f, NEUTRAL_ICON_TINT);
    }

    private void drawIconFace(float[] p0, float[] p1, float[] p2, float[] p3, float[] uv, float shade, float[] tint) {
        uiRenderer.drawTexturedQuadPoints(p0[0], p0[1], p1[0], p1[1], p2[0], p2[1], p3[0], p3[1],
                uv, atlas.getTextureId(), shade * tint[0], shade * tint[1], shade * tint[2], 1f);
    }

    // ---- com.example.testctl.GameAccess - the live-controller's only way to touch this class ----

    @Override
    public void teleport(float x, float y, float z, Float yawDegrees, Float pitchDegrees) {
        if (player == null) throw new IllegalStateException("no active world");
        player.teleport(x, y, z);
        if (yawDegrees != null) player.setYaw((float) Math.toRadians(yawDegrees));
        if (pitchDegrees != null) player.setPitch((float) Math.toRadians(pitchDegrees));
    }

    @Override
    public void setDay(long day) {
        dayCount = Math.max(0L, day);
    }

    @Override
    public void setTime(float dayTimeFraction) {
        dayTime = dayTimeFraction;
    }

    @Override
    public void setWeather(String stateName) {
        if (weather == null) throw new IllegalStateException("no active world");
        weather.forceState(Weather.State.valueOf(stateName));
    }

    @Override
    public void newTestWorld(long seed) {
        startNewWorld(new WorldSettings("Test World", WorldSettings.WorldType.SURVIVAL), seed);
    }

    @Override
    public void newFlatTestWorld() {
        startNewWorld(new WorldSettings("Flat Test World", WorldSettings.WorldType.FLAT), 0L);
    }

    @Override
    public void loadTestWorld(String folderName) {
        File dir = WorldIO.worldDir(folderName);
        if (!dir.isDirectory()) throw new IllegalStateException("no saved world folder named " + folderName);
        // loadWorld only ever reads saved.folderName() - the rest of SavedWorld exists for the
        // world-select screen's own display, which this controller path never renders.
        loadWorld(new WorldIO.SavedWorld(folderName, folderName, WorldSettings.WorldType.SURVIVAL, 0));
    }

    @Override
    public void spawnTree(String speciesName, int x, int y, int z, long seed) {
        if (world == null) throw new IllegalStateException("no active world");
        TreeSpecies species = TreeSpecies.valueOf(speciesName.toUpperCase(java.util.Locale.ROOT));
        int cx = World.worldToChunk(x), cz = World.worldToChunk(z);
        Chunk chunk = world.getChunk(cx, cz);
        if (chunk == null || !chunk.isGenerated()) throw new IllegalStateException("chunk not loaded yet");
        species.place(chunk, World.worldToLocal(x), y, World.worldToLocal(z), new Random(seed));
        chunk.markMeshDirty();
        world.markNeighborChunksDirty(cx, cz);
    }

    @Override
    public void setBlock(int x, int y, int z, String blockTypeName) {
        if (world == null) throw new IllegalStateException("no active world");
        BlockType type = BlockType.valueOf(blockTypeName.toUpperCase(java.util.Locale.ROOT));
        world.setBlock(x, y, z, type);
        SaplingSpecies species = SaplingSpecies.bySaplingBlock(type);
        if (species != null) {
            saplingGrowth.registerPlanted(x, y, z, species);
        }
    }

    @Override
    public void selectSlot(int index) {
        inventory.selectSlot(index); // throws IllegalArgumentException when outside the hotbar
    }

    @Override
    public void openInventoryScreen(int gridSize) {
        if (gridSize != 2 && gridSize != 3) throw new IllegalArgumentException("gridSize must be 2 or 3");
        openInventory(gridSize);
    }

    @Override
    public void inventoryClick(int slot, boolean right) {
        if (slot < 0) throw new IllegalArgumentException("slot must be >= 0");
        try {
            if (cookingMenu != null) {
                cookingMenu.click(slot, right);
                float xp = cookingMenu.takeEarnedExperience();
                if (xp > 0f) survival.addExperience(xp);
            } else if (menu != null) {
                menu.click(slot, right);
            } else {
                throw new IllegalStateException("inventory is not open");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("no such slot: " + slot);
        }
    }

    @Override
    public void openCookingScreen(int x, int y, int z) {
        if (world == null) throw new IllegalStateException("no active world");
        openCookingScreenAt(x, y, z);
    }

    @Override
    public void closeInventoryScreen() {
        if (cookingMenu != null) closeCookingScreen();
        else closeInventory();
    }

    @Override
    public void openMenu(String name) {
        String page = name.startsWith("options:") ? name.substring("options:".length()) : "";
        String base = name.startsWith("options") ? "options" : name;
        switch (base) {
            case "title" -> {
                titleScreen.invalidate();
                state = GameState.TITLE;
            }
            case "select" -> {
                selectWorldScreen.refresh();
                state = GameState.SELECT_WORLD;
            }
            case "create" -> {
                worldCreateScreen.reset();
                state = GameState.WORLD_CREATE;
            }
            case "options" -> {
                boolean inWorld = world != null && (state == GameState.IN_GAME || state == GameState.PAUSED);
                openOptions(inWorld ? GameState.PAUSED : GameState.TITLE);
                if (!page.isEmpty()) {
                    optionsScreen = OptionsScreens.create(newOptionsContext(inWorld), page);
                }
            }
            case "pause" -> {
                if (world == null) throw new IllegalStateException("no active world");
                pauseGame();
            }
            case "death" -> {
                if (world == null) throw new IllegalStateException("no active world");
                deathScreen.invalidate();
                window.releaseMouse();
                input.resyncMousePosition();
                state = GameState.DEATH;
            }
            default -> throw new IllegalArgumentException("unknown menu: " + name);
        }
    }

    @Override
    public void click(int x, int y) {
        input.simulateClick(x, y);
    }

    @Override
    public void resizeWindow(int width, int height) {
        window.resize(width, height);
    }

    @Override
    public void setGuiScale(int scale) {
        options.guiScale = scale;
        applyOptions();
    }

    @Override
    public void attack() {
        forcedAttackPresses++;
    }

    @Override
    public void spawnZombie(float x, float y, float z) {
        if (world == null) throw new IllegalStateException("no active world");
        if (!mobs.spawn(new Zombie(new Vector3f(x, y, z), 0f, () -> survival.getDifficulty()))) {
            throw new IllegalStateException("mob cap reached");
        }
    }

    @Override
    public void spawnMob(String type, float x, float y, float z) {
        if (world == null) throw new IllegalStateException("no active world");
        Mob m = com.example.entity.mob.MobTypes.create(type, new Vector3f(x, y, z), 0f, () -> survival.getDifficulty());
        if (m == null) throw new IllegalStateException("unknown mob type: " + type);
        if (!mobs.spawn(m)) throw new IllegalStateException("mob cap reached");
    }

    @Override
    public void setMobSpawning(boolean enabled) {
        mobSpawner.setEnabled(enabled);
        passiveSpawner.setEnabled(enabled);
    }

    @Override
    public void damageMobs(float amount) {
        for (Mob m : mobs.mobs()) m.damage(amount, com.example.survival.DamageSource.PLAYER);
    }

    @Override
    public void advanceSimulationTicks(int ticks) {
        if (world == null) throw new IllegalStateException("no active world");
        for (int i = 0; i < ticks; i++) cookingStations.tickAll(world);
    }

    @Override
    public int giveItem(String blockTypeName, int count) {
        if (world == null) throw new IllegalStateException("no active world");
        return inventory.add(BlockType.valueOf(blockTypeName.toUpperCase(Locale.ROOT)), count);
    }

    @Override
    public void holdAttack(boolean held) {
        forcedAttackHeld = held;
    }

    @Override
    public void holdUse(boolean held) {
        forcedUseHeld = held;
    }

    @Override
    public void useItem() {
        forcedUseRequests++;
    }

    @Override
    public void swapOffhand() {
        forcedSwapRequests++;
    }

    @Override
    public void dropItem(boolean wholeStack) {
        forcedDropRequests++;
        forcedDropWholeStack = wholeStack;
    }

    @Override
    public void lookAt(double x, double y, double z) {
        if (player == null) throw new IllegalStateException("no active world");
        Vector3f eye = player.eyePosition();
        double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) throw new IllegalArgumentException("target is at the eye position");
        player.setYaw((float) Math.atan2(dx, -dz));
        player.setPitch((float) Math.asin(dy / len));
    }

    @Override
    public String captureScreenshot(String name) throws IOException {
        return captureScreenshotTo(new File("test-output/screenshots"), name);
    }

    private String captureScreenshotTo(File dir, String name) throws IOException {
        int width = window.getWidth();
        int height = window.getHeight();
        ByteBuffer buffer = org.lwjgl.BufferUtils.createByteBuffer(width * height * 4);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (x + (height - 1 - y) * width) * 4; // glReadPixels rows are bottom-up; BufferedImage is top-down
                int r = buffer.get(i) & 0xFF;
                int g = buffer.get(i + 1) & 0xFF;
                int b = buffer.get(i + 2) & 0xFF;
                image.setRGB(x, y, (0xFF << 24) | (r << 16) | (g << 8) | b);
            }
        }

        dir.mkdirs();
        File out = new File(dir, name + ".png");
        ImageIO.write(image, "png", out);
        return out.getPath();
    }

    @Override
    public void damage(float amount, String source) {
        if (world == null) throw new IllegalStateException("no active world");
        if (amount <= 0f) throw new IllegalArgumentException("damage amount must be positive: " + amount);
        DamageSource parsed;
        try {
            parsed = DamageSource.valueOf(source.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown damage source: " + source);
        }
        survival.damage(amount, parsed);
    }

    @Override
    public void heal(float amount) {
        if (world == null) throw new IllegalStateException("no active world");
        survival.getHealth().heal(amount);
    }

    @Override
    public void respawn() {
        if (world == null) throw new IllegalStateException("no active world");
        survival.getHealth().reset();
        if (state == GameState.DEATH) {
            window.captureMouse();
            input.resyncMousePosition();
            state = GameState.IN_GAME;
        }
    }

    @Override
    public void setDifficulty(String level) {
        if (world == null) throw new IllegalStateException("no active world");
        Difficulty parsed;
        try {
            parsed = Difficulty.valueOf(level.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown difficulty: " + level);
        }
        survival.setDifficulty(parsed);
        currentSettings = new WorldSettings(currentSettings.name(), currentSettings.type(), parsed);
    }

    @Override
    public void setHunger(float foodLevel, float saturation, float exhaustion) {
        if (world == null) throw new IllegalStateException("no active world");
        survival.getHunger().forceState(foodLevel, saturation, exhaustion);
    }

    private static String stackSummary(ItemStack stack) {
        return stack.isEmpty() ? "" : stack.type().name() + "x" + stack.count();
    }

    @Override
    public String stateSnapshot() {
        if (player == null || world == null || weather == null) {
            return "state=no active world\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("player.position=").append(String.format("%.2f,%.2f,%.2f",
                player.position.x, player.position.y, player.position.z)).append('\n');
        sb.append("player.yawDeg=").append(String.format("%.2f", Math.toDegrees(player.getYaw()))).append('\n');
        sb.append("player.pitchDeg=").append(String.format("%.2f", Math.toDegrees(player.getPitch()))).append('\n');
        sb.append("player.swimming=").append(player.isSwimming()).append('\n');
        sb.append("player.headSubmerged=").append(player.isHeadSubmerged()).append('\n');
        sb.append("player.flying=").append(player.isFlying()).append('\n');

        // These are the biome-side tint values from ClimateSample, i.e. before the time-of-day/
        // storm blending renderWorld applies - not the exact final on-screen pixel color.
        ClimateSample climate = world.climateAt((int) Math.floor(player.position.x), (int) Math.floor(player.position.z));
        // A flat world's generator has no climate model at all (see FlatWorldGenerator/the F3
        // overlay's own "no climate model (flat world)" message) - climateAt returns null there,
        // same as every other climate consumer in this class (renderWorld's sky/fog/underwater-
        // fog blends) already has to handle; this just hadn't been made to handle it too.
        if (climate == null) {
            sb.append("biome.dominant=none (flat world)\n");
        } else {
            sb.append("biome.dominant=").append(climate.dominant()).append('\n');
            StringBuilder weights = new StringBuilder();
            Biome[] biomes = Biome.values();
            float[] w = climate.weights();
            for (int i = 0; i < biomes.length; i++) {
                if (w[i] < 0.01f) continue;
                if (weights.length() > 0) weights.append(',');
                weights.append(biomes[i].name()).append(':').append(String.format("%.2f", w[i]));
            }
            sb.append("biome.weights=").append(weights).append('\n');
            sb.append("biome.grassColor=").append(toHex(climate.grassColor())).append('\n');
            sb.append("biome.foliageColor=").append(toHex(climate.foliageColor())).append('\n');
            sb.append("biome.waterColor=").append(toHex(climate.waterColor())).append('\n');
            sb.append("biome.fogColor=").append(toHex(climate.fogColor())).append('\n');
            sb.append("biome.underwaterFogColor=").append(toHex(climate.underwaterFogColor())).append('\n');
            sb.append("biome.skyTint=").append(toHex(climate.skyTint())).append('\n');
        }

        sb.append("weather.state=").append(weather.getState()).append('\n');
        sb.append("weather.stormIntensity=").append(String.format("%.2f", weather.getStormIntensity())).append('\n');
        sb.append("time.dayTime=").append(String.format("%.4f", dayTime)).append('\n');

        sb.append("survival.difficulty=").append(survival.getDifficulty()).append('\n');
        sb.append("survival.health=").append(String.format("%.2f", survival.getHealth().getCurrentHealth()))
                .append('/').append(String.format("%.2f", survival.getHealth().getMaxHealth())).append('\n');
        sb.append("survival.dead=").append(survival.getHealth().isDead()).append('\n');
        sb.append("survival.hurtCooldown=").append(String.format("%.2f", survival.getHealth().hurtCooldownRemaining())).append('\n');
        sb.append("survival.food=").append(String.format("%.2f", survival.getHunger().getFoodLevel())).append('\n');
        sb.append("survival.saturation=").append(String.format("%.2f", survival.getHunger().getSaturation())).append('\n');
        sb.append("survival.exhaustion=").append(String.format("%.2f", survival.getHunger().getExhaustion())).append('\n');
        sb.append("survival.canSprint=").append(survival.getHunger().canSprint()).append('\n');
        sb.append("survival.regenAllowed=").append(survival.getHunger().regenerationAllowed()).append('\n');

        sb.append("world.folder=").append(currentWorldFolder).append('\n');
        long dirtyChunks = world.chunkMap().values().stream().filter(Chunk::isMeshDirty).count();
        StringBuilder slots = new StringBuilder();
        for (int i = 0; i < inventory.slotCount(); i++) {
            ItemStack st = inventory.get(i);
            if (st.isEmpty()) continue;
            if (slots.length() > 0) slots.append(',');
            slots.append(i).append(':').append(st.type().name()).append('x').append(st.count());
        }
        sb.append("inventory.selectedSlot=").append(inventory.getSelectedSlot()).append('\n');
        sb.append("inventory.slots=").append(slots).append('\n');
        ItemStack offhandNow = inventory.getOffhand();
        sb.append("inventory.offhand=").append(offhandNow.isEmpty() ? "-" : offhandNow.type().name() + "x" + offhandNow.count()).append('\n');
        sb.append("inventory.open=").append(menu != null).append('\n');
        if (menu != null) {
            sb.append("inventory.cursor=").append(menu.getCursor().isEmpty() ? "" : menu.getCursor().type().name() + "x" + menu.getCursor().count()).append('\n');
            sb.append("inventory.craftGrid=").append(menu.gridSummary()).append('\n');
            sb.append("inventory.craftResult=").append(menu.getResult().isEmpty() ? "" : menu.getResult().type().name() + "x" + menu.getResult().count()).append('\n');
        }
        sb.append("cooking.open=").append(cookingMenu != null).append('\n');
        if (cookingMenu != null) {
            CookingStationState st = cookingMenu.getStation();
            sb.append("cooking.station=").append(st.getX()).append(',').append(st.getY()).append(',').append(st.getZ())
                    .append(' ').append(st.getType()).append('\n');
            sb.append("cooking.input=").append(stackSummary(st.getInputStack())).append('\n');
            sb.append("cooking.fuel=").append(stackSummary(st.getFuelStack())).append('\n');
            sb.append("cooking.output=").append(stackSummary(st.getOutputStack())).append('\n');
            sb.append("cooking.burning=").append(st.isBurning()).append(" burnFraction=").append(st.burnFraction())
                    .append(" cookFraction=").append(st.cookFraction()).append('\n');
        }
        sb.append("armor.defense=").append(survival.getArmor().totalDefense()).append(" toughness=").append(survival.getArmor().totalToughness());
        for (com.example.combat.EquipmentSlot es : com.example.combat.EquipmentSlot.values()) {
            ItemStack worn = survival.getArmor().get(es);
            sb.append(' ').append(es.name().toLowerCase()).append('=').append(worn.isEmpty() ? "-" : worn.type().name() + "(" + worn.durabilityLeft() + ")");
        }
        sb.append("\n");
        ItemStack selStack = inventory.selected();
        BlockType selType = selStack.isEmpty() ? null : selStack.type();
        sb.append("attack.charge=").append(String.format(java.util.Locale.ROOT, "%.2f",
                com.example.combat.CombatMath.charge(attackTimer * 20f, com.example.combat.WeaponStats.periodTicks(selType))));
        sb.append(" selected.durabilityLeft=").append(selStack.isEmpty() ? 0 : selStack.durabilityLeft()).append("\n");
        sb.append("survival.experience=").append(survival.getExperience()).append('\n');
        sb.append("mobs.count=").append(mobs.size()).append(" spawning=").append(mobSpawner.isEnabled()).append("\n");
        for (Mob m : mobs.mobs()) {
            sb.append("mob=").append(m.getClass().getSimpleName())
                    .append(" hp=").append(m.getHealth().getCurrentHealth()).append('/').append(m.getHealth().getMaxHealth())
                    .append(" alive=").append(m.isAlive())
                    .append(" pos=").append(String.format(Locale.ROOT, "%.2f,%.2f,%.2f", m.position.x, m.position.y, m.position.z))
                    .append(" target=").append(m.hasTarget() ? "player" : "none")
                    .append(" dist=").append(m.hasTarget() ? String.format(Locale.ROOT, "%.2f", m.distanceToTarget()) : "-")
                    .append(" path=").append(m.getNavigator().hasValidPath() ? "VALID" : "NONE").append("\n");
        }
        sb.append("items.dropped=").append(itemEntities.size()).append('\n');
        StringBuilder itemList = new StringBuilder();
        java.util.List<ItemEntity> dropped = itemEntities.entities();
        for (int i = 0; i < Math.min(8, dropped.size()); i++) {
            ItemEntity e = dropped.get(i);
            if (itemList.length() > 0) itemList.append(';');
            itemList.append(e.getType().name()).append('x').append(e.getCount())
                    .append(String.format("@%.2f,%.2f,%.2f", e.position.x, e.position.y, e.position.z))
                    .append(e.isOnGround() ? ":ground" : ":air");
        }
        sb.append("items.list=").append(itemList).append('\n');
        sb.append("particles.active=").append(particles.count()).append('\n');
        sb.append("particles.crit=").append(critParticles.count()).append(' ').append("damageIndicators=").append(damageParticles.count()).append('\n');
        sb.append("mining.crackStage=").append(mining.crackStage()).append('\n');
        sb.append("chunks.loaded=").append(world.chunkMap().size()).append('\n');
        sb.append("chunks.meshDirty=").append(dirtyChunks).append('\n');
        return sb.toString();
    }

    private static String toHex(float[] rgb) {
        int r = Math.round(Math.max(0f, Math.min(1f, rgb[0])) * 255f);
        int g = Math.round(Math.max(0f, Math.min(1f, rgb[1])) * 255f);
        int b = Math.round(Math.max(0f, Math.min(1f, rgb[2])) * 255f);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    @Override
    public void shutdown() {
        window.close();
    }

    private void cleanup() {
        if (testController != null) testController.stop();
        saveWorld(); // safety net for any exit path that isn't "Save & Quit to Title" (e.g. the OS close button)
        for (ChunkRenderData rd : renderData.values()) rd.destroy();
        uiRenderer.destroy();
        font.destroy();
        if (hudTextures != null) hudTextures.destroy();
        sunMoonMesh.destroy();
        rainMesh.destroy();
        heldItemMesh.destroy();
        itemMesh.destroy();
        mobMesh.destroy();
        for (com.example.engine.EntityTexture t : mobSkins.values()) t.destroy();
        for (com.example.engine.EntityTexture t : realTextures.values()) t.destroy();
        particleMesh.destroy();
        crackOverlay.destroy();
        chunkShader.destroy();
        gameAudio.cleanup();
        window.destroy();
    }
}
