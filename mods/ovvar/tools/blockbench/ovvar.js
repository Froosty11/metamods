// The one namespace every file of the plugin shares. Blockbench loads the built ovvar.js as
// new Function('requireNativeModule', 'require', code), so `var` here is function-scoped to the
// plugin: nothing of ours lands on window, and onunload has nothing global to clean up.
var OVVAR = {};

OVVAR.VERSION = 1;

// ---- PNG, by hand
//
// Not the canvas: a canvas premultiplies alpha, so it loses the RGB of a texel written at alpha 0
// -- and datagen's `placed` copies any texel whose packed ARGB is non-zero, alpha 0 included, so
// those texels are part of the goldens. Everything here is byte-exact instead. zlib does the
// compression: Blockbench hands plugins 'zlib' with no permission prompt (it is on the
// unrestricted module list), and Node has it built in, so one code path serves both.

OVVAR.png = {
  SIGNATURE: [137, 80, 78, 71, 13, 10, 26, 10],

  decode: function (bytes, inflate) {
    for (var i = 0; i < 8; i++) {
      if (bytes[i] !== OVVAR.png.SIGNATURE[i]) throw new Error('not a PNG (bad signature)');
    }
    var view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    var at = 8;
    var w = 0, h = 0, depth = 0, colour = 0, interlace = 0;
    var palette = null, trns = null;
    var idat = [];
    while (at < bytes.length) {
      var len = view.getUint32(at);
      var type = String.fromCharCode(bytes[at + 4], bytes[at + 5], bytes[at + 6], bytes[at + 7]);
      var body = bytes.subarray(at + 8, at + 8 + len);
      if (type === 'IHDR') {
        w = view.getUint32(at + 8);
        h = view.getUint32(at + 12);
        depth = bytes[at + 16];
        colour = bytes[at + 17];
        interlace = bytes[at + 20];
      } else if (type === 'PLTE') {
        palette = body.slice();
      } else if (type === 'tRNS') {
        trns = body.slice();
      } else if (type === 'IDAT') {
        idat.push(body.slice());
      } else if (type === 'IEND') {
        break;
      }
      at += 12 + len;
    }
    if (depth !== 8) throw new Error('only 8-bit PNGs are supported, this one is ' + depth + '-bit');
    if (interlace !== 0) throw new Error('interlaced PNGs are not supported');
    var total = 0;
    for (var k = 0; k < idat.length; k++) total += idat[k].length;
    var joined = new Uint8Array(total);
    var off = 0;
    for (var k2 = 0; k2 < idat.length; k2++) { joined.set(idat[k2], off); off += idat[k2].length; }
    var raw = inflate(joined);

    // Samples per pixel, by colour type: 0 grey, 2 RGB, 3 palette index, 4 grey+alpha, 6 RGBA.
    var channels = colour === 0 ? 1 : colour === 2 ? 3 : colour === 3 ? 1 : colour === 4 ? 2 : 4;
    var stride = w * channels;
    var lines = new Uint8Array(h * stride);
    var pos = 0;
    for (var y = 0; y < h; y++) {
      var filter = raw[pos++];
      var line = raw.subarray(pos, pos + stride);
      pos += stride;
      var here = y * stride, above = here - stride;
      for (var x = 0; x < stride; x++) {
        var a = x >= channels ? lines[here + x - channels] : 0;
        var b = y > 0 ? lines[above + x] : 0;
        var c = (x >= channels && y > 0) ? lines[above + x - channels] : 0;
        var v = line[x];
        if (filter === 1) v = (v + a) & 255;
        else if (filter === 2) v = (v + b) & 255;
        else if (filter === 3) v = (v + ((a + b) >> 1)) & 255;
        else if (filter === 4) {
          var p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
          var pred = (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c);
          v = (v + pred) & 255;
        } else if (filter !== 0) throw new Error('unknown PNG filter ' + filter + ' on row ' + y);
        lines[here + x] = v;
      }
    }

    var out = new Uint8Array(w * h * 4);
    for (var py = 0; py < h; py++) {
      for (var px = 0; px < w; px++) {
        var s = py * stride + px * channels, d = (py * w + px) * 4;
        if (colour === 6) {
          out[d] = lines[s]; out[d + 1] = lines[s + 1]; out[d + 2] = lines[s + 2]; out[d + 3] = lines[s + 3];
        } else if (colour === 2) {
          out[d] = lines[s]; out[d + 1] = lines[s + 1]; out[d + 2] = lines[s + 2]; out[d + 3] = 255;
        } else if (colour === 0) {
          out[d] = out[d + 1] = out[d + 2] = lines[s]; out[d + 3] = 255;
        } else if (colour === 4) {
          out[d] = out[d + 1] = out[d + 2] = lines[s]; out[d + 3] = lines[s + 1];
        } else if (colour === 3) {
          var idx = lines[s];
          out[d] = palette[idx * 3]; out[d + 1] = palette[idx * 3 + 1]; out[d + 2] = palette[idx * 3 + 2];
          out[d + 3] = (trns && idx < trns.length) ? trns[idx] : 255;
        } else {
          throw new Error('unsupported PNG colour type ' + colour);
        }
      }
    }
    return {w: w, h: h, data: out};
  },

  // Always 8-bit RGBA, filter 0 (None) on every row: the smallest encoder that is still a legal
  // PNG, and the goldens are never compared byte for byte -- only pixel for pixel.
  encode: function (image, deflate) {
    var w = image.w, h = image.h;
    var raw = new Uint8Array(h * (1 + w * 4));
    for (var y = 0; y < h; y++) {
      raw[y * (1 + w * 4)] = 0;
      raw.set(image.data.subarray(y * w * 4, (y + 1) * w * 4), y * (1 + w * 4) + 1);
    }
    var idat = deflate(raw);
    var ihdr = new Uint8Array(13);
    var dv = new DataView(ihdr.buffer);
    dv.setUint32(0, w); dv.setUint32(4, h);
    ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
    var chunks = [
      OVVAR.png.chunk('IHDR', ihdr),
      OVVAR.png.chunk('IDAT', idat),
      OVVAR.png.chunk('IEND', new Uint8Array(0))
    ];
    var size = 8;
    for (var i = 0; i < chunks.length; i++) size += chunks[i].length;
    var out = new Uint8Array(size);
    out.set(OVVAR.png.SIGNATURE, 0);
    var at = 8;
    for (var j = 0; j < chunks.length; j++) { out.set(chunks[j], at); at += chunks[j].length; }
    return out;
  },

  chunk: function (type, body) {
    var out = new Uint8Array(12 + body.length);
    var dv = new DataView(out.buffer);
    dv.setUint32(0, body.length);
    for (var i = 0; i < 4; i++) out[4 + i] = type.charCodeAt(i);
    out.set(body, 8);
    dv.setUint32(8 + body.length, OVVAR.png.crc(out.subarray(4, 8 + body.length)));
    return out;
  },

  CRC_TABLE: (function () {
    var t = new Int32Array(256);
    for (var n = 0; n < 256; n++) {
      var c = n;
      for (var k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
      t[n] = c;
    }
    return t;
  })(),

  crc: function (bytes) {
    var c = -1;
    for (var i = 0; i < bytes.length; i++) c = OVVAR.png.CRC_TABLE[(c ^ bytes[i]) & 255] ^ (c >>> 8);
    return (c ^ -1) >>> 0;
  }
};

// ---- io
//
// One factory for both hosts. Blockbench hands a plugin a scoped `require(name, options)`:
// 'path', 'zlib' and 'buffer' are free, 'fs' prompts once per directory and returns a file
// system that refuses anything outside `scope`. Node's own require ignores the second argument,
// so this same call works there.
OVVAR.makeIo = function (req, scope) {
  var fs = req('fs', {
    scope: scope,
    optional: false,
    message: 'Ovvar needs to read the manifest and the patch art in your METAmods checkout, and to write exported patch art back into it.'
  });
  var zlib = req('zlib');
  var nodePath = req('path');
  var Buf = req('buffer').Buffer;
  var io = {
    scope: scope,
    join: function () { return nodePath.join.apply(nodePath, arguments); },
    read: function (p) { var b = fs.readFileSync(p); return new Uint8Array(b.buffer, b.byteOffset, b.length); },
    write: function (p, bytes) { fs.writeFileSync(p, Buf.from(bytes.buffer, bytes.byteOffset, bytes.length)); },
    exists: function (p) { return fs.existsSync(p); },
    list: function (d) { return fs.readdirSync(d); },
    mkdirp: function (d) { fs.mkdirSync(d, {recursive: true}); },
    inflate: function (bytes) {
      var b = zlib.inflateSync(Buf.from(bytes.buffer, bytes.byteOffset, bytes.length));
      return new Uint8Array(b.buffer, b.byteOffset, b.length);
    },
    deflate: function (bytes) {
      var b = zlib.deflateSync(Buf.from(bytes.buffer, bytes.byteOffset, bytes.length), {level: 9});
      return new Uint8Array(b.buffer, b.byteOffset, b.length);
    }
  };
  io.decode = function (bytes) { return OVVAR.png.decode(bytes, io.inflate); };
  io.encode = function (image) { return OVVAR.png.encode(image, io.deflate); };
  io.dataUrl = function (image) {
    var bytes = io.encode(image);
    return 'data:image/png;base64,' + Buf.from(bytes.buffer, bytes.byteOffset, bytes.length).toString('base64');
  };
  return io;
};

// ---- the manifest

OVVAR.MANIFEST_PATH = 'mods/ovvar/src/main/generated/ovvar/blockbench/manifest.json';

OVVAR.loadManifest = function (io, checkout) {
  var p = io.join(checkout, OVVAR.MANIFEST_PATH);
  if (!io.exists(p)) {
    throw new Error('No Ovvar manifest at ' + p + ' -- run ./gradlew :mods:ovvar:runDatagen in that checkout first.');
  }
  var text = '';
  var bytes = io.read(p);
  for (var i = 0; i < bytes.length; i += 8192) {
    text += String.fromCharCode.apply(null, bytes.subarray(i, Math.min(i + 8192, bytes.length)));
  }
  var m = JSON.parse(decodeURIComponent(escape(text)));
  if (m.version > OVVAR.VERSION) {
    throw new Error('This manifest is version ' + m.version + '; this plugin understands version '
      + OVVAR.VERSION + '. Update the plugin (tools/blockbench/ovvar.js).');
  }
  m.checkout = checkout;
  m.cellById = {};
  for (var c = 0; c < m.cells.length; c++) m.cellById[m.cells[c].id] = m.cells[c];
  m.patchById = {};
  for (var p2 = 0; p2 < m.patches.length; p2++) m.patchById[m.patches[p2].id] = m.patches[p2];
  m.chapterById = {};
  for (var ch = 0; ch < m.chapters.length; ch++) m.chapterById[m.chapters[ch].id] = m.chapters[ch];
  m.artByFile = {};
  for (var p3 = 0; p3 < m.patches.length; p3++) {
    var arts = m.patches[p3].arts;
    for (var a = 0; a < arts.length; a++) m.artByFile[arts[a].file] = arts[a];
  }
  return m;
};

// Where an art's PNG is: a drawing lives in the source tree, a generated size beside the manifest.
OVVAR.artPath = function (m, checkout, art) {
  if (art.generated) {
    var base = art.file.slice(art.file.lastIndexOf('/') + 1);
    return [checkout, 'mods/ovvar/src/main/generated/ovvar/blockbench/art', base].join('/');
  }
  return [checkout, 'mods/ovvar/src/main/resources/art/ovvar', art.file].join('/');
};
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

  /**
   * A hard copy of a rectangle, alpha-0 texels included -- unlike `blit`, which leaves them
   * alone the way Tex.blit does. The mirror strip needs this: where the left sleeve is
   * transparent, the right sleeve's art must not show through from underneath.
   */
  copyRect: function (dst, src, sx, sy, w, h, dx, dy) {
    var out = OVVAR.tex.copy(dst);
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) OVVAR.tex.set(out, dx + x, dy + y, OVVAR.tex.get(src, sx + x, sy + y));
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

/**
 * Everything compose() needs from the world: the manifest, and two ways of getting a PNG out of
 * the checkout -- a patch art by its catalogue file name, and any texture the generator already
 * wrote. Both memoised, because compose() runs on every brush stroke.
 */
OVVAR.compose.ctx = function (io, m) {
  var arts = {}, gen = {};
  var GENROOT = 'mods/ovvar/src/main/generated/assets/ovvar/textures/';
  var ctx = {
    m: m,
    io: io,
    warnings: [],
    art: function (file) {
      if (!arts[file]) {
        var entry = m.artByFile[file];
        if (!entry) throw new Error('no art named ' + file + ' in the manifest');
        var path = OVVAR.artPath(m, m.checkout, entry);
        if (!io.exists(path)) {
          ctx.warnings.push('missing art file: ' + path);
          arts[file] = OVVAR.compose.placeholder(entry.w, entry.h);
        } else {
          arts[file] = io.decode(io.read(path));
        }
      }
      return arts[file];
    },
    generated: function (rel) {
      if (!gen[rel]) gen[rel] = io.decode(io.read([m.checkout, GENROOT + rel].join('/')));
      return gen[rel];
    },
    base: function (chapterId, piece, nercabbad) {
      var chapter = m.chapterById[chapterId];
      if (!chapter) throw new Error('no chapter ' + chapterId + ' in the manifest');
      var key = nercabbad ? 'bottomNercabbad' : piece;
      var rel = chapter.layers[key];
      if (!rel) throw new Error(chapter.name + ' has no ' + key + ' layer (it does not roll down)');
      return ctx.generated('entity/equipment/' + rel);
    },
    forget: function () { arts = {}; gen = {}; ctx.warnings = []; }
  };
  return ctx;
};

/** A missing art file: solid magenta, so it is obvious on the model and in the catalogue list. */
OVVAR.compose.placeholder = function (w, h) {
  var out = OVVAR.tex.blank(w, h);
  for (var y = 0; y < h; y++) for (var x = 0; x < w; x++) OVVAR.tex.set(out, x, y, 0xFFFF00FF);
  return out;
};

/** Patches.artFor(patch, spot): the fit is the cell's, and the manifest has already worked it out. */
OVVAR.compose.artFor = function (m, patch, cell) {
  var file = patch.fits[cell.fit];
  var art = m.artByFile[file];
  if (!art) throw new Error('no art named ' + file + ' in the manifest');
  return art;
};

/** Patches.Art.offsetX: the art centred in the cell, which is not always one cell wide. */
OVVAR.compose.offsetX = function (m, cell, art) {
  return Math.trunc((cell.w * m.detail - art.w) / 2);
};

/** Spot.seatHalf: where a seat patch's art is cut for one leg, in art pixels. */
OVVAR.compose.seatHalf = function (m, side) {
  return (side === 'left' ? 0 : 1) * m.px;
};

/**
 * GeneratedAssets.placed: the art on a garment texture at texel column x (its top-left; the
 * cell's row, centred vertically), clipped to the part's side rows and wrapped round the part's
 * strip -- past the outer face of a limb lies its back face. A cell on a box's top face (the
 * shoulders) is clipped to the face both ways instead, since the top face has no neighbour in
 * the layout to continue onto.
 *
 * Returns null instead of throwing when the art lands entirely off the cell -- datagen's
 * `require` is a build failure there, but a plugin has a panel to warn in and a model to keep
 * drawing.
 */
OVVAR.compose.placed = function (m, cell, art, x) {
  var D = m.detail, W = m.texture[0], H = m.texture[1];
  var y = cell.v * D + Math.trunc((cell.h * D - art.h) / 2);
  var out = OVVAR.tex.blank(W, H);
  var any = false, ax, row, p;
  if (cell.top) {
    var x0 = cell.u * D, x1 = x0 + cell.w * D, y0 = m.topRow * D, y1 = m.faceRow * D;
    for (ax = 0; ax < art.w; ax++) {
      var column = x + ax;
      if (column < x0 || column >= x1) continue;
      for (row = Math.max(y, y0); row < Math.min(y + art.h, y1); row++) {
        p = OVVAR.tex.get(art, ax, row - y);
        if (p !== 0) { OVVAR.tex.set(out, column, row, p); any = true; }
      }
    }
    return any ? out : null;
  }
  var stripStart = cell.stripStart * D, stripWidth = cell.stripWidth * D;
  if (art.w > stripWidth) throw new Error('patch art is wider than the ' + cell.id + " cell's part");
  for (ax = 0; ax < art.w; ax++) {
    var col = stripStart + (((x + ax - stripStart) % stripWidth) + stripWidth) % stripWidth;
    for (row = Math.max(y, m.faceRow * D); row < Math.min(y + art.h, (m.faceRow + m.faceRows) * D); row++) {
      p = OVVAR.tex.get(art, ax, row - y);
      if (p !== 0) { OVVAR.tex.set(out, col, row, p); any = true; }
    }
  }
  return any ? out : null;
};

/**
 * One committed placement texture, minus the marker texels: the art this cell shows, mirrored
 * for a left limb (the model mirrors it back), cut in half for the seat. `side` is 'body',
 * 'right' or 'left' -- for the seat it is the leg being drawn, for anything else the cell's own.
 */
OVVAR.compose.placementTexture = function (ctx, cell, patch, side) {
  var m = ctx.m;
  var entry = OVVAR.compose.artFor(m, patch, cell);
  var art = ctx.art(entry.file);
  if (cell.side === 'seat') {
    // The art is drawn as seen from behind, so its left half belongs on the wearer's LEFT leg;
    // that half is then flipped in x, because the model flips the left leg's texture back.
    var half = OVVAR.tex.crop(art, OVVAR.compose.seatHalf(m, side), 0, m.px, art.h);
    if (side === 'left') half = OVVAR.tex.flipX(half);
    return OVVAR.compose.placed(m, cell, half, cell.u * m.detail);
  }
  var drawn = cell.side === 'left' ? OVVAR.tex.flipX(art) : art;
  return OVVAR.compose.placed(m, cell, drawn, cell.u * m.detail + OVVAR.compose.offsetX(m, cell, entry));
};

/**
 * GeneratedAssets.placedWrapped: `placed`, then the strip squeezed round the box the way the
 * shader does (`anchored`), baked column by column -- each column of the part's side rows shows
 * the art column the shader would sample there. Vanilla draws the trim channel, so this is the
 * only place the squeeze is written into pixels; it is also what the plugin draws a body cell's
 * art with, which is why art bends round the chest's corners in the preview.
 */
OVVAR.compose.placedWrapped = function (m, cell, art, x) {
  var flat = OVVAR.compose.placed(m, cell, art, x);
  if (flat === null) return null;
  var D = m.detail, W = m.texture[0], H = m.texture[1];
  var stripStart = cell.stripStart * D, stripEnd = stripStart + cell.stripWidth * D;
  var inflate = m.inflate[cell.piece];
  var anchor = cell.face;
  var out = OVVAR.tex.blank(W, H);
  for (var column = stripStart; column < stripEnd; column++) {
    var w = OVVAR.compose.anchored((column + 0.5) / D, inflate, anchor);
    if (w < 0) continue;
    var texel = stripStart + Math.floor(w * D);
    for (var row = m.faceRow * D; row < (m.faceRow + m.faceRows) * D; row++) {
      var p = OVVAR.tex.get(flat, texel, row);
      if (p !== 0) OVVAR.tex.set(out, column, row, p);
    }
  }
  return out;
};

/** Below this alpha a pixel votes as nothing at all, and a pixel that wins as nothing comes out clear. */
OVVAR.compose.VOTING_ALPHA = 128;

/**
 * Tex.downscaled, exactly: the source shrunk to w x h by an area-weighted majority vote, each
 * output pixel taking whichever colour covers most of the rectangle it stands for.
 *
 * A vote and not an average, because the result must use no colour the source did not -- the
 * trim channel permutes a key palette built from every opaque colour of every patch art, so a
 * blended edge pixel would have no slot, and it is pixel art besides. Ties go to the colour that
 * is RARER in the whole source: the background always has the votes, so an even split has to
 * fall the other way or every thin thing in the art dissolves. A remaining tie goes to whichever
 * colour appears first reading rows, so the answer never depends on iteration order.
 *
 * Integer arithmetic throughout (the overlap of output pixel i with source pixel j in units of
 * 1/(W*W')), so exact ties are exactly ties -- Number is good to 2^53 and the products here are
 * at most 16*16*16*16, so nothing rounds.
 */
OVVAR.compose.downscaled = function (im, w, h) {
  if (w <= 0 || h <= 0 || w > im.w || h > im.h) {
    throw new Error('cannot scale ' + im.w + 'x' + im.h + ' down to ' + w + 'x' + h);
  }
  // Keyed once: an invisible pixel votes as the same "nothing" whatever colour it is written in,
  // an opaque one as its exact ARGB, which is never 0 -- so key 0 is transparency alone.
  var n = im.w * im.h;
  var key = new Array(n);
  var frequency = new Map(), firstSeen = new Map();
  for (var i = 0; i < n; i++) {
    var p = OVVAR.tex.get(im, i % im.w, (i / im.w) | 0);
    var k = OVVAR.tex.a(p) < OVVAR.compose.VOTING_ALPHA ? 0 : p;
    key[i] = k;
    frequency.set(k, (frequency.get(k) || 0) + 1);
    if (!firstSeen.has(k)) firstSeen.set(k, i);
  }
  var out = OVVAR.tex.blank(w, h);
  for (var y = 0; y < h; y++) {
    for (var x = 0; x < w; x++) {
      var votes = new Map();
      for (var sy = 0; sy < im.h; sy++) {
        var oy = Math.min((y + 1) * im.h, (sy + 1) * h) - Math.max(y * im.h, sy * h);
        if (oy <= 0) continue;
        for (var sx = 0; sx < im.w; sx++) {
          var ox = Math.min((x + 1) * im.w, (sx + 1) * w) - Math.max(x * im.w, sx * w);
          if (ox > 0) {
            var kk = key[sy * im.w + sx];
            votes.set(kk, (votes.get(kk) || 0) + ox * oy);
          }
        }
      }
      var won = 0, winning = -1;
      votes.forEach(function (count, colour) {
        var better = count > winning || (count === winning && (frequency.get(colour) < frequency.get(won)
          || (frequency.get(colour) === frequency.get(won) && firstSeen.get(colour) < firstSeen.get(won))));
        if (winning < 0 || better) { won = colour; winning = count; }
      });
      OVVAR.tex.set(out, x, y, won);   // key 0 is transparency, which is the clear pixel it came from
    }
  }
  return out;
};

/**
 * Tex.withoutGreenKey: the website's overlays mark "erase the skin here" with pure green, and
 * armour has nothing to erase, so those pixels become transparent. Matched loosely, because a
 * colour-managed PNG can decode 00FF00 as 01FE00.
 */
OVVAR.compose.withoutGreenKey = function (im) {
  var out = OVVAR.tex.copy(im);
  for (var y = 0; y < im.h; y++) {
    for (var x = 0; x < im.w; x++) {
      var p = OVVAR.tex.get(im, x, y);
      if (OVVAR.tex.r(p) < 32 && OVVAR.tex.g(p) > 223 && OVVAR.tex.b(p) < 32 && OVVAR.tex.a(p) > 127) {
        OVVAR.tex.set(out, x, y, 0);
      }
    }
  }
  return out;
};

/**
 * GeneratedAssets.flattened: the website renders the skin's second layer as a raised 3D layer
 * (belt folds, pockets, the hanging top of a rolled-down ovve). The armour model has one box per
 * part, so that layer is painted onto the base boxes -- and onto the left limbs' own boxes, for
 * withLeft.
 */
OVVAR.compose.flattened = function (m, skin) {
  var boxes = m.skinBoxes;
  var pairs = [
    [boxes.body, boxes.bodyOuter], [boxes.rightArm, boxes.rightArmOuter], [boxes.rightLeg, boxes.rightLegOuter],
    [boxes.leftArm, boxes.leftArmOuter], [boxes.leftLeg, boxes.leftLegOuter]
  ];
  var out = skin;
  for (var i = 0; i < pairs.length; i++) {
    var base = pairs[i][0], outer = pairs[i][1];
    var over = OVVAR.tex.blit(OVVAR.tex.blank(skin.w, skin.h), skin, outer[0], outer[1], outer[2], outer[3], base[0], base[1]);
    out = OVVAR.tex.composite(out, over);
  }
  return out;
};

/**
 * GeneratedAssets.withLeft: the left limb's art one strip up from the right limb's box
 * (mirrorShift rows), with every face mirrored in place. The armour model draws the left limb as
 * a mirror image off the RIGHT limb's strips, so the art has to be pre-mirrored to come out
 * straight; the plugin's texture B then copies this strip down onto the limb rows, which is what
 * a mirror_uv cube reads.
 *
 * Box layout inside a 16x16 strip: top and bottom faces (4x4) at +4 and +8 on the first four
 * rows, then four 4x12 side faces.
 */
OVVAR.compose.withLeft = function (m, tex, box, skin, leftBox) {
  var x = box[0], y = box[1], my = y - m.mirrorShift;
  var left = OVVAR.tex.blit(OVVAR.tex.blank(64, 64), skin, leftBox[0], leftBox[1], leftBox[2], leftBox[3], 0, 0);
  var out;
  if (OVVAR.tex.isEmpty(left)) {
    out = OVVAR.tex.blit(tex, tex, x, y, box[2], box[3], x, my);
  } else {
    // The skin lays the left limb out for an unmirrored cube: its first side strip is the inner
    // face and its third the outer, the other way round from the right limb's strips the model
    // reads. Swap them so the outer art lands on the outer face.
    out = OVVAR.tex.blit(tex, skin, leftBox[0], leftBox[1], leftBox[2], leftBox[3], x, my);
    out = OVVAR.tex.blit(out, skin, leftBox[0] + 8, leftBox[1] + 4, 4, 12, x, my + 4);
    out = OVVAR.tex.blit(out, skin, leftBox[0], leftBox[1] + 4, 4, 12, x + 8, my + 4);
  }
  out = OVVAR.tex.flipXRect(out, x + 4, my, 4, 4);
  out = OVVAR.tex.flipXRect(out, x + 8, my, 4, 4);
  for (var face = 0; face < 4; face++) out = OVVAR.tex.flipXRect(out, x + face * 4, my + 4, 4, 12);
  return out;
};

/**
 * The chapter's cloth, cut out of its website overlay the way GeneratedAssets does it. The
 * plugin itself loads the committed layer texture instead (ctx.base) -- it is the same pixels,
 * and it costs nothing for a chapter whose cloth is tinted or hand-drawn. This is here so the
 * tests can prove withLeft and flattened were ported right.
 */
OVVAR.compose.buildBase = function (ctx, chapterId, piece, nercabbad) {
  var m = ctx.m, boxes = m.skinBoxes, D = m.detail;
  var chapter = m.chapterById[chapterId];
  // The manifest drops a null property rather than writing it (absentMeansNull), so a chapter
  // with no tint has no `tint` key at all -- read it for truth, not against null.
  if (chapter.tint) throw new Error(chapter.name + ' is tinted; load its committed layer instead');
  function overlay(file) {
    var path = [m.checkout, 'mods/ovvar/src/main/resources/art/ovvar', file].join('/');
    return OVVAR.compose.flattened(m, OVVAR.compose.withoutGreenKey(ctx.io.decode(ctx.io.read(path))));
  }
  function cut(src, list) {
    var out = OVVAR.tex.blank(64, 32);
    for (var i = 0; i < list.length; i++) {
      var b = list[i];
      out = OVVAR.tex.blit(out, src, b[0], b[1], b[2], b[3], b[0], b[1]);
    }
    return out;
  }
  if (piece === 'top') {
    var over = overlay(chapter.art);
    return OVVAR.tex.scale(OVVAR.compose.withLeft(m, cut(over, [boxes.body, boxes.rightArm]), boxes.rightArm, over, boxes.leftArm), D);
  }
  if (!nercabbad) {
    var o2 = overlay(chapter.art);
    return OVVAR.tex.scale(OVVAR.compose.withLeft(m, cut(o2, [boxes.rightLeg, boxes.waist]), boxes.rightLeg, o2, boxes.leftLeg), D);
  }
  // Rolled down: the legs plus the top hanging at the waist, all on the legs slot's layer.
  var rolled = overlay(chapter.nercabbad);
  return OVVAR.tex.scale(OVVAR.compose.withLeft(m, cut(rolled, [boxes.rightLeg, boxes.body]), boxes.rightLeg, rolled, boxes.leftLeg), D);
};

/**
 * Texture B: the mirror strip copied down onto the limb rows, so a mirror_uv cube reading the
 * standard strip shows the left limb's own art. The body box has no mirror strip -- it is one
 * box, drawn unmirrored -- so only the limb this piece owns is copied.
 */
OVVAR.compose.mirrorStrip = function (m, piece, im) {
  var D = m.detail;
  var box = piece === 'top' ? m.skinBoxes.rightArm : m.skinBoxes.rightLeg;
  var my = box[1] - m.mirrorShift;
  return OVVAR.tex.copyRect(im, im, box[0] * D, my * D, box[2] * D, box[3] * D, box[0] * D, box[1] * D);
};

/** Spot.stacked: bottom first -- layer order, and the order they came in within a layer. */
OVVAR.compose.stacked = function (m, placements) {
  return placements.slice().sort(function (a, b) {
    return m.cellById[a.cell].layer - m.cellById[b.cell].layer;
  });
};

/**
 * One half of the garment, as the two textures the cubes wear.
 *
 * A: the cloth, plus every BODY and RIGHT placement. B: the cloth with the mirror strip copied
 * down, plus every LEFT placement. The seat is one patch cut in half, `_r` on A and `_l` on B.
 *
 * A side cell's art is baked through the squeeze (`placedWrapped`) rather than laid on flat:
 * the game does the squeeze in the shader, Blockbench has no shader, and this is what makes art
 * bend round the chest's and the sleeve's corners the way it does in game. A top-face cell (the
 * shoulders) is never squeezed -- the top face is not on the strip's perimeter -- so it stays
 * `placed`. Mirroring a limb's art and then wrapping is the same as wrapping and then mirroring,
 * because the squeeze is symmetric about the anchor face's centre and a limb cell is that whole
 * face.
 */
OVVAR.compose.composePiece = function (ctx, piece, design) {
  var m = ctx.m;
  var base = ctx.base(design.chapter, piece, piece === 'bottom' && !!design.nercabbad);
  var A = OVVAR.tex.copy(base);
  var B = OVVAR.compose.mirrorStrip(m, piece, base);
  var placements = OVVAR.compose.stacked(m, design.placements || []);
  for (var i = 0; i < placements.length; i++) {
    var p = placements[i];
    var cell = m.cellById[p.cell], patch = m.patchById[p.patch];
    if (!cell || !patch) { ctx.warnings.push('unknown placement ' + p.cell + '/' + p.patch); continue; }
    if (cell.piece !== piece) continue;
    if (!patch.seat !== !(cell.id === 'seat')) { ctx.warnings.push(patch.name + ' does not fit ' + cell.label); continue; }
    var sides = cell.side === 'seat' ? ['right', 'left'] : [cell.side];
    for (var s = 0; s < sides.length; s++) {
      var side = sides[s];
      var entry = OVVAR.compose.artFor(m, patch, cell);
      var art = ctx.art(entry.file);
      var x, drawn;
      if (cell.side === 'seat') {
        drawn = OVVAR.tex.crop(art, OVVAR.compose.seatHalf(m, side), 0, m.px, art.h);
        if (side === 'left') drawn = OVVAR.tex.flipX(drawn);
        x = cell.u * m.detail;
      } else {
        drawn = cell.side === 'left' ? OVVAR.tex.flipX(art) : art;
        x = cell.u * m.detail + OVVAR.compose.offsetX(m, cell, entry);
      }
      var tex = cell.top ? OVVAR.compose.placed(m, cell, drawn, x) : OVVAR.compose.placedWrapped(m, cell, drawn, x);
      if (tex === null) {
        ctx.warnings.push(patch.name + ' lands entirely off ' + cell.label + '; not drawn');
        continue;
      }
      var onto = (side === 'left') ? 'B' : 'A';
      if (onto === 'A') A = OVVAR.tex.blit(A, tex, 0, 0, m.texture[0], m.texture[1], 0, 0);
      else B = OVVAR.tex.blit(B, tex, 0, 0, m.texture[0], m.texture[1], 0, 0);
      if (cell.side === 'body') B = OVVAR.tex.blit(B, tex, 0, 0, m.texture[0], m.texture[1], 0, 0);
    }
  }
  return {A: A, B: B};
};

/** Both halves, both sides: what the four textures of the project are set to. */
OVVAR.compose.compose = function (ctx, design) {
  return {top: OVVAR.compose.composePiece(ctx, 'top', design), bottom: OVVAR.compose.composePiece(ctx, 'bottom', design)};
};
