#version 150

#moj_import <fog.glsl>

// Pixel-art decal sheet, sampled with nearest filtering so every texel stays a crisp block:
// RG = per-texel tangent-space normal, B = height (0.5 = ground level), A = 1 on print texels.
uniform sampler2D Sampler0;

uniform float FogStart;
uniform float FogEnd;
// xyz = unit direction toward the sun or moon (world space), w = how much direct light arrives.
uniform vec4 LightDir;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightColor;
in vec2 texCoord0;
in vec3 relPos;
in vec2 decalData;

out vec4 fragColor;

void main() {
    vec4 nh = texture(Sampler0, texCoord0);
    float mask = nh.a;
    float wear = clamp(decalData.x, 0.0, 1.0);
    float fogFade = linear_fog_fade(vertexDistance, FogStart, FogEnd);
    vec3 tint = vertexColor.rgb;
    float depth = clamp((0.5 - nh.b) * 2.6, 0.0, 1.0);

    if (decalData.y > 0.5) {
        // Wet print on hard ground: a damp darkening in the shape of the sole that simply dries out.
        float stain = smoothstep(0.02, 0.2, depth) * mask * 0.55 * (1.0 - wear) * vertexColor.a * fogFade;
        if (stain < 0.004) {
            discard;
        }
        fragColor = vec4(tint * lightColor.rgb, stain);
        return;
    }

    // Tangent frame of the quad from screen-space derivatives. This handles any rotation and the
    // mirrored UVs of right-foot prints without extra vertex data.
    vec3 dp1 = dFdx(relPos);
    vec3 dp2 = dFdy(relPos);
    vec2 du1 = dFdx(texCoord0);
    vec2 du2 = dFdy(texCoord0);
    float handed = (du1.x * du2.y - du1.y * du2.x) < 0.0 ? -1.0 : 1.0;
    vec3 tangent = normalize((dp1 * du2.y - dp2 * du1.y) * handed + vec3(1.0e-6, 0.0, 0.0));
    vec3 bitangent = normalize((dp2 * du1.x - dp1 * du2.x) * handed + vec3(0.0, 0.0, 1.0e-6));

    vec2 nxy = nh.rg * 2.0 - 1.0;
    float nz = sqrt(max(0.05, 1.0 - dot(nxy, nxy)));
    // +y in the normal map points to the top of the cell, which is -v.
    vec3 normal = normalize(tangent * nxy.x - bitangent * nxy.y + vec3(0.0, nz, 0.0));

    // Lighting relative to flat ground: positive on texels turned toward the light, negative on those turned away.
    float lit = (dot(normal, LightDir.xyz) - LightDir.y) * LightDir.w;
    // The print wears away texel by texel: the shallow ones go first, the deepest last.
    float erode = step(wear * 1.15, depth + 0.12) * (1.0 - wear * wear * wear);

    // Each texel is a flat shade of the ground material: darker the deeper it is pressed in, a little
    // less dark on the side that catches the sun, and never lighter than the ground around it, so the
    // print always reads as pressed in rather than as a raised stud.
    float level = clamp(0.72 - depth * 0.2 + lit * 0.4, 0.42, 0.92);
    float alpha = (0.55 + 0.3 * depth) * mask * erode * vertexColor.a * fogFade;
    if (alpha < 0.004) {
        discard;
    }
    fragColor = vec4(tint * level * lightColor.rgb, alpha);
}
