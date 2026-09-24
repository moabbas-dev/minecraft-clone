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
            // 0.0 = rigid (every solid block, held item, rain, sun/moon); >0.0 = leaf faces and
            // sapling cross-plant tops only - see ChunkMeshBuilder.windWeightFor/emitCrossPlant.
            layout (location = 5) in float aWindWeight;

            uniform mat4 uProjection;
            uniform mat4 uView;
            uniform mat4 uModel;
            uniform vec3 uCameraPos;
            // Elapsed seconds (only while actually in-game and unpaused - see Game.updateInGame),
            // and a single debug-tunable strength multiplier (0 disables wind entirely).
            uniform float uWindTime;
            uniform float uWindStrength;

            out vec2 vUv;
            out float vSkyShade;
            out float vBlockLight;
            out float vDistance;
            out vec3 vTint;

            void main() {
                vec4 worldPos = uModel * vec4(aPos, 1.0);

                if (aWindWeight > 0.0) {
                    // Maximum displacement in block units at full weight/strength - this
                    // project's own tuning (see ChunkMeshBuilder's LEAF_WIND_WEIGHT javadoc),
                    // chosen within the suggested 0.02-0.08 (leaves) / 0.03-0.1 (sapling) ranges,
                    // not an official Minecraft value. sin() alone ranges roughly [-1.25, 1.25]
                    // (primary + a 25%-strength secondary term) - without this scale the raw
                    // sine value was being used directly as block-unit displacement, up to
                    // nearly a full block, which is what produced visibly torn/gappy leaf faces
                    // during live testing.
                    const float WIND_AMPLITUDE = 0.06;
                    // Stable world-space phase (not camera-relative, not per-frame-random) so
                    // neighboring trees sway out of sync with each other but never pop or drift
                    // as the camera moves.
                    float phase = worldPos.x * 0.9 + worldPos.z * 0.7;
                    float primary = sin(uWindTime * 1.2566 + phase);
                    float secondary = sin(uWindTime * 3.1416 + phase * 1.7) * 0.25;
                    // fract(aPos.y) gives each individual leaf cube's own top-vs-bottom edge a
                    // little extra lean (top sways more) for free - no additional per-vertex CPU
                    // data beyond aWindWeight itself; harmless for the sapling top/base vertices,
                    // whose own aWindWeight (0 or 1) already carries the real base-to-top gradient.
                    float heightFactor = mix(0.4, 1.0, fract(aPos.y));
                    float sway = (primary + secondary) * WIND_AMPLITUDE * aWindWeight * heightFactor * uWindStrength;
                    worldPos.x += sway;
                    worldPos.z += sway * 0.6;
                }

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
