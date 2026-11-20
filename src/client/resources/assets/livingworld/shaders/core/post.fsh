#version 150

// Living World atmosphere pass.
// One full-screen pass over the finished world image (before the hand and HUD are drawn) that adds
// everything Minecraft's fixed fog cannot: heat shimmer, wet ground, puddles, fresh snow, an overcast
// cloud deck, distance haze, low-lying mist, light shafts, eye adaptation and the colour grade.
// Every term is driven by the shared environment state, and every term costs nothing when its strength is 0.

uniform sampler2D DiffuseSampler;   // world colour
uniform sampler2D DepthSampler;     // world depth
uniform sampler2D NoiseSampler;     // tiling noise: R smooth, G fbm, B cellular, A white
uniform sampler2D ColumnSampler;    // 64x64 wrap-around terrain columns: RG = rain surface height, B = solid, A = puddle capacity

uniform mat4 InvViewProj;   // clip space -> camera-relative world space
uniform vec3 CamPos;        // camera position; x and z wrapped to 4096 blocks
uniform vec4 TimeWind;      // x = seconds (wrapped), yz = wind direction, w = wind speed
uniform vec4 Haze;          // rgb = colour, a = extinction per block
uniform vec4 Mist;          // rgb = colour, a = density at the mist base
uniform vec4 MistShape;     // x = camera height above the mist base, y = falloff per block, z = max mist opacity, w = max haze opacity
uniform vec4 Heat;          // x = heat shimmer, y = lightning flash, z = exposure, w = saturation
uniform vec4 Grade;         // rgb = tint, a = contrast
uniform vec4 Wet;           // x = wet darkening, y = puddle level, z = rain intensity, w = snow cover
uniform vec4 Sky;           // rgb = colour reflected by puddles, a = overcast cover
uniform vec4 Cloud;         // rgb = cloud deck colour, a = storm darkness
uniform vec4 Sun;           // xy = sun position on screen (uv), z = light shaft strength, w = 1 if in front of the camera
uniform vec4 SunColor;      // rgb = shaft colour, a = aspect ratio
uniform sampler2D BloomSampler;     // blurred bright parts of the frame, quarter resolution
uniform vec3 SunDir;        // unit direction toward the sun (or the moon at night), world space
uniform vec4 Glow;          // rgb = colour of the glow around the sun or moon, a = strength
uniform vec4 Look;          // x = bloom, y = film response, z = vignette, w = vibrance
uniform vec4 Under;         // x = 1 when the camera is under water, y = caustics, z = view wobble, w = daylight reaching the water

in vec2 texCoord;

out vec4 fragColor;

const float SKY_DEPTH = 0.999999;

vec3 viewRay(vec2 uv, float depth) {
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// 1 where the surface at height y in this terrain column is the one precipitation lands on, 0 if covered.
float exposure(vec2 cell, float y) {
    vec4 column = texture(ColumnSampler, (cell + 0.5) / 64.0);
    float top = (column.r * 255.0 + column.g * 65280.0) / 16.0 - 64.0;
    return 1.0 - smoothstep(0.12, 0.5, top - y);
}

// Slope of a rippling water surface (four octaves of the wave sum the water shader uses).
vec2 waveSlope(vec2 p, float t, vec2 wind) {
    vec2 slope = vec2(0.0);
    vec2 dir = wind;
    float freq = 0.6, amp = 1.0, speed = 1.05, total = 0.0;
    for (int i = 0; i < 4; i++) {
        float x = dot(dir, p) * freq - t * speed;
        float wave = exp(sin(x) - 1.0);
        float d = wave * cos(x);
        slope += dir * (d * amp * freq);
        p -= dir * (d * amp * 0.22);
        total += amp;
        dir = vec2(dir.x * 0.36 - dir.y * 0.933, dir.x * 0.933 + dir.y * 0.36);
        freq *= 1.45;
        amp *= 0.74;
        speed *= 1.13;
    }
    return slope / total;
}

// Expanding rings from raindrops landing on standing water, as a surface normal offset.
vec2 rippleNormal(vec2 p, float t) {
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

void main() {
    vec2 uv = texCoord;
    float depth = texture(DepthSampler, uv).r;
    vec3 rel = viewRay(uv, depth);
    float dist = length(rel);
    vec3 dir = rel / max(dist, 1.0e-4);
    bool sky = depth >= SKY_DEPTH;
    float t = TimeWind.x;
    bool under = Under.x > 0.5;

    // ---- heat shimmer: rising, rippling refraction, strongest toward the horizon and far away ----
    vec2 suv = uv;
    if (under) {
        // Under water the whole view sways slowly, more toward the edges of the screen.
        vec2 centred = uv - 0.5;
        vec2 sway = vec2(sin(uv.y * 13.0 + t * 1.7) + sin(uv.y * 5.0 - t * 1.1),
                         cos(uv.x * 11.0 + t * 1.3) + cos(uv.x * 4.0 + t * 0.9));
        suv = clamp(uv + sway * 0.0016 * Under.z * (0.4 + 2.4 * dot(centred, centred)), vec2(0.001), vec2(0.999));
    } else if (Heat.x > 0.002) {
        float horizon = 1.0 - smoothstep(0.02, 0.4, abs(dir.y));
        float far = sky ? 1.0 : smoothstep(5.0, 38.0, dist);
        vec2 n1 = texture(NoiseSampler, uv * vec2(2.5, 7.0) + vec2(t * 0.03, -t * 0.42)).rg - 0.5;
        vec2 n2 = texture(NoiseSampler, uv * vec2(6.0, 15.0) + vec2(-t * 0.05, -t * 0.7)).gr - 0.5;
        vec2 offset = (n1 + n2 * 0.5) * vec2(0.0035, 0.006) * Heat.x * (0.25 + 0.75 * horizon) * far;
        // Never drag nearby objects into the distortion.
        float d2 = texture(DepthSampler, uv + offset).r;
        float dist2 = d2 >= SKY_DEPTH ? 1000.0 : length(viewRay(uv + offset, d2));
        offset *= smoothstep(3.0, 9.0, dist2);
        suv = clamp(uv + offset, vec2(0.001), vec2(0.999));
    }
    vec3 col = texture(DiffuseSampler, suv).rgb;

    // ---- under water: sunlight focused by the waves dances over everything below the surface ----
    if (under && !sky && Under.y > 0.002 && dist < 40.0) {
        vec3 nrm = normalize(cross(dFdx(rel), dFdy(rel)));
        if (dot(nrm, dir) > 0.0) {
            nrm = -nrm;
        }
        // The same web of light the water shader draws on the bed when it is seen from above.
        vec3 wp = rel + vec3(CamPos.x, CamPos.y, CamPos.z);
        vec2 cs = waveSlope((wp.xz + wp.y * 0.37) * 1.7 + 31.7, t * 0.85, TimeWind.yz);
        float web = pow(max(0.0, 1.0 - abs(cs.x - cs.y) * 7.5), 3.0);
        float facing = clamp(nrm.y * 0.65 + 0.5, 0.12, 1.0);
        float light = web * Under.y * Under.w * facing * exp(-dist * 0.055);
        col += (col * 0.85 + vec3(0.02, 0.05, 0.06)) * light;
    }
    if (under) {
        // The bright window of the surface overhead.
        col += vec3(0.08, 0.15, 0.17) * pow(max(dir.y, 0.0), 2.0) * Under.w * 0.6;
    }

    // ---- wet ground, puddles and fresh snow on surfaces the weather can actually reach ----
    if (!under && !sky && dist < 44.0 && (Wet.x + Wet.y + Wet.w) > 0.002) {
        vec3 nrm = normalize(cross(dFdx(rel), dFdy(rel)));
        if (dot(nrm, dir) > 0.0) {
            nrm = -nrm;
        }
        float upFacing = smoothstep(0.75, 0.95, nrm.y);
        if (upFacing > 0.0) {
            vec3 wp = rel + CamPos;
            vec4 column = texture(ColumnSampler, (floor(wp.xz) + 0.5) / 64.0);
            // Only the surface that the rain lands on: not floors under a roof, not cave floors.
            // Exposure is blended between the four nearest columns so the edge of a roof or canopy
            // leaves a soft wet/dry boundary instead of a block-shaped one.
            vec2 g = wp.xz - 0.5;
            vec2 g0 = floor(g);
            vec2 gf = g - g0;
            float exposed = mix(mix(exposure(g0, wp.y), exposure(g0 + vec2(1.0, 0.0), wp.y), gf.x),
                                mix(exposure(g0 + vec2(0.0, 1.0), wp.y), exposure(g0 + vec2(1.0, 1.0), wp.y), gf.x), gf.y);
            float amount = (1.0 - smoothstep(30.0, 42.0, dist)) * upFacing * exposed * column.b;
            if (amount > 0.001) {
                // Damp surfaces are darker and a touch more saturated.
                col = mix(col, col * (0.6 + 0.3 * col), Wet.x * amount);

                float level = Wet.y * column.a;
                if (level > 0.01) {
                    float pn = texture(NoiseSampler, wp.xz / 19.0).r * 0.6 + texture(NoiseSampler, wp.xz / 6.3 + 0.37).g * 0.4;
                    float threshold = mix(0.2, 0.56, level);
                    float puddle = smoothstep(threshold, threshold - 0.05, pn) * amount;
                    if (puddle > 0.001) {
                        vec2 rn = Wet.z > 0.01 ? rippleNormal(wp.xz, t) * min(Wet.z, 1.0) * 0.5 : vec2(0.0);
                        vec3 n2 = normalize(vec3(rn.x, 1.0, rn.y));
                        // Seen from above a puddle is clear, dark water; at a glancing angle it mirrors the sky.
                        float fresnel = 0.2 + 0.72 * pow(1.0 - clamp(dot(-dir, n2), 0.0, 1.0), 4.0);
                        vec3 reflection = Sky.rgb * (1.0 + (rn.x + rn.y) * 0.8);
                        // A thin bright meniscus where the water meets the ground makes the edge read as water.
                        float edge = smoothstep(threshold - 0.05, threshold - 0.02, pn) * (1.0 - smoothstep(threshold - 0.02, threshold, pn));
                        col = mix(col, mix(col * 0.62, reflection, fresnel), puddle);
                        col += Sky.rgb * edge * 0.12 * amount;
                    }
                }

                if (Wet.w > 0.01) {
                    float sn = texture(NoiseSampler, wp.xz / 9.0).g + 0.25;
                    float cover = smoothstep(1.0 - Wet.w, 1.15 - Wet.w, sn) * amount;
                    float lum = dot(col, vec3(0.3, 0.59, 0.11));
                    vec3 snowColor = vec3(0.9, 0.94, 1.0) * clamp(lum * 1.8 + 0.3, 0.35, 1.0);
                    col = mix(col, snowColor, cover);
                }
            }
        }
    }

    // ---- overcast: a moving cloud deck that closes over the sky as the weather builds ----
    if (!under && sky && Sky.a > 0.01 && dir.y > 0.01) {
        vec2 p = dir.xz / (dir.y + 0.12) * 0.22 + TimeWind.yz * t * 0.004 + CamPos.xz * 0.0004;
        float n = texture(NoiseSampler, p).g * 0.55 + texture(NoiseSampler, p * 2.7 + 0.31).g * 0.3
                + texture(NoiseSampler, p * 6.1 + 0.77).r * 0.15;
        float edge = 1.0 - Sky.a * 1.15;
        float mask = smoothstep(edge, edge + 0.4, n + 0.25);
        float belly = mix(1.0, 0.45 + 0.5 * n, 0.6 + 0.4 * Cloud.a);
        vec3 cloud = Cloud.rgb * belly + vec3(0.8, 0.85, 1.0) * Heat.y * 0.9 * n;
        col = mix(col, cloud, mask * smoothstep(0.01, 0.18, dir.y) * min(1.0, Sky.a * 1.5));
    }

    // Optical path: terrain uses its real distance; the sky is treated as a haze layer above the camera.
    float path = sky ? (dir.y > 0.0 ? min(300.0, 45.0 / max(dir.y, 0.05)) : 300.0) : dist;

    // ---- the glow of the sky around the sun or moon, and of the air between here and the horizon below it ----
    if (!under && Glow.a > 0.003) {
        float toward = max(dot(dir, SunDir), 0.0);
        float halo = pow(toward, 5.0) * 0.2 + pow(toward, 28.0) * 0.4 + pow(toward, 220.0) * 0.5;
        float through = sky ? 1.0 : (1.0 - exp(-dist * 0.011)) * 0.55;
        col += Glow.rgb * halo * Glow.a * through;
    }

    // ---- distance haze, gently billowing with the wind ----
    if (!under && Haze.a > 1.0e-5) {
        vec2 hp = (CamPos.xz + dir.xz * min(path, 28.0)) * 0.035 + TimeWind.yz * t * 0.06;
        float billow = 0.75 + 0.5 * texture(NoiseSampler, hp).g;
        float amount = (1.0 - exp(-Haze.a * billow * path)) * MistShape.w;
        col = mix(col, Haze.rgb, amount);
    }

    // ---- ground mist: exponential height fog integrated along the view ray ----
    if (!under && Mist.a > 1.0e-5) {
        float b = MistShape.y;
        float span = min(path, 140.0);
        float k = b * dir.y;
        float columnLength = abs(k) > 1.0e-3 ? (1.0 - exp(-k * span)) / k : span;
        float optical = min(Mist.a * exp(-b * max(MistShape.x, -2.0)) * columnLength, 4.0);
        vec2 mp = (CamPos.xz + dir.xz * min(span, 20.0)) * 0.05 + TimeWind.yz * t * 0.03;
        float roll = 0.65 + 0.7 * texture(NoiseSampler, mp).r;
        float amount = (1.0 - exp(-max(optical, 0.0) * roll)) * MistShape.z;
        col = mix(col, Mist.rgb, amount);
    }

    // ---- light shafts: how much open sky lies between this pixel and the sun ----
    if (!under && Sun.z > 0.003 && Sun.w > 0.5) {
        vec2 delta = Sun.xy - uv;
        float sunDistance = length(delta * vec2(SunColor.a, 1.0));
        vec2 stepUv = delta / 14.0 * 0.92;
        vec2 p = uv + stepUv * texture(NoiseSampler, uv * 9.0 + t).a;
        float open = 0.0;
        for (int i = 0; i < 14; i++) {
            open += step(SKY_DEPTH, texture(DepthSampler, p).r);
            p += stepUv;
        }
        float glow = open / 14.0 * Sun.z * (1.0 - smoothstep(0.0, 0.95, sunDistance));
        col += SunColor.rgb * glow;
    }

    // ---- bloom: light sources, the sun, lightning and glittering water bleed into what is around them ----
    if (Look.x > 0.002) {
        col += texture(BloomSampler, uv).rgb * Look.x;
    }

    // ---- eye adaptation and colour grade ----
    col *= Heat.z;
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(lum), col, Heat.w);
    col *= Grade.rgb;
    col = (col - 0.5) * Grade.a + 0.5;
    col += vec3(0.75, 0.82, 1.0) * Heat.y * (sky ? 0.35 : 0.22);

    // ---- film response: muted colours gain a little life, highlights roll off instead of clipping,
    //      mid-tones gain contrast, shadows lean cool and highlights warm ----
    if (Look.y > 0.002) {
        col = max(col, 0.0);
        float l2 = dot(col, vec3(0.299, 0.587, 0.114));
        float chroma = max(col.r, max(col.g, col.b)) - min(col.r, min(col.g, col.b));
        vec3 film = mix(vec3(l2), col, 1.0 + Look.w * (1.0 - min(chroma, 1.0)));
        vec3 over = max(film - 0.8, 0.0);
        film = min(film, 0.8) + over / (1.0 + over * 2.5);
        vec3 c = clamp(film, 0.0, 1.0);
        film = mix(film, c * c * (3.0 - 2.0 * c), 0.42);
        film *= mix(vec3(0.95, 0.99, 1.06), vec3(1.04, 1.0, 0.95), smoothstep(0.12, 0.8, l2));
        col = mix(col, film, min(Look.y, 1.0));
    }

    // ---- vignette: the corners of the view fall off slightly, drawing the eye to the middle ----
    if (Look.z > 0.002) {
        vec2 v = (uv - 0.5) * vec2(mix(1.0, SunColor.a, 0.35), 1.0);
        col *= 1.0 - smoothstep(0.38, 1.0, length(v) * 1.18) * Look.z * 0.6;
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
