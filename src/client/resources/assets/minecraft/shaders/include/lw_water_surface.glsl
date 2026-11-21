// Living World: how a pixel of water is shaded.
// Shared by the override of the vanilla translucent terrain shader and by the patch applied to Sodium's
// terrain shader, so water looks the same under both renderers.
//
// The file that imports this must already have imported lw_water.glsl and lw_sky.glsl and declared:
//   sampler2D LWScene, LWDepth   the frame just before translucent terrain, and its depth
//   sampler2D LWNoise            the mod's tiling noise texture
//   sampler2D LWColumns          the terrain column cache (B = ground flag + nearness to the waterline)
//   vec4 LWWater                 x = wave clock, yz = ripple drift, w = wave height
//   vec4 LWWind                  xy = wind direction, z = wind speed, w = seconds of real time
//   vec4 LWSun, vec3 LWSunColor  direction and strength of the sun or moon, and its colour
//   vec4 LWSky                   rgb = colour of the sky overhead
//   vec4 LWCloud, LWCloudColor   the cloud layer for its mirror image; LWCloudColor.a = 1 when surf is drawn
//   mat4 LWInvViewProj, LWViewProj
//   vec2 LWScreen                1 / framebuffer size
//   vec4 LWOptions               x = reflection ray steps, y = caustics, z = rain intensity, w = refraction

const float SKY_DEPTH = 0.999999;

vec3 scenePos(vec2 uv, float depth) {
    vec4 p = LWInvViewProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// How near land a column of the terrain cache is: 1 for ground, and for water a value that falls from
// just under 1 beside the land to 0 four blocks out.
float columnLand(vec2 cell) {
    float bits = floor(texture(LWColumns, (cell + 0.5) / 64.0).b * 255.0 + 0.5);
    return bits > 127.5 ? 1.0 : bits / 127.0;
}

// The same, blended between the four nearest columns: a smooth field that follows the coastline instead
// of the block grid, which is what the surf is drawn from.
float landNear(vec2 xz) {
    vec2 g = xz - 0.5;
    vec2 g0 = floor(g);
    vec2 f = g - g0;
    return mix(mix(columnLand(g0), columnLand(g0 + vec2(1.0, 0.0)), f.x),
               mix(columnLand(g0 + vec2(0.0, 1.0)), columnLand(g0 + vec2(1.0, 1.0)), f.x), f.y);
}

// A fixed dither pattern (interleaved gradient noise). It does not change from frame to frame, so a
// reflection is a little grainy but never crawls.
float dither(vec2 fragCoord) {
    return fract(52.9829189 * fract(dot(fragCoord, vec2(0.06711056, 0.00583715))));
}

// Expanding rings from raindrops, as a slope.
vec2 rainRings(vec2 p, float t) {
    vec2 n = vec2(0.0);
    for (int i = 0; i < 2; i++) {
        vec2 q = p * (1.7 + float(i) * 1.1) + float(i) * 17.3;
        vec2 cell = floor(q);
        vec2 f = fract(q) - 0.5;
        float h = hash21(cell + float(i) * 7.1);
        float phase = fract(t * (0.8 + 0.6 * h) + h * 10.0);
        vec2 centre = (vec2(hash21(cell + 3.1), hash21(cell + 9.7)) - 0.5) * 0.5;
        vec2 d = f - centre;
        float r = length(d);
        float radius = phase * 0.45;
        float ring = sin((r - radius) * 42.0) * smoothstep(0.07, 0.0, abs(r - radius)) * (1.0 - phase);
        n += (r > 1.0e-4 ? d / r : vec2(0.0)) * ring;
    }
    return n;
}

// Follows the mirrored view ray through the scene behind the water. rgb = what it hits, a = confidence.
vec4 traceReflection(vec3 origin, vec3 dir, float jitter) {
    int steps = int(LWOptions.x + 0.5);
    if (steps < 1) {
        return vec4(0.0);
    }
    // Clip space is linear along the ray, so the two ends are transformed once and every step is one add.
    vec4 c0 = LWViewProj * vec4(origin, 1.0);
    vec4 cd = LWViewProj * vec4(dir, 0.0);
    float stride = 0.55;
    float before = 0.0;
    float t = stride * (0.4 + jitter);
    for (int i = 0; i < 28; i++) {
        if (i >= steps) {
            break;
        }
        vec4 clip = c0 + cd * t;
        if (clip.w <= 0.0) {
            break;
        }
        vec3 ndc = clip.xyz / clip.w;
        vec2 uv = ndc.xy * 0.5 + 0.5;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
            break;
        }
        float sceneDepth = texture(LWDepth, uv).r;
        if (sceneDepth < ndc.z * 0.5 + 0.5 && sceneDepth < SKY_DEPTH) {
            // The ray is behind something. Count it as a hit only if it is not far behind (otherwise it
            // passed behind a tree trunk or a ledge and carries on).
            float behindBy = length(origin + dir * t) - length(scenePos(uv, sceneDepth));
            if (behindBy < stride * 1.7 + 0.4) {
                float a = before, b = t;
                for (int j = 0; j < 3; j++) {
                    float m = (a + b) * 0.5;
                    vec4 c = c0 + cd * m;
                    vec3 n = c.xyz / c.w;
                    if (texture(LWDepth, n.xy * 0.5 + 0.5).r < n.z * 0.5 + 0.5) {
                        b = m;
                    } else {
                        a = m;
                    }
                }
                vec4 c = c0 + cd * b;
                vec2 hit = c.xy / c.w * 0.5 + 0.5;
                vec2 edge = smoothstep(vec2(0.0), vec2(0.1), hit) * (1.0 - smoothstep(vec2(0.9), vec2(1.0), hit));
                return vec4(texture(LWScene, hit).rgb, edge.x * edge.y);
            }
        }
        before = t;
        stride *= 1.2;
        t += stride;
    }
    return vec4(0.0);
}

// The colour and opacity of a pixel of water, before fog.
//   rel          position relative to the camera
//   world        world position (x and z wrapped)
//   skyLight     sky light reaching the water, 0..1
//   tint         the biome's water colour
//   light        light arriving at the surface (from the lightmap)
//   fogColor     colour of the horizon, for the sky's mirror image
//   streaks      brightness of the animated water texture here (falling and flowing sides)
//   viewRotation world space -> view space, rotation only
vec4 lwShadeWater(vec3 rel, vec3 world, float skyLight, vec3 tint, vec3 light, vec3 fogColor, float streaks, mat3 viewRotation) {
    vec4 shaded;
    float dist = length(rel);
    vec3 view = rel / max(dist, 1.0e-4);
    vec2 suv = gl_FragCoord.xy * LWScreen;
    vec3 face = normalize(cross(dFdx(rel), dFdy(rel)));
    float clock = LWWater.x;
    float open = smoothstep(0.3, 0.9, skyLight);

    // The body of the water: the biome's colour, pulled a little toward clear sea-green.
    vec3 body = mix(tint, vec3(0.05, 0.36, 0.42), 0.38) * light;
    const vec3 absorb = vec3(0.4, 0.105, 0.075);

    vec3 behind = scenePos(suv, texture(LWDepth, suv).r);
    float thickness = max(length(behind) - dist, 0.0);

    if (abs(face.y) > 0.6) {
        // ---- the surface ----
        // How much water one pixel covers here. Ripples smaller than that are faded out by the wave
        // function (they would only stripe and sparkle) and come back as a broader sheen below.
        // Seen at a low angle a pixel covers a long, thin strip of water. Judging by its long side alone
        // would wipe the ripples from everything but the water at your feet; the mean of the two keeps
        // them as the fine streaks distant water really shows.
        float footprint = sqrt(length(dFdx(world.xz)) * length(dFdy(world.xz))) * 1.3;
        // Water under a roof or in a cave feels almost none of the wind.
        vec3 wind = vec3(LWWind.xy, LWWind.z * (0.15 + 0.85 * open));
        vec2 slope = lwWaterSlope(world.xz, clock, LWWater.yz, wind, footprint) * LWWater.w;
        if (LWOptions.z > 0.01 && dist < 40.0) {
            slope += rainRings(world.xz, LWWind.w) * min(LWOptions.z, 1.0) * 0.35 * open;
        }
        float blur = lwWaterBlur(footprint);
        vec3 n = normalize(vec3(-slope.x, 1.0, -slope.y));
        vec2 bend = (viewRotation * vec3(n.x, 0.0, n.z)).xy;

        if (view.y < 0.0) {
            // Seen from above.
            // Refraction: look at the scene behind through the tilted surface, but never drag in
            // something that is in front of the water.
            vec2 ruv = clamp(suv + bend * LWOptions.w * 0.17 * min(thickness, 2.0) / (1.0 + dist * 0.1), vec2(0.002), vec2(0.998));
            vec3 bed = scenePos(ruv, texture(LWDepth, ruv).r);
            if (length(bed) < dist + 0.02) {
                ruv = suv;
                bed = behind;
            }
            float depth = max(length(bed) - dist, 0.0);
            vec3 seen = texture(LWScene, ruv).rgb;

            float facing = clamp(dot(-view, n), 0.0, 1.0);
            float fresnel = 0.02 + 0.98 * pow(1.0 - facing, 5.0);

            // Sunlight focused by the ripples onto the bed (skipped where the surface is mostly mirror).
            if (LWOptions.y > 0.01 && depth > 0.03 && depth < 10.0 && fresnel < 0.7) {
                float web = lwCaustics((bed.xz + world.xz - rel.xz) * 1.5, clock, LWWater.yz * 1.5);
                seen *= 1.0 + web * LWOptions.y * open * (0.25 + LWSun.w) * 0.8 * exp(-depth * 0.22) * smoothstep(0.03, 0.4, depth);
            }

            // What is seen through the water loses its red first and dissolves into the water's own
            // colour with depth, so shallows are clear and the bed fades out a few blocks down.
            vec3 through = seen * exp(-depth * absorb) + body * (1.0 - exp(-depth * 0.2)) * 0.85;

            // Reflection: the scene where the mirrored ray hits it, the sky and its clouds where it does not.
            vec3 r = reflect(view, n);
            r = normalize(vec3(r.x, max(r.y, 0.02), r.z));
            vec3 mirrored = mix(fogColor, LWSky.rgb, smoothstep(0.0, 0.45, r.y));
            float sun = max(dot(r, LWSun.xyz), 0.0);
            // The sky is brighter around the sun, and so is its mirror image: the broad sheen that
            // lies across the water toward a low sun.
            mirrored += LWSunColor * (pow(sun, 7.0) * 0.16 + pow(sun, 40.0) * 0.22) * LWSun.w;
            if (LWCloud.x > 0.001) {
                float reach = (LWCloud.y - rel.y) / r.y;
                float cloud = lwCloudCover2D(LWNoise, rel.xz + r.xz * reach + LWCloud.zw, LWCloud.x);
                // Clouds low on the horizon are lost in the haze, in the mirror as in the sky.
                cloud *= smoothstep(0.03, 0.22, r.y);
                mirrored = mix(mirrored, LWCloudColor.rgb, cloud * 0.92);
            }
            mirrored *= open;
            // Looking steeply down the mirror image is a few percent of the pixel: not worth a ray.
            vec4 hit = fresnel > 0.045 ? traceReflection(rel, r, dither(gl_FragCoord.xy)) : vec4(0.0);
            mirrored = mix(mirrored, hit.rgb, hit.a);
            vec3 surface = mix(through, mirrored, fresnel);

            // The sun or moon on the water: every ripple that happens to face it flashes, which close by
            // is a scatter of hard glints and farther out, where ripples are too small to see, adds up to
            // the long glittering path.
            float glint = pow(sun, mix(900.0, 240.0, blur)) * mix(2.2, 0.8, blur) + pow(sun, mix(120.0, 50.0, blur)) * 0.3;
            vec3 off = r - LWSun.xyz;
            vec2 toward = normalize(LWSun.xz + vec2(1.0e-4, 0.0));
            float across = dot(off.xz, vec2(-toward.y, toward.x));
            float along = length(vec2(dot(off.xz, toward), off.y));
            float sparkle = smoothstep(0.45, 0.9, lwNoise(world.xz * 3.1 + vec2(clock * 1.7, -clock * 1.4)));
            float path = exp(-(across * across * 420.0 + along * along * 22.0)) * blur * (0.2 + 2.4 * sparkle) * 0.6;
            surface += LWSunColor * (glint + path) * LWSun.w * open * (1.0 - hit.a);

            // The shore. Where the bed comes up to meet the surface the water turns white: a line of
            // foam lies along the very edge, and in from it run bands of foam that travel toward the
            // land the way small waves break up a beach, broader and reaching farther out when the
            // wind is up.
            float lap = 0.5 + 0.5 * sin(LWWind.w * 1.3 + lwNoise(world.xz * 0.9) * 9.0);
            float foam = (1.0 - smoothstep(0.04, 0.26 + 0.14 * lap, thickness)) * 0.3;
            if (LWCloudColor.a > 0.5 && dist < 34.0) {
                // Known only near the camera, where the terrain column cache reaches.
                float near = landNear(world.xz) * (1.0 - smoothstep(26.0, 34.0, dist));
                if (near > 0.02) {
                    float blow = clamp(wind.z, 0.0, 1.4);
                    float patchy = lwNoise(world.xz * 1.7 + vec2(clock * 0.21, -clock * 0.17));
                    float lace = lwNoise(world.xz * 5.3 - vec2(clock * 0.4, clock * 0.33));
                    // Constant phase moves toward the land as the clock runs.
                    float run = near * 9.0 - clock * (1.0 + 0.7 * blow) + lwNoise(world.xz * 0.35) * 5.0;
                    float band = smoothstep(0.55, 0.95, 0.5 + 0.5 * sin(run));
                    float zone = smoothstep(0.3 - 0.22 * blow, 0.85, near);
                    float surf = band * zone * smoothstep(0.25, 0.7, patchy + lace * 0.35) * (0.08 + 0.92 * blow) * open;
                    float edge = smoothstep(0.86, 0.94, near);
                    foam = max(foam, clamp(edge * 0.5 + surf * 0.85, 0.0, 0.9) * (0.6 + 0.4 * lace));
                }
            }
            surface = mix(surface, vec3(0.92, 0.96, 1.0) * light, foam);
            shaded = vec4(surface, smoothstep(0.0, 0.1, thickness));
        } else {
            // Seen from below: the world above through a rippling window, and beyond the critical
            // angle the surface mirrors the water underneath instead.
            vec2 ruv = clamp(suv + bend * LWOptions.w * 0.14, vec2(0.002), vec2(0.998));
            float window = smoothstep(0.42, 0.64, view.y + (slope.x + slope.y) * 1.6);
            vec3 above = texture(LWScene, ruv).rgb;
            vec3 mirror = body * 0.8 + vec3(0.02, 0.07, 0.09) * light;
            shaded = vec4(mix(mirror, above * (1.15 + 0.35 * open), window), 1.0);
        }
    } else {
        // ---- falling and flowing sides: a moving, see-through sheet with bright streaks ----
        float t = LWWind.w;
        vec2 ruv = clamp(suv + vec2(sin(world.y * 5.0 + t * 6.0), cos(world.y * 3.0 + t * 4.0)) * 0.004 * LWOptions.w, vec2(0.002), vec2(0.998));
        vec3 seen = texture(LWScene, ruv).rgb;
        float depth = min(thickness, 4.0);
        vec3 sheet = seen * exp(-depth * absorb) * 0.92 + body * (0.16 + 0.3 * (1.0 - exp(-depth * 0.3)));
        sheet += light * smoothstep(0.62, 0.95, streaks) * 0.3;
        shaded = vec4(sheet, smoothstep(0.0, 0.08, thickness + 0.05));
    }
    return shaded;
}
