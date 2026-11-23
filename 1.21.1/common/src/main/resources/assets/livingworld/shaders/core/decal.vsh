#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightColor;
out vec2 texCoord0;
out vec3 relPos;
out vec2 decalData;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexDistance = fog_distance(Position, FogShape);
    vertexColor = Color;
    lightColor = texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
    // Camera-relative position with world-aligned axes: used to derive the decal's tangent frame.
    relPos = Position;
    // The normal attribute is repurposed: x = wear (0 fresh .. 1 gone), y = 1 for flat wet stains.
    decalData = Normal.xy;
}
