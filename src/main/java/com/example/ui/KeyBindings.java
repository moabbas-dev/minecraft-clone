package com.example.ui;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Rebindable controls, stored in options.txt the vanilla way ({@code key_key.attack:key.mouse.left}).
 * A binding is one int: a GLFW keyboard code, or {@link #MOUSE_BASE} + a mouse button. Pure data, so it is
 * unit-testable without a window.
 */
public final class KeyBindings {
    public static final int MOUSE_BASE = com.example.engine.Input.MOUSE_CODE_BASE;
    public static final int UNBOUND = -1;

    /** Vanilla binding ids (the part after {@code key_}) and their defaults. Order is the Key Binds screen order. */
    public enum Bind {
        ATTACK("key.attack", "Attack/Destroy", MOUSE_BASE + GLFW_MOUSE_BUTTON_LEFT),
        USE("key.use", "Use Item/Place Block", MOUSE_BASE + GLFW_MOUSE_BUTTON_RIGHT),
        FORWARD("key.forward", "Walk Forwards", GLFW_KEY_W),
        LEFT("key.left", "Strafe Left", GLFW_KEY_A),
        BACK("key.back", "Walk Backwards", GLFW_KEY_S),
        RIGHT("key.right", "Strafe Right", GLFW_KEY_D),
        JUMP("key.jump", "Jump", GLFW_KEY_SPACE),
        SNEAK("key.sneak", "Sneak", GLFW_KEY_LEFT_SHIFT),
        SPRINT("key.sprint", "Sprint", GLFW_KEY_LEFT_CONTROL),
        DROP("key.drop", "Drop Selected Item", GLFW_KEY_Q),
        INVENTORY("key.inventory", "Open/Close Inventory", GLFW_KEY_E),
        PICK_ITEM("key.pickItem", "Pick Block", MOUSE_BASE + GLFW_MOUSE_BUTTON_MIDDLE),
        SCREENSHOT("key.screenshot", "Take Screenshot", GLFW_KEY_F2),
        FULLSCREEN("key.fullscreen", "Toggle Fullscreen", GLFW_KEY_F11),
        HOTBAR_1("key.hotbar.1", "Hotbar Slot 1", GLFW_KEY_1),
        HOTBAR_2("key.hotbar.2", "Hotbar Slot 2", GLFW_KEY_2),
        HOTBAR_3("key.hotbar.3", "Hotbar Slot 3", GLFW_KEY_3),
        HOTBAR_4("key.hotbar.4", "Hotbar Slot 4", GLFW_KEY_4),
        HOTBAR_5("key.hotbar.5", "Hotbar Slot 5", GLFW_KEY_5),
        HOTBAR_6("key.hotbar.6", "Hotbar Slot 6", GLFW_KEY_6),
        HOTBAR_7("key.hotbar.7", "Hotbar Slot 7", GLFW_KEY_7),
        HOTBAR_8("key.hotbar.8", "Hotbar Slot 8", GLFW_KEY_8),
        HOTBAR_9("key.hotbar.9", "Hotbar Slot 9", GLFW_KEY_9);

        public final String id;
        public final String label;
        public final int defaultCode;

        Bind(String id, String label, int defaultCode) {
            this.id = id;
            this.label = label;
            this.defaultCode = defaultCode;
        }

        /** Key name in options.txt, e.g. {@code key_key.attack}. */
        public String optionKey() {
            return "key_" + id;
        }
    }

    private final Map<Bind, Integer> codes = new EnumMap<>(Bind.class);

    public KeyBindings() {
        reset();
    }

    public void reset() {
        for (Bind b : Bind.values()) codes.put(b, b.defaultCode);
    }

    public int get(Bind b) {
        return codes.get(b);
    }

    public void set(Bind b, int code) {
        codes.put(b, code);
    }

    public static boolean isMouse(int code) {
        return code >= MOUSE_BASE;
    }

    /** True when another binding uses the same code (the Key Binds screen paints these red). */
    public boolean conflicts(Bind b) {
        int c = get(b);
        if (c == UNBOUND) return false;
        for (Bind o : Bind.values()) if (o != b && get(o) == c) return true;
        return false;
    }

    // ---- vanilla key ids ----

    private static final Map<String, Integer> NAMED = new LinkedHashMap<>();
    private static final Map<Integer, String> BY_CODE = new LinkedHashMap<>();

    private static void name(String id, int code, String shown) {
        NAMED.put(id, code);
        BY_CODE.put(code, id);
        DISPLAY.put(code, shown);
    }

    private static final Map<Integer, String> DISPLAY = new LinkedHashMap<>();

    static {
        for (char c = 'a'; c <= 'z'; c++) name("key.keyboard." + c, GLFW_KEY_A + (c - 'a'), String.valueOf((char) (c - 32)));
        for (char c = '0'; c <= '9'; c++) name("key.keyboard." + c, GLFW_KEY_0 + (c - '0'), String.valueOf(c));
        for (int i = 1; i <= 12; i++) name("key.keyboard.f" + i, GLFW_KEY_F1 + i - 1, "F" + i);
        name("key.keyboard.space", GLFW_KEY_SPACE, "Space");
        name("key.keyboard.left.shift", GLFW_KEY_LEFT_SHIFT, "Left Shift");
        name("key.keyboard.right.shift", GLFW_KEY_RIGHT_SHIFT, "Right Shift");
        name("key.keyboard.left.control", GLFW_KEY_LEFT_CONTROL, "Left Control");
        name("key.keyboard.right.control", GLFW_KEY_RIGHT_CONTROL, "Right Control");
        name("key.keyboard.left.alt", GLFW_KEY_LEFT_ALT, "Left Alt");
        name("key.keyboard.right.alt", GLFW_KEY_RIGHT_ALT, "Right Alt");
        name("key.keyboard.tab", GLFW_KEY_TAB, "Tab");
        name("key.keyboard.enter", GLFW_KEY_ENTER, "Enter");
        name("key.keyboard.backspace", GLFW_KEY_BACKSPACE, "Backspace");
        name("key.keyboard.up", GLFW_KEY_UP, "Up Arrow");
        name("key.keyboard.down", GLFW_KEY_DOWN, "Down Arrow");
        name("key.keyboard.left", GLFW_KEY_LEFT, "Left Arrow");
        name("key.keyboard.right", GLFW_KEY_RIGHT, "Right Arrow");
        name("key.keyboard.grave.accent", GLFW_KEY_GRAVE_ACCENT, "`");
        name("key.keyboard.comma", GLFW_KEY_COMMA, ",");
        name("key.keyboard.period", GLFW_KEY_PERIOD, ".");
        name("key.keyboard.slash", GLFW_KEY_SLASH, "/");
        name("key.mouse.left", MOUSE_BASE + GLFW_MOUSE_BUTTON_LEFT, "Left Button");
        name("key.mouse.right", MOUSE_BASE + GLFW_MOUSE_BUTTON_RIGHT, "Right Button");
        name("key.mouse.middle", MOUSE_BASE + GLFW_MOUSE_BUTTON_MIDDLE, "Middle Button");
        for (int b = 3; b <= 7; b++) name("key.mouse." + (b + 1), MOUSE_BASE + b, "Button " + (b + 1));
    }

    /** {@code key.keyboard.w}-style id for a code, or {@code key.keyboard.unknown}. */
    public static String idOf(int code) {
        String id = BY_CODE.get(code);
        return id == null ? "key.keyboard.unknown" : id;
    }

    /** Code for an id; {@link #UNBOUND} when unknown (including "key.keyboard.unknown"). */
    public static int codeOf(String id) {
        if (id == null) return UNBOUND;
        Integer c = NAMED.get(id.trim().toLowerCase(Locale.ROOT));
        return c == null ? UNBOUND : c;
    }

    /** Text on the rebind button, e.g. "W" or "Left Button"; "Not Bound" when empty. */
    public static String displayName(int code) {
        if (code == UNBOUND) return "Not Bound";
        String s = DISPLAY.get(code);
        return s == null ? "Unknown" : s;
    }

    /** True for codes that can be bound (keyboard keys we know a name for, and mouse buttons). */
    public static boolean isBindable(int code) {
        return BY_CODE.containsKey(code);
    }
}
