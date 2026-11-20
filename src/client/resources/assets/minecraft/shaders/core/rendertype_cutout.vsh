#version 150

// Living World override of the vanilla cut-out terrain vertex shader.
// Identical to vanilla except that vertices tagged as vegetation (see SwayModel) are displaced by the
// wind field and pushed aside by the player. Untagged vertices take exactly the vanilla path.

#moj_import <light.glsl>
#moj_import <fog.glsl>

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

uniform vec4 LWWind;     // xy = wind direction, z = sway strength (0 = off), w = time in seconds
uniform vec3 LWCamera;   // camera position wrapped to 4096 blocks, for a stable world-space phase
uniform vec4 LWBend;     // xyz = camera-relative feet position of the player, w = bending strength (0 = off)

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec4 normal;

void main() {
    vec3 pos = Position + ChunkOffset;
    vec4 tint = Color;

    // Vegetation is tagged in the otherwise unused vertex alpha: 254 - (kind * 16 + weight).
    int code = 254 - int(Color.a * 255.0 + 0.5);
    if (code >= 0 && code < 64) {
        tint.a = 1.0;
        int kind = code / 16;
        float w = float(code - kind * 16) / 15.0;
        if (w > 0.0) {
            if (LWWind.z > 0.001) {
                vec3 wp = pos + LWCamera;
                float t = LWWind.w;
                float s = LWWind.z;
                // A wave travelling downwind, so gusts visibly roll across fields and canopies.
                float along = dot(wp.xz, LWWind.xy);
                float gust = 0.5 + 0.5 * sin(along * 0.33 - t * (1.4 + 1.2 * s) + sin(wp.x * 0.11 + wp.z * 0.07) * 2.0);
                float flutter = sin(t * 5.1 + wp.x * 2.3 + wp.z * 1.9) + 0.5 * sin(t * 7.9 + wp.z * 3.7 - wp.x * 1.3);
                vec2 d = LWWind.xy * (0.02 + 0.14 * s) * (0.3 + gust) + vec2(flutter, flutter * 0.7) * 0.01 * (0.4 + s);
                if (kind == 0) {
                    // Rooted plants: anchored at the base, bending progressively toward the tip.
                    float bend = w * w;
                    pos.xz += d * bend;
                    pos.y -= length(d) * bend * 0.35;
                } else {
                    // Leaves, vines and stacked plants: a small displacement that depends only on world
                    // position, so neighbouring blocks stay joined.
                    pos.xz += d * 0.3 * w;
                    pos.y += flutter * 0.006 * w * (0.4 + s);
                }
            }
            if (kind == 0 && LWBend.w > 0.001) {
                vec3 away = pos - LWBend.xyz;
                float r = length(away.xz);
                float push = (1.0 - smoothstep(0.25, 1.1, r)) * (1.0 - smoothstep(0.7, 1.7, abs(away.y - 0.6))) * w * LWBend.w;
                pos.xz += (r > 0.001 ? away.xz / r : vec2(0.0)) * push * 0.3;
                pos.y -= push * 0.2;
            }
        }
    }

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(ModelViewMat, pos, FogShape);
    vertexColor = tint * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    normal = ProjMat * ModelViewMat * vec4(Normal, 0.0);
}
