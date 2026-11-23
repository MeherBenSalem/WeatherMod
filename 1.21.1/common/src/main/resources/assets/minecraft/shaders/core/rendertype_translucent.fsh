#version 150

// Living World override of the vanilla translucent terrain fragment shader.
//
// Water is shaded as water instead of as a tinted, animated texture:
//   - you see through it: the scene behind is refracted by the ripples, and loses its red first with
//     depth, so shallows are clear and the deeps go blue-green in the biome's water colour;
//   - it mirrors the world: reflections are traced in screen space against the scene behind, and fall
//     back to the sky and its clouds where the ray leaves the screen;
//   - the sun or moon glitters on it, the bed below carries caustics, rain rings it, and it fades
//     softly into the shore instead of ending in a hard line.
// The shading itself lives in lw_water_surface.glsl (shared with the Sodium patch), the shape of the
// surface in lw_water.glsl.
// Everything else in the layer (glass, ice, slime) takes the vanilla path unchanged.

#moj_import <fog.glsl>
#moj_import <lw_water.glsl>
#moj_import <lw_sky.glsl>

uniform sampler2D Sampler0;
uniform sampler2D LWScene;     // the frame as it was just before translucent terrain: what lies behind the water
uniform sampler2D LWDepth;     // its depth
uniform sampler2D LWNoise;     // the mod's tiling noise texture (cloud shapes)
uniform sampler2D LWColumns;   // 64x64 wrap-around terrain columns around the camera; B = ground flag + nearness to the waterline

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

uniform vec4 LWWater;          // x = wave clock in seconds, yz = how far the wind has carried the ripples, w = wave height (0 = off)
uniform vec4 LWWind;           // xy = direction the wind blows toward, z = its speed (0 calm .. 1.6), w = seconds of real time
uniform vec4 LWSun;            // xyz = direction toward the sun or moon, w = how much of its direct light arrives
uniform vec3 LWSunColor;       // colour of that light
uniform vec4 LWSky;            // rgb = colour of the sky overhead, a = 1 when water shading is on
uniform vec4 LWCloud;          // x = cloud cover (0 = none drawn), y = height of the cloud layer above the camera, zw = cloud origin
uniform vec4 LWCloudColor;     // rgb = colour of the clouds as seen from below, a = 1 when surf is drawn along shores
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

#moj_import <lw_water_surface.glsl>

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;

    if (lwWater.x > 0.5 && LWSky.a > 0.5) {
        float streaks = dot(texture(Sampler0, texCoord0).rgb, vec3(0.3333));
        color = lwShadeWater(lwRel, lwWorld, lwWater.y, lwTint, lwLight, FogColor.rgb, streaks, mat3(ModelViewMat)) * ColorModulator;
    }

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
