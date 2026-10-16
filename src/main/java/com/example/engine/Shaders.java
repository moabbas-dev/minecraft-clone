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
            // The 16 x 16 lightmap (column = block light, row = sky light), see game.LightmapColors.
            uniform sampler2D uLightmap;
            // How a draw is lit: 0 terrain and particles (lightmap), 1 unlit (sky objects), 2 entity in the world,
            // 3 first-person held item, 4 inventory preview (full-bright lightmap). See the fragment shader.
            uniform int uLitMode;

            out vec2 vUv;
            out vec3 vLight;
            out vec3 vPos;
            out float vDistance;
            out vec3 vTint;
            out float vFan;

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
                // Vanilla samples the lightmap per vertex (sample_lightmap.glsl): the light levels (aSkyShade and
                // aBlockLight carry level / 15, fractional where smooth lighting averaged four cells) pick a point between
                // the texel centres of the 16 x 16 map, and the linear filter blends the neighbours.
                vec3 light = vec3(1.0);
                vFan = uLitMode == 5 ? aSkyShade : 1.0; // mode 5: the sunrise fan, whose alpha rides in the sky-light slot
                if (uLitMode != 1 && uLitMode != 5) {
                    vec2 level = uLitMode == 4 ? vec2(15.0) : clamp(vec2(aBlockLight, aSkyShade) * 15.0, 0.0, 15.0);
                    light = textureLod(uLightmap, (level + 0.5) / 16.0, 0.0).rgb;
                }
                vLight = light;
                vPos = uLitMode == 2 ? worldPos.xyz : (uView * worldPos).xyz;
                vDistance = length(worldPos.xyz - uCameraPos);
                vTint = aTint;
            }
            """;

    public static final String CHUNK_FRAGMENT = """
            #version 330 core
            in vec2 vUv;
            in vec3 vLight;
            in vec3 vPos;
            in float vDistance;
            in vec3 vTint;
            in float vFan;

            uniform sampler2D uTexture;
            uniform float uAlpha;
            // The alpha test of a cutout pass (0.5 for terrain, as RenderPipelines' ALPHA_CUTOUT_THRESHOLD_CUTOUT_TERRAIN); 0 = none.
            // A cutout fragment is written opaque, so the mip-scaled alpha at a distance never blends the leaves into the sky.
            uniform float uCutoff;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            // The second, environmental fog (rain thickens it): a linear fog from uEnvFogStart to uEnvFogEnd, scaled by uEnvFogStrength.
            // A draw whose uFogEnd is the unfogged sentinel (>= 100000) gets neither.
            uniform float uEnvFogStart;
            uniform float uEnvFogEnd;
            uniform float uEnvFogStrength;
            uniform int uLitMode;

            out vec4 FragColor;

            // Vanilla's entity lighting (shaders/include/light.glsl): two directional lights, 60% of their sum on top of 40% ambient.
            float entityLight(vec3 n, vec3 l0, vec3 l1) {
                return min(1.0, (max(0.0, dot(n, l0)) + max(0.0, dot(n, l1))) * 0.6 + 0.4);
            }

            void main() {
                vec4 texColor = texture(uTexture, vUv);
                if (texColor.a < max(uCutoff, 0.05)) discard;
                float directional = 1.0;
                if (uLitMode >= 2) {
                    // The face normal from the screen-space derivatives of the position (flat faces only, which is all
                    // the entity models are): it points toward the viewer for any face that is drawn.
                    vec3 p = vPos;
                    if (uLitMode == 4) p.y = -p.y; // the preview works in pixels with y down
                    vec3 c = cross(dFdx(p), dFdy(p));
                    if (dot(c, c) > 1e-18) {
                        vec3 n = normalize(c);
                        if (uLitMode == 4) {
                            directional = entityLight(n, normalize(vec3(0.2, 1.0, 1.0)), normalize(vec3(-0.2, 1.0, 0.0)));
                        } else {
                            directional = entityLight(n, normalize(vec3(0.2, 1.0, -0.7)), normalize(vec3(-0.2, 1.0, 0.7)));
                        }
                    }
                }
                // vTint is (1,1,1) - a no-op multiply - for every face except grass-top/leaves,
                // which carry the real per-column biome colormap color baked in at mesh time; terrain also folds the
                // cardinal shade and the ambient occlusion into it.
                vec3 shaded = texColor.rgb * vTint * vLight * directional;
                float fogFactor = clamp((vDistance - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
                if (uFogEnd < 100000.0 && uEnvFogEnd > uEnvFogStart) {
                    float envFog = clamp((vDistance - uEnvFogStart) / (uEnvFogEnd - uEnvFogStart), 0.0, 1.0) * uEnvFogStrength;
                    fogFactor = max(fogFactor, envFog);
                }
                vec3 finalColor = mix(shaded, uFogColor, fogFactor);
                FragColor = vec4(finalColor, (uCutoff > 0.0 ? 1.0 : texColor.a) * uAlpha * vFan);
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
