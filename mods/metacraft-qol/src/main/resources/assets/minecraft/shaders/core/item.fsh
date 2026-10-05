// Vanilla 26.3 item.fsh with one addition (metacraft-qol, the void anchor): where item.vsh flagged a
// void-anchor rift, the painted sprite is replaced by a view into space — stars and nebula at depth,
// a crackling edge — using the sprite's alpha as the rift's shape. Every other item takes the vanilla path. Re-diff against vanilla on
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
layout(location = 9) in vec3 riftPos;
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

float riftNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(riftHash(i), riftHash(i + vec2(1.0, 0.0)), u.x),
               mix(riftHash(i + vec2(0.0, 1.0)), riftHash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float riftFbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * riftNoise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

mat2 riftRot(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c);
}

// One layer of stars: a star in some of the cells of a grid, twinkling.
vec3 riftStars(vec2 p, float density, float t, float seed) {
    vec2 g = p * density;
    vec2 cell = floor(g);
    float h = riftHash(cell + seed);
    vec2 at = vec2(riftHash(cell + seed + 3.1), riftHash(cell + seed + 7.7)) * 0.8 + 0.1;
    float d = length(fract(g) - at);
    float twinkle = 0.6 + 0.4 * sin(t * (2.0 + h * 4.0) + h * 30.0);
    float star = smoothstep(0.13, 0.0, d) * step(0.68, h) * twinkle;
    vec3 tint = mix(vec3(0.80, 0.62, 1.00), vec3(0.60, 1.00, 0.95), riftHash(cell + seed + 11.0));
    return tint * star * 1.7;
}

// Where on the sprite this fragment is, 0..1 along the sprite's own axes. riftUv numbers the quad's
// corners from wherever its draw starts in a shared buffer, so it can come out turned by a quarter;
// texCoord0 cannot. The derivatives of both give the map from one to the other, and from it the
// sprite's box in the atlas, so the result doesn't depend on which corner came first.
vec2 riftLocal() {
    mat2 dUv = mat2(dFdx(riftUv), dFdy(riftUv));
    mat2 dTex = mat2(dFdx(texCoord0), dFdy(texCoord0));
    if (abs(determinant(dUv)) < 1e-12) return riftUv;
    mat2 m = dTex * inverse(dUv);
    vec2 o = texCoord0 - m * riftUv;
    vec2 c1 = o + m[0], c2 = o + m[1], c3 = o + m[0] + m[1];
    vec2 lo = min(min(o, c1), min(c2, c3)), hi = max(max(o, c1), max(c2, c3));
    return (texCoord0 - lo) / max(hi - lo, vec2(1e-9));
}

// The rift: a torn lens (the sprite's alpha) looking into space. Its stars and nebula sit at depths
// below the surface and shift with the angle you look from; its edge crackles magenta and teal.
// GameTime counts days, so * 1200 is seconds.
vec4 riftColor(vec4 sprite) {
    float t = GameTime * 1200.0;
    vec2 local = riftLocal();

    // the surface's own axes in view space, and how far one sprite-width is
    vec3 dPx = dFdx(riftPos), dPy = dFdy(riftPos);
    mat2 dL = mat2(dFdx(local), dFdy(local));
    vec3 tu = vec3(1.0, 0.0, 0.0), tv = vec3(0.0, 0.0, 1.0);
    if (abs(determinant(dL)) > 1e-12) {
        mat2 inv = inverse(dL);
        tu = dPx * inv[0][0] + dPy * inv[0][1];
        tv = dPx * inv[1][0] + dPy * inv[1][1];
    }
    float width = max(length(tu), 1e-4);
    vec3 n = normalize(cross(tu, tv));
    vec3 toEye = normalize(-riftPos);
    vec3 eye = vec3(dot(toEye, tu / width), dot(toEye, normalize(tv)), max(abs(dot(toEye, n)), 0.2));
    vec2 depth = eye.xy / eye.z / width;   // sprite-widths of shift per block of depth

    vec2 c = local - 0.5;
    vec3 space = vec3(0.012, 0.0, 0.03);
    vec2 nb = riftRot(t * 0.02) * (c - depth * 6.0);
    float cloud = riftFbm(nb * 3.0 + vec2(t * 0.03, -t * 0.02));
    float hue = riftFbm(nb * 6.0 - vec2(t * 0.05, t * 0.04) + 5.0);
    space += mix(vec3(0.34, 0.06, 0.50), vec3(0.05, 0.42, 0.42), hue) * smoothstep(0.30, 0.85, cloud);
    space += riftStars(riftRot(t * 0.015) * (c - depth * 4.0), 9.0, t, 1.0) * 0.55;
    space += riftStars(riftRot(-t * 0.02) * (c - depth * 2.0), 6.0, t, 7.0) * 0.8;
    space += riftStars(riftRot(t * 0.03) * (c - depth * 0.8), 4.0, t, 13.0);

    float a = sprite.a;
    float inside = smoothstep(0.80, 0.97, a);
    float crackle = riftFbm(local * 9.0 + vec2(t * 0.9, -t * 0.7));
    float band = exp(-pow((a - 0.70) / 0.18, 2.0));
    float rim = band * (0.6 + 1.0 * crackle);
    vec3 rimColor = mix(vec3(0.95, 0.35, 1.0), vec3(0.35, 1.0, 0.9), smoothstep(0.35, 0.75, riftFbm(local * 4.0 - t * 0.4)));
    float halo = (1.0 - inside) * smoothstep(0.0, 0.45, a) * (1.0 - band);

    vec3 color = space * inside + rimColor * (rim * 1.7 + halo * 0.8);
    float alpha = clamp(max(inside, max(rim, halo * 0.85)), 0.0, 1.0);
    return vec4(color, alpha);
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
