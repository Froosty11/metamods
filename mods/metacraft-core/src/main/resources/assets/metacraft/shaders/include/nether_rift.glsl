// Nether rifts (nether-rifts), drawn as pixel art on a grid fixed to the world, 16 pixels a block like block
// textures, so the panels a rift is built from join without a seam and nothing swims as the camera moves.
// The body is a membrane in the nether portal's purples, swirling at 20 frames a second, with a glitch over
// it (rows torn sideways, a magenta/cyan channel split, flashing corrupt tiles, scanlines). It cracks like
// struck glass: from an impact or two, fixed to the world, spokes run out and rings join them like a spider
// web, and the shards between catch the light each their own way. Stage 1 to 3 the web spreads (hairlines,
// then spokes and rings, then all the way to the frame), glitches harder and turns the purples red; at 3
// shards fall out, nearest the impact first, and through the holes the nether shows: the panel's sprite is a
// 360-degree nether panorama, looked up by view direction like a skybox, so it reads as a far place.
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

// 0..1 from a key already hashed once, mixed with a salt.
float netherRiftUnit(uint key, uint salt) {
    return float(netherRiftMix(key ^ salt) >> 8) * (1.0 / 16777216.0);
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

// The swirl with its palette steps moved by lift (a shard turned a little more or less to the light).
vec3 netherRiftSwirlLift(ivec2 px, float t, float red, int lift) {
    float q = netherRiftFbm(px, 3, vec2(t * 0.17, -t * 0.11), 3);
    float v = netherRiftFbm(px, 3, vec2(q * 2.2 + t * 0.03, t * 0.13 + q), 5);
    int i = clamp(int(floor(v * 6.4 - 0.6)) + lift, 0, 5);
    return mix(NETHER_RIFT_PURPLE[i], NETHER_RIFT_RED[i], red);
}

// The membrane's swirl: noise warped by noise, cut into six steps of the palette.
vec3 netherRiftSwirl(ivec2 px, float t, float red) {
    return netherRiftSwirlLift(px, t, red, 0);
}

// Impacts. Every panel has one near its middle, which holds still while the panel pulses (it scales about its
// middle), so even the smallest rift is struck where it can be seen. Big rifts get more from the world: one
// candidate per cell of 5 by 5 blocks on the plane, every other row staggered half a cell, jittered within the
// middle of its cell; none crowding the panel's own. Panels of one rift share those; where two panels' own
// impacts disagree, a crack runs along the seam between them.
const int NETHER_RIFT_IMPACT_CELL = 80;

int netherRiftFloorDiv(int a, int b) {
    return a >= 0 ? a / b : -((-a - 1) / b) - 1;
}

// A world candidate's impact, in pixels from o (geometry is done near o, so floats keep their precision
// however far out the rift is).
vec2 netherRiftImpact(ivec2 cell, int plane, ivec2 o) {
    vec2 j = vec2(netherRiftHash(ivec3(cell, plane * 4 + 61)), netherRiftHash(ivec3(cell, plane * 4 + 62)));
    ivec2 corner = cell * NETHER_RIFT_IMPACT_CELL + ivec2((cell.y & 1) * (NETHER_RIFT_IMPACT_CELL / 2), 0) - o;
    return vec2(corner) + (0.2 + 0.6 * j) * float(NETHER_RIFT_IMPACT_CELL);
}

// What is known of the strike before looking at any pixel: which plane the panel lies in (its block along the
// normal, and which way it faces: rifts in other planes crack differently), the panel's middle o in world
// pixels (exact: panels are whole blocks, so it falls on a multiple of 8) and half its size, where this
// fragment is from o, and the panel's own impact (from o). Got from how the world and riftUv (0..1 over the
// panel's quad) move together across the screen; seen edge-on, the panel has no impact of its own.
struct NetherRiftStrike {
    int plane;
    ivec2 o;
    vec2 span;
    vec2 at;
    bool hasMine;
    vec2 mine;
    uint mineKey;
};

NetherRiftStrike netherRiftStrike(ivec2 px) {
    NetherRiftStrike k;
    vec3 n = cross(dFdx(riftWorld), dFdy(riftWorld));
    bool alongX = abs(n.z) >= abs(n.x);
    int depth = alongX ? int(floor(riftWorld.z)) + CameraBlockPos.z : int(floor(riftWorld.x)) + CameraBlockPos.x;
    k.plane = depth * 2 + (alongX ? 0 : 1);
    vec2 at = vec2(alongX ? riftWorld.x : riftWorld.z, riftWorld.y) * 16.0;
    ivec2 base = ivec2(alongX ? CameraBlockPos.x : CameraBlockPos.z, CameraBlockPos.y) * 16;
    mat2 dUv = mat2(dFdx(riftUv), dFdy(riftUv));
    k.hasMine = abs(determinant(dUv)) > 1e-12;
    k.o = px;
    k.span = vec2(0.0);
    k.at = vec2(0.5);
    k.mine = vec2(0.0);
    k.mineKey = 0u;
    if (k.hasMine) {
        mat2 m = mat2(dFdx(at), dFdy(at)) * inverse(dUv);
        vec2 mid = floor((at + m * (vec2(0.5) - riftUv)) / 8.0 + 0.5) * 8.0;
        k.o = ivec2(mid) + base;
        k.at = at - mid;
        k.span = 0.5 * (abs(m[0]) + abs(m[1]));
        k.mineKey = netherRiftHashU(ivec3(k.o >> 3, k.plane * 4 + 64));
        k.mine = (vec2(netherRiftUnit(k.mineKey, 0x3c6ef372u), netherRiftUnit(k.mineKey, 0xa54ff53au)) - 0.5)
                * vec2(12.0, 20.0);
    }
    return k;
}

// A distance measured across a line of this normal, counted along the nearer grid axis instead, so a crack
// drawn where it is under half a pixel comes out one pixel wide at any angle.
float netherRiftAxis(vec2 n) {
    return max(abs(n.x), abs(n.y));
}

// A crack's sideways wander at distance d along it: straight runs of 5 pixels with a kink between, up to
// about a pixel either way, none at the impact.
float netherRiftZig(uint key, float d) {
    float seg = floor(d / 5.0);
    int i = int(seg);
    float a = netherRiftUnit(key, uint(i) * 0x27d4eb2du), b = netherRiftUnit(key, uint(i + 1) * 0x27d4eb2du);
    return (mix(a, b, d / 5.0 - seg) - 0.5) * 2.0 * clamp(d / 8.0, 0.0, 1.0) * (0.8 + d * 0.006);
}

// Spoke s of n around an impact (s may run one past either end): its key and its angle, jittered within its
// share of the circle.
uint netherRiftSpokeKey(uint imp, int s, int n) {
    int w = s < 0 ? s + n : (s >= n ? s - n : s);
    return netherRiftMix(imp ^ (uint(w) + 1u) * 0x9e3779b9u);
}

float netherRiftSpokeAngle(uint key, int s, int n) {
    return (float(s) + 0.5 + 0.6 * (netherRiftUnit(key, 0x51ed270bu) - 0.5)) * 6.2831853 / float(n);
}

// How far out a spoke runs, in pixels: at stage 1 only some do, a little way; at 2 all, further; at 3 for ever.
float netherRiftReach(uint key, int st) {
    float h = netherRiftUnit(key, 0xc2b2ae35u), h2 = netherRiftUnit(key, 0x165667b1u);
    if (st <= 1) return h < 0.5 ? 6.0 + 20.0 * h2 : 0.0;
    if (st == 2) return 20.0 + 50.0 * h2;
    return 1e4;
}

// The glass at pixel q (at: where its middle is from the panel's middle). edge: how far (in pixels, see
// netherRiftAxis) from the nearest crack round its shard; normal: from that crack towards q; shard: a hash of
// the shard; whole: cracks close it all round (only those catch the light their own way or fall out); fallen:
// it fell out; pin: how far inside the hole the impact punched (> 0 inside).
void netherRiftShard(NetherRiftStrike k, ivec2 q, vec2 at, int st, out float edge, out vec2 normal, out uint shard,
        out bool whole, out bool fallen, out float pin) {
    vec2 p = vec2(q - k.o) + 0.5;
    // the nearest impact (i1, its key imp; own if it is the panel's) and the next (i2), among the world's in
    // the 3 by 3 cells around q (the jitter keeps the nearest among them) and the panel's own
    vec2 i1 = p + vec2(1e4), i2 = p + vec2(2e4);
    uint imp = 0u;
    bool own = false;
    float b1 = 1e12, b2 = 1e12;
    int cy = netherRiftFloorDiv(q.y, NETHER_RIFT_IMPACT_CELL);
    for (int j = -1; j <= 1; j++) {
        int y = cy + j;
        int cx = netherRiftFloorDiv(q.x - (y & 1) * (NETHER_RIFT_IMPACT_CELL / 2), NETHER_RIFT_IMPACT_CELL);
        for (int i = -1; i <= 1; i++) {
            ivec2 c = ivec2(cx + i, y);
            vec2 spot = netherRiftImpact(c, k.plane, k.o);
            if (k.hasMine && distance(spot, k.mine) < 40.0) continue;
            float dd = dot(p - spot, p - spot);
            if (dd < b1) {
                b2 = b1;
                i2 = i1;
                b1 = dd;
                i1 = spot;
                imp = netherRiftHashU(ivec3(c, k.plane * 4 + 63));
            } else if (dd < b2) {
                b2 = dd;
                i2 = spot;
            }
        }
    }
    float dm = dot(p - k.mine, p - k.mine);
    if (k.hasMine && dm < b1) {
        i2 = i1;
        i1 = k.mine;
        imp = k.mineKey;
        own = true;
    } else if (k.hasMine && dm < b2) {
        i2 = k.mine;
    }
    vec2 r = p - i1;
    float d = max(length(r), 0.01);
    int n = 8 + int(imp % 5u);
    float a = atan(r.y, r.x + 1e-6);
    if (a < 0.0) a += 6.2831853;

    // the sector: between the spoke at or below this angle and the next, the spoke's wander counted
    int below = min(int(a / (6.2831853 / float(n))), n - 1);
    uint keyK = netherRiftSpokeKey(imp, below, n);
    float angK = netherRiftSpokeAngle(keyK, below, n);
    vec2 uK = vec2(cos(angK), sin(angK));
    int s = dot(r, vec2(-uK.y, uK.x)) - netherRiftZig(keyK, dot(r, uK)) < 0.0 ? below - 1 : below;
    uint keyA = netherRiftSpokeKey(imp, s, n), keyB = netherRiftSpokeKey(imp, s + 1, n);
    float angA = netherRiftSpokeAngle(keyA, s, n), angB = netherRiftSpokeAngle(keyB, s + 1, n);
    float reachA = netherRiftReach(keyA, st), reachB = netherRiftReach(keyB, st);
    vec2 uA = vec2(cos(angA), sin(angA)), nA = vec2(-uA.y, uA.x);
    vec2 uB = vec2(cos(angB), sin(angB)), nB = vec2(-uB.y, uB.x);
    float alongA = dot(r, uA), alongB = dot(r, uB);
    // signed across each spoke, > 0 on the sector's side
    float sideA = (dot(r, nA) - netherRiftZig(keyA, alongA)) / netherRiftAxis(nA);
    float sideB = (netherRiftZig(keyB, alongB) - dot(r, nB)) / netherRiftAxis(nB);

    edge = 1e4;
    normal = vec2(0.0, 1.0);
    if (alongA < reachA && abs(sideA) < edge) {
        edge = abs(sideA);
        normal = sideA >= 0.0 ? nA : -nA;
    }
    if (alongB < reachB && abs(sideB) < edge) {
        edge = abs(sideB);
        normal = sideB >= 0.0 ? -nB : nB;
    }

    // the rings across this sector, spoke to spoke, further apart going out (they need both spokes to reach)
    uint sector = netherRiftMix(keyA ^ 0x632be5abu);
    int jMax = st <= 1 ? -1 : (st == 2 ? 2 : 9);
    int passed = 0;
    float ring = 0.0, beyond = 1e4;
    bool outward = false;
    for (int j = 0; j <= jMax; j++) {
        float base = 5.0 + 5.5 * float(j) + 1.1 * float(j * j);
        float ra = base * (1.0 + 0.16 * (netherRiftUnit(keyA, uint(j) * 0x2545f491u) - 0.5));
        float rb = base * (1.0 + 0.16 * (netherRiftUnit(keyB, uint(j) * 0x2545f491u) - 0.5));
        if (ra > reachA - 1.0 || rb > reachB - 1.0) break;
        if (netherRiftUnit(sector, uint(j) * 0x9e3779b9u) > 0.6) continue;
        vec2 ca = uA * ra + nA * netherRiftZig(keyA, ra), cb = uB * rb + nB * netherRiftZig(keyB, rb);
        float len = length(cb - ca);
        vec2 dir = (cb - ca) / len, m = vec2(dir.y, -dir.x);
        float t = dot(r - ca, dir);
        float seg = floor(t / 4.0);
        uint key = sector ^ uint(j) * 0x85ebca6bu;
        float wander = mix(netherRiftUnit(key, uint(int(seg)) * 0x27d4eb2du),
                netherRiftUnit(key, uint(int(seg) + 1) * 0x27d4eb2du), t / 4.0 - seg) - 0.5;
        float side = (dot(r - ca, m) - wander * 1.4 * clamp(min(t, len - t) / 2.0, 0.0, 1.0)) / netherRiftAxis(m);
        if (side > 0.0) {
            passed = j + 1;
            ring = base;
            if (side < edge) {
                edge = side;
                normal = m;
            }
        } else if (!outward) {
            outward = true;
            beyond = base;
            if (-side < edge) {
                edge = -side;
                normal = -m;
            }
        }
    }

    // where this impact's web meets the next one's, the nearer impact wins along a crack between them
    vec2 nb = normalize(i2 - i1);
    float border = (dot(p - i2, p - i2) - d * d) / (2.0 * length(i2 - i1)) / netherRiftAxis(nb);
    if ((st >= 3 || (st == 2 && d < 36.0)) && border < edge) {
        edge = border;
        normal = -nb;
    }

    // along the panel's sides, where its own impact owns the glass: on a seam the next panel's web may not
    // match (on the outline the edge strip covers it)
    vec2 inside = k.span - abs(at);
    if (own && (st >= 3 || (st == 2 && d < 36.0)) && min(inside.x, inside.y) < 1.5) {
        float seam = abs(min(inside.x, inside.y) - 0.5);
        if (seam < edge) {
            edge = seam;
            normal = inside.x < inside.y ? vec2(at.x < 0.0 ? 1.0 : -1.0, 0.0) : vec2(0.0, at.y < 0.0 ? 1.0 : -1.0);
        }
    }

    whole = st >= 3 || outward;
    shard = netherRiftMix(sector ^ uint(passed + 1) * 0x85ebca6bu);
    // At stage 3 each sector has broken out from the impact to a way of its own (less in a small panel): the
    // shards whose outer ring lies within it are out, so the hole is one jagged star and what glass is left
    // still hangs on the glass round it. At 2 some of the innermost shards are out.
    float h = netherRiftUnit(sector, 0x1b873593u);
    float broken = min(8.0 + 34.0 * h, k.hasMine ? (0.45 + 0.7 * h) * min(k.span.x, k.span.y) : 1e4);
    fallen = whole && (st >= 3 ? beyond < broken + 8.0
            : (st == 2 && ring == 0.0 && netherRiftUnit(shard, 0x1b873593u) < 0.5));
    pin = (st <= 1 ? 1.6 : (st == 2 ? 2.6 : 0.0)) - d;
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

    NetherRiftStrike k = netherRiftStrike(px);
    float edge, pin;
    vec2 normal;
    uint shard;
    bool whole, fallen;
    netherRiftShard(k, px, k.at, st, edge, normal, shard, whole, fallen, pin);

    // A fallen shard leaves a jagged lip of glass, one to three pixels, with a hot rim, along each crack it
    // shared with glass still there; where the shard across fell too, nothing is left between.
    float hole = pin;
    if (fallen) {
        float lip = 1.0 + floor(1.6 * netherRiftNoise(px, 1, vec2(0.0), 71) + 0.9 * netherRiftHash(ivec3(px, 73)));
        hole = 9.0;
        if (edge < lip) {
            ivec2 q = px + ivec2(floor(-normal / netherRiftAxis(normal) * (edge + 1.0) + 0.5));
            float e2, pin2;
            vec2 n2;
            uint shard2;
            bool whole2, fallen2;
            netherRiftShard(k, q, k.at + vec2(q - px), st, e2, n2, shard2, whole2, fallen2, pin2);
            if (pin2 <= 0.0 && !fallen2) hole = edge - lip;
        }
    }

    if (hole > 0.0) {
        // the nether, far away, through the hole (each hole a texel off the next); darker under its lip. At
        // stage 3 the view glitches too: now and then a row band slips sideways or splits its channels.
        vec3 dir = normalize(riftView);
        int slip = int(shard % 3u) - 1;
        if (st >= 3 && netherRiftHash(ivec3(band, tearFrame, 17)) < 0.1) {
            slip += int(floor((netherRiftHash(ivec3(band, tearFrame, 18)) - 0.5) * 6.0));
        }
        vec3 view = netherRiftPanorama(dir, slip);
        if (st >= 3 && netherRiftHash(ivec3(band, tearFrame, 19)) < 0.05) {
            view = vec3(netherRiftPanorama(dir, slip + 1).r, view.g * 0.7, netherRiftPanorama(dir, slip - 1).b);
        }
        return vec4(view * mix(0.7, 1.0, clamp(hole / 3.0, 0.0, 1.0)), 1.0);
    }
    if (hole > -1.0) {
        // the hole's rim, hot, flickering pixel by pixel
        float flick = netherRiftHash(ivec3(px, frame / 2 * 8 + 23));
        vec3 hot = st >= 3 ? vec3(1.0, 0.42, 0.16) : vec3(1.0, 0.4, 0.95);
        vec3 white = st >= 3 ? vec3(1.0, 0.86, 0.6) : vec3(1.0, 0.9, 1.0);
        vec3 rim = flick < 0.35 ? hot * 0.55 : (flick < 0.9 ? hot : white);
        return vec4(rim, 1.0);
    }

    // the membrane, glitched: torn rows, channel split, corrupt tiles, scanlines. Each whole shard is tilted
    // its own way: its swirl a few pixels off its neighbours', a step lighter or darker, a little brighter
    // or dimmer, so the pattern breaks at every crack.
    ivec2 sp = px;
    int lift = 0;
    float bright = 1.0;
    if (whole) {
        float h = netherRiftUnit(shard, 0x68e31da4u);
        lift = h < 0.15 ? -1 : (h < 0.75 ? 0 : 1);
        sp += ivec2(int(netherRiftUnit(shard, 0xb5297a4du) * 7.0), int(netherRiftUnit(shard, 0x1b56c4e9u) * 7.0)) - 3;
        bright = 0.88 + 0.22 * netherRiftUnit(shard, 0x7fb5d329u);
    }
    if (netherRiftHash(ivec3(band, tearFrame, 11)) < 0.18 * glitch) {
        sp.x += int(floor((netherRiftHash(ivec3(band, tearFrame, 12)) - 0.5) * 10.0 * glitch));
    }
    vec3 c = netherRiftSwirlLift(sp, ts, red, lift);
    if (netherRiftHash(ivec3(band, tearFrame, 13)) < 0.12 * glitch) {
        c = vec3(netherRiftSwirlLift(sp + ivec2(2, 0), ts, red, lift).r, c.g * 0.6,
                netherRiftSwirlLift(sp - ivec2(2, 0), ts, red, lift).b);
    }
    c *= bright;
    vec3 pale = mix(NETHER_RIFT_PURPLE[5], NETHER_RIFT_RED[5], red);
    if (whole && netherRiftUnit(shard, 0x2c1b3c6du) < 0.4 && ((px.x - px.y + int(shard & 31u)) & 31) < 2) {
        // some catch a streak of light, each at its own place
        c = mix(c, pale, 0.3);
    }
    float alpha = 0.92;
    if (edge < 0.5) {
        // a crack: a dark pixel line
        c = mix(NETHER_RIFT_PURPLE[0], NETHER_RIFT_RED[0], red) * 0.6;
        alpha = 0.97;
    } else if (edge < 1.5 && dot(normal, vec2(-0.6, 0.8)) < -0.25) {
        // and its far wall catching the light (from the upper left), a pixel wide
        c = mix(c, pale, 0.55);
    }
    ivec2 tile = px >> 2;
    if (netherRiftHash(ivec3(tile, tearFrame * 64 + 14)) < 0.05 * glitch) {
        float pick = netherRiftHash(ivec3(tile, tearFrame * 64 + 15));
        c = pick < 0.3 ? vec3(1.0, 0.16, 0.78)
                : (pick < 0.55 ? vec3(0.16, 0.94, 1.0) : (pick < 0.75 ? vec3(0.98) : vec3(0.0)));
    }
    if (((px.y + frame) & 3) == 0) c *= 1.0 - 0.25 * glitch;
    return vec4(c, alpha);
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
