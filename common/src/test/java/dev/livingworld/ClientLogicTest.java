package dev.livingworld;

import dev.livingworld.client.audio.synth.AmbienceParams;
import dev.livingworld.client.audio.synth.AmbienceSynth;
import dev.livingworld.client.audio.synth.OneShotSynth;
import dev.livingworld.client.compat.sodium.SodiumShaders;
import dev.livingworld.client.footprint.DecalStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Client-side logic that has no dependency on a running game: the decal store and the synthesiser. */
class ClientLogicTest {
    private static final float SAND_WIND = 1f, SAND_RAIN = 0.55f, SAND_SNOW = 0.7f;

    private static int addPrint(DecalStore store, int budget, double x, double z, float lifetime, boolean exposed) {
        return store.add(budget, x, 64.0, z, 1f, 0f, 0.16f, 0.3f, DecalStore.BOOT, false, 1f, lifetime, exposed, false,
                0.85f, 0.76f, 0.55f, 0xF000F0, SAND_WIND, SAND_RAIN, SAND_SNOW);
    }

    // ---- footprints ------------------------------------------------------------------------------

    @Test
    void decalsNeverExceedTheBudget() {
        DecalStore store = new DecalStore(64);
        for (int i = 0; i < 500; i++) {
            addPrint(store, 32, i * 2.0, 0.0, 100f, true);
            assertTrue(store.count <= 32, "budget exceeded");
        }
        assertEquals(32, store.count);
        assertEquals(500 - 32, store.evicted);
        // A budget above the capacity is clamped to the capacity.
        for (int i = 0; i < 200; i++) {
            addPrint(store, 10_000, 5000 + i * 2.0, 0.0, 100f, true);
        }
        assertEquals(64, store.count);
    }

    @Test
    void theMostWornDecalIsEvictedFirst() {
        DecalStore store = new DecalStore(8);
        for (int i = 0; i < 4; i++) {
            addPrint(store, 4, i * 3.0, 0.0, 100f, true);
        }
        store.wear[2] = 0.9f;
        double wornX = store.x[2];
        addPrint(store, 4, 100.0, 0.0, 100f, true);
        assertEquals(4, store.count);
        for (int i = 0; i < store.count; i++) {
            assertTrue(store.x[i] != wornX, "the worn decal should have been the one removed");
        }
    }

    @Test
    void printsFadeWithAgeAndTheUserLifetimeScale() {
        DecalStore store = new DecalStore(8);
        addPrint(store, 8, 0.0, 0.0, 10f, false);
        for (int tick = 0; tick < 20 * 5; tick++) {
            store.tick(0.05f, 1f, 0f, 0f, 0f);
        }
        assertEquals(0.5f, store.wear[0], 0.01f, "half the lifetime gone, half worn");
        for (int tick = 0; tick < 20 * 6; tick++) {
            store.tick(0.05f, 1f, 0f, 0f, 0f);
        }
        assertEquals(0, store.count, "a print disappears at the end of its lifetime");

        addPrint(store, 8, 0.0, 0.0, 10f, false);
        for (int tick = 0; tick < 20 * 10; tick++) {
            store.tick(0.05f, 4f, 0f, 0f, 0f);
        }
        assertEquals(0.25f, store.wear[0], 0.01f, "a 4x lifetime setting makes prints last four times as long");
    }

    @Test
    void weatherErasesOnlyExposedPrints() {
        DecalStore store = new DecalStore(8);
        int open = addPrint(store, 8, 0.0, 0.0, 1000f, true);
        int sheltered = addPrint(store, 8, 10.0, 0.0, 1000f, false);
        // Ten seconds of sandstorm-strength wind.
        for (int tick = 0; tick < 200; tick++) {
            store.tick(0.05f, 1f, 0.15f, 0f, 0f);
        }
        assertEquals(1, store.count, "the exposed print is swept away");
        assertTrue(store.x[0] == 10.0 && store.wear[0] < 0.05f, "the print under a roof is untouched");
        assertTrue(open == 0 && sheltered == 1);

        DecalStore snow = new DecalStore(8);
        addPrint(snow, 8, 0.0, 0.0, 1000f, true);
        for (int tick = 0; tick < 200; tick++) {
            snow.tick(0.05f, 1f, 0f, 0f, 0.035f);
        }
        assertTrue(snow.wear[0] > 0.2f && snow.wear[0] < 0.3f, "snowfall fills prints at the configured rate");
    }

    @Test
    void trampledSpotsAreThinnedOut() {
        DecalStore store = new DecalStore(64);
        for (int i = 0; i < 12; i++) {
            addPrint(store, 64, 0.05 * i, 0.0, 1000f, true);
        }
        int fresh = 0;
        for (int i = 0; i < store.count; i++) {
            if (store.wear[i] < 0.5f) {
                fresh++;
            }
        }
        assertTrue(fresh <= 6, "old prints under new ones must fade, but " + fresh + " are still fresh");
        assertEquals(0f, store.wear[store.count - 1], "the newest print is untouched");
    }

    @Test
    void directionsAreNormalisedAndRemovalKeepsRowsIntact() {
        DecalStore store = new DecalStore(8);
        store.add(8, 1.0, 2.0, 3.0, 3f, 4f, 0.1f, 0.2f, DecalStore.PAW, true, 0.7f, 50f, true, true, 0.1f, 0.2f, 0.3f, 7, 0f, 0f, 0f);
        store.add(8, 9.0, 8.0, 7.0, 0f, 0f, 0.3f, 0.4f, DecalStore.HOOF, false, 0.5f, 60f, false, false, 0.4f, 0.5f, 0.6f, 9, 0f, 0f, 0f);
        assertEquals(0.6f, store.dirX[0], 1.0e-6f);
        assertEquals(0.8f, store.dirZ[0], 1.0e-6f);
        assertEquals(1f, store.dirX[1], "a zero direction falls back to +X");
        store.remove(0);
        assertEquals(1, store.count);
        assertEquals(9.0, store.x[0]);
        assertEquals(DecalStore.HOOF, store.sprite[0]);
        assertEquals(0.3f, store.halfWidth[0]);
        assertEquals(9, store.light[0]);
        assertTrue(!store.stain[0] && !store.exposed[0] && !store.mirror[0]);
    }

    // ---- audio -----------------------------------------------------------------------------------

    private static double rms(short[] pcm, int samples) {
        double sum = 0.0;
        for (int i = 0; i < samples; i++) {
            sum += (double) pcm[i] * pcm[i];
        }
        return Math.sqrt(sum / samples) / 32768.0;
    }

    private static int peak(short[] pcm, int samples) {
        int peak = 0;
        for (int i = 0; i < samples; i++) {
            peak = Math.max(peak, Math.abs(pcm[i]));
        }
        return peak;
    }

    private static short[] render(AmbienceSynth synth, int bus, int seconds) {
        int chunk = 6615;
        short[] buffer = new short[chunk * 2];
        short[] all = new short[AmbienceSynth.SAMPLE_RATE * seconds * 2];
        int written = 0;
        while (written + chunk * 2 <= all.length) {
            synth.render(bus, buffer, chunk);
            System.arraycopy(buffer, 0, all, written, chunk * 2);
            written += chunk * 2;
        }
        return all;
    }

    @Test
    void synthIsSilentWithNothingToPlay() {
        AmbienceSynth synth = new AmbienceSynth(1L);
        assertTrue(rms(render(synth, AmbienceSynth.BUS_AMBIENT, 2), 80000) < 1.0e-4, "ambient bus must be silent with all parameters at zero");
        assertTrue(rms(render(synth, AmbienceSynth.BUS_WEATHER, 2), 80000) < 1.0e-4, "weather bus must be silent with all parameters at zero");
    }

    @Test
    void stormIsAudibleButNeverClips() {
        AmbienceSynth synth = new AmbienceSynth(2L);
        AmbienceParams p = synth.params();
        p.openness = 1f;
        p.windSpeed = 1.2f;
        p.windGust = 1f;
        p.foliage = 1f;
        p.rain = 1.3f;
        p.rainOnLeaves = 0.4f;
        p.rainOnStone = 0.3f;
        p.rainOnWater = 0.3f;
        p.birds = 1f;
        p.crickets = 1f;
        synth.triggerThunder(0f, 0f, 1f);
        short[] weather = render(synth, AmbienceSynth.BUS_WEATHER, 6);
        short[] ambient = render(synth, AmbienceSynth.BUS_AMBIENT, 6);
        // Skip the first second while parameters ramp in.
        int skip = AmbienceSynth.SAMPLE_RATE * 2;
        short[] weatherTail = java.util.Arrays.copyOfRange(weather, skip, weather.length);
        short[] ambientTail = java.util.Arrays.copyOfRange(ambient, skip, ambient.length);
        assertTrue(rms(weatherTail, weatherTail.length) > 0.01, "a torrential storm must be clearly audible");
        assertTrue(rms(ambientTail, ambientTail.length) > 0.003, "gale-force wind must be audible");
        int limit = (int) (32768 * 0.72);
        assertTrue(peak(weather, weather.length) <= limit, "weather bus exceeds -3 dBFS");
        assertTrue(peak(ambient, ambient.length) <= limit, "ambient bus exceeds -3 dBFS");
    }

    @Test
    void roofMufflesTheRain() {
        AmbienceSynth outside = new AmbienceSynth(3L);
        outside.params().rain = 0.8f;
        outside.params().rainOnStone = 1f;
        outside.params().openness = 1f;
        AmbienceSynth inside = new AmbienceSynth(3L);
        inside.params().rain = 0.8f;
        inside.params().rainOnStone = 1f;
        inside.params().openness = 0.1f;
        inside.params().roofed = 1f;
        short[] a = render(outside, AmbienceSynth.BUS_WEATHER, 4);
        short[] b = render(inside, AmbienceSynth.BUS_WEATHER, 4);
        assertTrue(highFrequencyEnergy(b) < highFrequencyEnergy(a) * 0.35,
                "rain heard from indoors must lose most of its high-frequency energy");
    }

    /** Energy of the first difference, a cheap high-pass. */
    private static double highFrequencyEnergy(short[] stereo) {
        double sum = 0.0;
        for (int i = AmbienceSynth.SAMPLE_RATE * 2 + 2; i < stereo.length; i += 2) {
            double d = stereo[i] - stereo[i - 2];
            sum += d * d;
        }
        return sum;
    }

    // ---- the Sodium bridge: patching a shader it does not own ---------------------------------------

    /** The lines of Sodium 0.5's terrain vertex shader that the patch hangs on, in their order. */
    private static final String SODIUM_VERTEX = """
            #version 330 core

            #import <sodium:include/chunk_vertex.glsl>
            #import <sodium:include/chunk_material.glsl>

            out vec4 v_Color;

            void main() {
                _vert_init();

                vec3 translation = u_RegionOffset + _get_draw_translation(_draw_id);
                vec3 position = _vert_position + translation;

                gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);

                v_Color = _vert_color * _sample_lightmap(u_LightTex, _vert_tex_light_coord);
                v_TexCoord = _vert_tex_diffuse_coord;
            }
            """;

    private static final String SODIUM_FRAGMENT = """
            #version 330 core

            uniform sampler2D u_BlockTex; // The block texture

            out vec4 fragColor;

            void main() {
                vec4 diffuseColor = texture(u_BlockTex, v_TexCoord, v_MaterialMipBias);

                // Apply per-vertex color
                diffuseColor.rgb *= v_Color.rgb;

                fragColor = _linearFog(diffuseColor, v_FragDistance, u_FogColor, u_FogStart, u_FogEnd);
            }
            """;

    @Test
    void sodiumsVertexShaderGainsTagsSwayAndSeasons() {
        String patched = SodiumShaders.patchVertex(SODIUM_VERTEX);
        assertNotNull(patched);
        // The tag is read and the light coordinate cleaned before anything uses either...
        int tag = patched.indexOf("uint lwTag = (uint(a_LightCoord.x) >> 8u) & 0xFFu;");
        int mask = patched.indexOf("_vert_tex_light_coord = a_LightCoord & ivec2(0xFF);");
        int move = patched.indexOf("lwSway(position, lwWorldPos, lwKind, lwWeight);");
        int project = patched.indexOf("gl_Position = ");
        int shade = patched.indexOf("lwSeasonal(lwTinted.rgb");
        assertTrue(tag > patched.indexOf("_vert_init();") && mask > tag, "the tag is read right after the vertex is unpacked");
        assertTrue(move > mask && move < project, "plants are moved before the vertex is projected");
        assertTrue(shade > project, "and recoloured where Sodium set the colour");
        // ...the declarations sit with Sodium's own imports, each import on a line of its own (Sodium
        // only recognises an import that is the whole line)...
        assertTrue(patched.contains("\n#import <minecraft:include/lw_foliage.glsl>\n"));
        assertTrue(patched.indexOf("uniform vec4 LWSeason;") < patched.indexOf("void main()"));
        // ...and Sodium's own colour line is replaced, not doubled.
        assertFalse(patched.contains("v_Color = _vert_color * _sample_lightmap"));
        assertTrue(patched.contains("v_Color = lwTinted * lwLit;"));
        assertTrue(patched.contains("v_TexCoord = _vert_tex_diffuse_coord;"), "what follows is left alone");
        // Windows line endings in the jar make no difference.
        assertEquals(patched, SodiumShaders.patchVertex(SODIUM_VERTEX.replace("\n", "\r\n")));
    }

    /** The same stage as Embeddium 0.3 has it: the vertex colour is applied in one of two ways. */
    private static final String EMBEDDIUM_FRAGMENT = """
            #version 330 core

            uniform sampler2D u_BlockTex; // The block texture

            out vec4 fragColor;

            void main() {
                vec4 diffuseColor = texture(u_BlockTex, v_TexCoord, v_MaterialMipBias);

            #ifdef USE_VANILLA_COLOR_FORMAT
                // Apply per-vertex color. AO shade is applied ahead of time on the CPU.
                diffuseColor *= v_Color;
            #else
                // Apply per-vertex color
                diffuseColor.rgb *= v_Color.rgb;
            #endif

                fragColor = _linearFog(diffuseColor, v_FragDistance, u_FogColor, u_FogStart, u_FogEnd);
            }
            """;

    @Test
    void embeddiumsFragmentShaderGainsWaterToo() {
        String patched = SodiumShaders.patchFragment(EMBEDDIUM_FRAGMENT);
        assertNotNull(patched, "Embeddium's two ways of applying the vertex colour do not stop the patch");
        int branch = patched.indexOf("if (v_LWWater > 0.5 && LWSky.a > 0.5) {");
        // The water branch comes before the choice between them, so it is taken whichever is compiled in.
        assertTrue(branch > patched.indexOf("vec4 diffuseColor = texture(") && branch < patched.indexOf("#ifdef USE_VANILLA_COLOR_FORMAT"));
        assertEquals(1, patched.split("v_LWWater > 0.5", -1).length - 1, "and only once");
        assertTrue(patched.contains("#else\n    // Apply per-vertex color\n    diffuseColor.rgb *= v_Color.rgb;\n#endif"), "the rest is left alone");
    }

    @Test
    void theTagIsOnlyReadFromAVertexFormatThatHasRoomForIt() {
        String patched = SodiumShaders.patchVertex(SODIUM_VERTEX);
        assertNotNull(patched);
        int compressed = patched.indexOf("#ifdef USE_VERTEX_COMPRESSION");
        int read = patched.indexOf("uint lwTag = (uint(a_LightCoord.x) >> 8u) & 0xFFu;");
        int otherwise = patched.indexOf("#else", compressed);
        int none = patched.indexOf("uint lwTag = 0u;");
        assertTrue(compressed > 0 && read > compressed && otherwise > read && none > otherwise && patched.indexOf("#endif", none) > none);
    }

    @Test
    void sodiumsFragmentShaderGainsWater() {
        String patched = SodiumShaders.patchFragment(SODIUM_FRAGMENT);
        assertNotNull(patched);
        int declarations = patched.indexOf("uniform sampler2D LWScene;");
        int branch = patched.indexOf("if (v_LWWater > 0.5 && LWSky.a > 0.5) {");
        assertTrue(declarations > patched.indexOf("uniform sampler2D u_BlockTex;") && declarations < patched.indexOf("void main()"));
        assertTrue(branch > patched.indexOf("vec4 diffuseColor = texture(") && branch < patched.indexOf("// Apply per-vertex color"),
                "water is shaded after the texture is read and before Sodium's own colouring");
        assertTrue(patched.contains("diffuseColor.rgb *= v_Color.rgb;"), "everything that is not water takes Sodium's path");
    }

    @Test
    void anUnfamiliarSodiumShaderIsLeftAlone() {
        // A version of Sodium whose shader no longer has a line the patch hangs on.
        assertNull(SodiumShaders.patchVertex(SODIUM_VERTEX.replace("_vert_init();", "unpack_vertex();")));
        assertNull(SodiumShaders.patchVertex(SODIUM_VERTEX.replace("v_Color = _vert_color * _sample_lightmap", "v_Color = shade")));
        assertNull(SodiumShaders.patchFragment(SODIUM_FRAGMENT.replace("// Apply per-vertex color", "// tint")));
        // A line that appears twice is as bad as one that is missing: there would be no telling which to use.
        assertNull(SodiumShaders.patchVertex(SODIUM_VERTEX + "\n    _vert_init();\n"));
        // Files that are not the terrain shader, and files that are not Sodium's, come back as they went in.
        assertSame(SODIUM_VERTEX, SodiumShaders.patch("sodium", "include/fog.glsl", SODIUM_VERTEX));
        assertSame(SODIUM_VERTEX, SodiumShaders.patch("minecraft", "blocks/block_layer_opaque.vsh", SODIUM_VERTEX));
        // And when the terrain shader itself is unfamiliar, the source is returned untouched.
        String odd = SODIUM_FRAGMENT.replace("// Apply per-vertex color", "// tint");
        assertSame(odd, SodiumShaders.patch("sodium", "blocks/block_layer_opaque.fsh", odd));
    }

    @Test
    void oneShotsAreNotEmpty() {
        assertTrue(OneShotSynth.splash(0.5f, 1L).length > 1000);
        assertTrue(OneShotSynth.drip(1L).length > 500);
    }
}
