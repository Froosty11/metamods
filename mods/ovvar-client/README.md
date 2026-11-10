# Ovvar Client

The one optional client-side jar for ovvar (`ovvar-client-<version>.jar`, offered as `ovvar-client.jar`).
It replaced both `OvvarShaderPatcher.jar` and the `ovvar-shaderpatch` mod. Players never need it: a vanilla
client gets exactly what it always got.

## In the mods folder (Fabric, client only)

**Shaderpacks.** At `preLaunch` it looks in `<game dir>/shaderpacks` and, for every shaderpack (`.zip` or
folder) without a current `+ovvar` copy, writes `<name>+ovvar(.zip)` with the ovvar and rift shader code
spliced in (the patcher in `mods/ovvar/tools/shaderpatcher`, compiled into this jar). A copy whose
`ovvar-patch.properties` stamp differs from the current one is rewritten; originals are never touched. If
`config/iris.properties` has `shaderPack=<original>` and that original now has a current copy, that one line
is changed to the copy. Choosing the original pack in Iris is switched back to the `+ovvar` copy at every
launch; the copy starts with the original's Iris settings (`shaderpacks/<pack>.txt`) when it has none of its own.

Nothing here can fail startup: every error is logged (`[ovvar-client]`) and swallowed.

No Polymer, no Fabric API dependency in `fabric.mod.json`. It is client-only and on no server deploy list;
the `dist` bundle skips it.

## Double-clicked (`java -jar`)

The jar's `Main-Class` is the standalone shader patcher (`metacraft.ovvar.shaderpatcher.Patcher`, built for
Java 11+ in its own source set): a file chooser opens in `.minecraft/shaderpacks` and a `+ovvar` copy of each
pick is written. From a terminal: `java -jar ovvar-client.jar [pack.zip|folder ...] [--dry-run]`. The patcher
loads nothing but itself and the JDK, so it runs with no Minecraft or Fabric on the classpath. `mods/ovvar`
embeds this jar at `assets/ovvar/shaderpatcher/ovvar-client.jar` for autohost to serve.

## Tests

`./gradlew :mods:ovvar-client:test -x :mods:metacraft-lib:test`: `StartupPatchTest` (the startup patch) and
`StandaloneJarTest` (builds the jar and runs it with `java -jar` on a fixture pack). Cloud check: "Client test
under a shaderpack" in `mods/ovvar/README.md` (`-PovvarClientMod`).
