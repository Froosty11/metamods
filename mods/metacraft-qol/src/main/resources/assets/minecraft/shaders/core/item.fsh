// Vanilla 26.3 item.fsh with one addition (metacraft-qol, the void anchor): where item.vsh flagged a
// void-anchor rift, the painted sprite is replaced by a moving swirl of stars, using the sprite's
// alpha as the rift's shape. Every other item takes the vanilla path. Re-diff against vanilla on
// every Minecraft update.
#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

#ifdef GLINT
uniform sampler2D GlintSampler;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#endif
layout(location = 2) in vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location = 3) in vec4 lightMapColor;
layout(location = 4) in vec4 overlayColor;
#endif
layout(location = 5) in vec2 texCoord0;
layout(location = 7) in vec2 riftUv;
layout(location = 8) in float riftFlag;
#ifdef GLINT
layout(location = 6) in vec2 texCoordGlint;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

#ifndef OIT_ALPHA_ONLY
vec4 calculateFinalColor(vec4 color) {
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

    #ifdef GLINT
    vec4 glintColor = GlintAlpha * texture(GlintSampler, texCoordGlint);// Glint color modulator?
    // Matches BlendFuntion.GLINT
    color.rgb += glintColor.rgb * glintColor.rgb;
    #endif

    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif

    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}
#endif

float riftHash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// The rift: polar coordinates round the quad's centre, bands that wind inward and turn with
// time, stars drifting through them, a bright rim. GameTime counts days, so * 1200 is seconds.
// Everything that depends on the angle repeats every quarter turn: item.vsh numbers the corners
// from wherever the draw starts in a shared buffer, which turns the pattern by a multiple of 90°,
// and a pattern that looks the same turned a quarter cannot jump when that happens.
const float RIFT_QUARTER = 1.5707963;

vec4 riftColor(vec4 sprite) {
    vec2 p = riftUv * 2.0 - 1.0;
    float r = length(p);
    float a = atan(p.y, p.x);
    float t = GameTime * 1200.0;
    float swirl = a + 2.6 * (1.0 - r) + t * 0.9;
    float bands = 0.5 + 0.5 * sin(swirl * 4.0 + r * 9.0 - t * 2.2);
    vec2 starCell = floor(vec2(mod(swirl, RIFT_QUARTER) / RIFT_QUARTER * 6.0, r * 14.0 - t * 1.5));
    float star = step(0.93, riftHash(starCell)) * smoothstep(1.0, 0.3, r);
    vec3 deep = vec3(0.02, 0.0, 0.06);
    vec3 purple = vec3(0.42, 0.10, 0.70);
    vec3 teal = vec3(0.10, 0.78, 0.72);
    vec3 col = mix(deep, mix(purple, teal, bands), smoothstep(0.05, 0.85, r) * (0.35 + 0.65 * bands));
    col += star * vec3(0.95, 0.9, 1.0);
    float rim = smoothstep(0.72, 0.9, r);
    col = mix(col, vec3(0.85, 0.6, 1.0), rim * 0.7);
    return vec4(col, sprite.a);
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    color *= vertexColor * ColorModulator;

    if (riftFlag > 0.5) {
        color = riftColor(texture(Sampler0, texCoord0));
    }

    #ifdef GLINT
    color.a = max(color.a, GlintAlpha);
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
