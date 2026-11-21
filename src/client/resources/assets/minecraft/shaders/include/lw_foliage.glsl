// Living World: what the terrain vertex shader does to foliage.
// Shared by the override of the vanilla cut-out terrain shader and by the patch applied to Sodium's
// terrain shader.
//
// The file that imports this must already have imported lw_water.glsl (for its noise) and declared:
//   vec4 LWSway     xy = wind direction, z = sway strength (0 = off), w = time in seconds
//   vec4 LWDrift    xy = how far the wind has carried its gusts, in blocks
//   vec4 LWBend     xyz = camera-relative feet position of the player, w = bending strength (0 = off)
//   vec4 LWSeason   x = autumn colour, y = winter bareness, z = spring freshness, w = snow lying on the ground

// Colour of foliage for the time of year. c is the biome tint as the game baked it (already shaded by
// ambient occlusion), leaf tells tree canopies from grass and other low plants, up is 1 on faces that look
// at the sky (where snow settles).
vec3 lwSeasonal(vec3 c, bool leaf, vec3 wp, float up) {
    float bright = c.g;
    float rg = c.r / c.g, bg = c.b / c.g;
    // Spruce and other conifers carry one fixed blue-green (red = blue): they stay as they are, apart
    // from the snow that settles on them.
    bool evergreen = abs(rg - bg) < 0.04 && rg > 0.55 && rg < 0.72;
    // Slow variation from tree to tree and a faster one from branch to branch, so a wood turns unevenly.
    float tree = lwNoise(wp.xz / 11.0 + wp.y * 0.05);
    float twig = lwNoise(wp.xz / 2.3 + wp.y * 0.31 + 17.0);
    float v = tree * 0.72 + twig * 0.28;
    vec3 col = c;
    if (!evergreen) {
        if (LWSeason.z > 0.001) {
            // Spring: pale, bright new growth, and here and there a tree in blossom.
            vec3 fresh = c * vec3(1.1, 1.13, 0.74) + vec3(0.02, 0.05, 0.0) * bright;
            col = mix(col, fresh, LWSeason.z * 0.7);
            if (leaf) {
                float blossom = smoothstep(0.68, 0.78, tree) * smoothstep(0.35, 0.6, twig);
                col = mix(col, vec3(1.0, 0.8, 0.86) * bright * 1.12, blossom * LWSeason.z * 0.75);
            }
        }
        if (LWSeason.x > 0.001) {
            if (leaf) {
                // Autumn: each tree turns in its own time, to gold, to orange or to red.
                vec3 gold = vec3(1.0, 0.8, 0.2), orange = vec3(1.0, 0.52, 0.12), red = vec3(0.92, 0.25, 0.13);
                vec3 turned = v < 0.5 ? mix(gold, orange, smoothstep(0.3, 0.5, v)) : mix(orange, red, smoothstep(0.5, 0.72, v));
                // Birch always goes yellow.
                if (abs(rg - 0.766) < 0.03 && abs(bg - 0.509) < 0.03) {
                    turned = mix(gold, vec3(0.93, 0.66, 0.16), twig);
                }
                float when = tree * 0.55;
                col = mix(col, turned * bright * 1.3, smoothstep(when, when + 0.45, LWSeason.x));
            } else {
                // Grass yellows and dries.
                col = mix(col, vec3(0.86, 0.74, 0.32) * bright * (0.96 + 0.12 * twig), LWSeason.x * 0.8);
            }
        }
        if (LWSeason.y > 0.001) {
            // Winter: what is left on the trees is brown and thin, the grass is bleached.
            vec3 dormant = leaf ? vec3(0.7, 0.53, 0.36) * (0.9 + 0.25 * twig) : vec3(0.8, 0.7, 0.45);
            col = mix(col, dormant * bright, LWSeason.y * (leaf ? 0.88 : 0.8));
        }
    }
    if (LWSeason.w > 0.001) {
        // Lying snow: on whatever faces the sky, patchy until the cover is deep.
        float facing = up > 0.5 ? 1.0 : (leaf ? 0.5 : 0.12);
        float patch = smoothstep(1.0 - LWSeason.w * 1.15, 1.2 - LWSeason.w * 1.15, 0.25 + lwNoise(wp.xz / 5.0) * 0.5 + twig * 0.4);
        // Leaf and grass textures are mid-grey, so white snow needs a tint well above 1.
        col = mix(col, vec3(1.9, 1.95, 2.0) * bright, patch * facing);
    }
    return col;
}

// Moves a vertex of vegetation with the wind and aside from the player.
//   pos    position relative to the camera, changed in place
//   world  world position (x and z wrapped), as it was before any movement
//   kind   0 = rooted at its base (grass, flowers), 1 = moves as a whole (leaves, vines)
//   w      how far along the plant the vertex is, 0 at the root .. 1 at the tip
void lwSway(inout vec3 pos, vec3 world, int kind, float w) {
    if (LWSway.z > 0.001) {
        float t = LWSway.w;
        float s = LWSway.z;
        // Gusts are a pattern carried along by the wind (never a wave aimed along its current
        // direction, which would swing about the world origin whenever the wind veers), so they
        // visibly roll across fields and canopies and stop when the air does.
        vec2 q = world.xz - LWDrift.xy;
        float gust = 0.5 + 0.25 * (sin(q.x * 0.31 + sin(q.y * 0.17) * 1.6) + sin(q.y * 0.27 + sin(q.x * 0.19) * 1.6));
        // The small restless movement on top of the gusts. Grass shivers; a canopy only breathes:
        // leaf blocks are full of cut-out holes, and moving them quickly makes their edges sparkle.
        float flutter = kind == 0
                ? sin(t * 3.9 + world.x * 2.3 + world.z * 1.9) + 0.5 * sin(t * 6.1 + world.z * 3.7 - world.x * 1.3)
                : sin(t * 1.7 + world.x * 0.9 + world.z * 0.7) + 0.5 * sin(t * 2.6 + world.z * 1.3 - world.x * 0.5);
        // Beyond a few dozen blocks the movement is smaller than a pixel and only makes foliage
        // shimmer, so it is faded out.
        float near = 1.0 - smoothstep(26.0, 60.0, length(pos));
        vec2 d = (LWSway.xy * (0.02 + 0.14 * s) * (0.3 + gust) + vec2(flutter, flutter * 0.7) * 0.01 * (0.4 + s)) * near;
        flutter *= near;
        if (kind == 0) {
            // Rooted plants: anchored at the base, bending progressively toward the tip.
            float bend = w * w;
            pos.xz += d * bend;
            pos.y -= length(d) * bend * 0.35;
        } else {
            // Leaves, vines and stacked plants: a small displacement that depends only on world
            // position, so neighbouring blocks stay joined.
            pos.xz += d * 0.3 * w;
            pos.y += flutter * 0.006 * w * (0.4 + s);
        }
    }
    if (kind == 0 && LWBend.w > 0.001) {
        vec3 away = pos - LWBend.xyz;
        float r = length(away.xz);
        float push = (1.0 - smoothstep(0.25, 1.1, r)) * (1.0 - smoothstep(0.7, 1.7, abs(away.y - 0.6))) * w * LWBend.w;
        pos.xz += (r > 0.001 ? away.xz / r : vec2(0.0)) * push * 0.3;
        pos.y -= push * 0.2;
    }
}
