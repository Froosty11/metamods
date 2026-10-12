// Nether rifts (nether-rifts): the body is a churning swirl laid in screen space, the way the End portal and
// the void anchor draw their void, so the many panels a rift is built from join without a seam. The edge
// is a thin strip along the rift's outline that glows and flickers. Stage 1 to 3 speeds the swirl up,
// reddens it, and makes both flicker harder. Needs rift_common.glsl and Globals.

vec4 netherRiftBody(float stage) {
    float t = GameTime * 1200.0;
    vec2 p = gl_FragCoord.xy / ScreenSize.y * 3.0;
    float speed = 0.25 + 0.2 * stage;
    vec2 q = vec2(riftFbm(p + vec2(t * speed * 0.6, -t * speed * 0.4)), riftFbm(p * 1.3 - vec2(t * speed * 0.3, t * speed * 0.5)));
    float swirl = riftFbm(p * 1.7 + q * 2.5 + t * speed * 0.2);
    vec3 color = mix(vec3(0.10, 0.02, 0.18), vec3(0.55, 0.12, 0.85), smoothstep(0.30, 0.75, swirl));
    color = mix(color, vec3(0.85, 0.12, 0.18), smoothstep(0.55, 0.90, swirl) * (0.25 + 0.25 * stage));
    vec2 cell = floor(p * 18.0 + vec2(0.0, t * 2.0));
    float ember = step(0.985 - 0.004 * stage, riftHash(cell)) * (0.6 + 0.4 * sin(t * 9.0 + riftHash(cell + 3.7) * 6.28));
    color += vec3(1.0, 0.55, 0.15) * ember;
    float flicker = 1.0 - (0.06 + 0.07 * stage) * step(0.93 - 0.03 * stage, riftHash(vec2(floor(t * (6.0 + 4.0 * stage)), 1.3)));
    return vec4(color * flicker, 0.92);
}

vec4 netherRiftEdge(float stage) {
    float t = GameTime * 1200.0;
    vec2 local = riftLocal();
    float across = abs(local.y * 2.0 - 1.0);
    float glow = pow(1.0 - across, 2.0);
    float flicker = 0.65 + (0.25 + 0.15 * stage) * riftFbm(vec2(local.x * 10.0 + t * (1.5 + stage), t * 0.7));
    vec3 hot = mix(vec3(1.0, 0.45, 0.95), vec3(1.0, 0.35, 0.2), smoothstep(0.4, 0.8, riftFbm(local * 6.0 - t * 0.6)) * (stage - 1.0) * 0.5);
    return vec4(mix(hot, vec3(1.0, 0.95, 1.0), glow * glow * 0.5), clamp(glow * flicker, 0.0, 1.0));
}
