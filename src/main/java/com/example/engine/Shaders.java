package com.example.engine;

public final class Shaders {
    private Shaders() {
    }

    public static final String CHUNK_VERTEX = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aUv;
            layout (location = 2) in float aSkyShade;
            layout (location = 3) in float aBlockLight;
            layout (location = 4) in vec3 aTint;

            uniform mat4 uProjection;
            uniform mat4 uView;
            uniform mat4 uModel;
            uniform vec3 uCameraPos;

            out vec2 vUv;
            out float vSkyShade;
            out float vBlockLight;
            out float vDistance;
            out vec3 vTint;

            void main() {
                vec4 worldPos = uModel * vec4(aPos, 1.0);
                gl_Position = uProjection * uView * worldPos;
                vUv = aUv;
                vSkyShade = aSkyShade;
                vBlockLight = aBlockLight;
                vDistance = length(worldPos.xyz - uCameraPos);
                vTint = aTint;
            }
            """;

    public static final String CHUNK_FRAGMENT = """
            #version 330 core
            in vec2 vUv;
            in float vSkyShade;
            in float vBlockLight;
            in float vDistance;
            in vec3 vTint;

            uniform sampler2D uTexture;
            uniform float uAlpha;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            // Sky light fades toward night; block light (torches, glowstone...) does not.
            uniform float uDaylightFactor;

            out vec4 FragColor;

            void main() {
                vec4 texColor = texture(uTexture, vUv);
                if (texColor.a < 0.05) discard;
                float light = max(vSkyShade * uDaylightFactor, vBlockLight);
                light = max(light, 0.04);
                // vTint is (1,1,1) - a no-op multiply - for every face except grass-top/leaves,
                // which carry the real per-column biome colormap color baked in at mesh time.
                vec3 shaded = texColor.rgb * light * vTint;
                float fogFactor = clamp((vDistance - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
                vec3 finalColor = mix(shaded, uFogColor, fogFactor);
                FragColor = vec4(finalColor, texColor.a * uAlpha);
            }
            """;

    public static final String UI_VERTEX = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            layout (location = 1) in vec2 aUv;

            uniform vec2 uScreenSize;

            out vec2 vUv;

            void main() {
                vec2 ndc = vec2((aPos.x / uScreenSize.x) * 2.0 - 1.0,
                                 1.0 - (aPos.y / uScreenSize.y) * 2.0);
                gl_Position = vec4(ndc, 0.0, 1.0);
                vUv = aUv;
            }
            """;

    public static final String UI_FRAGMENT = """
            #version 330 core
            in vec2 vUv;

            uniform sampler2D uTexture;
            uniform vec4 uColor;
            uniform int uUseTexture;

            out vec4 FragColor;

            void main() {
                if (uUseTexture == 1) {
                    vec4 texColor = texture(uTexture, vUv);
                    FragColor = texColor * uColor;
                } else {
                    FragColor = uColor;
                }
            }
            """;
}
