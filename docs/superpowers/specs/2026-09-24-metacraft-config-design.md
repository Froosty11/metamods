# metacraft-config: server-side config screen, and metacraft-lib's config described

Date: 2026-09-24 · Branch `metacraft-config`, cut from `upstream/dev` (29a038a7) so it can go back as one PR.

## Goal

Operators change mod settings in game, through a vanilla dialog, instead of editing JSON over SFTP or
growing a one-off command per option (`/ovvar minigame`). One screen lists every described config the
server runs: ours through metacraft-lib, and mods we can add a small adapter for (the PolyDecorations
fork). What the owner said: dialogs "similar to other config APIs but server-side"; scope B (ours plus
hookable mods); options described with **annotations on the config records**; everything stays in
METAmods; metacraft-lib's config should come out of this improved where it needs it.

Success: an operator on a vanilla client opens `/config`, changes faster-minecarts' speed, saves, and
the change is live without a restart; toggling a PolyDecorations feature is saved and marked as
applying after restart; a broken edit is refused with a message and nothing is written.

## Non-goals

- Configs of mods we cannot hook (scope C). The adapter interface allows them later.
- Editing registry-dependent values (predicates, tags, holders) in the screen: shown read-only.
- Migrating every existing config. This branch converts faster-minecarts as the proof; the others move
  when their owners touch them. Ovvar is not on `dev` and follows on its own branch.
- Splitting the monorepo or changing how `dist` deploys.

## Part 1: describing a config (metacraft-lib)

A config is a **record** (classes cannot be described; converting one to a record is part of joining).
Annotations, `RUNTIME` retention, target `RECORD_COMPONENT`:

```java
@Config(name = "Faster Minecarts", description = "Faster minecarts on powered rails.")
public record FasterMinecartsConfig(
        @Option(description = "Every minecart is fast, not only on the listed rails.") boolean globalFasterMinecarts,
        @Option(description = "Top speed, blocks per second.", min = 0) double maxMinecartSpeed,
        @Option(description = "…", min = 0) Optional<Double> dangerousMinecartSpeed,
        @Option(description = "…") ExperimentalMinecartMode experimentalMinecartMode) {
    public static final FasterMinecartsConfig DEFAULT = new FasterMinecartsConfig(…);
}
```

- `@Config(name, description)` on the record; `@Option(description, min, max, step, restart, key)` on a
  component. `key` overrides the file key; by default it is the component name in snake_case.
  `min`/`max` default to unbounded (`-Infinity`/`Infinity`); `restart` defaults to false.
- Supported types and their input:
  - `boolean` → toggle;
  - `int`, `long`, `double`, `float` → a number field, or a slider for short ranges (see Part 3);
  - `String`, `Identifier` → text field;
  - an `enum` implementing `StringRepresentable` → single option;
  - `Optional<T>` of any of the above → the same input, empty meaning absent;
  - `List<String>`, `List<Identifier>` → multi-line text, one entry per line;
  - a nested `@Config` record → its own page, opened by a button.
  Anything else is shown read-only with "edit in the file".
- The record declares `public static final <Type> DEFAULT`.
- Cross-field rules: an optional `public Optional<String> validate()` on the record; a present value is
  an error shown to the operator and blocks the save.

**File format.** `ConfigSpec.of(Record.class)` builds the `MapCodec` from the components (walking them
directly, so no 16-field limit): snake_case keys, a missing key means the component's value in
`DEFAULT`, and a `_help` object with every description (ignored on read). A config whose codec is
hand-written keeps it; the annotations then only describe, and registration checks that every
described key appears when `DEFAULT` is encoded, failing startup with the key's name otherwise.

**Checked at registration**, failing startup with the record and component named: `min > max`, an
unsupported type on an editable component, a missing or wrong-typed `DEFAULT`, a key collision.

**Editing goes through the codec**: encode the current value to JSON, apply the edited fields, decode
(the same validation as loading from disk), then `validate()`. There is no second validation path.

## Part 2: metacraft-lib's config changes

1. **Registration.** `ConfigContainer.Builder#describedBy(Class<R>)` (and on the registry-aware
   builder, describing the static part) registers the container in `ConfigRegistry` with its mod id,
   record class and `ConfigSpec`. Undescribed containers behave as today and are not listed.
2. **A file that does not load.** The container keeps the last good value (or `DEFAULT` on first
   start) and does **not** overwrite or back up the file. The error is kept on the container
   (`loadError()`), shown on that config's page and to operators when they join. Replaces today's
   back-up-and-reset.
3. **One way to edit.** `update(UnaryOperator<T>)`: build, validate, save, notify. `replace` stays as
   its plain form. `modify`, its queue and `Modifiable` are deprecated, not removed: three mods use
   them (loot-containers, resource-packs, saved-items) and keep working.
4. **Change listeners.** `onChange(BiConsumer<T, T>)`, called with old and new after an `update` or a
   reload that changed the value. The container tracks, per `restart = true` option, whether the saved
   value differs from the one the server started with ("pending until restart").
5. **Tidy** the unchecked casts in `RegistryAwareBuilder` (the "super hacky hack"), behaviour unchanged.

Existing callers compile and behave as before.

## Part 3: the screen (new mod `mods/metacraft-config`)

- `/config` opens the main page; `/config <id>` opens one config. Permission `metacraft.config`,
  level 3 (same as `/reload`), through fabric-permissions-api like the other mods.
- **Main page**: a button per registered config, marked when it has a load error or pending-restart
  changes; a "restart needed" line lists the pending ones.
- **Config page**: one dialog with an input per option, label = its description, range in the label.
  Nested records are buttons. Buttons: Save, Cancel, Back, Reset to defaults (asks first). Vanilla
  dialogs scroll; pages past ~20 options should be split into nested records by the config's author.
- Dialog inputs are vanilla's: `boolean`, `number_range`, `text` (multi-line for lists),
  `single_option`. Vanilla's slider cannot be typed into, so a number is a slider only when its range
  is bounded and has at most 50 steps (an `int` 1–16, or a `double` with `@Option(step = 0.05)` over
  0–1); every other number is a `text` input labelled with its range and parsed on save, with an error
  for text that is not a number or is out of range. `@Option` gains `step` (default 1 for integers;
  a `double` without `step` is always a text input). Button presses go back to the server through metacraft-lib's existing
  `CodecMessageHandler` / `DialogHelper`.
- **Saving**: the dialog submits every input at once. Decode as in Part 1 → `update`. On success the
  page reopens with "Saved"; on failure it reopens with the operator's values and the error at the top,
  nothing written. Restart options are saved and shown as "applies after restart".
- **Concurrent edits**: a page carries the hash of the value it was opened with; a save whose hash no
  longer matches is refused with "changed by someone else" and the page reopens with the current value.

**Sources.** The screen talks to a `ConfigSource` interface (id, name, options, current values, load
error, apply edited values → result). metacraft-lib's registered containers are one implementation;
adapters are others.

**PolyDecorations adapter.** Inside metacraft-config, active only when `polydecorations` is loaded and
`config/polydecorations.json` has a `features` object (the Froosty11 fork, branch `metacraft-config`).
Each feature is a toggle, all `restart = true`; writes the same JSON back. No dependency from the fork
on us, and none from us on the fork at compile time.

## Testing

Server game tests (in CI with the rest):
- every registered config passes the registration checks;
- round trip: update → save → reload gives the same value; the file has `_help`;
- an out-of-range or invalid value is refused and the file is unchanged;
- `validate()` errors block the save;
- the concurrent-edit check refuses a stale save;
- a config file that does not parse leaves the last good value in use, the file untouched, and
  `loadError()` set;
- the PolyDecorations adapter reads and writes a fork-format file (from a fixture, so the test does
  not need the mod).

A client test takes screenshots of the main page, the faster-minecarts page and a page with an error,
for review.

## Order of work

1. metacraft-lib: annotations, `ConfigSpec`, registration checks, `ConfigRegistry`.
2. metacraft-lib: load-error handling, `update`, `onChange`, pending-restart tracking; tidy casts.
3. faster-minecarts: static part to a described record (file keys unchanged, so existing files load).
4. metacraft-config: sources, pages, saving, `/config`.
5. PolyDecorations adapter.
6. Tests alongside each step; screenshots at the end.

## Risks

- Changing load behaviour (no more reset-to-defaults) is visible to every mod: a broken file now keeps
  the server on its old settings instead of defaults. Called out in the PR.
- faster-minecarts' file must keep loading unchanged: its keys are pinned by a test, and its ranges stay
  what its codec accepts today (`min = 0`, no maximum), so no existing value becomes invalid.
- Dialog size limits on very long pages; mitigated by nested pages.
