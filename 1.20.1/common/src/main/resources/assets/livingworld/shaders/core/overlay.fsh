#version 150

// Living World lens overlay: what the weather does to the "glass" between the player and the world.
// Drawn after the world and the hand, before the HUD. Only runs while one of its effects is visible.

uniform sampler2D DiffuseSampler;   // finished frame
uniform sampler2D FrostSampler;     // frost crystals growing in from the screen edges (alpha = thickness)
uniform sampler2D DropSampler;      // RG = refraction offset, B = highlight, A = droplet mask

uniform vec4 Lens;   // x = frost amount, y = droplet amount, z = seconds, w = aspect ratio
uniform vec4 Tone;   // x = heat vignette, y = cold vignette, zw unused

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    vec2 offset = vec2(0.0);
    float highlight = 0.0;

    if (Lens.y > 0.004) {
        // Two layers of droplets creeping down the lens at different speeds; the amount decides how
        // much of each droplet's body is visible, so they appear to bead up and dry off.
        // The first layer fades in with the amount; the second only joins when the lens is really wet.
        for (int i = 0; i < 2; i++) {
            float fi = float(i);
            vec2 duv = vec2(uv.x * Lens.w, 1.0 - uv.y) * (0.7 + 0.45 * fi) + vec2(0.37 * fi, -Lens.z * (0.008 + 0.012 * fi));
            vec4 drop = texture(DropSampler, duv);
            float body = drop.a * smoothstep(0.08 + 0.45 * fi, 0.6 + 0.4 * fi, Lens.y);
            offset += (drop.rg - 0.5) * vec2(1.0, -1.0) * body * 0.05;
            highlight += drop.b * body;
        }
    }

    vec3 col = texture(DiffuseSampler, clamp(uv + offset, vec2(0.001), vec2(0.999))).rgb;
    col += vec3(0.9, 0.95, 1.0) * highlight * 0.18;

    float vignette = smoothstep(0.32, 0.8, length(uv - 0.5));
    col = mix(col, col * vec3(1.1, 0.96, 0.8), vignette * Tone.x);
    col = mix(col, col * vec3(0.78, 0.9, 1.1), vignette * Tone.y);

    if (Lens.x > 0.004) {
        vec4 frost = texture(FrostSampler, uv);
        // Thickest ice (the screen edge) appears first and the crystals creep inward as it gets colder.
        float ice = smoothstep(1.0 - Lens.x, 1.0 - Lens.x + 0.4, frost.a) * frost.a;
        col = mix(col, frost.rgb, ice * 0.85);
    }

    fragColor = vec4(col, 1.0);
}
