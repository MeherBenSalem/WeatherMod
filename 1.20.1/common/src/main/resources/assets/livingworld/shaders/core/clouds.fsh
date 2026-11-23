#version 150

// Living World: the cloud layer on its own, drawn into a half-resolution buffer.
// The atmosphere pass then takes the clouds for every pixel of open sky from that buffer instead of
// working them out per pixel: a quarter of the cost, and clouds are soft enough not to show it.

#moj_import <lw_sky.glsl>

uniform sampler2D NoiseSampler;

uniform mat4 InvViewProj;
uniform vec4 CloudShape;
uniform vec4 CloudOrigin;
uniform vec4 CloudLit;
uniform vec4 CloudAmbient;
uniform vec4 Horizon;
uniform vec3 SunDir;
uniform vec4 Bolt;

in vec2 texCoord;

out vec4 fragColor;

#moj_import <lw_clouds.glsl>

void main() {
    vec4 far = InvViewProj * vec4(texCoord * 2.0 - 1.0, 1.0, 1.0);
    fragColor = marchClouds(normalize(far.xyz / far.w), 1.0e6);
}
