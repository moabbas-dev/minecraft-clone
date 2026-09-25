package com.example.testctl;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * Pure command parsing/validation/dispatch logic - no HTTP server, no queue, no GL context.
 * Deliberately decoupled from {@link TestController} so this can be unit-tested against a fake
 * {@link GameAccess} with no real Game/HttpServer/GL context involved, matching this project's
 * established "test the pure logic, not the thing that needs a window" boundary.
 */
public final class Commands {
    public record Result(int status, String body) {
        static Result ok(String body) {
            return new Result(200, body);
        }

        static Result badRequest(String reason) {
            return new Result(400, reason);
        }
    }

    private final GameAccess game;

    public Commands(GameAccess game) {
        this.game = game;
    }

    public Result execute(String command, Map<String, String> params) {
        return switch (command) {
            case "teleport" -> teleport(params);
            case "setTime" -> setTime(params);
            case "setWeather" -> setWeather(params);
            case "newTestWorld" -> newTestWorld(params);
            case "spawnTree" -> spawnTree(params);
            case "setBlock" -> setBlock(params);
            case "selectSlot" -> selectSlot(params);
            case "giveItem" -> giveItem(params);
            case "holdAttack" -> holdAttack(params);
            case "useItem" -> useItem();
            case "openInventory" -> openInventory(params);
            case "inventoryClick" -> inventoryClick(params);
            case "closeInventory" -> closeInventory();
            case "lookAt" -> lookAt(params);
            case "screenshot" -> screenshot(params);
            case "state" -> state();
            case "shutdown" -> shutdown();
            default -> Result.badRequest("Unknown command: " + command);
        };
    }

    private Result teleport(Map<String, String> p) {
        Float x = parseFloat(p, "x");
        Float y = parseFloat(p, "y");
        Float z = parseFloat(p, "z");
        if (x == null || y == null || z == null) {
            return Result.badRequest("teleport requires numeric x, y, z params");
        }
        Float yaw = parseFloat(p, "yaw");
        Float pitch = parseFloat(p, "pitch");
        game.teleport(x, y, z, yaw, pitch);
        return Result.ok("teleported to " + x + "," + y + "," + z);
    }

    private Result setTime(Map<String, String> p) {
        Float t = parseFloat(p, "time");
        if (t == null) return Result.badRequest("setTime requires a numeric 'time' param in [0,1)");
        if (t < 0f || t >= 1f) return Result.badRequest("time must be in [0,1), got: " + t);
        game.setTime(t);
        return Result.ok("time set to " + t);
    }

    private Result setWeather(Map<String, String> p) {
        String state = p.get("state");
        if (state == null || state.isBlank()) {
            return Result.badRequest("setWeather requires a 'state' param (CLEAR, RAIN, or THUNDER)");
        }
        String normalized = state.toUpperCase(Locale.ROOT);
        if (!normalized.equals("CLEAR") && !normalized.equals("RAIN") && !normalized.equals("THUNDER")) {
            return Result.badRequest("state must be CLEAR, RAIN, or THUNDER, got: " + state);
        }
        game.setWeather(normalized);
        return Result.ok("weather set to " + normalized);
    }

    private Result newTestWorld(Map<String, String> p) {
        if ("flat".equalsIgnoreCase(p.get("type"))) {
            game.newFlatTestWorld();
            return Result.ok("new flat test world created");
        }
        Long seed = parseLong(p, "seed");
        long resolved = seed != null ? seed : System.currentTimeMillis();
        game.newTestWorld(resolved);
        return Result.ok("new test world created with seed " + resolved);
    }

    private Result spawnTree(Map<String, String> p) {
        Integer x = parseInt(p, "x"), y = parseInt(p, "y"), z = parseInt(p, "z");
        Long seed = p.containsKey("seed") ? parseLong(p, "seed") : Long.valueOf(0);
        String species = p.get("species");
        if (x == null || y == null || z == null || seed == null || species == null || species.isBlank()) {
            return Result.badRequest("spawnTree requires species (a TreeSpecies name) and integer x, y, z (optional integer seed)");
        }
        try {
            game.spawnTree(species, x, y, z, seed);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.badRequest(e.getMessage());
        }
        return Result.ok("spawned " + species.toUpperCase(Locale.ROOT) + " at " + x + "," + y + "," + z + " seed " + seed);
    }

    private Result setBlock(Map<String, String> p) {
        Integer x = parseInt(p, "x");
        Integer y = parseInt(p, "y");
        Integer z = parseInt(p, "z");
        String type = p.get("type");
        if (x == null || y == null || z == null || type == null || type.isBlank()) {
            return Result.badRequest("setBlock requires integer x, y, z and a 'type' param (a BlockType enum name)");
        }
        try {
            game.setBlock(x, y, z, type);
        } catch (IllegalArgumentException e) {
            return Result.badRequest("unknown block type: " + type);
        }
        return Result.ok("set " + x + "," + y + "," + z + " to " + type.toUpperCase(Locale.ROOT));
    }

    private Result selectSlot(Map<String, String> p) {
        Integer index = parseInt(p, "index");
        if (index == null) return Result.badRequest("selectSlot requires an integer 'index' param");
        try {
            game.selectSlot(index);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        }
        return Result.ok("selected slot " + index);
    }

    private Result giveItem(Map<String, String> p) {
        String type = p.get("type");
        Integer count = parseInt(p, "count");
        if (type == null || type.isBlank() || count == null) {
            return Result.badRequest("giveItem requires a 'type' (BlockType name) and an integer 'count'");
        }
        try {
            int added = game.giveItem(type, count);
            return Result.ok("added " + added + " of " + count + " " + type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Result.badRequest("cannot give " + type + " x" + count + ": " + e.getMessage());
        }
    }

    private Result holdAttack(Map<String, String> p) {
        String held = p.get("held");
        if (held == null || !(held.equalsIgnoreCase("true") || held.equalsIgnoreCase("false"))) {
            return Result.badRequest("holdAttack requires held=true or held=false");
        }
        game.holdAttack(Boolean.parseBoolean(held));
        return Result.ok("attack held = " + held.toLowerCase(Locale.ROOT));
    }

    private Result useItem() {
        game.useItem();
        return Result.ok("use queued");
    }

    private Result openInventory(Map<String, String> p) {
        Integer size = p.containsKey("grid") ? parseInt(p, "grid") : Integer.valueOf(2);
        if (size == null || (size != 2 && size != 3)) return Result.badRequest("openInventory grid must be 2 or 3");
        try {
            game.openInventoryScreen(size);
        } catch (IllegalStateException e) {
            return Result.badRequest(e.getMessage());
        }
        return Result.ok("inventory opened with a " + size + "x" + size + " crafting grid");
    }

    private Result inventoryClick(Map<String, String> p) {
        Integer slot = parseInt(p, "slot");
        String button = p.getOrDefault("button", "left").toLowerCase(Locale.ROOT);
        if (slot == null || !(button.equals("left") || button.equals("right"))) {
            return Result.badRequest("inventoryClick requires an integer 'slot' and button=left|right");
        }
        try {
            game.inventoryClick(slot, button.equals("right"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.badRequest(e.getMessage());
        }
        return Result.ok("clicked slot " + slot + " (" + button + ")");
    }

    private Result closeInventory() {
        try {
            game.closeInventoryScreen();
        } catch (IllegalStateException e) {
            return Result.badRequest(e.getMessage());
        }
        return Result.ok("inventory closed");
    }

    private Result lookAt(Map<String, String> p) {
        Float x = parseFloat(p, "x"), y = parseFloat(p, "y"), z = parseFloat(p, "z");
        if (x == null || y == null || z == null) return Result.badRequest("lookAt requires numeric x, y, z params");
        try {
            game.lookAt(x, y, z);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        }
        return Result.ok("looking at " + x + "," + y + "," + z);
    }

    private Result screenshot(Map<String, String> p) {
        String name = p.get("name");
        if (name == null || name.isBlank()) return Result.badRequest("screenshot requires a 'name' param");
        if (!name.matches("[A-Za-z0-9_-]+")) {
            return Result.badRequest("name must contain only letters, digits, '_' or '-', got: " + name);
        }
        try {
            String path = game.captureScreenshot(name);
            return Result.ok("saved to " + path);
        } catch (IOException e) {
            return new Result(500, "screenshot failed: " + e.getMessage());
        }
    }

    private Result state() {
        return Result.ok(game.stateSnapshot());
    }

    private Result shutdown() {
        game.shutdown();
        return Result.ok("shutting down");
    }

    private Float parseFloat(Map<String, String> p, String key) {
        String v = p.get(key);
        if (v == null) return null;
        try {
            return Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(Map<String, String> p, String key) {
        String v = p.get(key);
        if (v == null) return null;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(Map<String, String> p, String key) {
        String v = p.get(key);
        if (v == null) return null;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
