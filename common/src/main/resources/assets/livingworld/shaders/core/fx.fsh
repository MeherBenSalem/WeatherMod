#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// Soft particles. Unlike the stock particle shader this keeps faint alpha (the stock one discards
// everything under 10%, which would cut hard edges into mist, dust and fading sprites), and it fades
// into fog instead of turning fog-coloured.
void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    color.a *= linear_fog_fade(vertexDistance, FogStart, FogEnd);
    if (color.a < 0.003) {
        discard;
    }
    fragColor = color;
}
