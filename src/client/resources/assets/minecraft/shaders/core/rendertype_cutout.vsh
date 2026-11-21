#version 150

// Living World override of the vanilla cut-out terrain vertex shader.
// Identical to vanilla except that
//   - vertices tagged as vegetation (see SwayModel) are displaced by the wind and pushed aside by the player;
//   - vertices carrying a biome's green tint (leaves, grass, vines) change colour with the season.
// Everything else takes exactly the vanilla path. The work itself is in lw_foliage.glsl, shared with the
// Sodium patch.

#moj_import <light.glsl>
#moj_import <fog.glsl>
#moj_import <lw_water.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;

uniform vec4 LWSway;     // xy = wind direction, z = sway strength (0 = off), w = time in seconds
uniform vec4 LWDrift;    // xy = how far the wind has carried its gusts, in blocks
uniform vec3 LWCamera;   // camera position wrapped to 4096 blocks, for a stable world-space phase
uniform vec4 LWBend;     // xyz = camera-relative feet position of the player, w = bending strength (0 = off)
uniform vec4 LWSeason;   // x = autumn colour, y = winter bareness, z = spring freshness, w = snow lying on the ground (all 0 = off)

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec4 normal;

#moj_import <lw_foliage.glsl>

void main() {
    vec3 pos = Position + ChunkOffset;
    vec4 tint = Color;
    vec3 world = pos + LWCamera;
    bool leaf = false;

    // Vegetation is tagged in the otherwise unused vertex alpha: 254 - (kind * 16 + weight).
    int code = 254 - int(Color.a * 255.0 + 0.5);
    if (code >= 0 && code < 64) {
        tint.a = 1.0;
        int kind = code / 16;
        float w = float(code - kind * 16) / 15.0;
        leaf = code == 31;
        if (w > 0.0) {
            lwSway(pos, world, kind, w);
        }
    }

    // A biome tint is green-dominant; an untinted vertex is a shade of grey and is left alone.
    if (dot(LWSeason, vec4(1.0)) > 0.001 && Color.g > 0.03 && Color.r < Color.g * 0.93 && Color.b < Color.g * 0.93) {
        tint.rgb = clamp(lwSeasonal(Color.rgb, leaf, world, Normal.y), 0.0, 2.0);
    }

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(ModelViewMat, pos, FogShape);
    vertexColor = tint * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    normal = ProjMat * ModelViewMat * vec4(Normal, 0.0);
}
