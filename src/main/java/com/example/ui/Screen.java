package com.example.ui;

import com.example.engine.Font;
import com.example.engine.Input;
import com.example.engine.UiRenderer;

public interface Screen {
    void update(float dt, Input input, int screenWidth, int screenHeight);

    void render(UiRenderer ui, Font font, int screenWidth, int screenHeight);
}
