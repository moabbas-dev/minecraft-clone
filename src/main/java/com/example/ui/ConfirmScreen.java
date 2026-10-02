package com.example.ui;

/** Vanilla yes/no confirmation: a title, a message line, and Confirm / Cancel 150px buttons. */
public class ConfirmScreen extends McScreen {
    private final String title, message, confirmLabel;
    private final Runnable onConfirm;

    public ConfirmScreen(GuiEnv env, String title, String message, String confirmLabel, Runnable onConfirm) {
        super(env);
        this.title = title;
        this.message = message;
        this.confirmLabel = confirmLabel;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int cx = guiW / 2, y = guiH / 6 + 96;
        widgets.add(new Button(cx - 155, y, 150, 20, confirmLabel, () -> {
            McScreen p = parent;
            onConfirm.run();
            if (p != null) p.closeChild();
        }));
        widgets.add(new Button(cx + 5, y, 150, 20, "Cancel", () -> parent.closeChild()));
    }

    @Override
    protected void drawContent(GuiContext g) {
        g.textCentered(title, g.w / 2f, 70, 0xFFFFFF);
        g.textCentered(message, g.w / 2f, 90, 0xFFFFFF);
    }
}
