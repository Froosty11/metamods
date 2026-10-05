// Vanilla 26.3 item.vsh with one addition (metacraft-qol, the void anchor): a vertex tinted exactly
// #FEFEFD belongs to a void-anchor rift, and gets a flag plus a corner index for its place on the quad.
// Re-diff against vanilla on every Minecraft update.
#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
#ifdef GLINT_SPECIAL
layout(location = 5) in vec2 UV3;
#endif
layout(location = 6) in vec3 Normal;

#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
#endif
layout(location = 2) out vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location = 3) out vec4 lightMapColor;
layout(location = 4) out vec4 overlayColor;
#endif

layout(location = 5) out vec2 texCoord0;
layout(location = 7) out vec2 riftUv;
layout(location = 8) out float riftFlag;
#ifdef GLINT
layout(location = 6) out vec2 texCoordGlint;
#endif

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    #ifndef OIT_ALPHA_ONLY
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    #endif
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    #ifndef OIT_ALPHA_ONLY
    lightMapColor = sample_lightmap(Sampler2, UV2);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    #endif

    texCoord0 = UV0;

    if (all(lessThan(abs(Color.rgb - vec3(254.0, 254.0, 253.0) / 255.0), vec3(0.5 / 255.0)))) {
        // Quads are drawn as four vertices in order round the face; which one this is gives its corner.
        int corner = gl_VertexIndex % 4;
        riftFlag = 1.0;
        riftUv = vec2((corner == 1 || corner == 2) ? 1.0 : 0.0, corner >= 2 ? 1.0 : 0.0);
    } else {
        riftFlag = 0.0;
        riftUv = vec2(0.0);
    }
    #ifdef GLINT
    #ifdef GLINT_SPECIAL
    texCoordGlint = (TextureMat * vec4(UV3, 0.0, 1.0)).xy;
    #else
    texCoordGlint = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
    #endif
    #endif
}
