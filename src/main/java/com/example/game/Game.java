package com.example.game;

import com.example.engine.*;
import com.example.physics.Player;
import com.example.ui.PauseScreen;
import com.example.ui.SelectWorldScreen;
import com.example.ui.TitleScreen;
import com.example.ui.WorldCreateScreen;
import com.example.world.*;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

@Slf4j
public class Game {
    private static final int RENDER_DISTANCE = 6; // in chunks
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
    private static final float FOV_DEGREES = 70f;
    private static final float SKY_R = 0.53f, SKY_G = 0.81f, SKY_B = 0.92f;
    /** Full day/night cycle length. Vanilla is 1200s (20 min); shortened a bit here. */
    private static final float DAY_LENGTH_SECONDS = 600f;
    private static final float CELESTIAL_DISTANCE = 180f;
    private static final float CELESTIAL_SIZE = 28f;
    private static final int RAIN_PARTICLE_COUNT = 500;
    private static final float RAIN_RADIUS = 20f;
    private static final float RAIN_SPAWN_HEIGHT = 15f;
    private static final float RAIN_FALL_SPEED = 12f;
    private static final float SPRINT_FOV_BOOST = 5f;
    private static final float FOV_LERP_RATE = 9f;
    private static final float SWING_DURATION = 0.28f;

    private enum GameState {TITLE, SELECT_WORLD, WORLD_CREATE, IN_GAME, PAUSED}

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

    private final Map<Long, ChunkRenderData> renderData = new ConcurrentHashMap<>();

    private String currentWorldFolder;
    private WorldSettings currentSettings;

    private int selectedIndex = 0;
    private int[] breakingTarget = null;
    private float breakingProgress = 0f;

    private double titleTimer = 0;
    /** 0=midnight, 0.25=sunrise, 0.5=noon, 0.75=sunset. */
    private float dayTime = 0.5f;
    private float currentFov = FOV_DEGREES;
    private float swingTimer = 0f;
    private boolean debugOverlay = false;
    private Mesh sunMoonMesh;
    private Mesh heldItemMesh;

    private Weather weather;
    private Mesh rainMesh;
    private final java.util.Random rainRandom = new java.util.Random();
    private final float[] rainX = new float[RAIN_PARTICLE_COUNT];
    private final float[] rainY = new float[RAIN_PARTICLE_COUNT];
    private final float[] rainZ = new float[RAIN_PARTICLE_COUNT];

    private static final class ChunkRenderData {
        final int chunkX, chunkZ;
        final Mesh opaqueMesh = new Mesh(new int[]{3, 2, 1, 1, 3});
        final Mesh transparentMesh = new Mesh(new int[]{3, 2, 1, 1, 3});

        ChunkRenderData(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

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
        sunMoonMesh = new Mesh(new int[]{3, 2, 1, 1, 3});
        rainMesh = new Mesh(new int[]{3, 2, 1, 1, 3});
        heldItemMesh = new Mesh(new int[]{3, 2, 1, 1, 3});

        titleScreen = new TitleScreen(new TitleScreen.Listener() {
            @Override
            public void onPlay() {
                selectWorldScreen.refresh();
                state = GameState.SELECT_WORLD;
            }

            @Override
            public void onQuit() {
                window.close();
            }
        });

        selectWorldScreen = new SelectWorldScreen(new SelectWorldScreen.Listener() {
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
                state = GameState.TITLE;
            }
        });

        worldCreateScreen = new WorldCreateScreen(new WorldCreateScreen.Listener() {
            @Override
            public void onCreateWorld(WorldSettings settings) {
                startNewWorld(settings);
            }

            @Override
            public void onBack() {
                selectWorldScreen.refresh();
                state = GameState.SELECT_WORLD;
            }
        });

        pauseScreen = new PauseScreen(new PauseScreen.Listener() {
            @Override
            public void onBackToGame() {
                resumeGame();
            }

            @Override
            public void onQuitToTitle() {
                quitToTitle();
            }

            @Override
            public void onQuitGame() {
                window.close();
            }
        });

        glfwPollEvents();
        System.out.println("Controls: WASD move, mouse look, Space jump / swim up / double-tap fly, "
                + "Shift sneak / swim down (fly), Ctrl sprint, Left click break, Right click place, "
                + "1-9 or scroll to select block, Esc for menu.");
    }

    private void startNewWorld(WorldSettings settings) {
        for (ChunkRenderData rd : renderData.values()) rd.destroy();
        renderData.clear();

        long seed = System.currentTimeMillis();
        WorldGenerator generator = settings.type() == WorldSettings.WorldType.FLAT
                ? new FlatWorldGenerator()
                : new TerrainGenerator(seed);
        world = new World(seed, generator);

        currentWorldFolder = uniqueFolderName(WorldIO.sanitizeFolderName(settings.name()));
        currentSettings = settings;
        world.setSaveDir(WorldIO.worldDir(currentWorldFolder));
        dayTime = 0.3f; // fresh worlds start shortly after sunrise

        Chunk spawnChunk = world.getOrCreateChunk(0, 0);
        world.generateChunk(spawnChunk);

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

        selectedIndex = 0;
        breakingTarget = null;
        breakingProgress = 0;
        weather = new Weather(seed);
        initRainParticles();

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

        int spawnCx = World.worldToChunk((int) Math.floor(meta.playerPos().x));
        int spawnCz = World.worldToChunk((int) Math.floor(meta.playerPos().z));
        Chunk spawnChunk = world.getOrCreateChunk(spawnCx, spawnCz);
        world.generateChunk(spawnChunk); // restores from disk since it was already saved

        player = new Player(new Vector3f(meta.playerPos()));
        selectedIndex = Math.max(0, Math.min(BlockType.PLACEABLE.length - 1, meta.selectedIndex()));
        breakingTarget = null;
        breakingProgress = 0;
        weather = new Weather(meta.seed());
        initRainParticles();

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
        File dir = WorldIO.worldDir(currentWorldFolder);
        WorldIO.saveMeta(dir, currentSettings, world.seed, player.position.x, player.position.y, player.position.z, dayTime, selectedIndex);
        for (Chunk c : world.chunkMap().values()) {
            if (c.isGenerated()) WorldIO.saveChunk(dir, c);
        }
    }

    private void pauseGame() {
        window.releaseMouse();
        input.resyncMousePosition();
        state = GameState.PAUSED;
    }

    private void resumeGame() {
        window.captureMouse();
        input.resyncMousePosition();
        state = GameState.IN_GAME;
    }

    private void quitToTitle() {
        saveWorld();
        for (ChunkRenderData rd : renderData.values()) rd.destroy();
        renderData.clear();
        world = null;
        player = null;
        weather = null;
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
            render();
            window.update();

            // update(), render() and the buffer swap all run sequentially on this one thread -
            // there is no separate render thread here, so a stall in any of them freezes input
            // handling AND the displayed frame together, not just movement. This log is how to
            // empirically confirm that: if frames stay under the threshold, the freeze is gone;
            // if one still fires, the timestamp pins exactly when it happened.
            long frameNanos = System.nanoTime() - frameStartNanos;
            if (frameNanos > FRAME_WARN_THRESHOLD_NANOS) {
                log.warn("Frame took {} ms (update+render+swap combined) - target is under 16.7ms for 60fps",
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
            case PAUSED -> {
                if (input.consumeKeyPressed(GLFW_KEY_ESCAPE)) {
                    resumeGame();
                } else {
                    pauseScreen.update(dt, input, window.getWidth(), window.getHeight());
                }
            }
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

        player.look(input.getMouseDeltaX(), input.getMouseDeltaY());
        player.update(dt, input, world, now);

        float targetFov = FOV_DEGREES + (player.isSprinting() ? SPRINT_FOV_BOOST : 0f);
        currentFov += (targetFov - currentFov) * Math.min(1f, FOV_LERP_RATE * dt);
        if (swingTimer > 0f) swingTimer = Math.max(0f, swingTimer - dt);

        dayTime += dt / DAY_LENGTH_SECONDS;
        dayTime -= (float) Math.floor(dayTime);
        weather.update(dt);
        updateRainParticles(dt);

        updateHotbarSelection();
        updateChunkStreaming();
        handleInteraction(dt);

        titleTimer += dt;
        if (titleTimer > 0.25) {
            titleTimer = 0;
            BlockType sel = BlockType.PLACEABLE[selectedIndex];
            window.setTitle(String.format("Minecraft 1.8 Clone - Selected: %s (%.0f,%.0f,%.0f)%s",
                    sel.displayName, player.position.x, player.position.y, player.position.z,
                    player.isFlying() ? " [flying]" : ""));
        }
    }

    private void updateHotbarSelection() {
        int len = BlockType.PLACEABLE.length;
        double scroll = input.getScrollDelta();
        if (scroll > 0) selectedIndex = Math.floorMod(selectedIndex - 1, len);
        else if (scroll < 0) selectedIndex = Math.floorMod(selectedIndex + 1, len);

        int[] numberKeys = {GLFW_KEY_1, GLFW_KEY_2, GLFW_KEY_3, GLFW_KEY_4, GLFW_KEY_5,
                GLFW_KEY_6, GLFW_KEY_7, GLFW_KEY_8, GLFW_KEY_9};
        for (int i = 0; i < numberKeys.length; i++) {
            if (input.consumeKeyPressed(numberKeys[i]) && i < len) {
                selectedIndex = i;
            }
        }
    }

    private void updateChunkStreaming() {
        long deadline = System.nanoTime() + STREAMING_TIME_BUDGET_NANOS;
        int pcx = World.worldToChunk((int) Math.floor(player.position.x));
        int pcz = World.worldToChunk((int) Math.floor(player.position.z));

        int generated = 0;
        generation:
        for (int dx = -RENDER_DISTANCE; dx <= RENDER_DISTANCE; dx++) {
            for (int dz = -RENDER_DISTANCE; dz <= RENDER_DISTANCE; dz++) {
                if (dx * dx + dz * dz > RENDER_DISTANCE * RENDER_DISTANCE) continue;
                int cx = pcx + dx, cz = pcz + dz;
                if (!world.hasChunk(cx, cz)) {
                    Chunk c = world.getOrCreateChunk(cx, cz);
                    world.generateChunk(c); // biome climate sampling + height noise + block fill
                    generated++;
                    // Check the deadline only AFTER doing a chunk of work, never before, so a
                    // frame is always guaranteed to make forward progress even if a single
                    // chunk happens to cost more than the whole budget by itself.
                    if (System.nanoTime() >= deadline) break generation;
                }
            }
        }

        int unloaded = 0;
        int unloadRadius = RENDER_DISTANCE + 2;
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

    private void initRainParticles() {
        for (int i = 0; i < RAIN_PARTICLE_COUNT; i++) {
            respawnRainParticle(i, true);
        }
    }

    private void updateRainParticles(float dt) {
        if (!weather.isRaining()) return;
        for (int i = 0; i < RAIN_PARTICLE_COUNT; i++) {
            rainY[i] -= RAIN_FALL_SPEED * dt;
            int groundY = world.getColumnHeightAt((int) Math.floor(rainX[i]), (int) Math.floor(rainZ[i]));
            if (rainY[i] < groundY + 1f || rainY[i] < player.position.y - 30f) {
                respawnRainParticle(i, false);
            }
        }
    }

    /** randomHeight=true scatters the initial Y too, so particles don't all start at once mid-air in a flat sheet. */
    private void respawnRainParticle(int i, boolean randomHeight) {
        double angle = rainRandom.nextDouble() * Math.PI * 2;
        double radius = rainRandom.nextDouble() * RAIN_RADIUS;
        rainX[i] = player.position.x + (float) (Math.cos(angle) * radius);
        rainZ[i] = player.position.z + (float) (Math.sin(angle) * radius);
        rainY[i] = player.position.y + RAIN_SPAWN_HEIGHT * (randomHeight ? rainRandom.nextFloat() : 1f);
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
        if (input.consumeMousePressed(GLFW_MOUSE_BUTTON_LEFT)) {
            swingTimer = SWING_DURATION; // swings even on a whiff, matching vanilla's air-punch
        }

        Vector3f eye = player.eyePosition();
        Vector3f dir = player.frontVector();
        Raycast.Hit hit = Raycast.cast(world, eye, dir, REACH_DISTANCE);

        if (hit == null) {
            breakingTarget = null;
            breakingProgress = 0;
            return;
        }

        if (input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT)) {
            if (breakingTarget == null || breakingTarget[0] != hit.x() || breakingTarget[1] != hit.y() || breakingTarget[2] != hit.z()) {
                breakingTarget = new int[]{hit.x(), hit.y(), hit.z()};
                breakingProgress = 0;
            }
            BlockType targetType = world.getBlock(hit.x(), hit.y(), hit.z());
            float breakTime = targetType.handBreakSeconds();
            if (breakTime >= 0) {
                breakingProgress += dt;
                if (swingTimer <= 0f) swingTimer = SWING_DURATION; // repeat the punch while actively mining
                if (breakingProgress >= breakTime) {
                    world.setBlock(hit.x(), hit.y(), hit.z(), BlockType.AIR);
                    breakingProgress = 0;
                    breakingTarget = null;
                }
            }
        } else {
            breakingTarget = null;
            breakingProgress = 0;
        }

        if (input.consumeMousePressed(GLFW_MOUSE_BUTTON_RIGHT)) {
            BlockType place = BlockType.PLACEABLE[selectedIndex];
            if (!blockIntersectsPlayer(hit.placeX(), hit.placeY(), hit.placeZ())) {
                world.setBlock(hit.placeX(), hit.placeY(), hit.placeZ(), place);
                swingTimer = SWING_DURATION;
            }
        }
    }

    private boolean blockIntersectsPlayer(int bx, int by, int bz) {
        float half = Player.WIDTH / 2f;
        float px0 = player.position.x - half, px1 = player.position.x + half;
        float py0 = player.position.y, py1 = player.position.y + Player.HEIGHT;
        float pz0 = player.position.z - half, pz1 = player.position.z + half;
        return bx + 1 > px0 && bx < px1 && by + 1 > py0 && by < py1 && bz + 1 > pz0 && bz < pz1;
    }

    private void render() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (world != null && (state == GameState.IN_GAME || state == GameState.PAUSED)) {
            renderWorld();
        }

        glDisable(GL_DEPTH_TEST);
        uiRenderer.begin(window.getWidth(), window.getHeight());
        switch (state) {
            case TITLE -> titleScreen.render(uiRenderer, font, window.getWidth(), window.getHeight());
            case SELECT_WORLD -> selectWorldScreen.render(uiRenderer, font, window.getWidth(), window.getHeight());
            case WORLD_CREATE -> worldCreateScreen.render(uiRenderer, font, window.getWidth(), window.getHeight());
            case IN_GAME -> renderHud();
            case PAUSED -> {
                renderHud();
                pauseScreen.render(uiRenderer, font, window.getWidth(), window.getHeight());
            }
        }
        uiRenderer.end();
        glEnable(GL_DEPTH_TEST);
    }

    private void renderWorld() {
        float fogEnd = RENDER_DISTANCE * Chunk.SIZE_X * 0.9f;
        float fogStart = fogEnd * 0.6f;

        Vector3f skyColor = computeSkyColor(dayTime);
        Vector3f sunDir = sunDirection(dayTime);
        float daylightFactor = computeDaylightFactor(sunDir);
        if (weather.isRaining()) {
            Vector3f stormColor = new Vector3f(0.35f, 0.37f, 0.40f);
            skyColor = new Vector3f(skyColor).lerp(stormColor, weather.isThundering() ? 0.75f : 0.55f);
            daylightFactor *= weather.isThundering() ? 0.6f : 0.8f;
        }
        glClearColor(skyColor.x, skyColor.y, skyColor.z, 1f);

        // The far plane must comfortably exceed CELESTIAL_DISTANCE or the sun/moon quads get
        // clipped and silently vanish (they were previously placed beyond a too-close far plane).
        float farPlane = Math.max(fogEnd + 32f, CELESTIAL_DISTANCE + 50f);
        Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(currentFov), window.aspectRatio(), 0.1f, farPlane);
        Matrix4f view = player.viewMatrix();
        Vector3f eye = player.renderEyePosition();

        chunkShader.bind();
        chunkShader.setUniform("uProjection", projection);
        chunkShader.setUniform("uView", view);
        chunkShader.setUniform("uCameraPos", eye.x, eye.y, eye.z);
        chunkShader.setUniform("uTexture", 0);
        chunkShader.setUniform("uFogColor", skyColor.x, skyColor.y, skyColor.z);
        chunkShader.setUniform("uFogStart", fogStart);
        chunkShader.setUniform("uFogEnd", fogEnd);
        chunkShader.setUniform("uDaylightFactor", daylightFactor);
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

        renderSunAndMoon(eye, sunDir, weather.isRaining(), weather.isThundering());
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
    private void renderHeldItem() {
        glClear(GL_DEPTH_BUFFER_BIT);

        Matrix4f vmProjection = new Matrix4f().perspective((float) Math.toRadians(50f), window.aspectRatio(), 0.05f, 10f);
        Matrix4f vmView = new Matrix4f();

        float bobPhase = player.getBobPhase();
        float bobAmount = player.getBobAmplitude();
        float bobX = (float) Math.sin(bobPhase) * bobAmount * 0.6f;
        float bobY = (float) Math.abs(Math.sin(bobPhase * 2f)) * bobAmount * 0.5f;

        float swingArc = swingTimer > 0f ? (float) Math.sin((1f - swingTimer / SWING_DURATION) * Math.PI) : 0f;

        BlockType held = BlockType.PLACEABLE[selectedIndex];
        float[] verts = buildHeldItemVerts(held);

        Matrix4f model = new Matrix4f()
                .translate(0.62f + bobX, -0.52f - bobY - swingArc * 0.12f, -1.1f - swingArc * 0.25f)
                .rotateY((float) Math.toRadians(28f - swingArc * 35f))
                .rotateX((float) Math.toRadians(-12f - swingArc * 25f))
                .scale(0.5f);

        chunkShader.setUniform("uProjection", vmProjection);
        chunkShader.setUniform("uView", vmView);
        chunkShader.setUniform("uModel", model);
        chunkShader.setUniform("uAlpha", 1f);
        chunkShader.setUniform("uFogStart", 1_000_000f);
        chunkShader.setUniform("uFogEnd", 2_000_000f);

        heldItemMesh.upload(verts);
        heldItemMesh.draw();
    }

    /** A standalone unit cube (-0.5..0.5) with all 6 faces always emitted - no neighbor culling needed for a held item. */
    private float[] buildHeldItemVerts(BlockType block) {
        BlockTextureMap.Faces faces = textureMap.facesFor(block);
        float[] top = atlas.getUv(faces.top());
        float[] bottom = atlas.getUv(faces.bottom());
        float[] side = atlas.getUv(faces.side());

        float[] verts = new float[6 * 6 * 10];
        int idx = 0;
        idx = emitCubeFace(verts, idx, new float[]{-0.5f, 0.5f, -0.5f}, new float[]{-0.5f, 0.5f, 0.5f}, new float[]{0.5f, 0.5f, 0.5f}, new float[]{0.5f, 0.5f, -0.5f}, top, 1.0f);
        idx = emitCubeFace(verts, idx, new float[]{-0.5f, -0.5f, 0.5f}, new float[]{-0.5f, -0.5f, -0.5f}, new float[]{0.5f, -0.5f, -0.5f}, new float[]{0.5f, -0.5f, 0.5f}, bottom, 0.6f);
        idx = emitCubeFace(verts, idx, new float[]{-0.5f, -0.5f, 0.5f}, new float[]{0.5f, -0.5f, 0.5f}, new float[]{0.5f, 0.5f, 0.5f}, new float[]{-0.5f, 0.5f, 0.5f}, side, 0.85f);
        idx = emitCubeFace(verts, idx, new float[]{0.5f, -0.5f, -0.5f}, new float[]{-0.5f, -0.5f, -0.5f}, new float[]{-0.5f, 0.5f, -0.5f}, new float[]{0.5f, 0.5f, -0.5f}, side, 0.85f);
        idx = emitCubeFace(verts, idx, new float[]{0.5f, -0.5f, 0.5f}, new float[]{0.5f, -0.5f, -0.5f}, new float[]{0.5f, 0.5f, -0.5f}, new float[]{0.5f, 0.5f, 0.5f}, side, 0.7f);
        idx = emitCubeFace(verts, idx, new float[]{-0.5f, -0.5f, -0.5f}, new float[]{-0.5f, -0.5f, 0.5f}, new float[]{-0.5f, 0.5f, 0.5f}, new float[]{-0.5f, 0.5f, -0.5f}, side, 0.7f);
        return verts;
    }

    private int emitCubeFace(float[] arr, int idx, float[] p0, float[] p1, float[] p2, float[] p3, float[] uv, float shade) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        idx = emitHeldVert(arr, idx, p0, u0, v1, shade);
        idx = emitHeldVert(arr, idx, p1, u1, v1, shade);
        idx = emitHeldVert(arr, idx, p2, u1, v0, shade);
        idx = emitHeldVert(arr, idx, p0, u0, v1, shade);
        idx = emitHeldVert(arr, idx, p2, u1, v0, shade);
        idx = emitHeldVert(arr, idx, p3, u0, v0, shade);
        return idx;
    }

    private int emitHeldVert(float[] arr, int idx, float[] p, float u, float v, float shade) {
        arr[idx++] = p[0];
        arr[idx++] = p[1];
        arr[idx++] = p[2];
        arr[idx++] = u;
        arr[idx++] = v;
        arr[idx++] = 0f; // skyShade unused - the held item ignores day/night and world lighting
        arr[idx++] = shade; // baked per-face brightness lives in the blockLight channel instead,
        // since that one isn't multiplied by uDaylightFactor, so it stays constant day or night
        arr[idx++] = 1f; // tint r - the held item is never grass/leaves, always neutral
        arr[idx++] = 1f; // tint g
        arr[idx++] = 1f; // tint b
        return idx;
    }

    private void renderRain() {
        if (!weather.isRaining()) return;

        Vector3f front = player.frontVector();
        Vector3f right = new Vector3f(-front.z, 0, front.x);
        if (right.lengthSquared() < 1e-6f) right.set(1, 0, 0);
        right.normalize();
        Vector3f up = new Vector3f(0, 1, 0);

        float[] uv = atlas.getUv(Tile.RAIN);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float halfW = 0.05f, halfH = 0.45f;

        chunkShader.setUniform("uModel", new Matrix4f());
        chunkShader.setUniform("uAlpha", 1f);
        chunkShader.setUniform("uFogStart", 1_000_000f);
        chunkShader.setUniform("uFogEnd", 2_000_000f);

        float[] verts = new float[RAIN_PARTICLE_COUNT * 6 * 10];
        int idx = 0;
        for (int i = 0; i < RAIN_PARTICLE_COUNT; i++) {
            float cx = rainX[i], cy = rainY[i], cz = rainZ[i];
            float rx = right.x * halfW, ry = right.y * halfW, rz = right.z * halfW;
            float ux = up.x * halfH, uy = up.y * halfH, uz = up.z * halfH;

            float p0x = cx - rx - ux, p0y = cy - ry - uy, p0z = cz - rz - uz;
            float p1x = cx + rx - ux, p1y = cy + ry - uy, p1z = cz + rz - uz;
            float p2x = cx + rx + ux, p2y = cy + ry + uy, p2z = cz + rz + uz;
            float p3x = cx - rx + ux, p3y = cy - ry + uy, p3z = cz - rz + uz;

            idx = putVertex(verts, idx, p0x, p0y, p0z, u0, v1);
            idx = putVertex(verts, idx, p1x, p1y, p1z, u1, v1);
            idx = putVertex(verts, idx, p2x, p2y, p2z, u1, v0);
            idx = putVertex(verts, idx, p0x, p0y, p0z, u0, v1);
            idx = putVertex(verts, idx, p2x, p2y, p2z, u1, v0);
            idx = putVertex(verts, idx, p3x, p3y, p3z, u0, v0);
        }
        rainMesh.upload(verts);
        rainMesh.draw();
    }

    private int putVertex(float[] arr, int idx, float x, float y, float z, float u, float v) {
        arr[idx++] = x;
        arr[idx++] = y;
        arr[idx++] = z;
        arr[idx++] = u;
        arr[idx++] = v;
        arr[idx++] = 1f; // sky shade - rain is drawn fully bright regardless of local block lighting
        arr[idx++] = 1f; // block light
        arr[idx++] = 1f; // tint r
        arr[idx++] = 1f; // tint g
        arr[idx++] = 1f; // tint b
        return idx;
    }

    /**
     * Vanilla doesn't actually remove the sun/moon sprite during rain - the whole atmosphere
     * (sky color, fog, ambient light) darkens together so it reads as washed out behind storm
     * clouds rather than deleted. Previously this engine darkened the sky and terrain lighting
     * for a storm (see renderWorld) but drew the sun/moon at a hardcoded full alpha regardless -
     * a bright, fully-lit disc sitting on top of a dark stormy sky, which is the actual bug: not
     * that the sun was "visible" (it should be), but that it wasn't dimmed with everything else.
     */
    private static final float CELESTIAL_ALPHA_RAIN = 0.35f;
    private static final float CELESTIAL_ALPHA_THUNDER = 0.12f;

    private void renderSunAndMoon(Vector3f eye, Vector3f sunDir, boolean raining, boolean thundering) {
        Vector3f right = new Vector3f(-sunDir.z, 0, sunDir.x);
        if (right.lengthSquared() < 1e-6f) right.set(1, 0, 0);
        right.normalize();
        Vector3f up = new Vector3f(right).cross(sunDir).normalize();

        float celestialAlpha = celestialAlpha(raining, thundering);

        Matrix4f identity = new Matrix4f();
        chunkShader.setUniform("uModel", identity);
        // Celestial bodies aren't part of the terrain - keep them unfogged (their dimming for
        // weather is handled by uAlpha below instead, so they still blend into the storm sky
        // color rather than the distance-fog color).
        chunkShader.setUniform("uFogStart", 1_000_000f);
        chunkShader.setUniform("uFogEnd", 2_000_000f);
        chunkShader.setUniform("uAlpha", celestialAlpha);

        Vector3f sunCenter = new Vector3f(eye).add(new Vector3f(sunDir).mul(CELESTIAL_DISTANCE));
        renderCelestialBody(Tile.SUN, sunCenter, right, up);

        Vector3f moonDir = new Vector3f(sunDir).negate();
        Vector3f moonCenter = new Vector3f(eye).add(new Vector3f(moonDir).mul(CELESTIAL_DISTANCE));
        renderCelestialBody(Tile.MOON, moonCenter, right, up);
    }

    /** Pure formula, exercised directly by tests in this package - see com.example.game.unit. */
    static float celestialAlpha(boolean raining, boolean thundering) {
        if (thundering) return CELESTIAL_ALPHA_THUNDER;
        if (raining) return CELESTIAL_ALPHA_RAIN;
        return 1f;
    }

    private void renderCelestialBody(Tile tile, Vector3f center, Vector3f right, Vector3f up) {
        float[] uv = atlas.getUv(tile);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float half = CELESTIAL_SIZE / 2f;

        Vector3f rHalf = new Vector3f(right).mul(half);
        Vector3f uHalf = new Vector3f(up).mul(half);
        Vector3f p0 = new Vector3f(center).sub(rHalf).sub(uHalf);
        Vector3f p1 = new Vector3f(center).add(rHalf).sub(uHalf);
        Vector3f p2 = new Vector3f(center).add(rHalf).add(uHalf);
        Vector3f p3 = new Vector3f(center).sub(rHalf).add(uHalf);

        float sky = 1f, block = 1f, t = 1f;
        float[] verts = {
                p0.x, p0.y, p0.z, u0, v1, sky, block, t, t, t,
                p1.x, p1.y, p1.z, u1, v1, sky, block, t, t, t,
                p2.x, p2.y, p2.z, u1, v0, sky, block, t, t, t,
                p0.x, p0.y, p0.z, u0, v1, sky, block, t, t, t,
                p2.x, p2.y, p2.z, u1, v0, sky, block, t, t, t,
                p3.x, p3.y, p3.z, u0, v0, sky, block, t, t, t,
        };
        sunMoonMesh.upload(verts);
        sunMoonMesh.draw();
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
        float cx = window.getWidth() / 2f;
        float cy = window.getHeight() / 2f;
        uiRenderer.drawColorQuad(cx - 5, cy - 1, 10, 2, 1, 1, 1, 0.9f);
        uiRenderer.drawColorQuad(cx - 1, cy - 5, 2, 10, 1, 1, 1, 0.9f);

        int len = BlockType.PLACEABLE.length;
        int visibleSlots = 9;
        int windowStart = Math.max(0, Math.min(selectedIndex - 4, len - visibleSlots));
        float slotSize = 44f;
        float margin = 4f;
        float totalWidth = visibleSlots * (slotSize + margin) - margin;
        float startX = (window.getWidth() - totalWidth) / 2f;
        float y = window.getHeight() - slotSize - 12f;

        for (int i = 0; i < visibleSlots; i++) {
            int idx = windowStart + i;
            float x = startX + i * (slotSize + margin);
            boolean selected = idx == selectedIndex;
            if (selected) {
                uiRenderer.drawColorQuad(x - 3, y - 3, slotSize + 6, slotSize + 6, 1f, 1f, 0.4f, 0.95f);
            }
            uiRenderer.drawColorQuad(x, y, slotSize, slotSize, 0.1f, 0.1f, 0.1f, 0.55f);

            drawBlockIcon(BlockType.PLACEABLE[idx], x, y, slotSize);
        }

        float flash = weather.getLightningFlash();
        if (flash > 0.01f) {
            uiRenderer.drawColorQuad(0, 0, window.getWidth(), window.getHeight(), 1f, 1f, 1f, flash * 0.8f);
        }

        renderDebugOverlay();
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

    /**
     * Draws a block as a proper isometric 3D cube icon (top + 2 side faces), matching how
     * Minecraft actually renders inventory/hotbar items, instead of a single flat top texture.
     */
    private void drawBlockIcon(BlockType block, float slotX, float slotY, float slotSize) {
        BlockTextureMap.Faces faces = textureMap.facesFor(block);
        float[] topUv = atlas.getUv(faces.top());
        float[] sideUv = atlas.getUv(faces.side());

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
        drawIconFace(pts[2], pts[4], pts[7], pts[6], topUv, 1.0f);  // top face (y=1)
        drawIconFace(pts[4], pts[7], pts[5], pts[1], sideUv, 0.75f); // east face (x=1)
        drawIconFace(pts[6], pts[7], pts[5], pts[3], sideUv, 0.55f); // south face (z=1)
    }

    private void drawIconFace(float[] p0, float[] p1, float[] p2, float[] p3, float[] uv, float shade) {
        uiRenderer.drawTexturedQuadPoints(p0[0], p0[1], p1[0], p1[1], p2[0], p2[1], p3[0], p3[1],
                uv, atlas.getTextureId(), shade, shade, shade, 1f);
    }

    private void cleanup() {
        saveWorld(); // safety net for any exit path that isn't "Save & Quit to Title" (e.g. the OS close button)
        for (ChunkRenderData rd : renderData.values()) rd.destroy();
        uiRenderer.destroy();
        font.destroy();
        sunMoonMesh.destroy();
        rainMesh.destroy();
        heldItemMesh.destroy();
        chunkShader.destroy();
        window.destroy();
    }
}
