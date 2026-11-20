#version 150

// Living World override of the vanilla translucent terrain vertex shader.
// Identical to vanilla except that vertices tagged as water (see FluidRendererMixin) on the water's
// surface ride a wind-driven swell, and pass what the fragment shader needs to shade the water.
// Everything else in the layer (glass, ice, slime) takes the vanilla path.

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

uniform vec4 LWWater;    // x = time in seconds, yz = wind direction, w = wave height (0 = water shading off)
uniform vec3 LWCamera;   // camera position wrapped to 4096 blocks, for a stable world-space phase

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec4 normal;
out vec3 lwRel;      // position relative to the camera
out vec3 lwWorld;    // world position (x and z wrapped)
out vec2 lwWater;    // x = 1 on water, y = sky light reaching it (0..1)
out vec3 lwTint;     // the biome's water colour
out vec3 lwLight;    // light arriving at the surface (block light + sky light, from the lightmap)

void main() {
    vec3 pos = Position + ChunkOffset;
    vec4 tint = Color;
    float water = 0.0;
    vec3 world = pos + LWCamera;
    float sky = clamp(float(UV2.y) / 240.0, 0.0, 1.0);

    // Water is tagged with a vertex alpha of 250/255 (vanilla always writes 255).
    if (Color.a > 0.972 && Color.a < 0.988) {
        tint.a = 1.0;
        water = 1.0;
        if (LWWater.w > 0.001) {
            // Only the surface moves: the bottom of a water block, and water with more water above it,
            // sit on whole block heights and stay put so nothing ever opens a gap.
            float h = fract(Position.y);
            float surface = step(0.05, h) * step(h, 0.95);
            vec2 w = LWWater.yz;
            vec2 c = vec2(-w.y, w.x);
            float t = LWWater.x;
            float swell = sin(dot(world.xz, w) * 0.9 - t * 1.5) * 0.55
                        + sin(dot(world.xz, w * 0.6 + c * 0.8) * 1.7 - t * 2.1 + 1.3) * 0.3
                        + sin(dot(world.xz, c) * 1.3 + t * 1.1) * 0.15;
            pos.y += swell * 0.045 * LWWater.w * (0.35 + 0.65 * sky) * surface;
        }
    }

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vec4 lightmap = minecraft_sample_lightmap(Sampler2, UV2);
    vertexDistance = fog_distance(ModelViewMat, pos, FogShape);
    vertexColor = tint * lightmap;
    texCoord0 = UV0;
    normal = ProjMat * ModelViewMat * vec4(Normal, 0.0);
    lwRel = pos;
    lwWorld = world;
    lwWater = vec2(water, sky);
    lwTint = Color.rgb;
    lwLight = lightmap.rgb;
}
