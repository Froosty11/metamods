// The port of metacraft.ovvar.datagen.Tex, GeneratedAssets and content.Spot, as plain functions
// on {w, h, data} images. No Blockbench types in this file: node test.js runs it as it is, and
// its goldens are the very PNGs the mod ships.

OVVAR.tex = {
  blank: function (w, h) {
    return {w: w, h: h, data: new Uint8Array(w * h * 4)};
  },

  copy: function (im) {
    return {w: im.w, h: im.h, data: im.data.slice()};
  },

  // Packed ARGB, the way Tex keeps a texel. Reading a texel that is transparent but coloured
  // gives back its colour: Tex does, and `placed` branches on the packed value being non-zero.
  get: function (im, x, y) {
    var i = (y * im.w + x) * 4;
    return ((im.data[i + 3] << 24) | (im.data[i] << 16) | (im.data[i + 1] << 8) | im.data[i + 2]) >>> 0;
  },

  set: function (im, x, y, argb) {
    var i = (y * im.w + x) * 4;
    im.data[i] = (argb >>> 16) & 255;
    im.data[i + 1] = (argb >>> 8) & 255;
    im.data[i + 2] = argb & 255;
    im.data[i + 3] = (argb >>> 24) & 255;
  },

  a: function (p) { return (p >>> 24) & 255; },
  r: function (p) { return (p >>> 16) & 255; },
  g: function (p) { return (p >>> 8) & 255; },
  b: function (p) { return p & 255; },

  /** Tex.blit: source alpha replaces, no blending, alpha-0 source texels left alone. */
  blit: function (dst, src, sx, sy, w, h, dx, dy) {
    if (sx + w > src.w || sy + h > src.h || dx + w > dst.w || dy + h > dst.h) {
      throw new Error('blit outside bounds: ' + w + 'x' + h + ' from (' + sx + ',' + sy + ') to (' + dx + ',' + dy + ')');
    }
    var out = OVVAR.tex.copy(dst);
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        var p = OVVAR.tex.get(src, sx + x, sy + y);
        if (OVVAR.tex.a(p) > 0) OVVAR.tex.set(out, dx + x, dy + y, p);
      }
    }
    return out;
  },

  crop: function (im, x, y, w, h) {
    return OVVAR.tex.blit(OVVAR.tex.blank(w, h), im, x, y, w, h, 0, 0);
  },

  flipXRect: function (im, x, y, w, h) {
    var out = OVVAR.tex.copy(im);
    for (var yy = y; yy < y + h; yy++) {
      for (var i = 0; i < w; i++) OVVAR.tex.set(out, x + i, yy, OVVAR.tex.get(im, x + (w - 1 - i), yy));
    }
    return out;
  },

  flipX: function (im) {
    return OVVAR.tex.flipXRect(im, 0, 0, im.w, im.h);
  },

  /** Nearest-neighbour upscale by an integer factor (Tex.scale). */
  scale: function (im, factor) {
    var w = im.w * factor, h = im.h * factor;
    var out = OVVAR.tex.blank(w, h);
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        OVVAR.tex.set(out, x, y, OVVAR.tex.get(im, (x / factor) | 0, (y / factor) | 0));
      }
    }
    return out;
  },

  /** Tex.composite: `over` alpha-composited on top of `base`, same size, rounding as Java's. */
  composite: function (base, over) {
    if (over.w !== base.w || over.h !== base.h) throw new Error('size mismatch');
    var out = OVVAR.tex.blank(base.w, base.h);
    for (var y = 0; y < base.h; y++) {
      for (var x = 0; x < base.w; x++) {
        var bp = OVVAR.tex.get(base, x, y), tp = OVVAR.tex.get(over, x, y);
        var ta = OVVAR.tex.a(tp) / 255, ba = OVVAR.tex.a(bp) / 255;
        var oa = ta + ba * (1 - ta);
        if (oa === 0) continue;
        var rr = Math.round((OVVAR.tex.r(tp) * ta + OVVAR.tex.r(bp) * ba * (1 - ta)) / oa);
        var gg = Math.round((OVVAR.tex.g(tp) * ta + OVVAR.tex.g(bp) * ba * (1 - ta)) / oa);
        var bb = Math.round((OVVAR.tex.b(tp) * ta + OVVAR.tex.b(bp) * ba * (1 - ta)) / oa);
        OVVAR.tex.set(out, x, y, (Math.round(oa * 255) << 24 | rr << 16 | gg << 8 | bb) >>> 0);
      }
    }
    return out;
  },

  isEmpty: function (im) {
    for (var i = 3; i < im.data.length; i += 4) if (im.data[i] !== 0) return false;
    return true;
  },

  /**
   * Up to twelve differences between two images, as readable lines; [] when they are the same.
   * `ignore` is a list of [x, y] the comparison skips -- the marker texels the manifest names,
   * which are the shader's contract and not part of the cloth.
   */
  diff: function (a, b, ignore) {
    if (a.w !== b.w || a.h !== b.h) return ['sizes differ: ' + a.w + 'x' + a.h + ' vs ' + b.w + 'x' + b.h];
    var skip = {};
    for (var i = 0; i < (ignore || []).length; i++) skip[ignore[i][0] + ',' + ignore[i][1]] = true;
    var out = [];
    for (var y = 0; y < a.h && out.length < 12; y++) {
      for (var x = 0; x < a.w && out.length < 12; x++) {
        if (skip[x + ',' + y]) continue;
        var pa = OVVAR.tex.get(a, x, y), pb = OVVAR.tex.get(b, x, y);
        if (pa !== pb) {
          out.push('(' + x + ',' + y + ') ' + pa.toString(16).padStart(8, '0') + ' != ' + pb.toString(16).padStart(8, '0'));
        }
      }
    }
    return out;
  }
};

OVVAR.compose = {};

/** Spot.pixel: the model unit a layer's texel is drawn at (ovvar_pixel in ovvar.glsl). */
OVVAR.compose.pixel = function (skinX, inflate) {
  return skinX < 16 ? 13.0 / 12 : (12 + 2 * inflate) / 12;
};

/**
 * Spot.anchored, line for line: the strip-local texel column shown at side-row texel `skinX`
 * when the art is anchored on face `anchor` of an `inflate`-inflated box, or -1 in the slack on
 * the opposite face. This is what makes art bend round a corner, and it is baked into the trim
 * textures, so it is pinned by 512 samples in the manifest.
 */
OVVAR.compose.anchored = function (skinX, inflate, anchor) {
  var p = OVVAR.compose.pixel(Math.floor(skinX), inflate), e = 2 * inflate;
  var body = skinX >= 16 && skinX < 40;
  var stripStart = skinX < 16 ? 0 : body ? 16 : 40;
  var local = skinX - stripStart;
  var n1 = body ? 8 : 4, total = 8 + 2 * n1;
  var perimeter = total + 4 * e;
  var k = local < 4 ? 0 : local < 4 + n1 ? 1 : local < 8 + n1 ? 2 : 3;
  var s = [0, 4, 4 + n1, 8 + n1], n = [4, n1, 4, n1], U = [0, 4 + e, 4 + n1 + 2 * e, 8 + n1 + 3 * e];
  var u = U[k] + (local - s[k]) * ((n[k] + e) / n[k]);
  var c = U[anchor] + (n[anchor] + e) / 2, t = s[anchor] + n[anchor] / 2.0;
  var du = u - c;
  if (du >= perimeter / 2) du -= perimeter; else if (du < -perimeter / 2) du += perimeter;
  var dt = du / p;
  if (Math.abs(dt) > total / 2.0) return -1;
  var w = t + dt;
  return w - Math.floor(w / total) * total;
};
