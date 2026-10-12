// Nether rifts (nether-rifts), drawn as pixel art on a grid fixed to the world, 16 pixels a block like block
// textures, so the panels a rift is built from join without a seam and nothing swims as the camera moves.
// The body is a membrane in the nether portal's purples, swirling at 20 frames a second, with a glitch over
// it (rows torn sideways, a magenta/cyan channel split, flashing corrupt tiles, scanlines). Cracks run through
// it, and through them the nether shows: the panel's sprite is a 360-degree nether panorama, looked up by view
// direction like a skybox, so it reads as a far place seen through a hole. Stage 1 to 3 glitches harder,
// widens and branches the cracks (at 3 only islands of membrane are left) and turns the purples red.
// The edge is a pixel strip along the outline that flickers and drops pixels. Needs rift_common.glsl,
// Globals, Sampler0, riftWorld (world position less CameraBlockPos) and riftView (relative to the camera).

// Exact integer hashing, so the grid is the same anywhere in the world.
uint netherRiftMix(uint h) {
    h ^= h >> 16;
    h *= 0x7feb352du;
    h ^= h >> 15;
    h *= 0x846ca68bu;
    h ^= h >> 16;
    return h;
}

uint netherRiftHashU(ivec3 p) {
    uvec3 u = uvec3(p);
    return netherRiftMix(u.x ^ netherRiftMix(u.y ^ netherRiftMix(u.z + 0x9e3779b9u)));
}

float netherRiftHash(ivec3 p) {
    return float(netherRiftHashU(p) >> 8) * (1.0 / 16777216.0);
}

// Value noise on a lattice of 2^shift pixels; off moves it, in lattice cells.
float netherRiftNoise(ivec2 px, int shift, vec2 off, int seed) {
    int size = 1 << shift;
    vec2 local = (vec2(px & ivec2(size - 1)) + 0.5) / float(size) + off;
    vec2 fl = floor(local);
    ivec2 i = (px >> shift) + ivec2(fl);
    vec2 f = local - fl;
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = netherRiftHash(ivec3(i, seed)), b = netherRiftHash(ivec3(i + ivec2(1, 0), seed));
    float c = netherRiftHash(ivec3(i + ivec2(0, 1), seed)), d = netherRiftHash(ivec3(i + ivec2(1, 1), seed));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float netherRiftFbm(ivec2 px, int shift, vec2 off, int seed) {
    return netherRiftNoise(px, shift, off, seed) * 0.55 + netherRiftNoise(px, shift - 1, off * 2.0, seed + 7) * 0.3
            + netherRiftNoise(px, shift - 2, off * 4.0, seed + 13) * 0.15;
}

// This fragment's cell on the world's pixel grid, in the panel's plane: x along the panel (world x or z,
// whichever it spans; the plane's normal comes from the derivatives), y up.
ivec2 netherRiftPixel() {
    vec3 n = cross(dFdx(riftWorld), dFdy(riftWorld));
    bool alongX = abs(n.z) >= abs(n.x);
    vec2 inPlane = vec2(alongX ? riftWorld.x : riftWorld.z, riftWorld.y);
    ivec2 base = ivec2(alongX ? CameraBlockPos.x : CameraBlockPos.z, CameraBlockPos.y);
    return ivec2(floor(inPlane * 16.0)) + base * 16;
}

// The nether portal's purples, dark to pale, and the red they turn to.
const vec3 NETHER_RIFT_PURPLE[6] = vec3[](
    vec3(20.0, 6.0, 34.0) / 255.0, vec3(48.0, 14.0, 78.0) / 255.0, vec3(92.0, 28.0, 140.0) / 255.0,
    vec3(140.0, 54.0, 196.0) / 255.0, vec3(196.0, 110.0, 236.0) / 255.0, vec3(244.0, 200.0, 255.0) / 255.0
);
const vec3 NETHER_RIFT_RED[6] = vec3[](
    vec3(30.0, 6.0, 14.0) / 255.0, vec3(80.0, 12.0, 30.0) / 255.0, vec3(140.0, 22.0, 44.0) / 255.0,
    vec3(196.0, 46.0, 60.0) / 255.0, vec3(236.0, 110.0, 90.0) / 255.0, vec3(255.0, 210.0, 170.0) / 255.0
);

// The membrane's swirl: noise warped by noise, cut into six steps of the palette.
vec3 netherRiftSwirl(ivec2 px, float t, float red) {
    float q = netherRiftFbm(px, 3, vec2(t * 0.17, -t * 0.11), 3);
    float v = netherRiftFbm(px, 3, vec2(q * 2.2 + t * 0.03, t * 0.13 + q), 5);
    int i = clamp(int(floor(v * 6.4 - 0.6)), 0, 5);
    return mix(NETHER_RIFT_PURPLE[i], NETHER_RIFT_RED[i], red);
}

// A cell's feature point, within the cell.
vec2 netherRiftPoint(ivec2 c, int seed) {
    return 0.1 + 0.8 * vec2(netherRiftHash(ivec3(c, seed)), netherRiftHash(ivec3(c, seed + 1)));
}

// Voronoi cells of 2^shift pixels: x is how far (in pixels) the point is from its cell's nearest edge, y a
// hash of that edge (the same from both sides), z a hash of the cell.
vec3 netherRiftCells(ivec2 px, vec2 jitter, int shift, int seed) {
    float size = float(1 << shift);
    vec2 f = (vec2(px & ivec2((1 << shift) - 1)) + 0.5 + jitter) / size;
    vec2 fl = floor(f);
    ivec2 cell = (px >> shift) + ivec2(fl);
    f -= fl;
    float best = 8.0;
    vec2 near = vec2(0.0);
    ivec2 nearCell = cell;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            ivec2 c = cell + ivec2(i, j);
            vec2 r = vec2(i, j) + netherRiftPoint(c, seed) - f;
            float d = dot(r, r);
            if (d < best) {
                best = d;
                near = r;
                nearCell = c;
            }
        }
    }
    float edge = 8.0;
    uint edgeId = 0u;
    uint own = netherRiftHashU(ivec3(nearCell, seed + 2));
    for (int j = -2; j <= 2; j++) {
        for (int i = -2; i <= 2; i++) {
            ivec2 c = nearCell + ivec2(i, j);
            if (c == nearCell) continue;
            vec2 r = vec2(c - cell) + netherRiftPoint(c, seed) - f;
            float e = dot(0.5 * (near + r), normalize(r - near));
            if (e < edge) {
                edge = e;
                edgeId = own ^ netherRiftHashU(ivec3(c, seed + 2));
            }
        }
    }
    return vec3(edge * size, float(netherRiftMix(edgeId) >> 8) / 16777216.0, float(own >> 8) / 16777216.0);
}

// The panorama behind the rift, by view direction, nearest texel. The sprite holds six cube faces 3 by 2,
// laid out by tools/gen_panorama.py (nether-rifts): row 0 faces 0 1 2, row 1 faces 3 4 5; face 0 looks south
// (+Z, image right -X, up +Y), 1 west (-X, right -Z), 2 north (-Z, right +X), 3 east (+X, right +Z), 4 up
// (+Y, right -X, up -Z), 5 down (-Y, right -X, up +Z). shift moves the texel column (the stage 3 glitch).
vec3 netherRiftPanorama(vec3 d, int shift) {
    vec2 lo, hi;
    if (!riftSpriteBox(lo, hi)) return texture(Sampler0, texCoord0).rgb;
    vec2 atlas = vec2(textureSize(Sampler0, 0));
    ivec2 boxMin = ivec2(floor(lo * atlas + 0.5));
    int face = 0;
    vec2 s;
    vec3 a = abs(d);
    if (a.y >= a.x && a.y >= a.z) {
        face = d.y > 0.0 ? 4 : 5;
        s = d.y > 0.0 ? vec2(-d.x, -d.z) / a.y : vec2(-d.x, d.z) / a.y;
    } else if (a.x >= a.z) {
        face = d.x < 0.0 ? 1 : 3;
        s = vec2(d.z, d.y) / a.x * vec2(d.x < 0.0 ? -1.0 : 1.0, 1.0);
    } else {
        face = d.z > 0.0 ? 0 : 2;
        s = vec2(d.z > 0.0 ? -d.x : d.x, d.y) / a.z;
    }
    int size = int(floor((hi.x - lo.x) * atlas.x + 0.5)) / 3;
    ivec2 texel = ivec2(floor(vec2(s.x + 1.0, 1.0 - s.y) * 0.5 * float(size))) + ivec2(shift, 0);
    texel = clamp(texel, ivec2(0), ivec2(size - 1));
    return texelFetch(Sampler0, boxMin + ivec2(face % 3, face / 3) * size + texel, 0).rgb;
}

vec4 netherRiftBody(float stage) {
    float t = GameTime * 1200.0;
    int frame = int(floor(t * 20.0));
    int tearFrame = int(floor(t * 12.0));
    float ts = float(frame) / 20.0;
    int st = int(stage + 0.5);
    float glitch = 0.25 + 0.25 * stage;
    float red = st >= 3 ? 0.65 : (st == 2 ? 0.2 : 0.0);
    ivec2 px = netherRiftPixel();
    int band = int(floor(float(px.y) / 3.0));

    // the cracks: jagged Voronoi edges, more of them, wider and branching as the stage rises
    vec2 jitter = (vec2(netherRiftNoise(px, 2, vec2(0.0), 41), netherRiftNoise(px, 2, vec2(0.0), 43)) - 0.5) * 3.0;
    vec3 crack = netherRiftCells(px, jitter, 4, 31);
    vec3 branch = netherRiftCells(px, jitter * 0.6, 3, 51);
    // open: how far inside a crack, in pixels (> 0 is in a crack, -1..0 its rim). Stage 1: a few hairlines;
    // 2: more, the first ones two pixels wide, with branches off them; 3: all of them wide.
    float open = 0.0;
    if (st <= 1) {
        open = crack.y < 0.3 ? 0.55 - crack.x : -9.0;
    } else if (st == 2) {
        open = crack.y < 0.65 ? (crack.y < 0.3 ? 1.05 : 0.55) - crack.x : -9.0;
        if (branch.y < 0.35 && crack.x < 9.0 && crack.y < 0.65) open = max(open, 0.5 - branch.x);
    } else {
        // every crack wide open: what is left of each cell is an island of membrane
        open = max(2.0 + 1.5 * crack.y - crack.x, branch.y < 0.3 ? 0.55 - branch.x : -9.0);
    }

    if (open > 0.0) {
        // the nether, far away, through the crack; darker towards the crack's rim. At stage 3 the view
        // glitches too: now and then a row band slips sideways or splits its channels.
        vec3 dir = normalize(riftView);
        int slip = 0;
        if (st >= 3 && netherRiftHash(ivec3(band, tearFrame, 17)) < 0.1) {
            slip = int(floor((netherRiftHash(ivec3(band, tearFrame, 18)) - 0.5) * 6.0));
        }
        vec3 view = netherRiftPanorama(dir, slip);
        if (st >= 3 && netherRiftHash(ivec3(band, tearFrame, 19)) < 0.05) {
            view = vec3(netherRiftPanorama(dir, slip + 1).r, view.g * 0.7, netherRiftPanorama(dir, slip - 1).b);
        }
        return vec4(view * mix(0.75, 1.0, clamp(open / 3.0, 0.0, 1.0)), 1.0);
    }
    if (open > -1.0) {
        // the crack's rim, white-hot, flickering pixel by pixel
        float flick = netherRiftHash(ivec3(px, frame / 2 * 8 + 23));
        vec3 hot = st >= 3 ? vec3(1.0, 0.42, 0.16) : vec3(1.0, 0.4, 0.95);
        vec3 white = st >= 3 ? vec3(1.0, 0.86, 0.6) : vec3(1.0, 0.9, 1.0);
        vec3 rim = flick < 0.2 ? hot * 0.55 : (flick < 0.6 ? hot : white);
        return vec4(rim, 1.0);
    }

    // the membrane, glitched: torn rows, channel split, corrupt tiles, scanlines
    ivec2 sp = px;
    if (netherRiftHash(ivec3(band, tearFrame, 11)) < 0.18 * glitch) {
        sp.x += int(floor((netherRiftHash(ivec3(band, tearFrame, 12)) - 0.5) * 10.0 * glitch));
    }
    vec3 c = netherRiftSwirl(sp, ts, red);
    if (netherRiftHash(ivec3(band, tearFrame, 13)) < 0.12 * glitch) {
        c = vec3(netherRiftSwirl(sp + ivec2(2, 0), ts, red).r, c.g * 0.6, netherRiftSwirl(sp - ivec2(2, 0), ts, red).b);
    }
    ivec2 tile = px >> 2;
    if (netherRiftHash(ivec3(tile, tearFrame * 64 + 14)) < 0.05 * glitch) {
        float k = netherRiftHash(ivec3(tile, tearFrame * 64 + 15));
        c = k < 0.3 ? vec3(1.0, 0.16, 0.78) : (k < 0.55 ? vec3(0.16, 0.94, 1.0) : (k < 0.75 ? vec3(0.98) : vec3(0.0)));
    }
    if (((px.y + frame) & 3) == 0) c *= 1.0 - 0.25 * glitch;
    return vec4(c, 0.92);
}

vec4 netherRiftEdge(float stage) {
    float t = GameTime * 1200.0;
    int st = int(stage + 0.5);
    float red = st >= 3 ? 0.65 : (st == 2 ? 0.2 : 0.0);
    ivec2 px = netherRiftPixel();
    // the strip is four pixels across, centred on the outline: a two-pixel core and a pixel of glow each side
    int across = clamp(int(floor(riftLocal().y * 4.0)), 0, 3);
    bool core = across == 1 || across == 2;
    bool on = netherRiftHash(ivec3(px, int(floor(t * 10.0)) * 8 + 21)) > 0.12 * stage;
    vec3 hot = mix(vec3(1.0, 0.47, 0.94), vec3(1.0, 0.35, 0.27), red);
    if (!on) return core ? vec4(0.12, 0.04, 0.12, 1.0) : vec4(0.0);
    return core ? vec4(mix(hot, vec3(1.0, 0.92, 1.0), 0.3), 1.0) : vec4(hot * 0.85, 0.5);
}
