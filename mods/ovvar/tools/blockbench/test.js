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
