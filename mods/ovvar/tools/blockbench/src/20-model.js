// The Blockbench half: the format, the six armour cubes, and the textures they wear. Everything
// that knows what a pixel should be lives in 10-compose.js; this file only hangs the result on a
// model. Nothing here runs under Node.

OVVAR.state = {
  io: null,          // the scoped file system, once a checkout is open
  ctx: null,         // OVVAR.compose.ctx
  checkout: null,
  project: null,     // the ModelProject the cubes below belong to
  design: {chapter: null, nercabbad: false, placements: []},
  textures: {},      // topA, topB, bottomA, bottomB
  artTextures: {},   // catalogue file name -> Texture Vlad paints on
  added: {},         // patch id -> the catalogue entry this project invented
  dirty: {},         // art file name -> true, for Export
  cubes: [],
  group: null,
  format: null,
  panel: null,
  listeners: [],
  pending: null      // the requestAnimationFrame that coalesces a burst of refreshes
};

OVVAR.model = {};

/**
 * The armour model, in Blockbench units. Read out of Blockbench's own `armor` skin template
 * (js/formats/minecraft/skin.ts) and converted from bedrock origin/size the way its bedrock
 * codec does -- from[0] = -(origin[0] + size[0]), to = from + size -- with three corrections:
 * the template fudges the chest to inflate 1.01 and the belt to 0.51 to avoid z-fighting with
 * the skin, and nudges the legs 0.1 apart. The game inflates exactly Spot.inflate (1 and 0.5)
 * and the legs meet at x 0, so the plugin uses those.
 *
 * Blockbench's +x is the wearer's RIGHT. The armour model's arms are 4 wide on Alex as on Steve
 * -- there is no slim armour model -- so this table never varies.
 */
OVVAR.model.CUBES = [
  {name: 'ovve_body',  piece: 'top',    side: 'A', from: [-4, 12, -2], to: [4, 24, 2],   uv: [16, 16], inflate: 1.0, mirror: false},
  {name: 'ovve_arm_r', piece: 'top',    side: 'A', from: [4, 12, -2],  to: [8, 24, 2],   uv: [40, 16], inflate: 1.0, mirror: false},
  {name: 'ovve_arm_l', piece: 'top',    side: 'B', from: [-8, 12, -2], to: [-4, 24, 2],  uv: [40, 16], inflate: 1.0, mirror: true},
  {name: 'ovve_belt',  piece: 'bottom', side: 'A', from: [-4, 12, -2], to: [4, 24, 2],   uv: [16, 16], inflate: 0.5, mirror: false},
  {name: 'ovve_leg_r', piece: 'bottom', side: 'A', from: [0, 0, -2],   to: [4, 12, 2],   uv: [0, 16],  inflate: 0.5, mirror: false},
  {name: 'ovve_leg_l', piece: 'bottom', side: 'B', from: [-4, 0, -2],  to: [0, 12, 2],   uv: [0, 16],  inflate: 0.5, mirror: true}
];

/**
 * The UV resolution is the SKIN's, 64 x 32, not the texture's 128 x 64.
 *
 * Blockbench computes a box UV in cube-size units offset by `uv_offset` and only then divides by
 * the UV resolution (Canvas.updateUV in outliner/types/cube.js), so the offsets above -- 16, 40,
 * 0 -- are skin texels and the divisor has to be the skin. The mod's armour layers are that same
 * vanilla layout drawn at 2x; the image is 128 x 64 and every face still lands on whole texels.
 * Setting the resolution to 128 x 64 instead loses the arms and reads the body as garbage.
 */
OVVAR.model.uvSize = function (m) { return [m.skin[0], m.skin[1]]; };

OVVAR.model.registerFormat = function () {
  OVVAR.state.format = new ModelFormat('ovvar', {
    name: 'Ovvar',
    description: 'A METAcraft ovve: patches on the armour model, drawn the way the game draws them.',
    icon: 'checkroom',
    category: 'minecraft',
    target: ['Minecraft: Java Edition'],
    box_uv: true,
    // The built-in `skin` format is single_texture and could not hold top and bottom at once,
    // let alone their two mirror-strip variants. This one holds four.
    single_texture: false,
    per_texture_uv_size: true,
    bone_rig: false,
    centered_grid: true,
    model_identifier: false,
    rotate_cubes: false,
    integer_size: false,
    // Blockbench calls this with (project, new_model): true from File > New, false when a saved
    // project is being set up, and nothing at all when a project is converted into this format.
    // Only a brand new one should go looking for a checkout.
    onSetup: function (project, newModel) {
      if (!newModel) return;
      var skin = [64, 32];
      Project.texture_width = skin[0];
      Project.texture_height = skin[1];
      OVVAR.panel.askForCheckout();
    }
  });
  return OVVAR.state.format;
};

/** True when the six cubes of this state are still in the project they were made in. */
OVVAR.model.hasGeometry = function () {
  var s = OVVAR.state;
  return !!(s.project && typeof Project !== 'undefined' && Project === s.project && s.cubes.length);
};

/** Open a checkout: load its manifest, build the cubes, make the textures, compose once. */
OVVAR.model.build = function (checkout) {
  var s = OVVAR.state;
  var io = OVVAR.makeIo(OVVAR.require, checkout);
  var m = OVVAR.loadManifest(io, checkout);
  s.checkout = checkout;
  s.io = io;
  s.ctx = OVVAR.model.paintable(OVVAR.compose.ctx(io, m));
  if (!s.design.chapter || !m.chapterById[s.design.chapter]) s.design.chapter = m.chapters[0].id;
  if (!m.chapterById[s.design.chapter].rollable) s.design.nercabbad = false;

  var uv = OVVAR.model.uvSize(m);
  Project.texture_width = uv[0];
  Project.texture_height = uv[1];

  if (!OVVAR.model.hasGeometry()) {
    Undo.initEdit({outliner: true, elements: [], textures: []});
    s.project = Project;
    s.group = new Group({name: 'ovve', origin: [0, 0, 0]}).init();
    s.cubes = OVVAR.model.CUBES.map(function (c) {
      var cube = new Cube({
        name: c.name, from: c.from.slice(), to: c.to.slice(), origin: [0, 0, 0],
        box_uv: true, uv_offset: c.uv.slice(), inflate: c.inflate, mirror_uv: c.mirror
      });
      cube.addTo(s.group);
      return cube.init();
    });

    s.textures = {};
    ['topA', 'topB', 'bottomA', 'bottomB'].forEach(function (key) {
      var tex = new Texture({name: 'ovve_' + key + '.png'})
        .fromDataURL(io.dataUrl(OVVAR.tex.blank(m.texture[0], m.texture[1]))).add(false);
      tex.uv_width = uv[0];
      tex.uv_height = uv[1];
      s.textures[key] = tex;
    });
    OVVAR.model.CUBES.forEach(function (c, i) {
      s.cubes[i].applyTexture(s.textures[c.piece + c.side], true);
    });
    Undo.finishEdit('Build the ovve', {outliner: true, elements: s.cubes, textures: Texture.all});
  }

  OVVAR.model.loadCatalogueTextures();
  OVVAR.model.refresh();
  Canvas.updateAll();
};

/**
 * One texture per catalogue art, named by its file, so Vlad paints on the same PNG the mod
 * loads. None of them is on a cube: they are edited in Paint mode and the garment textures are
 * recomposed from them on every finished stroke.
 */
OVVAR.model.loadCatalogueTextures = function () {
  var s = OVVAR.state, m = s.ctx.m;
  m.patches.forEach(function (patch) {
    patch.arts.forEach(function (art) {
      if (s.artTextures[art.file]) return;
      var image = s.ctx.art(art.file);
      var tex = new Texture({name: art.file.replace('patches/', '')})
        .fromDataURL(s.io.dataUrl(image)).add(false);
      tex.uv_width = image.w;
      tex.uv_height = image.h;
      tex.ovvar_art = art.file;
      s.artTextures[art.file] = tex;
    });
  });
};

OVVAR.model.artTextureFor = function (file) {
  return OVVAR.state.artTextures[file];
};

/**
 * Recompose and push. Called on every panel change and on every finished paint stroke whose
 * texture is a patch art -- a few 128x64 blits, milliseconds.
 *
 * `ctx.warnings` is append-only by design (composePiece only ever pushes), so it is emptied in
 * place here, before the run that fills it, and what comes out is handed to the panel.
 */
OVVAR.model.refresh = function () {
  var s = OVVAR.state;
  if (!s.ctx) return;
  s.ctx.warnings.length = 0;
  var result = OVVAR.compose.compose(s.ctx, s.design);
  s.textures.topA.updateSource(s.io.dataUrl(result.top.A));
  s.textures.topB.updateSource(s.io.dataUrl(result.top.B));
  s.textures.bottomA.updateSource(s.io.dataUrl(result.bottom.A));
  s.textures.bottomB.updateSource(s.io.dataUrl(result.bottom.B));
  if (s.panel && s.panel.inside_vue) {
    s.panel.inside_vue.warnings = s.ctx.warnings.slice();
    s.panel.inside_vue.revision++;
  }
  Canvas.updateAll();
};

/**
 * A refresh at the end of this frame. A stroke can finish several edits in a row and the model
 * only has to be right once; a recompose is a handful of milliseconds, so one frame of delay is
 * all the coalescing it needs.
 *
 * The timer beside the frame is not a second debounce: requestAnimationFrame does not run at all
 * while the window is hidden, and an edit can still finish there. Without it the first such edit
 * would leave `pending` set for ever and the ovve would stop redrawing even once the window came
 * back. Whichever fires first cancels the other.
 */
OVVAR.model.scheduleRefresh = function () {
  var s = OVVAR.state;
  if (s.pending) return;
  var run = function () {
    if (!s.pending) return;
    cancelAnimationFrame(s.pending.frame);
    clearTimeout(s.pending.timer);
    s.pending = null;
    OVVAR.model.refresh();
  };
  s.pending = {frame: requestAnimationFrame(run), timer: setTimeout(run, 100)};
};

/**
 * A patch art texture Vlad has just painted, read back into an image. `forgetArt` below then
 * makes the composer read it again.
 *
 * Canvas pixels are premultiplied, so a texel painted at alpha 0 loses its colour here -- which
 * is right: the brush never produces one, and the PNG the export writes is encoded from this
 * same image.
 */
OVVAR.model.readTexture = function (tex) {
  if (tex.layers_enabled) tex.updateLayerChanges();
  var w = tex.width, h = tex.height;
  var data = tex.ctx.getImageData(0, 0, w, h).data;
  return {w: w, h: h, data: new Uint8Array(data)};
};

/**
 * A ctx whose `art` can be overruled by what is on a Texture right now.
 *
 * 10-compose.js memoises every art it reads off disk, which is what makes a recompose cheap --
 * but a stroke Vlad has just painted is not on disk, and a patch invented in this session has no
 * file at all. So `art` is wrapped rather than the memo picked open: `put` wins, the memo is
 * consulted only for files nothing has painted, and 10-compose.js stays a file that knows
 * nothing about Blockbench.
 */
OVVAR.model.paintable = function (ctx) {
  var painted = {};
  var read = ctx.art;
  ctx.painted = painted;
  ctx.art = function (file) { return painted[file] || read(file); };
  ctx.put = function (file, image) { painted[file] = image; };
  return ctx;
};

/** Put what Vlad has painted on a texture in front of whatever the checkout holds. */
OVVAR.model.readBack = function (file) {
  var s = OVVAR.state;
  var tex = s.artTextures[file];
  if (!s.ctx || !tex || !tex.width) return;
  s.ctx.put(file, OVVAR.model.readTexture(tex));
};
