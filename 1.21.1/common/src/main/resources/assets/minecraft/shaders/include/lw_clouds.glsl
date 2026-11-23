// Living World: the cloud layer itself.
// Shared by the atmosphere pass (which draws it at full resolution, or only where terrain stands in
// front of it) and by the cloud buffer pass (which draws it at half resolution for the sky).
//
// The file that imports this must already have imported lw_sky.glsl and declared:
//   sampler2D NoiseSampler   the mod's tiling noise texture
//   vec4 CloudShape          x = cloud cover, y = cloud base above the camera, z = thickness, w = slices
//   vec4 CloudOrigin         xy = cloud origin (see lw_sky.glsl), z = storm gloom
//   vec4 CloudLit            rgb = sun or moon light on the clouds
//   vec4 CloudAmbient        rgb = sky light on the clouds
//   vec4 Horizon             rgb = colour of the sky at the horizon
//   vec3 SunDir              direction toward the sun or moon
//   vec4 Bolt                xyz = lightning that is flashing now, relative to the camera, w = its brightness

// Amount of cloud at a point of the layer. xz is in the cloud layer's frame, h is the height within the
// layer (0 = base, 1 = top). The fine version adds the puffs and wisps that erode the outline.
// soft is 1 for a crisp cloud and grows where neighbouring samples lie far apart (low over the horizon):
// there the outline is widened and the fine detail dropped, so that samples blend into one another
// instead of showing up as separate streaks.
// x = density 0..1, y = how far inside the cloud the point is (not capped: an overcast sky is several
// times "thicker" than the edge of a fair-weather cloud, which is what gives a storm its dark bellies).
vec2 cloudDensity(vec2 xz, float h, float threshold, bool fine, float soft) {
    float shape = fine && soft < 3.5 ? lwCloudShape(NoiseSampler, xz) : lwCloudShapeCoarse(NoiseSampler, xz);
    float depth = (shape - threshold) * 6.0;
    if (h <= 0.0 || h >= 1.0) {
        return vec2(0.0);
    }
    // A wider, softer edge reaches a little way outside the crisp outline.
    float d = clamp((depth + (soft - 1.0) * 0.5) / soft, 0.0, 1.0);
    if (d <= 0.0) {
        return vec2(0.0);
    }
    // Flat bases, and tops that stand taller where the cloud is thicker.
    float top = 0.32 + 0.68 * clamp(depth * 0.6, 0.0, 1.0);
    d *= smoothstep(0.0, 0.08, h) * (1.0 - smoothstep(top * 0.55, top, h));
    if (fine && d > 0.0 && soft < 3.5) {
        vec2 q = xz * (1.0 / 200.0) + vec2(h * 0.7, h * 0.45);
        float puff = texture(NoiseSampler, q).b;
        float wisp = texture(NoiseSampler, q * 4.0 + 0.17).g;
        float detail = 1.0 / (soft * soft);
        d = clamp(d * (1.0 + 0.45 * detail) - ((1.0 - puff) * 0.42 + (1.0 - wisp) * 0.24 * (1.0 - d)) * detail, 0.0, 1.0);
    }
    return vec2(d, max(depth, 0.0));
}

// The cloud layer along a view ray, nearest first. rgb = the light of the clouds along the ray, a = how
// much of what lies behind them still shows.
//
// The layer is cut into horizontal slices at fixed heights and the ray is sampled where it crosses each
// one. Because the slices stay put in the world, every pixel samples the same sheets of cloud at a
// slightly different place, and the picture is smooth by construction: no stepping along the ray, so
// no banding, and no per-pixel dither to hide it, so no grain.
vec4 marchClouds(vec3 dir, float maxDist) {
    float base = CloudShape.y;
    float thick = CloudShape.z;
    int slices = int(CloudShape.w + 0.5);
    float threshold = lwCloudThreshold(CloudShape.x);
    float dy = abs(dir.y) < 1.0e-4 ? (dir.y < 0.0 ? -1.0e-4 : 1.0e-4) : dir.y;
    // The stretch of the ray each slice stands for.
    float len = thick / (float(slices) * abs(dy));
    // Looking low over the horizon, neighbouring slices are crossed tens of blocks apart; the cloud is
    // softened to match (see cloudDensity) so the slices blend instead of striping.
    float soft = 1.0 + max(len - 8.0, 0.0) / 30.0;
    vec3 light = SunDir;
    float toward = max(dot(dir, light), 0.0);
    // Light scattered forward through thin cloud: the bright rim of a cloud in front of the sun.
    float silver = 1.0 + 1.3 * pow(toward, 8.0);
    float reach = thick * 0.3;
    vec3 sum = vec3(0.0);
    float show = 1.0;

    // Inside the layer the slices above and below say nothing about the cloud right around the camera.
    if (base < 0.0 && base + thick > 0.0) {
        float here = cloudDensity(CloudOrigin.xy, -base / thick, threshold, false, 1.0).x;
        float veil = here * (1.0 - exp(-min(maxDist, 160.0) * 0.035));
        sum = (CloudLit.rgb * 0.3 + CloudAmbient.rgb * 0.6) * veil;
        show = 1.0 - veil;
    }

    for (int i = 0; i < 32; i++) {
        if (i >= slices) {
            break;
        }
        // From the base upward when looking up, from the top downward when looking down.
        float h = (float(dy > 0.0 ? i : slices - 1 - i) + 0.5) / float(slices);
        float t = (base + h * thick) / dy;
        if (t <= 0.0) {
            continue;
        }
        if (t > maxDist || t > 6500.0) {
            break;
        }
        vec3 p = dir * t;
        vec2 xz = p.xz + CloudOrigin.xy;
        vec2 cloud = cloudDensity(xz, h, threshold, true, soft);
        float d = cloud.x;
        if (d > 0.004) {
            // How much cloud lies between here and the sun, and how much is piled up overhead.
            vec2 sunward = cloudDensity(xz + light.xz * reach, h + light.y * reach / thick, threshold, false, soft);
            float lit = exp(-sunward.x * 1.5 - max(sunward.y - 1.0, 0.0) * 0.9 - d * 0.6) * silver;
            float overhead = exp(-max(cloud.y - 1.0, 0.0) * 0.8 * (1.0 - h));
            // Sunlight, the light of the open sky reaching in from above, and a little coming back up
            // off the ground.
            vec3 c = CloudLit.rgb * lit * (0.5 + 0.3 * h)
                   + CloudAmbient.rgb * (0.36 + 0.5 * h) * overhead
                   + CloudAmbient.rgb * 0.24 * (1.0 - h);
            c *= 1.0 - CloudOrigin.z * 0.35 * (1.0 - h);
            if (Bolt.w > 0.004) {
                // Lightning: the clouds near the stroke light up from within, the rest of the sky only faintly.
                vec2 away = p.xz - Bolt.xz;
                float glow = Bolt.w / (1.0 + dot(away, away) / (380.0 * 380.0));
                c += vec3(0.78, 0.84, 1.0) * glow * (0.7 + 0.5 * d);
            }
            // Distant cloud sinks into the colour of the horizon and thins out.
            float far = 1.0 - exp(-t * 0.0006);
            c = mix(c, Horizon.rgb * 0.94, far * 0.9);
            float a = (1.0 - exp(-d * 0.085 * len)) * (1.0 - smoothstep(1800.0, 5600.0, t) * 0.92);
            sum += c * a * show;
            show *= 1.0 - a;
            if (show < 0.02) {
                show = 0.0;
                break;
            }
        }
    }
    return vec4(sum, show);
}
