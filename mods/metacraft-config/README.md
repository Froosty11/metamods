# metacraft-config

`/config` opens a vanilla dialog listing every config the server describes, so operators change mod
settings in game instead of editing JSON over SFTP. Requires permission `metacraft.config`, level 3
(the same as `/reload`). `/config <id>` jumps straight to one config.

The main page has a button per config, marked when it has a load error or changes waiting for a
restart, plus a line listing what's pending. A config's page has one input per option — label is its
description, with the range appended for numbers — and Save, Close, Back, and Reset to defaults
(which asks first). Nested config records get their own page, opened by a button.

Saving checks each number against its option's range, then decodes the whole page through the config's
codec, the same validation used when loading from disk, so a bad value or a failed `validate()` is
refused with the error and nothing is written. The saved value is written out once more and each edited
key must come back as typed; if the codec stores the option under another key, the save is refused
rather than silently dropped. A save
made against a config that changed since the page was opened — someone else saved first — is also
refused, and the page reopens with the current values. Options marked `(restart)` are saved right away
but only take effect after the server restarts; they're what shows up in the pending-restart list.

If a config's file fails to load, the server keeps running instead of overwriting the file with
defaults. It keeps the values it could read; the rest are defaults or the last good values. How much
of a partly readable file counts as read is up to the config's codec: a `RecordCodecBuilder` codec
gives nothing back when one key is out of range, so the whole config stays at its defaults (or its
last good values). The error shows on that config's page and is sent to operators when they join. The file
itself is left alone until someone saves from `/config`, which writes the page's values over it. An
empty or whitespace-only file is a load error too; it is no longer reset to defaults.

## Joining a config

Keep your codec: it stays the file format. Make the config's static part a record, annotate it with
`@Config` and an `@Option` per component, add a `public static final DEFAULT`, and call
`.describedBy(X.class)`:

```java
@Config(name = "Faster Minecarts", description = "Faster minecarts, with speed boosts from blocks the server lists.")
public record FasterMinecartsConfig(
        @Option(description = "Every minecart is fast, not only upgraded ones.") boolean globalFasterMinecarts,
        @Option(description = "Top speed of a fast minecart, blocks per second.", min = 0) double maxMinecartSpeed,
        @Option(description = "Use vanilla's experimental minecart physics.", restart = true) ExperimentalMinecartMode experimentalMinecartMode
) {
    public static final FasterMinecartsConfig DEFAULT = new FasterMinecartsConfig(false, 60, ExperimentalMinecartMode.EXPERIMENTAL);

    public static final MapCodec<FasterMinecartsConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.fieldOf("global_faster_minecarts").forGetter(FasterMinecartsConfig::globalFasterMinecarts),
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("max_minecart_speed").forGetter(FasterMinecartsConfig::maxMinecartSpeed),
            ExperimentalMinecartMode.CODEC.fieldOf("experimental_minecart_mode").forGetter(FasterMinecartsConfig::experimentalMinecartMode)
    ).apply(instance, FasterMinecartsConfig::new));
}

ConfigContainer.Builder.create(CODEC, () -> DEFAULT).describedBy(FasterMinecartsConfig.class).build(path);
```

An option's key is its component's name in snake_case, or `@Option(key = …)`. At startup every
described key must be one of the codec's keys (`MapCodec#keys`, which `RecordCodecBuilder` fills in,
so `optionalFieldOf(key, default)` counts), so the description and the file can't drift apart. A
custom `MapCodec` that declares no keys is checked by writing `DEFAULT` instead. A nested section has its
own codec, so each section written for `DEFAULT` must be an object holding the section's keys (a
failure names the path, like `store.interval`). A key or a section may be left out only while it holds
the value in its record's `DEFAULT`, which is what `optionalFieldOf(key, default)` does. `.describedBy(...)` also works on
`RegistryAwareBuilder#makeRegistryAware(...)`, to describe just the static part. An undescribed
container behaves as before and isn't listed in `/config`.

`@Option(min, max)` is the range the screen allows: an edit outside it is refused. Your codec keeps
enforcing its own ranges when the file is loaded, so give both the same range.

Numbers, `boolean`, `String`/`Identifier`, `StringRepresentable` enums, `Optional<T>` of those, and
`List<String>`/`List<Identifier>` can be edited. An option of any other type is shown read-only, to be
edited in the file. A number is a
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
