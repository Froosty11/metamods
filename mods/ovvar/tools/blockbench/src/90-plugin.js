// Registration and cleanup. Blockbench runs a plugin file as
// new Function('requireNativeModule', 'require', code), so `require` here is the scoped one --
// 'path', 'zlib' and 'buffer' come free, and 'fs' asks once per folder and then only lets us
// touch that folder. Nothing of ours reaches window, so unloading is a matter of taking back
// what we added to Blockbench.

OVVAR.require = typeof require === 'function' ? require : null;

/**
 * A finished edit on a patch art: remember it for Export, read it back into the composer, and
 * redraw the ovve at the end of the frame.
 *
 * A painted texture arrives as `aspects.textures` normally and as `aspects.layers` once layers
 * are switched on, so both are looked through.
 */
OVVAR.onPaint = function (event) {
  var s = OVVAR.state;
  if (!s.ctx) return;
  var aspects = (event && event.aspects) || {};
  var touched = (aspects.textures || []).slice();
  (aspects.layers || []).forEach(function (layer) { if (layer && layer.texture) touched.push(layer.texture); });
  var hit = false;
  touched.forEach(function (tex) {
    if (!tex || !tex.ovvar_art) return;
    s.dirty[tex.ovvar_art] = true;
    OVVAR.model.readBack(tex.ovvar_art);
    hit = true;
  });
  if (hit) OVVAR.model.scheduleRefresh();
};

if (typeof Plugin !== 'undefined') {
  Plugin.register('ovvar', {
    title: 'Ovvar',
    author: 'METAcraft',
    icon: 'checkroom',
    description: 'Draw a patch, sew it onto an ovve, and see it exactly as the game draws it.',
    about: 'Point it at a METAmods checkout (File > New > Ovvar). It reads '
      + 'mods/ovvar/src/main/generated/ovvar/blockbench/manifest.json, so run '
      + './gradlew :mods:ovvar:runDatagen there first.',
    version: '1.0.0',
    min_version: '5.0.0',
    variant: 'desktop',
    tags: ['Minecraft: Java Edition'],

    onload: function () {
      OVVAR.model.registerFormat();
      OVVAR.panel.register();
      Blockbench.on('finished_edit', OVVAR.onPaint);
      OVVAR.state.listeners.push(['finished_edit', OVVAR.onPaint]);
    },

    onunload: function () {
      var s = OVVAR.state;
      s.listeners.forEach(function (l) { Blockbench.removeListener(l[0], l[1]); });
      s.listeners = [];
      if (s.pending) { cancelAnimationFrame(s.pending.frame); clearTimeout(s.pending.timer); s.pending = null; }
      if (s.panel) { s.panel.delete(); s.panel = null; }
      if (s.format) { s.format.delete(); s.format = null; }
      s.ctx = null;
      s.io = null;
      s.checkout = null;
      s.project = null;
      s.textures = {};
      s.artTextures = {};
      s.added = {};
      s.dirty = {};
      s.cubes = [];
      s.group = null;
    }
  });
}
