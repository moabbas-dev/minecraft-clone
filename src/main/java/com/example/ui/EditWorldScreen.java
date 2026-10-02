package com.example.ui;

import java.util.function.Consumer;

/** Rename dialog for a saved world (Edit button on Select World). */
public class EditWorldScreen extends McScreen {
    private final StringBuilder name;
    private final Consumer<String> onDone;

    public EditWorldScreen(GuiEnv env, String currentName, Consumer<String> onDone) {
        super(env);
        this.name = new StringBuilder(currentName);
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        int cx = guiW / 2;
        TextField field = new TextField(cx - 100, 60, 200, 20, name, 32);
        field.setFocused(true);
        widgets.add(field);
        widgets.add(new Button(cx - 154, guiH - 28, 150, 20, "Done", this::finish));
        widgets.add(new Button(cx + 4, guiH - 28, 150, 20, "Cancel", () -> parent.closeChild()));
    }

    private void finish() {
        String n = name.toString().trim();
        if (!n.isEmpty()) onDone.accept(n);
        if (parent != null) parent.closeChild();
    }

    @Override
    protected void onEnter() {
        finish();
    }

    @Override
    protected void drawContent(GuiContext g) {
        g.textCentered("Edit World", g.w / 2f, 20, 0xFFFFFF);
        g.text("World Name", g.w / 2f - 100, 47, 0xA0A0A0);
    }
}
