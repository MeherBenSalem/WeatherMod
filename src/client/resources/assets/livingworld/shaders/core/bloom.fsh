#version 150

// Living World bloom, run at quarter resolution.
// Mode.x = 0: keep only the brightest parts of the frame (box-filtered while shrinking it).
// Mode.x = 1: one direction of a separable Gaussian blur.

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;   // prefilter only: the frame's depth, to tell the sky from things in it

uniform vec2 Step;   // prefilter: one source texel; blur: one texel along the blur direction
uniform vec2 Mode;   // x = pass, y = brightness threshold
uniform float SkyBloom;   // how much of the open sky's own brightness is allowed to bloom

in vec2 texCoord;

out vec4 fragColor;

void main() {
    if (Mode.x < 0.5) {
        vec3 c = (texture(DiffuseSampler, texCoord + vec2(-Step.x, -Step.y)).rgb
                + texture(DiffuseSampler, texCoord + vec2(Step.x, -Step.y)).rgb
                + texture(DiffuseSampler, texCoord + vec2(-Step.x, Step.y)).rgb
                + texture(DiffuseSampler, texCoord + vec2(Step.x, Step.y)).rgb) * 0.25;
        float peak = max(c.r, max(c.g, c.b));
        // A daytime sky is bright all over (its blue channel sits at full), and letting all of it bloom
        // lays a white veil over the horizon. By day the sky is mostly left out; at night the moon and
        // stars keep their glow.
        float open = texture(DepthSampler, texCoord).r >= 0.999999 ? SkyBloom : 1.0;
        fragColor = vec4(c * smoothstep(Mode.y, Mode.y + 0.22, peak) * open, 1.0);
    } else {
        // Nine-tap Gaussian folded into five bilinear fetches.
        vec3 c = texture(DiffuseSampler, texCoord).rgb * 0.2270270270;
        c += (texture(DiffuseSampler, texCoord + Step * 1.3846153846).rgb
            + texture(DiffuseSampler, texCoord - Step * 1.3846153846).rgb) * 0.3162162162;
        c += (texture(DiffuseSampler, texCoord + Step * 3.2307692308).rgb
            + texture(DiffuseSampler, texCoord - Step * 3.2307692308).rgb) * 0.0702702703;
        fragColor = vec4(c, 1.0);
    }
}
