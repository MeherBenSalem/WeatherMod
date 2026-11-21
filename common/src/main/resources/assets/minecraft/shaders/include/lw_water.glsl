// Living World: the shape of a water surface.
// Shared by the translucent terrain shader (the surface itself) and the atmosphere pass (the light it
// throws on things seen from under water), so both always agree.
//
// The surface is built from two things:
//   - a handful of travelling wave trains with FIXED headings, whose crest lines are bent by slow noise;
//   - small ripples: the gradient of a few layers of smooth noise that slide over each other.
//
// Nothing here is ever rotated by the wind direction. The wind only decides how strong each train is
// (trains heading downwind grow), how fast the common wave clock runs and how far the ripples have been
// carried, and all three arrive as values accumulated on the CPU. Turning a wave pattern with the wind
// would swing it around the world origin: a degree of wobble moves water a thousand blocks out by
// seventeen blocks, which reads as water racing sideways in a dead calm.

const float LW_TAU = 6.2831853;

float lwHash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// Smooth value noise, 0..1.
float lwNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float a = lwHash12(i);
    float b = lwHash12(i + vec2(1.0, 0.0));
    float c = lwHash12(i + vec2(0.0, 1.0));
    float d = lwHash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// The same noise with its exact gradient: x = value, yz = d/dp. Quintic interpolation keeps the
// gradient continuous across cells, which is what keeps the surface normal free of facets.
vec3 lwNoiseD(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    vec2 du = 30.0 * f * f * (f * (f - 2.0) + 1.0);
    float a = lwHash12(i);
    float b = lwHash12(i + vec2(1.0, 0.0));
    float c = lwHash12(i + vec2(0.0, 1.0));
    float d = lwHash12(i + vec2(1.0, 1.0));
    float k1 = b - a, k2 = c - a, k4 = a - b - c + d;
    return vec3(a + k1 * u.x + k2 * u.y + k4 * u.x * u.y, du * vec2(k1 + k4 * u.y, k2 + k4 * u.x));
}

// One wave train. heading: unit vector it travels along. k: radians per block. w: radians per second of
// wave clock. gain: the steepest slope it reaches. warp: slow noise (value, gradient) that bends its crests.
vec2 lwTrain(vec2 p, vec2 heading, float k, float w, float clock, float gain, vec3 warp, float bend) {
    float phase = dot(heading, p) * k - clock * w + warp.x * bend;
    return (heading * k + warp.yz * bend) * (cos(phase) * gain / k);
}

// One layer of small ripples: the gradient of noise stretched along one fixed axis, so the bumps read
// as short curved wavelets and not as round dimples.
vec2 lwRipple(vec2 q, mat2 turn, vec2 scale, vec2 slide, float gain) {
    vec3 n = lwNoiseD((turn * q) * scale + slide);
    return (n.yz * scale / max(scale.x, scale.y)) * turn * gain;
}

// Slope of the water surface (dh/dx, dh/dz) at a world position.
//   p         world xz in blocks
//   clock     wave clock in seconds (runs slowly in a calm, faster in wind)
//   drift     how far the wind has carried the ripples, in blocks
//   wind      xy = unit direction the wind blows toward, z = its speed (0 calm .. 1 gale .. 1.6)
//   footprint blocks of water covered by one pixel; anything finer than a pixel is faded out instead of
//             being left to shimmer and stripe in the distance
vec2 lwWaterSlope(vec2 p, float clock, vec2 drift, vec3 wind, float footprint) {
    float speed = clamp(wind.z, 0.0, 1.6);
    vec2 slope = vec2(0.0);

    // ---- wave trains: a faint swell from every side in a calm, short steep seas downwind in a blow ----
    vec3 warp = lwNoiseD(p * 0.11 + vec2(3.7, 9.2));
    warp.yz *= 0.11;
    vec3 warp2 = lwNoiseD(p * 0.27 + vec2(41.3, 17.9));
    warp2.yz *= 0.27;
    vec3 bendNoise = vec3(warp.x + 0.5 * warp2.x, warp.yz + 0.5 * warp2.yz);

    const vec2 H0 = vec2(0.978, 0.208);
    const vec2 H1 = vec2(0.225, 0.974);
    const vec2 H2 = vec2(-0.777, 0.629);
    const vec2 H3 = vec2(-0.921, -0.391);
    const vec2 H4 = vec2(-0.139, -0.990);
    const vec2 H5 = vec2(0.848, -0.530);
    float a0 = max(dot(H0, wind.xy), 0.0), a1 = max(dot(H1, wind.xy), 0.0), a2 = max(dot(H2, wind.xy), 0.0);
    float a3 = max(dot(H3, wind.xy), 0.0), a4 = max(dot(H4, wind.xy), 0.0), a5 = max(dot(H5, wind.xy), 0.0);
    float base = 0.3 + 0.25 * speed;
    float lift = 1.5 * speed;
    float f0 = 1.0 - smoothstep(0.3, 0.8, footprint * 0.60 * 0.32);
    float f1 = 1.0 - smoothstep(0.3, 0.8, footprint * 0.92 * 0.32);
    float f2 = 1.0 - smoothstep(0.3, 0.8, footprint * 1.31 * 0.32);
    float f3 = 1.0 - smoothstep(0.3, 0.8, footprint * 1.93 * 0.32);
    float f4 = 1.0 - smoothstep(0.3, 0.8, footprint * 0.76 * 0.32);
    float f5 = 1.0 - smoothstep(0.3, 0.8, footprint * 2.62 * 0.32);
    slope += lwTrain(p, H0, 0.60, 0.92, clock, 0.036 * (base + lift * a0 * a0) * f0, bendNoise, 3.4);
    slope += lwTrain(p, H1, 0.92, 1.14, clock, 0.038 * (base + lift * a1 * a1) * f1, bendNoise, -3.0);
    slope += lwTrain(p, H2, 1.31, 1.36, clock, 0.038 * (base + lift * a2 * a2) * f2, bendNoise, 3.8);
    slope += lwTrain(p, H3, 1.93, 1.65, clock, 0.036 * (base + lift * a3 * a3) * f3, bendNoise, -3.3);
    slope += lwTrain(p, H4, 0.76, 1.03, clock, 0.036 * (base + lift * a4 * a4) * f4, bendNoise, 2.9);
    slope += lwTrain(p, H5, 2.62, 1.92, clock, 0.034 * (base + lift * a5 * a5) * f5, bendNoise, -3.6);

    // ---- ripples: what gives still water its texture, and a breeze its cat's-paws ----
    // Five layers of short wavelets, each lying along its own fixed axis and sliding its own way, from
    // a couple of blocks long down to a hand's width.
    vec2 q = p - drift;
    float ripple = 0.85 + 1.15 * speed;
    float r0 = 1.0 - smoothstep(0.45, 1.1, footprint * 0.52);
    float r1 = 1.0 - smoothstep(0.45, 1.1, footprint * 1.15);
    float r2 = 1.0 - smoothstep(0.45, 1.1, footprint * 2.5);
    float r3 = 1.0 - smoothstep(0.45, 1.1, footprint * 5.4);
    float r4 = 1.0 - smoothstep(0.45, 1.1, footprint * 11.0);
    if (r0 > 0.0) {
        slope += lwRipple(q, mat2(0.921, 0.391, -0.391, 0.921), vec2(0.52, 0.22), vec2(clock * 0.11, clock * 0.06), 0.085 * ripple * r0);
    }
    if (r1 > 0.0) {
        slope += lwRipple(q, mat2(0.454, -0.891, 0.891, 0.454), vec2(1.15, 0.46), vec2(-clock * 0.17, clock * 0.1) + 13.1, 0.09 * ripple * r1);
    }
    if (r2 > 0.0) {
        slope += lwRipple(q, mat2(-0.602, 0.799, -0.799, -0.602), vec2(2.5, 1.0), vec2(clock * 0.24, -clock * 0.16) + 29.4, 0.08 * ripple * r2);
    }
    if (r3 > 0.0) {
        slope += lwRipple(q, mat2(0.995, -0.105, 0.105, 0.995), vec2(5.4, 2.3), vec2(-clock * 0.31, -clock * 0.26) + 47.7, 0.06 * ripple * r3);
    }
    if (r4 > 0.0) {
        slope += lwRipple(q, mat2(0.276, 0.961, -0.961, 0.276), vec2(11.0, 5.0), vec2(clock * 0.45, clock * 0.33) + 61.3, 0.04 * ripple * r4);
    }
    return slope;
}

// How much of the surface's fine detail was faded out at this distance (0 = none .. 1 = all of it).
// Used to widen the sun's glitter path: what the eye can no longer resolve as ripples it sees as sheen.
float lwWaterBlur(float footprint) {
    return smoothstep(0.05, 0.7, footprint);
}

// The web of light the surface focuses onto whatever lies below it, 0..~1.2.
// Bright where two drifting noise fields cross, which draws the thin, shifting, closed lines of real caustics.
float lwCaustics(vec2 p, float clock, vec2 drift) {
    vec2 q = p - drift;
    float a = lwNoise(q * 0.85 + vec2(clock * 0.19, clock * 0.11));
    float b = lwNoise(mat2(0.8, -0.6, 0.6, 0.8) * q * 1.25 + vec2(-clock * 0.15, clock * 0.17) + 7.7);
    float c = lwNoise(mat2(-0.6, 0.8, -0.8, -0.6) * q * 2.3 + vec2(clock * 0.22, -clock * 0.2) + 3.1);
    float web = max(0.0, 1.0 - abs(a - b) * 6.5);
    float fine = max(0.0, 1.0 - abs(b - c) * 6.5);
    web *= web;
    fine *= fine;
    return web * web * 0.85 + fine * fine * 0.45;
}

// Height of the long swell only (blocks), for moving the water mesh. Vertices sit at block corners, so
// nothing shorter than a few blocks can be shown this way; the rest is shading.
float lwSwell(vec2 p, float clock) {
    return sin(dot(p, vec2(0.978, 0.208)) * 0.42 - clock * 0.77) * 0.5
         + sin(dot(p, vec2(-0.139, -0.990)) * 0.55 - clock * 0.88 + 1.3) * 0.3
         + sin(dot(p, vec2(0.225, 0.974)) * 0.66 - clock * 0.97 + 2.1) * 0.2;
}
