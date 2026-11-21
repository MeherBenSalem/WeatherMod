#version 150

// Living World atmosphere pass.
// One full-screen pass over the finished world image (before the hand and HUD are drawn) that adds
// everything Minecraft's fixed fog cannot: heat shimmer, wet ground, puddles, fresh snow, the sky's
// colours, stars and northern lights, a layer of real cloud with its shadows, distance haze, low-lying
// mist, light shafts, eye adaptation and the colour grade.
// Every term is driven by the shared environment state, and every term costs nothing when its strength is 0.

#moj_import <lw_water.glsl>
#moj_import <lw_sky.glsl>

uniform sampler2D DiffuseSampler;   // world colour
uniform sampler2D DepthSampler;     // world depth
uniform sampler2D NoiseSampler;     // tiling noise: R smooth, G fbm, B cellular, A blue noise
uniform sampler2D ColumnSampler;    // 64x64 wrap-around terrain columns: RG = rain surface height, B = solid (top bit) + nearness to water (low 7 bits), A = puddle capacity (top 7 bits) + ice (lowest bit)

uniform mat4 InvViewProj;   // clip space -> camera-relative world space
uniform mat4 ViewProj;      // and back
uniform vec3 CamPos;        // camera position; x and z wrapped to 4096 blocks
uniform vec4 TimeWind;      // x = seconds (wrapped), yz = wind direction, w = wind speed
uniform vec4 Wave;          // x = the water's wave clock, yz = ripple drift, w = steps of the puddle reflection ray (0 = sky only)
uniform vec4 Haze;          // rgb = colour, a = extinction per block
uniform vec4 Mist;          // rgb = colour, a = density at the mist base
uniform vec4 MistShape;     // x = camera height above the mist base, y = falloff per block, z = max mist opacity, w = max haze opacity
uniform vec4 Heat;          // x = heat shimmer, y = lightning flash, z = exposure, w = saturation
uniform vec4 Grade;         // rgb = tint, a = contrast
uniform vec4 Wet;           // x = wet darkening, y = puddle level, z = rain intensity, w = snow cover
uniform vec4 Sky;           // rgb = colour of the sky overhead, a = cover of the flat overcast deck (0 when real clouds are drawn)
uniform vec4 Horizon;       // rgb = colour of the sky at the horizon, a = how much the sky's colours are enhanced
uniform vec4 Cloud;         // rgb = flat deck colour, a = storm darkness
uniform vec4 CloudShape;    // x = cloud cover (0 = no cloud layer), y = cloud base above the camera, z = thickness, w = march steps
uniform vec4 CloudOrigin;   // xy = cloud origin (camera + wind travel, wrapped to 6400), z = storm gloom, w = shadow strength
uniform vec4 CloudLit;      // rgb = sun or moon light on the clouds, a = lightning inside them
uniform vec4 CloudAmbient;  // rgb = sky light on the clouds
uniform vec4 CloudSeen;     // rgb = the clouds' overall colour from the ground (mirror images)
uniform vec4 Celestial;     // xyz = direction toward the sun (also below the horizon), w = rotation of the night sky in radians
uniform vec4 SkyFx;         // x = starlight, y = northern lights, z = rainbow, w = unused
uniform vec4 Sun;           // xy = sun position on screen (uv), z = light shaft strength, w = 1 if in front of the camera
uniform vec4 SunColor;      // rgb = shaft colour, a = aspect ratio
uniform sampler2D BloomSampler;     // blurred bright parts of the frame, quarter resolution
uniform sampler2D CloudSampler;     // the cloud layer drawn at reduced resolution: rgb = its light, a = how much sky shows through
uniform vec4 CloudMode;     // x = 1 when CloudSampler holds this frame's clouds
uniform vec4 Bolt;          // xyz = lightning that is flashing now, relative to the camera, w = its brightness
uniform vec4 Shore;         // x = 1 when there is water nearby and the shoreline is drawn damp
uniform vec3 SunDir;        // unit direction toward the sun (or the moon at night), world space
uniform vec4 Glow;          // rgb = colour of the glow around the sun or moon, a = strength
uniform vec4 Look;          // x = bloom, y = film response, z = vignette, w = vibrance
uniform vec4 Under;         // x = 1 when the camera is under water, y = caustics, z = view wobble, w = daylight reaching the water

in vec2 texCoord;

out vec4 fragColor;

const float SKY_DEPTH = 0.999999;
const float TAU = 6.2831853;

vec3 viewRay(vec2 uv, float depth) {
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

// The surface normal at a pixel, rebuilt from the depth buffer. On each screen axis it uses the
// neighbour that lies on the same surface (the one nearer in depth), so a pixel on the edge of a leaf or
// a block does not get a normal that belongs to neither side, which shows as specks that crawl whenever
// the leaves move.
vec3 surfaceNormal(vec2 uv, float depth, vec3 rel) {
    vec2 px = 1.0 / vec2(textureSize(DepthSampler, 0));
    vec2 ox = vec2(px.x, 0.0), oy = vec2(0.0, px.y);
    float l = texture(DepthSampler, uv - ox).r, r = texture(DepthSampler, uv + ox).r;
    float d = texture(DepthSampler, uv - oy).r, u = texture(DepthSampler, uv + oy).r;
    vec3 dx = abs(l - depth) < abs(r - depth) ? rel - viewRay(uv - ox, l) : viewRay(uv + ox, r) - rel;
    vec3 dy = abs(d - depth) < abs(u - depth) ? rel - viewRay(uv - oy, d) : viewRay(uv + oy, u) - rel;
    return normalize(cross(dx, dy));
}

vec4 columnAt(vec2 cell) {
    return texture(ColumnSampler, (cell + 0.5) / 64.0);
}

// World height of the surface precipitation lands on in a terrain column.
float columnTop(vec4 column) {
    return (column.r * 255.0 + column.g * 65280.0) / 16.0 - 64.0;
}

// 1 where a column's surface is solid ground (takes damp, puddles and snow), 0 for water or nothing.
float columnGround(vec4 column) {
    return step(127.5, floor(column.b * 255.0 + 0.5));
}

// How close a column of ground is to open water at about its own level: 1 at the water's edge, falling
// to 0 a few blocks back from it.
float columnShore(vec4 column) {
    return mod(floor(column.b * 255.0 + 0.5), 128.0) / 127.0;
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

#moj_import <lw_clouds.glsl>

// The sky a puddle mirrors along a reflected ray: horizon to zenith, with the clouds in it.
vec3 skyMirror(vec3 from, vec3 r) {
    r.y = max(r.y, 0.02);
    vec3 c = mix(Horizon.rgb, Sky.rgb, smoothstep(0.0, 0.45, r.y));
    if (CloudShape.x > 0.001) {
        float lift = (CloudShape.y + CloudShape.z * 0.35 - from.y) / r.y;
        float cloud = lwCloudCover2D(NoiseSampler, from.xz + r.xz * lift + CloudOrigin.xy, CloudShape.x) * smoothstep(0.03, 0.22, r.y);
        c = mix(c, CloudSeen.rgb, cloud * 0.92);
    }
    return c;
}

// Follows a reflected ray through the finished frame. rgb = what it hits, a = confidence.
vec4 traceMirror(vec3 origin, vec3 dir, float jitter) {
    int steps = int(Wave.w + 0.5);
    if (steps < 1) {
        return vec4(0.0);
    }
    vec4 c0 = ViewProj * vec4(origin, 1.0);
    vec4 cd = ViewProj * vec4(dir, 0.0);
    float stride = 0.4;
    float t = stride * (0.5 + jitter);
    for (int i = 0; i < 16; i++) {
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
        float sceneDepth = texture(DepthSampler, uv).r;
        if (sceneDepth < ndc.z * 0.5 + 0.5 && sceneDepth < SKY_DEPTH) {
            float behindBy = length(origin + dir * t) - length(viewRay(uv, sceneDepth));
            if (behindBy < stride * 1.8 + 0.5) {
                vec2 edge = smoothstep(vec2(0.0), vec2(0.1), uv) * (1.0 - smoothstep(vec2(0.9), vec2(1.0), uv));
                return vec4(texture(DiffuseSampler, uv).rgb, edge.x * edge.y);
            }
        }
        stride *= 1.3;
        t += stride;
    }
    return vec4(0.0);
}

// ---- night sky ---------------------------------------------------------------------------------

// Stars as points scattered through space, seen where the view direction passes close to one. d is the
// view direction in the frame of the turning night sky. crowd raises the number of faint stars (0..1).
vec3 starField(vec3 d, float t, float crowd) {
    vec3 sum = vec3(0.0);
    for (int i = 0; i < 2; i++) {
        float scale = i == 0 ? 52.0 : 118.0;
        float size = i == 0 ? 0.17 : 0.13;
        float share = i == 0 ? 0.05 : 0.09 + 0.2 * crowd;
        vec3 q = d * scale;
        vec3 id = floor(q);
        float h = hash31(id + float(i) * 19.0);
        if (h < share) {
            vec3 at = (vec3(hash31(id + 3.3), hash31(id + 7.7), hash31(id + 11.1)) - 0.5) * 0.6;
            float b = smoothstep(size, 0.0, length(q - id - 0.5 - at));
            float twinkle = 0.72 + 0.28 * sin(t * (1.5 + h * 60.0) + h * 400.0);
            vec3 tint = mix(vec3(0.72, 0.84, 1.0), vec3(1.0, 0.88, 0.7), hash31(id + 5.5));
            sum += tint * b * (0.35 + b) * twinkle * (i == 0 ? 1.15 : 0.6);
        }
    }
    return sum;
}

// Curtains of light over a cold night. Each curtain is described directly on the dome of the sky: a
// lower edge that wanders up and down along the horizon, light that is brightest just above that edge
// and fades upward from green into violet, folds of brighter and dimmer stretches, and fine rays that
// run straight up. Nothing is stacked or stepped, so the sheets are smooth.
vec3 northernLights(vec3 dir, float t) {
    float elevation = asin(clamp(dir.y, -1.0, 1.0));
    // Position around the horizon as a fraction of a turn (0 = north). The factors applied to it below
    // are whole numbers, so the tiling noise meets itself seamlessly in the south.
    float around = atan(dir.x, -dir.z) / TAU;
    vec3 sum = vec3(0.0);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float wander = texture(NoiseSampler, vec2(around * 2.0 + fi * 0.37 + t * 0.0012, fi * 0.21 + t * 0.0007)).r;
        float above = elevation - (0.15 + fi * 0.13 + (wander - 0.5) * 0.32);
        float sheet = smoothstep(0.0, 0.04, above) * exp(-max(above, 0.0) * (3.4 - fi * 0.6));
        float folds = texture(NoiseSampler, vec2(around * 5.0 + fi * 0.5 - t * 0.002, 0.3 + fi * 0.17)).g;
        float rays = texture(NoiseSampler, vec2(around * 34.0 + wander * 0.6 + fi * 0.11, 0.6 + t * 0.004)).r;
        vec3 c = mix(vec3(0.14, 1.0, 0.44), vec3(0.52, 0.24, 0.95), clamp(above * 2.2, 0.0, 1.0));
        sum += c * sheet * smoothstep(0.28, 0.66, folds) * (0.4 + 1.0 * rays);
    }
    // Mostly in the northern half of the sky.
    vec2 heading = normalize(dir.xz + vec2(1.0e-5));
    float north = 0.25 + 0.75 * smoothstep(-0.3, 0.7, -heading.y);
    return sum * 0.7 * north;
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
    float grain = texture(NoiseSampler, gl_FragCoord.xy / 256.0).a;

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
        vec3 wp = rel + CamPos;
        float web = lwCaustics((wp.xz + wp.y * 0.37) * 1.5, Wave.x, Wave.yz * 1.5);
        float facing = clamp(nrm.y * 0.65 + 0.5, 0.12, 1.0);
        float light = web * Under.y * Under.w * facing * exp(-dist * 0.055);
        col += (col * 0.85 + vec3(0.02, 0.05, 0.06)) * light;
    }
    if (under) {
        // The bright window of the surface overhead.
        col += vec3(0.08, 0.15, 0.17) * pow(max(dir.y, 0.0), 2.0) * Under.w * 0.6;
    }

    // ---- the ground: cloud shadows, wet surfaces, puddles and snow where the weather can reach ----
    bool shadows = CloudOrigin.w > 0.003 && CloudShape.x > 0.001;
    if (!under && !sky && (shadows || (dist < 44.0 && (Wet.x + Wet.y + Wet.w + Shore.x) > 0.002))) {
        vec3 wp = rel + CamPos;
        // The pixels of a wall lie exactly on the line between two terrain columns. The column is looked
        // up a finger's width toward the viewer (the air in front of the wall), or the answer would flip
        // from one pixel to the next and stripe the wall.
        vec2 cell = floor(wp.xz - dir.xz * 0.04);
        // Near the camera the terrain column cache knows what is under open sky; beyond it, assume it is.
        float known = 1.0 - smoothstep(30.0, 42.0, dist);
        vec4 own = columnAt(cell);
        float ownOpen = 1.0 - smoothstep(0.12, 0.5, columnTop(own) - wp.y);

        if (shadows) {
            // Where the light that reaches this point came through the cloud layer.
            float lift = CloudShape.y + CloudShape.z * 0.35 - rel.y;
            if (lift > 0.0) {
                vec2 through = rel.xz + SunDir.xz * (lift / max(SunDir.y, 0.14)) + CloudOrigin.xy;
                float shade = lwCloudCover2D(NoiseSampler, through, CloudShape.x);
                col *= 1.0 - shade * CloudOrigin.w * 0.34 * mix(1.0, ownOpen, known);
            }
        }

        if (dist < 44.0 && (Wet.x + Wet.y + Wet.w + Shore.x) > 0.002) {
            vec3 nrm = surfaceNormal(uv, depth, rel);
            if (dot(nrm, dir) > 0.0) {
                nrm = -nrm;
            }
            float upFacing = smoothstep(0.75, 0.95, nrm.y);
            if (upFacing > 0.0) {
                // Exposure, puddle capacity and "is ground" are blended between the four nearest columns
                // so that a roof edge or a change of material leaves a soft boundary, not a block-shaped
                // one. A neighbour that merely stands a block higher is a step in the ground, not a roof:
                // rain still reaches its foot. A neighbour at another height is not the same surface, so
                // its material is not blended in.
                vec2 g = wp.xz - 0.5;
                vec2 g0 = floor(g);
                vec2 gf = g - g0;
                float open = 0.0, capacity = 0.0, ground = 0.0, shore = 0.0;
                float ownGround = columnGround(own), ownShore = columnShore(own);
                for (int i = 0; i < 4; i++) {
                    vec2 corner = vec2(float(i - (i / 2) * 2), float(i / 2));
                    vec2 c = g0 + corner;
                    vec4 column = columnAt(c);
                    float lift = columnTop(column) - wp.y;
                    float same = 1.0 - smoothstep(0.2, 0.45, abs(lift));
                    float weight = (corner.x > 0.5 ? gf.x : 1.0 - gf.x) * (corner.y > 0.5 ? gf.y : 1.0 - gf.y);
                    open += weight * (all(equal(c, cell)) ? ownOpen : 1.0 - smoothstep(1.3, 2.2, lift));
                    capacity += weight * mix(own.a, column.a, same);
                    ground += weight * mix(ownGround, columnGround(column), same);
                    shore += weight * mix(ownShore, columnShore(column), same);
                }
                // The waterline: sand and soil within reach of the water are dark and damp, and the edge
                // of the damp creeps up and slides back as the water laps. Not a matter of rain, so it
                // does not care about roofs.
                if (Shore.x > 0.002 && shore > 0.01) {
                    float lapping = 0.5 + 0.5 * sin(Wave.x * 1.25 + lwNoise(wp.xz * 0.45) * 6.0);
                    float damp = smoothstep(0.2, 0.62, shore + lapping * 0.13) * known * upFacing * ownGround * Shore.x;
                    col = mix(col, col * (0.58 + 0.3 * col), damp);
                    col += Sky.rgb * damp * 0.05 * pow(1.0 - clamp(-dir.y, 0.0, 1.0), 3.0);
                }
                float exposed = min(ownOpen, open);
                float amount = known * upFacing * exposed * ground;
                if (amount > 0.001) {
                    // Damp surfaces are darker and a touch more saturated.
                    col = mix(col, col * (0.6 + 0.3 * col), Wet.x * amount);

                    float level = Wet.y * capacity;
                    if (level > 0.01) {
                        // Water runs off an edge instead of standing on it: no puddle within a hand's
                        // breadth of a drop to lower ground.
                        vec2 f = wp.xz - cell;
                        float rim = 1.0;
                        if (columnTop(columnAt(cell + vec2(1.0, 0.0))) < wp.y - 0.3) {
                            rim *= smoothstep(0.02, 0.42, 1.0 - f.x);
                        }
                        if (columnTop(columnAt(cell - vec2(1.0, 0.0))) < wp.y - 0.3) {
                            rim *= smoothstep(0.02, 0.42, f.x);
                        }
                        if (columnTop(columnAt(cell + vec2(0.0, 1.0))) < wp.y - 0.3) {
                            rim *= smoothstep(0.02, 0.42, 1.0 - f.y);
                        }
                        if (columnTop(columnAt(cell - vec2(0.0, 1.0))) < wp.y - 0.3) {
                            rim *= smoothstep(0.02, 0.42, f.y);
                        }
                        // Each level of the ground has puddles of its own; one pool never carries on,
                        // identical, a step higher up.
                        vec2 pp = wp.xz + floor(wp.y + 0.5) * vec2(7.31, 3.17);
                        float pn = texture(NoiseSampler, pp / 19.0).r * 0.6 + texture(NoiseSampler, pp / 6.3 + 0.37).g * 0.4;
                        float threshold = mix(0.2, 0.56, level);
                        // The noise is turned into a depth: shallow at the edge, deeper toward the middle.
                        float pool = clamp((threshold - pn) / 0.05, 0.0, 1.0) * rim;
                        float puddle = smoothstep(0.0, 1.0, pool) * amount;
                        if (puddle > 0.001) {
                            vec2 rn = Wet.z > 0.01 ? rippleNormal(wp.xz, t) * min(Wet.z, 1.0) * 0.5 : vec2(0.0);
                            // A breath of wind crosses standing water too.
                            rn += lwNoiseD(wp.xz * 2.3 + vec2(Wave.x * 0.5, -Wave.x * 0.35)).yz * 0.012 * min(TimeWind.w, 1.0);
                            vec3 n2 = normalize(vec3(rn.x, 1.0, rn.y));
                            // Seen from above a puddle is clear, dark water; at a glancing angle it is a mirror.
                            float fresnel = 0.08 + 0.92 * pow(1.0 - clamp(dot(-dir, n2), 0.0, 1.0), 4.0);
                            vec3 r = reflect(dir, n2);
                            vec3 mirror = skyMirror(rel, r);
                            vec4 hit = traceMirror(rel + vec3(0.0, 0.02, 0.0), normalize(vec3(r.x, max(r.y, 0.02), r.z)), grain);
                            mirror = mix(mirror, hit.rgb, hit.a);
                            vec3 soaked = col * mix(0.72, 0.5, smoothstep(0.0, 1.0, pool));
                            col = mix(col, mix(soaked, mirror, fresnel), puddle);
                            // A thin bright meniscus where the water meets the ground makes the edge read as water.
                            float edge = smoothstep(0.0, 0.25, pool) * (1.0 - smoothstep(0.25, 0.6, pool));
                            col += Sky.rgb * edge * 0.07 * amount;
                        }
                    }

                    if (Wet.w > 0.01) {
                        float sn = texture(NoiseSampler, wp.xz / 9.0).g + 0.25;
                        float cover = smoothstep(1.0 - Wet.w, 1.15 - Wet.w, sn) * amount;
                        // The wind sweeps ice: snow lies on it in drifts, with bare ice between them.
                        float icy = mod(floor(own.a * 255.0 + 0.5), 2.0);
                        if (icy > 0.5) {
                            float drift = texture(NoiseSampler, wp.xz / 27.0 + vec2(0.37, 0.11)).r
                                    + 0.35 * texture(NoiseSampler, wp.xz / 6.0).b;
                            cover *= smoothstep(0.62, 0.9, drift) * 0.9;
                        }
                        float lum = dot(col, vec3(0.3, 0.59, 0.11));
                        vec3 snowColor = vec3(0.9, 0.94, 1.0) * clamp(lum * 1.8 + 0.3, 0.35, 1.0);
                        col = mix(col, snowColor, cover);
                    }
                }
            }
        }
    }

    // ---- lightning: for the instant of the flash, the stroke is a light where it stands ----
    if (!under && !sky && Bolt.w > 0.01) {
        vec3 toBolt = Bolt.xyz - rel;
        float away = length(toBolt);
        vec3 nrm = surfaceNormal(uv, depth, rel);
        if (dot(nrm, dir) > 0.0) {
            nrm = -nrm;
        }
        // Surfaces turned toward the stroke catch it; those turned away keep a little from the lit air.
        float facing = clamp(dot(nrm, toBolt / max(away, 1.0e-3)) * 0.75 + 0.35, 0.0, 1.0);
        float lit = Bolt.w * facing / (1.0 + away * away / (150.0 * 150.0));
        col += (col * 1.7 + vec3(0.03, 0.035, 0.05)) * vec3(0.8, 0.87, 1.0) * lit;
    }

    // ---- the sky: its colours by day and at dusk, and what a clear night shows ----
    if (!under && sky) {
        vec3 sun = Celestial.xyz;
        float up = max(dir.y, 0.0);
        float lowSky = pow(1.0 - up, 5.0);
        if (Horizon.a > 0.003) {
            float day = smoothstep(-0.08, 0.22, sun.y);
            // A deeper blue overhead and a paler, glowing band along the horizon.
            vec3 rich = col * mix(vec3(1.0), vec3(0.74, 0.86, 1.07), smoothstep(0.08, 0.95, up));
            rich += vec3(0.03, 0.032, 0.03) * lowSky * day;
            // Sunrise and sunset: gold along the horizon toward the sun, climbing into rose, and opposite
            // the sun the dusky pink band that sits above the earth's shadow.
            float low = smoothstep(0.4, 0.0, abs(sun.y)) * smoothstep(-0.24, -0.02, sun.y);
            vec2 sunFlat = normalize(sun.xz + vec2(1.0e-5));
            vec2 dirFlat = normalize(dir.xz + vec2(1.0e-5));
            float toward = 0.5 + 0.5 * dot(sunFlat, dirFlat);
            vec3 warm = mix(vec3(1.0, 0.4, 0.13), vec3(0.95, 0.5, 0.42), smoothstep(0.0, 0.35, up));
            rich += warm * pow(1.0 - up, 3.0) * (0.18 + 0.82 * toward * toward) * low * 0.5;
            rich += vec3(0.4, 0.2, 0.34) * smoothstep(0.0, 0.1, up) * pow(1.0 - up, 7.0) * (1.0 - toward) * low * 0.55;
            col = mix(col, rich, Horizon.a);
        }
        if (SkyFx.x > 0.003 && dir.y > -0.03) {
            // The night sky turns about the north-south axis with the sun and moon.
            float ca = cos(Celestial.w), sa = sin(Celestial.w);
            vec3 cd = vec3(dir.x * ca + dir.y * sa, -dir.x * sa + dir.y * ca, dir.z);
            const vec3 pole = vec3(0.3511, 0.2508, 0.9021);
            float lat = dot(cd, pole);
            float band = exp(-lat * lat * 13.0);
            vec3 e1 = normalize(cross(pole, vec3(0.0, 1.0, 0.0)));
            vec3 e2 = cross(pole, e1);
            vec2 muv = vec2(atan(dot(cd, e2), dot(cd, e1)) / TAU * 4.0, lat * 1.5);
            float dust = texture(NoiseSampler, muv).g;
            float lanes = texture(NoiseSampler, muv * 2.0 + 0.3).g;
            float clearNight = SkyFx.x * smoothstep(-0.03, 0.18, dir.y);
            col += vec3(0.4, 0.46, 0.7) * band * (0.2 + 1.2 * dust * dust) * (0.5 + 0.7 * lanes) * 0.6 * clearNight;
            col += starField(cd, t, band * dust) * clearNight;
        }
        if (SkyFx.y > 0.003 && dir.y > 0.03) {
            col += northernLights(dir, t) * SkyFx.y;
        }
    }

    // ---- clouds ----
    // skyShow: how much of the sky behind the clouds is still visible at this pixel.
    float skyShow = 1.0;
    if (!under && CloudShape.x > 0.001) {
        // Open sky takes the clouds from the reduced-resolution buffer when there is one: they are soft
        // enough that nothing is lost, and it is a quarter of the work. Where terrain stands in front
        // of or inside the layer the ray is followed here, at full resolution, so that mountains and
        // towers cut the clouds cleanly; for ordinary ground that ends before the first slice.
        vec4 clouds = sky && CloudMode.x > 0.5 ? texture(CloudSampler, uv) : marchClouds(dir, sky ? 1.0e6 : dist);
        col = col * clouds.a + clouds.rgb;
        skyShow = clouds.a;
    } else if (!under && sky && Sky.a > 0.01 && dir.y > 0.01) {
        // The flat overcast deck, for when the cloud layer is not drawn.
        vec2 p = dir.xz / (dir.y + 0.12) * 0.22 + TimeWind.yz * t * 0.004 + CamPos.xz * 0.0004;
        float n = texture(NoiseSampler, p).g * 0.55 + texture(NoiseSampler, p * 2.7 + 0.31).g * 0.3
                + texture(NoiseSampler, p * 6.1 + 0.77).r * 0.15;
        float edge = 1.0 - Sky.a * 1.15;
        float mask = smoothstep(edge, edge + 0.4, n + 0.25);
        float belly = mix(1.0, 0.45 + 0.5 * n, 0.6 + 0.4 * Cloud.a);
        vec3 cloud = Cloud.rgb * belly + vec3(0.8, 0.85, 1.0) * Heat.y * 0.9 * n;
        col = mix(col, cloud, mask * smoothstep(0.01, 0.18, dir.y) * min(1.0, Sky.a * 1.5));
    }

    // ---- a rainbow, opposite the sun, in air that is still wet. It stands in the rain between the
    //      viewer and the far clouds, so it is drawn over them ----
    if (!under && SkyFx.z > 0.003 && dir.y > -0.02) {
        float angle = degrees(acos(clamp(dot(dir, -Celestial.xyz), -1.0, 1.0)));
        float x = (angle - 39.8) / 2.7;
        if (x > 0.0 && x < 1.0) {
            vec3 bow = clamp(abs(mod((1.0 - x) * 4.6 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
            float strength = smoothstep(0.0, 0.2, x) * smoothstep(1.0, 0.8, x) * smoothstep(-0.02, 0.12, dir.y);
            col += bow * strength * SkyFx.z * 0.8 * (sky ? 1.0 : smoothstep(20.0, 90.0, dist) * 0.7);
        }
    }

    // Optical path: terrain uses its real distance; the sky is treated as a haze layer above the camera.
    float path = sky ? (dir.y > 0.0 ? min(300.0, 45.0 / max(dir.y, 0.05)) : 300.0) : dist;

    // ---- the glow of the sky around the sun or moon, and of the air between here and the horizon below it ----
    if (!under && Glow.a > 0.003) {
        float toward = max(dot(dir, SunDir), 0.0);
        float halo = pow(toward, 5.0) * 0.2 + pow(toward, 28.0) * 0.4 + pow(toward, 220.0) * 0.5;
        float through = sky ? 0.25 + 0.75 * skyShow : (1.0 - exp(-dist * 0.011)) * 0.55;
        col += Glow.rgb * halo * Glow.a * through;
    }

    // ---- distance haze, gently billowing with the wind ----
    if (!under && Haze.a > 1.0e-5) {
        vec2 hp = (CamPos.xz + dir.xz * min(path, 28.0)) * 0.035 + Wave.yz * 0.3;
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
        vec2 mp = (CamPos.xz + dir.xz * min(span, 20.0)) * 0.05 + Wave.yz * 0.14;
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
