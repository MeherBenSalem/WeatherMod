#version 150

// Living World override of the vanilla translucent terrain fragment shader.
//
// Water is shaded as water instead of as a tinted, animated texture:
//   - you see through it: the scene behind is refracted by the ripples, and loses its red first with
//     depth, so shallows are clear and the deeps go blue-green in the biome's water colour;
//   - it mirrors the world: reflections are traced in screen space against the scene behind, and fall
//     back to the sky where the ray leaves the screen;
//   - the sun or moon glitters on it, the bed below carries caustics, rain rings it, and it fades
//     softly into the shore instead of ending in a hard line.
// Everything else in the layer (glass, ice, slime) takes the vanilla path unchanged.

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform sampler2D LWScene;     // the frame as it was just before translucent terrain: what lies behind the water
uniform sampler2D LWDepth;     // its depth

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

uniform vec4 LWWater;          // x = time in seconds, yz = wind direction, w = wave height (0 = off)
uniform vec4 LWSun;            // xyz = direction toward the sun or moon, w = how much of its direct light arrives
uniform vec3 LWSunColor;       // colour of that light
uniform vec4 LWSky;            // rgb = colour of the sky overhead, a = 1 when water shading is on
uniform mat4 LWInvViewProj;    // clip space -> camera-relative world space
uniform mat4 LWViewProj;       // and back
uniform vec2 LWScreen;         // 1 / framebuffer size
uniform vec4 LWOptions;        // x = reflection ray steps (0 = sky only), y = caustics, z = rain intensity, w = refraction

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec4 normal;
in vec3 lwRel;
in vec3 lwWorld;
in vec2 lwWater;
in vec3 lwTint;
in vec3 lwLight;

out vec4 fragColor;

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

// Slope of the water surface at p (in blocks): travelling waves summed over octaves, each one shorter,
// faster and turned by a fixed angle, with the sample point dragged along by the octave before it so
// that crests sharpen and troughs flatten the way real ripples do.
vec2 waveSlope(vec2 p, float t, vec2 wind, int octaves) {
    vec2 slope = vec2(0.0);
    vec2 dir = wind;
    float freq = 0.6, amp = 1.0, speed = 1.05, total = 0.0;
    for (int i = 0; i < 8; i++) {
        if (i >= octaves) {
            break;
        }
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

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;

    if (lwWater.x > 0.5 && LWSky.a > 0.5) {
        float dist = length(lwRel);
        vec3 view = lwRel / max(dist, 1.0e-4);
        vec2 suv = gl_FragCoord.xy * LWScreen;
        vec3 face = normalize(cross(dFdx(lwRel), dFdy(lwRel)));
        float t = LWWater.x;
        float open = smoothstep(0.3, 0.9, lwWater.y);

        // The body of the water: the biome's colour, pulled a little toward clear sea-green.
        vec3 body = mix(lwTint, vec3(0.05, 0.36, 0.42), 0.38) * lwLight;
        const vec3 absorb = vec3(0.4, 0.105, 0.075);

        vec3 behind = scenePos(suv, texture(LWDepth, suv).r);
        float thickness = max(length(behind) - dist, 0.0);

        if (abs(face.y) > 0.6) {
            // ---- the surface ----
            float calm = 1.0 / (1.0 + dist * 0.03);
            vec2 slope = waveSlope(lwWorld.xz, t, LWWater.yz, 7) * (0.35 + 0.55 * LWWater.w) * (0.35 + 0.65 * calm);
            if (LWOptions.z > 0.01 && dist < 40.0) {
                slope += rainRings(lwWorld.xz, t) * min(LWOptions.z, 1.0) * 0.35 * open;
            }
            vec3 n = normalize(vec3(-slope.x, 1.0, -slope.y));
            vec2 bend = (mat3(ModelViewMat) * vec3(n.x, 0.0, n.z)).xy;

            if (view.y < 0.0) {
                // Seen from above.
                // Refraction: look at the scene behind through the tilted surface, but never drag in
                // something that is in front of the water.
                vec2 ruv = clamp(suv + bend * LWOptions.w * 0.07 * min(thickness, 2.0) / (1.0 + dist * 0.1), vec2(0.002), vec2(0.998));
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
                    vec2 cs = waveSlope((bed.xz + lwWorld.xz - lwRel.xz) * 1.7 + 31.7, t * 0.85, LWWater.yz, 4);
                    float web = pow(max(0.0, 1.0 - abs(cs.x - cs.y) * 7.5), 3.0);
                    seen *= 1.0 + web * LWOptions.y * open * (0.25 + LWSun.w) * 0.75 * exp(-depth * 0.22) * smoothstep(0.03, 0.4, depth);
                }

                vec3 through = seen * exp(-depth * absorb) + body * (1.0 - exp(-depth * 0.15)) * 0.8;

                // Reflection: the scene where the mirrored ray hits it, the sky where it does not.
                vec3 r = reflect(view, n);
                r = normalize(vec3(r.x, max(r.y, 0.02), r.z));
                vec3 mirrored = mix(FogColor.rgb, LWSky.rgb, smoothstep(0.0, 0.5, r.y)) * open;
                // Looking steeply down the mirror image is a few percent of the pixel: not worth a ray.
                vec4 hit = fresnel > 0.045 ? traceReflection(lwRel, r, hash21(gl_FragCoord.xy + fract(t) * 61.0)) : vec4(0.0);
                mirrored = mix(mirrored, hit.rgb, hit.a);
                vec3 surface = mix(through, mirrored, fresnel);

                float sun = max(dot(r, LWSun.xyz), 0.0);
                surface += LWSunColor * (pow(sun, 1300.0) * 1.7 + pow(sun, 150.0) * 0.2) * LWSun.w * open * (1.0 - hit.a);

                // A faint line of foam and a soft fade where the water meets the shore.
                float lap = 0.5 + 0.5 * sin(t * 1.7 + (lwWorld.x + lwWorld.z) * 2.3 + slope.x * 30.0);
                float foam = (1.0 - smoothstep(0.04, 0.28 + 0.12 * lap, thickness)) * 0.22;
                surface = mix(surface, vec3(0.92, 0.96, 1.0) * lwLight, foam);
                color = vec4(surface, smoothstep(0.0, 0.1, thickness)) * ColorModulator;
            } else {
                // Seen from below: the world above through a rippling window, and beyond the critical
                // angle the surface mirrors the water underneath instead.
                vec2 ruv = clamp(suv + bend * LWOptions.w * 0.06, vec2(0.002), vec2(0.998));
                float window = smoothstep(0.42, 0.64, view.y + (slope.x + slope.y) * 0.8);
                vec3 above = texture(LWScene, ruv).rgb;
                vec3 mirror = body * 0.8 + vec3(0.02, 0.07, 0.09) * lwLight;
                color = vec4(mix(mirror, above * (1.15 + 0.35 * open), window), 1.0) * ColorModulator;
            }
        } else {
            // ---- falling and flowing sides: a moving, see-through sheet with bright streaks ----
            float streaks = dot(texture(Sampler0, texCoord0).rgb, vec3(0.3333));
            vec2 ruv = clamp(suv + vec2(sin(lwWorld.y * 5.0 + t * 6.0), cos(lwWorld.y * 3.0 + t * 4.0)) * 0.004 * LWOptions.w, vec2(0.002), vec2(0.998));
            vec3 seen = texture(LWScene, ruv).rgb;
            float depth = min(thickness, 4.0);
            vec3 sheet = seen * exp(-depth * absorb) * 0.92 + body * (0.16 + 0.3 * (1.0 - exp(-depth * 0.3)));
            sheet += lwLight * smoothstep(0.62, 0.95, streaks) * 0.3;
            color = vec4(sheet, smoothstep(0.0, 0.08, thickness + 0.05)) * ColorModulator;
        }
    }

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
