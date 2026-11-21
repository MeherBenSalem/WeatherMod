package dev.livingworld.client.compat.sodium;

import dev.livingworld.LivingWorld;

/**
 * Patches the source of Sodium's terrain shader (Sodium 0.5.x) as it is loaded, so that it does what the
 * mod's overrides of the vanilla terrain shaders do: sway vegetation, recolour foliage with the season
 * and shade water. Plain string work with no reference to Sodium's classes, so it can be unit-tested.
 *
 * <p>The patch is anchored on a handful of lines of Sodium's shader. If any of them is missing from a
 * stage, that stage is returned untouched; the vertex stage alone is still useful (sway and seasons),
 * the fragment stage alone is harmless.
 *
 * <p>How vertices are told apart: Sodium's vertex format has no spare attribute, but its light
 * coordinate is two 16-bit numbers that only ever hold 0..240. The mod writes a tag into the high byte
 * of the first (see {@code SodiumBridge}); the patched shader reads the tag and masks it off before the
 * coordinate is used.
 *
 * <pre>
 *   0x80  vegetation, with 0x10 = moves as a whole (leaves) and the low four bits = how far along the plant
 *   0x40  water
 *   0x20  the face looks at the sky (where snow settles)
 * </pre>
 */
public final class SodiumShaders {
    public static final int TAG_VEGETATION = 0x80;
    public static final int TAG_WATER = 0x40;
    public static final int TAG_UP = 0x20;
    public static final int TAG_WHOLE = 0x10;

    static final String VERTEX = "blocks/block_layer_opaque.vsh";
    static final String FRAGMENT = "blocks/block_layer_opaque.fsh";

    // ---- the lines of Sodium's shader the patch hangs on -------------------------------------------
    private static final String V_IMPORTS = "#import <sodium:include/chunk_material.glsl>";
    private static final String V_INIT = "    _vert_init();";
    private static final String V_POSITION = "    vec3 position = _vert_position + translation;";
    private static final String V_COLOR = "    v_Color = _vert_color * _sample_lightmap(u_LightTex, _vert_tex_light_coord);";
    private static final String F_SAMPLER = "uniform sampler2D u_BlockTex;";
    private static final String F_COLOR = "    // Apply per-vertex color";

    private static final String V_DECLARATIONS = """

            // ---- Living World: vegetation sway, seasonal foliage, water (see SodiumShaders) ----
            #import <minecraft:include/lw_water.glsl>
            uniform vec4 LWSway;
            uniform vec4 LWDrift;
            uniform vec4 LWBend;
            uniform vec4 LWSeason;
            uniform vec3 LWCamera;
            uniform vec4 LWWater;
            uniform vec4 LWWind;
            #import <minecraft:include/lw_foliage.glsl>
            out float v_LWWater;
            out float v_LWSky;
            out vec3 v_LWRel;
            out vec3 v_LWWorld;
            out vec3 v_LWTint;
            out vec3 v_LWLight;
            """;

    private static final String V_TAG = """

                // Living World: the high byte of the block-light coordinate carries a tag. Read it, then
                // give the light coordinate back its own eight bits.
                uint lwTag = (uint(a_LightCoord.x) >> 8u) & 0xFFu;
                _vert_tex_light_coord = a_LightCoord & ivec2(0xFF);
            """;

    private static final String V_MOVE = """

                // Living World: vegetation moves with the wind, and the water's surface rides a slow swell.
                vec3 lwWorldPos = position + LWCamera;
                bool lwLeaf = false;
                v_LWWater = 0.0;
                if ((lwTag & 0x80u) != 0u) {
                    int lwKind = int((lwTag >> 4u) & 1u);
                    float lwWeight = float(lwTag & 15u) / 15.0;
                    lwLeaf = lwKind == 1 && (lwTag & 15u) == 15u;
                    if (lwWeight > 0.0) {
                        lwSway(position, lwWorldPos, lwKind, lwWeight);
                    }
                } else if ((lwTag & 0x40u) != 0u) {
                    v_LWWater = 1.0;
                    if (LWWater.w > 0.001) {
                        // Only the surface moves: the bottom of a water block, and water with more water above
                        // it, sit on whole block heights and stay put so nothing ever opens a gap.
                        float lwHeight = fract(_vert_position.y);
                        float lwSurface = step(0.05, lwHeight) * step(lwHeight, 0.95);
                        float lwOpen = clamp(float(_vert_tex_light_coord.y) / 240.0, 0.0, 1.0);
                        position.y += lwSwell(lwWorldPos.xz, LWWater.x) * 0.03 * LWWater.w * (0.45 + 0.9 * min(LWWind.z, 1.4))
                                * (0.35 + 0.65 * lwOpen) * lwSurface;
                    }
                }
            """;

    private static final String V_SHADE = """
                // Living World: a biome tint is green-dominant; it follows the season. Everything else is left alone.
                vec4 lwTinted = _vert_color;
                if (dot(LWSeason, vec4(1.0)) > 0.001 && lwTinted.g > 0.03 && lwTinted.r < lwTinted.g * 0.93 && lwTinted.b < lwTinted.g * 0.93) {
                    lwTinted.rgb = clamp(lwSeasonal(lwTinted.rgb, lwLeaf, lwWorldPos, (lwTag & 0x20u) != 0u ? 1.0 : 0.0), 0.0, 2.0);
                }
                vec4 lwLit = _sample_lightmap(u_LightTex, _vert_tex_light_coord);
                v_Color = lwTinted * lwLit;
                v_LWSky = clamp(float(_vert_tex_light_coord.y) / 240.0, 0.0, 1.0);
                v_LWRel = position;
                v_LWWorld = lwWorldPos;
                v_LWTint = _vert_color.rgb;
                v_LWLight = lwLit.rgb;
            """;

    private static final String F_DECLARATIONS = """

            // ---- Living World: water (see SodiumShaders) ----
            #import <minecraft:include/lw_water.glsl>
            #import <minecraft:include/lw_sky.glsl>
            uniform sampler2D LWScene;
            uniform sampler2D LWDepth;
            uniform sampler2D LWNoise;
            uniform sampler2D LWColumns;
            uniform vec4 LWWater;
            uniform vec4 LWWind;
            uniform vec4 LWSun;
            uniform vec3 LWSunColor;
            uniform vec4 LWSky;
            uniform vec4 LWCloud;
            uniform vec4 LWCloudColor;
            uniform mat4 LWInvViewProj;
            uniform mat4 LWViewProj;
            uniform mat4 LWView;
            uniform vec2 LWScreen;
            uniform vec4 LWOptions;
            in float v_LWWater;
            in float v_LWSky;
            in vec3 v_LWRel;
            in vec3 v_LWWorld;
            in vec3 v_LWTint;
            in vec3 v_LWLight;
            #import <minecraft:include/lw_water_surface.glsl>
            """;

    private static final String F_WATER = """
                // Living World: water is shaded as water (LWSky.a is 0 when the option is off).
                if (v_LWWater > 0.5 && LWSky.a > 0.5) {
                    float lwStreaks = dot(diffuseColor.rgb, vec3(0.3333));
                    vec4 lwShaded = lwShadeWater(v_LWRel, v_LWWorld, v_LWSky, v_LWTint, v_LWLight, u_FogColor.rgb, lwStreaks, mat3(LWView));
                    fragColor = _linearFog(lwShaded, v_FragDistance, u_FogColor, u_FogStart, u_FogEnd);
                    return;
                }

            """;

    private SodiumShaders() {
    }

    /**
     * Returns the patched source of one of Sodium's shader files, or the source unchanged if it is not
     * one the mod patches or does not look the way the patch expects.
     */
    public static String patch(String namespace, String path, String source) {
        if (source == null || !"sodium".equals(namespace)) {
            return source;
        }
        if (VERTEX.equals(path)) {
            String patched = patchVertex(source);
            if (patched == null) {
                giveUp(path);
                return source;
            }
            SodiumState.vertexPatched = true;
            return patched;
        }
        if (FRAGMENT.equals(path)) {
            String patched = patchFragment(source);
            if (patched == null) {
                LivingWorld.LOGGER.warn("Sodium's {} does not look as expected; water keeps Sodium's look", path);
                return source;
            }
            SodiumState.fragmentPatched = true;
            return patched;
        }
        return source;
    }

    private static void giveUp(String path) {
        if (!SodiumState.gaveUp) {
            SodiumState.gaveUp = true;
            LivingWorld.LOGGER.warn("Sodium's {} does not look as expected (a version this mod does not know); "
                    + "water shading, vegetation sway and seasonal foliage stay off under Sodium", path);
        }
    }

    /** The vertex stage with the mod's code added, or null if an anchor is missing. */
    public static String patchVertex(String source) {
        String s = source.replace("\r\n", "\n");
        if (!once(s, V_IMPORTS) || !once(s, V_INIT) || !once(s, V_POSITION) || !once(s, V_COLOR)) {
            return null;
        }
        s = s.replace(V_IMPORTS, V_IMPORTS + V_DECLARATIONS);
        s = s.replace(V_INIT, V_INIT + V_TAG);
        s = s.replace(V_POSITION, V_POSITION + V_MOVE);
        s = s.replace(V_COLOR + "\n", V_SHADE);
        return s;
    }

    /** The fragment stage with the water branch added, or null if an anchor is missing. */
    public static String patchFragment(String source) {
        String s = source.replace("\r\n", "\n");
        int sampler = s.indexOf(F_SAMPLER);
        if (sampler < 0 || s.indexOf(F_SAMPLER, sampler + 1) >= 0 || !once(s, F_COLOR)) {
            return null;
        }
        int lineEnd = s.indexOf('\n', sampler);
        if (lineEnd < 0) {
            return null;
        }
        s = s.substring(0, lineEnd + 1) + F_DECLARATIONS + s.substring(lineEnd + 1);
        s = s.replace(F_COLOR, F_WATER + F_COLOR);
        return s;
    }

    private static boolean once(String s, String needle) {
        int first = s.indexOf(needle);
        return first >= 0 && s.indexOf(needle, first + 1) < 0;
    }
}
