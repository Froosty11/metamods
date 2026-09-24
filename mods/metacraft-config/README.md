# metacraft-config

`/config` opens a vanilla dialog listing every config the server describes, so operators change mod
settings in game instead of editing JSON over SFTP. Requires permission `metacraft.config`, level 3
(the same as `/reload`). `/config <id>` jumps straight to one config.

The main page has a button per config, marked when it has a load error or changes waiting for a
restart, plus a line listing what's pending. A config's page has one input per option — label is its
description, with the range appended for numbers — and Save, Close, Back, and Reset to defaults
(which asks first). Nested config records get their own page, opened by a button.

Saving decodes the whole page through the config's codec, the same validation used when loading from
disk, so a bad value or a failed `validate()` is refused with the error and nothing is written. A save
made against a config that changed since the page was opened — someone else saved first — is also
refused, and the page reopens with the current values. Options marked `(restart)` are saved right away
but only take effect after the server restarts; they're what shows up in the pending-restart list.

If a config's file fails to load, the server keeps running on its last good values instead of
overwriting the file with defaults. A described config's file is read key by key: a bad value (out of
range, an unknown choice, the wrong type) is reported and replaced by its default, and every other key
keeps the file's value, so the config's page shows what the file says apart from the bad keys. The
error shows on that config's page and is sent to operators when they join. The file itself is left
alone until someone saves from `/config`, which writes the page's values, defaults included, over it.
An empty or whitespace-only file is a load error too; it is no longer reset to defaults.

## Joining a config

Convert the config's static part to a record, annotate it, and register it:

```java
@Config(name = "Faster Minecarts", description = "Faster minecarts, with speed boosts from blocks the server lists.")
public record FasterMinecartsConfig(
        @Option(description = "Every minecart is fast, not only upgraded ones.") boolean globalFasterMinecarts,
        @Option(description = "Top speed of a fast minecart, blocks per second.", min = 0) double maxMinecartSpeed,
        @Option(description = "Use vanilla's experimental minecart physics.", restart = true) ExperimentalMinecartMode experimentalMinecartMode
) {
    public static final FasterMinecartsConfig DEFAULT = new FasterMinecartsConfig(false, 60, ExperimentalMinecartMode.EXPERIMENTAL);

    public static final MapCodec<FasterMinecartsConfig> CODEC = ConfigSpec.of(FasterMinecartsConfig.class).codec();
}
```

Every component needs an `@Option`; the record needs a `public static final DEFAULT`. Build the codec
with `ConfigSpec.of(X.class).codec()`, and register with `.describedBy(X.class)` on
`ConfigContainer.Builder` (or on `RegistryAwareBuilder#makeRegistryAware(...)`, to describe just the
static part). An undescribed container behaves as before and isn't listed in `/config`.

Numbers, `boolean`, `String`/`Identifier`, `StringRepresentable` enums, `Optional<T>` of those, and
`List<String>`/`List<Identifier>` are supported. With a generated codec (`ConfigSpec.of(X.class).codec()`)
any other type is refused at startup; only a config with a hand-written codec can have such an option,
and it shows up read-only. A number is a
**slider** only when it's not optional, both `min` and `max` are set, and the range has at most 50 steps
(`@Option(step = …)`, default 1 for whole numbers) — everything else, including every `double` without a
`step`, is a typed field parsed on save. See `mods/faster-minecarts` for a worked example, and
`mods/metacraft-lib/src/main/java/nu/metacraft/lib/config/describe/` for the annotations and
`ConfigSpec`.

## PolyDecorations adapter

`metacraft-config` also edits `config/polydecorations.json`'s `features` object, the format used by the
Froosty11 fork's branch of PolyDecorations. It's detected at server start when `polydecorations` is
loaded and the file has that shape; every feature is a `(restart)` toggle. There's no compile-time
dependency either way.

## Screenshots

`docs/` has screenshots of the main page, the faster-minecarts page, and a config whose file failed to
load, taken by a vanilla client against an in-process server. Regenerate them with:

```
./gradlew :mods:metacraft-config:runClientGameTest
```
