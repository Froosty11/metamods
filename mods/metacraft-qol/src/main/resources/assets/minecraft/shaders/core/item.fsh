// Vanilla 26.3 item.fsh with one addition (metacraft-qol, the void anchor): where item.vsh flagged a
// void-anchor rift, the painted sprite is replaced. The main crack (1) shows the End's void through
// its split with glowing edges, a glowing crack (2) is all light, both using the sprite's alpha as
// the crack's shape; the core (3) is a swirling white-hot vortex. Every other item takes the vanilla
// path. Re-diff against vanilla on every Minecraft update.
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

// The End portal's colours, one per layer, as vanilla's rendertype_end_portal has them.
const vec3 RIFT_LAYER_COLORS[16] = vec3[](
    vec3(0.022087, 0.098399, 0.110818), vec3(0.011892, 0.095924, 0.089485), vec3(0.027636, 0.101689, 0.100326),
    vec3(0.046564, 0.109883, 0.114838), vec3(0.064901, 0.117696, 0.097189), vec3(0.063761, 0.086895, 0.123646),
    vec3(0.084817, 0.111994, 0.166380), vec3(0.097489, 0.154120, 0.091064), vec3(0.106152, 0.131144, 0.195191),
    vec3(0.097721, 0.110188, 0.187229), vec3(0.133516, 0.138278, 0.148582), vec3(0.070006, 0.243332, 0.235792),
    vec3(0.196766, 0.142899, 0.214696), vec3(0.047281, 0.315338, 0.321970), vec3(0.204675, 0.390010, 0.302066),
    vec3(0.080955, 0.314821, 0.661491)
);

// Sparse specks, as the end portal texture's: one in some cells of a grid.
float riftSpecks(vec2 p, float seed) {
    vec2 g = p * 12.0;
    vec2 cell = floor(g);
    float h = riftHash(cell + seed);
    vec2 at = vec2(riftHash(cell + seed + 3.1), riftHash(cell + seed + 7.7)) * 0.7 + 0.15;
    return smoothstep(0.16, 0.02, length(fract(g) - at)) * step(0.80, h);
}

// The void behind the crack, the way the End portal and gateway draw theirs: layers of specks laid
// in screen space, each turned, scaled and drifting its own way, so the void stays put on the screen
// while the crack moves over it and reads as endlessly deep.
vec3 riftVoid() {
    vec2 screen = gl_FragCoord.xy / ScreenSize.y;
    vec3 color = RIFT_LAYER_COLORS[0] * 0.6;
    for (int i = 0; i < 15; i++) {
        float layer = float(i + 1);
        vec2 p = riftRot(radians((layer * layer * 4321.0 + layer * 9.0) * 2.0)) * (screen * (4.5 - layer / 4.0) * 0.5)
                + vec2(17.0 / layer, (2.0 + layer / 1.5) * GameTime * 1.5);
        color += riftSpecks(p, layer * 13.0) * RIFT_LAYER_COLORS[i] * 2.4;
    }
    return color;
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

// The rift: a crack in space (the sprite's alpha). Through the split, the End's void; along its
// edges and hairline branches, white-hot light flickering magenta and violet; round it, a glow.
// GameTime counts days, so * 1200 is seconds.
vec4 riftColor(vec4 sprite) {
    float t = GameTime * 1200.0;
    vec2 local = riftLocal();
    float a = sprite.a;
    float inside = smoothstep(0.90, 0.99, a);
    float edge = exp(-pow((a - 0.76) / 0.16, 2.0));
    float flicker = 0.7 + 0.6 * riftFbm(local * 14.0 + vec2(t * 1.6, -t * 1.1));
    vec3 hot = mix(vec3(1.0, 0.28, 0.95), vec3(0.5, 0.22, 1.0), smoothstep(0.42, 0.68, riftFbm(local * 5.0 - t * 0.5)));
    vec3 line = mix(hot, vec3(1.0, 0.92, 1.0), 0.35 * smoothstep(0.82, 0.92, a));
    float halo = (1.0 - inside) * smoothstep(0.0, 0.45, a) * (1.0 - edge);
    vec3 color = riftVoid() * inside + line * edge * flicker * 1.5 + hot * halo * 1.3;
    float alpha = clamp(max(inside, max(edge * flicker, halo)), 0.0, 1.0);
    return vec4(color, alpha);
}

// A glowing crack, as the shatter rift crosses its main one with: no void, the split and its edges
// white-hot, the hairlines lit, and a wide soft glow smeared along them that drifts like an aurora.
vec4 riftGlow(vec4 sprite) {
    float t = GameTime * 1200.0;
    vec2 local = riftLocal();
    float a = sprite.a;
    float core = smoothstep(0.9, 0.99, a);
    float edge = smoothstep(0.5, 0.8, a) * (1.0 - core);
    float flicker = 0.75 + 0.5 * riftFbm(local * 12.0 + vec2(t * 2.0, -t * 1.3));
    // the halo's alpha falls off fast; its root spreads the glow wider
    float glow = (1.0 - smoothstep(0.45, 0.6, a)) * sqrt(clamp(a / 0.45, 0.0, 1.0));
    float aurora = 0.45 + 0.9 * riftFbm(vec2(local.x * 3.0 - t * 0.7, local.y * 9.0 + t * 0.3));
    vec3 hot = mix(vec3(1.0, 0.3, 0.95), vec3(0.55, 0.25, 1.0), riftFbm(local * 4.0 + t * 0.4));
    vec3 color = mix(hot * 1.4, vec3(1.0, 0.92, 1.0), core);
    float alpha = clamp(max(core, max(edge * flicker, glow * aurora * 0.6)), 0.0, 1.0);
    return vec4(color, alpha);
}

// The shatter rift's core, facing the camera: a white-hot point, a violet glow round it, and arms of
// light swirling into it.
vec4 riftCore() {
    float t = GameTime * 1200.0;
    vec2 p = riftLocal() * 2.0 - 1.0;
    float r = length(p);
    float angle = atan(p.y, p.x);
    float pulse = 1.0 + 0.12 * sin(t * 5.0);
    float hot = exp(-pow(r / (0.13 * pulse), 2.0));
    float glow = exp(-r / 0.2) * (1.0 - smoothstep(0.6, 1.0, r));
    float arms = pow(0.5 + 0.5 * sin(angle * 3.0 + r * 14.0 - t * 6.0), 6.0);
    float rays = smoothstep(0.55, 0.95, riftFbm(vec2(angle * 4.0, r * 2.0 - t * 1.5)));
    float swirl = (arms * 0.8 + rays * 0.5) * exp(-r / 0.35) * (1.0 - smoothstep(0.5, 1.0, r));
    vec3 color = mix(vec3(0.72, 0.3, 1.0), vec3(1.0, 0.55, 1.0), glow) + vec3(1.0, 0.95, 1.0) * hot * 2.0;
    float alpha = clamp(hot + glow * 0.85 + swirl, 0.0, 1.0);
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

    if (riftFlag > 2.5) {
        color = riftCore();
    } else if (riftFlag > 1.5) {
        color = riftGlow(texture(Sampler0, texCoord0));
    } else if (riftFlag > 0.5) {
        color = riftColor(texture(Sampler0, texCoord0));
    }
    // Nothing to draw: leave no depth behind either, or a rift's empty corners hide its other parts.
    if (riftFlag > 0.5 && color.a < 0.02) {
        discard;
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
