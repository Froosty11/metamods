// node test.js [<checkout>]
// Builds the plugin the way build.sh does (cat src/*.js) and runs it through the same
// wrapper Blockbench uses -- new Function('requireNativeModule', 'require', code) -- so the
// tests exercise the very file Vlad loads, not a parallel copy of it.
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert');

const HERE = __dirname;
const SRC = path.join(HERE, 'src');
const CHECKOUT = process.argv[2] || path.resolve(HERE, '..', '..', '..', '..');

const files = fs.readdirSync(SRC).filter(f => f.endsWith('.js')).sort();
const code = files.map(f => fs.readFileSync(path.join(SRC, f), 'utf8')).join('\n');
const OVVAR = new Function('requireNativeModule', 'require', code + '\nreturn OVVAR;')(require, require);

const io = OVVAR.makeIo(require, CHECKOUT);

module.exports = {OVVAR, io, CHECKOUT, test, assert, path, fs};

test('the built plugin exposes its namespace', () => {
  assert.ok(OVVAR.png, 'OVVAR.png missing');
  assert.ok(OVVAR.makeIo, 'OVVAR.makeIo missing');
});

test('png round-trips a committed 8-bit RGBA file', () => {
  const p = path.join(CHECKOUT, 'mods/ovvar/src/main/resources/art/ovvar/patches/itk_8x8.png');
  const im = io.decode(io.read(p));
  assert.strictEqual(im.w, 8);
  assert.strictEqual(im.h, 8);
  assert.strictEqual(im.data.length, 8 * 8 * 4);
  const again = io.decode(io.encode(im));
  assert.strictEqual(again.w, im.w);
  assert.strictEqual(again.h, im.h);
  assert.deepStrictEqual(Array.from(again.data), Array.from(im.data));
});

test('png round-trip preserves RGB under alpha 0 (the canvas would not)', () => {
  // it-nercabbad.png carries 182 texels with alpha 0 and non-zero RGB -- datagen's `placed`
  // treats any texel with a non-zero packed ARGB as present, alpha-0 included, so this fixture's
  // hidden colour under transparency is part of the goldens. Assert the fixture still has such
  // pixels (so this test fails loudly, not silently, if the file is ever replaced) and that the
  // codec's round trip is byte-identical, RGB-under-alpha-0 included.
  const p = path.join(CHECKOUT, 'mods/ovvar/src/main/resources/art/ovvar/it-nercabbad.png');
  const im = io.decode(io.read(p));
  assert.strictEqual(im.w, 64);
  assert.strictEqual(im.h, 64);
  assert.strictEqual(im.data.length, 64 * 64 * 4);
  let alphaZeroWithColour = 0;
  for (let i = 0; i < im.data.length; i += 4) {
    if (im.data[i + 3] === 0 && (im.data[i] !== 0 || im.data[i + 1] !== 0 || im.data[i + 2] !== 0)) {
      alphaZeroWithColour++;
    }
  }
  assert.strictEqual(alphaZeroWithColour, 182,
    'fixture no longer has the expected alpha-0-with-colour pixels; this test would not catch a premultiplying codec');
  const again = io.decode(io.encode(im));
  assert.strictEqual(again.w, im.w);
  assert.strictEqual(again.h, im.h);
  assert.deepStrictEqual(Array.from(again.data), Array.from(im.data));
});

test('png decodes a 128x64 generated texture and a non-RGBA file', () => {
  const big = io.decode(io.read(path.join(CHECKOUT,
    'mods/ovvar/src/main/generated/assets/ovvar/textures/entity/equipment/humanoid/data/top.png')));
  assert.strictEqual(big.w, 128);
  assert.strictEqual(big.h, 64);
  // bakparti.png is colour type 2 (RGB, no alpha): the decoder must fill alpha 255.
  const rgb = io.decode(io.read(path.join(CHECKOUT,
    'mods/ovvar/src/main/resources/art/ovvar/patches/bakparti.png')));
  assert.strictEqual(rgb.w, 32);
  assert.strictEqual(rgb.h, 8);
  assert.strictEqual(rgb.data[3], 255);
});

// The checkout has no palette or grayscale PNGs, so colour types 0 and 3 (with tRNS) are
// otherwise unexercised. These two tiny fixtures were generated once with Pillow and are
// committed as data (no npm dependency, nothing regenerates them at test time):
//
//   python3 -c "
//   from PIL import Image
//   palette_pixels = [[0,1,2,3],[3,2,1,0],[0,0,3,3],[1,2,3,0]]
//   im = Image.new('P', (4, 4))
//   pal = [255,0,0, 0,255,0, 0,0,255, 255,255,255] + [0] * (768 - 12)
//   im.putpalette(pal)
//   for y, row in enumerate(palette_pixels):
//       for x, idx in enumerate(row):
//           im.putpixel((x, y), idx)
//   im.info['transparency'] = bytes([255, 128, 0, 255])
//   im.save('palette-trns-4x4.png', 'PNG')
//   gray_pixels = [[0,64,128,255],[255,128,64,0],[16,32,48,64],[200,150,100,50]]
//   gim = Image.new('L', (4, 4))
//   for y, row in enumerate(gray_pixels):
//       for x, v in enumerate(row):
//           gim.putpixel((x, y), v)
//   gim.save('gray-4x4.png', 'PNG')
//   "
//
// Both came out 8-bit (this decoder only supports 8-bit depth; Pillow does not have a
// documented way to force sub-byte palette/grayscale depths, so there is no 2-bit or 16-bit
// fixture here -- OVVAR.png.decode throws on any depth other than 8, which the truncated-file
// test below exercises the general failure path for).
const FIXTURES = path.join(HERE, 'test-fixtures');

test('png decodes a palette (colour type 3) image with tRNS', () => {
  const im = io.decode(io.read(path.join(FIXTURES, 'palette-trns-4x4.png')));
  assert.strictEqual(im.w, 4);
  assert.strictEqual(im.h, 4);
  const RED = [255, 0, 0, 255];
  const GREEN_HALF = [0, 255, 0, 128];
  const BLUE_TRANSPARENT = [0, 0, 255, 0];
  const WHITE = [255, 255, 255, 255];
  const rows = [
    [RED, GREEN_HALF, BLUE_TRANSPARENT, WHITE],
    [WHITE, BLUE_TRANSPARENT, GREEN_HALF, RED],
    [RED, RED, WHITE, WHITE],
    [GREEN_HALF, BLUE_TRANSPARENT, WHITE, RED]
  ];
  for (let y = 0; y < 4; y++) {
    for (let x = 0; x < 4; x++) {
      const d = (y * 4 + x) * 4;
      assert.deepStrictEqual(Array.from(im.data.subarray(d, d + 4)), rows[y][x], `pixel (${x},${y})`);
    }
  }
});

test('png decodes a grayscale (colour type 0) image', () => {
  const im = io.decode(io.read(path.join(FIXTURES, 'gray-4x4.png')));
  assert.strictEqual(im.w, 4);
  assert.strictEqual(im.h, 4);
  const rows = [
    [0, 64, 128, 255],
    [255, 128, 64, 0],
    [16, 32, 48, 64],
    [200, 150, 100, 50]
  ];
  for (let y = 0; y < 4; y++) {
    for (let x = 0; x < 4; x++) {
      const d = (y * 4 + x) * 4;
      const v = rows[y][x];
      assert.deepStrictEqual(Array.from(im.data.subarray(d, d + 4)), [v, v, v, 255], `pixel (${x},${y})`);
    }
  }
});

test('png decode throws on a truncated file instead of returning garbage', () => {
  const full = io.read(path.join(CHECKOUT, 'mods/ovvar/src/main/resources/art/ovvar/patches/itk_8x8.png'));
  const truncated = full.subarray(0, 40);
  assert.throws(() => io.decode(truncated));
});

test('the image ops behave like Tex', () => {
  const t = OVVAR.tex;
  const a = t.blank(4, 3);
  assert.strictEqual(a.w, 4);
  assert.strictEqual(a.h, 3);
  assert.strictEqual(t.get(a, 1, 1), 0);
  t.set(a, 1, 1, 0xFF804020);
  assert.strictEqual(t.get(a, 1, 1), 0xFF804020);
  // A texel written at alpha 0 keeps its colour: datagen's `placed` copies any non-zero packed
  // ARGB, so those texels are part of the goldens and may not be flattened away.
  t.set(a, 2, 1, 0x0000FF00);
  assert.strictEqual(t.get(a, 2, 1), 0x0000FF00);
  // blit skips alpha-0 source texels (Tex.blit tests a(p) > 0), so 0x0000FF00 does not travel.
  const b = t.blit(t.blank(4, 3), a, 0, 0, 4, 3, 0, 0);
  assert.strictEqual(t.get(b, 1, 1), 0xFF804020);
  assert.strictEqual(t.get(b, 2, 1), 0);
  // flipX mirrors the whole image; flipXRect only the rectangle.
  const f = t.flipX(a);
  assert.strictEqual(t.get(f, 2, 1), 0xFF804020);
  // scale is nearest-neighbour by an integer factor.
  const s = t.scale(a, 2);
  assert.strictEqual(s.w, 8);
  assert.strictEqual(s.h, 6);
  assert.strictEqual(t.get(s, 3, 3), 0xFF804020);
  assert.strictEqual(t.get(s, 2, 2), 0xFF804020);
  // crop lifts a rectangle out; the source's alpha-0 texels do not survive blit, as above.
  const c = t.crop(a, 1, 1, 2, 1);
  assert.strictEqual(c.w, 2);
  assert.strictEqual(t.get(c, 0, 0), 0xFF804020);
  assert.deepStrictEqual(t.diff(a, t.copy(a), []), []);
});

test('anchored matches all 512 manifest samples exactly', () => {
  const m = OVVAR.loadManifest(io, CHECKOUT);
  assert.strictEqual(m.anchoredSamples.length, 512);
  let worst = 0;
  for (const [skinX, inflate, anchor, want] of m.anchoredSamples) {
    const got = OVVAR.compose.anchored(skinX, inflate, anchor);
    worst = Math.max(worst, Math.abs(got - want));
  }
  assert.ok(worst < 1e-9, 'anchored is off by ' + worst);
});
