# More Dyes: animated colours (Rainbow) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A `colors.json` colour can be animated. Rainbow ships as the third colour and drifts
through 24 hues over 3 minutes on blocks, items, sheep, banners and worn dyed armour, and paints
random rainbow pixels on PolyDecorations canvases.

**Architecture:** An optional `Animation` on `ModColor`. Datagen writes each of an animated
colour's textures as a vertical frame strip plus a `.png.mcmeta`. The banner pack-build step does
the same. Armour is marked with a data component and rewritten per viewer by Polymer to the
colour at that viewer's pack-load clock, with worn armour re-sent every 20 ticks. The canvas mixin
picks a random keyframe map colour per pixel.

**Tech Stack:** Fabric (Minecraft 26.3-rc-1), Polymer 0.18.0+26.3-rc-1 (core, resource-pack),
Fabric game tests, PolyDecorations 0.13.1 (compileOnly), Java 25.

**Spec:** `/Users/winsty-dev/Personal Projects/MinecraftModding/Metacraft/METAmods/docs/superpowers/specs/2026-10-20-moredyes-animated-colours-design.md`

## Global Constraints

- **Workspace:** a new worktree on a new branch `moredyes-rainbow` cut from `origin/edvin-test`.
  That branch has the canvas compat (`d61ba906`) and the newer `content.block` / `content.item`
  layout. Don't use the existing `.claude/worktrees/edvin-test` worktree: it carries another
  session's uncommitted nether-rifts work. All paths below are relative to `mods/moredyes/`.
- **Commits:** repo style `moredyes: <what a player gets>`. No `Co-Authored-By` or Claude Code
  lines (METAcraft-KTH rule). Don't commit anything under `docs/superpowers/`.
- **Build and test commands** (from the repo root):
  - datagen: `./gradlew :mods:moredyes:runDatagen`
  - server game tests: `JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:moredyes:runServer`.
    This runs the whole `MoreDyesGameTests` class, exits non-zero on any failure, and writes
    `build/test-results/gametest.xml`.
  - Run datagen before the game tests whenever `colors.json` or datagen changed.
- **Never open a Minecraft client window on the laptop.** The client screenshot test runs in a
  cloud session with environment `Minecraft` (`env_01ATSwYvqMJquK74VvpTAJUc`).
- **Values from the spec:**
  - Rainbow: `"id": "rainbow"`, `"name": "Rainbow"`, `"rgb": "#FF4040"`, `frametime` 150,
    `interpolate` true, and the 24 frames exactly as listed in the spec, starting `#FF0000`.
  - Cycle = frames × frametime = 3600 ticks.
  - Armour refresh: every 20 ticks.
  - Armour phase clock: wall-clock milliseconds / 50.
- **Static colours stay byte-identical.** Cerise and Laserviolet generated output must not change.
- **Fallbacks are errors** (module rule). Malformed animation JSON fails `ModColors.load`. A
  hand-drawn texture for an animated colour fails datagen with a message naming the file.
- No donor/`Looks` changes. Edvin expects panes to stay in the donor look with three colours. If
  the startup log says otherwise, report it, don't work around it.

## Review Focus

- **A viewer with no recorded pack load** (declined pack, mock player, packet not seen yet): their
  rainbow armour shows the first keyframe and nothing throws. Test in Task 5.
- **Rejoining** reloads the pack and restarts that client's animation, so a second
  `SUCCESSFULLY_LOADED` must replace the first timestamp. Test in Task 5.
- **Stale marker** (armour washed in a cauldron, re-coloured by any route, or naming a colour
  that's gone or no longer animated): plain armour, no crash. Test in Task 4.
- **Non-player wearers** (armour stand, horse, wolf) animate too, and leave the wearer set when
  the armour comes off. Test in Task 5.
- **Rainbow + another dye in the grid** produces nothing, even though our own recipe and vanilla's
  both look at the grid. Test in Task 4.

---

### Task 1: `Animation` on `ModColor`

**Files:**
- Create: `src/main/java/metacraft/moredyes/color/Animation.java`
- Modify: `src/main/java/metacraft/moredyes/color/ModColor.java` (record components, `CODEC`, both `of(...)` factories)
- Modify: every `ModColor.of(` / `new ModColor(` call site (`grep -rn "ModColor.of(\|new ModColor(" src`)
- Test: `src/main/java/metacraft/moredyes/gametest/MoreDyesGameTests.java`

**Interfaces:**
- Produces:
  - `public record Animation(List<Frame> frames, int frametime, boolean interpolate)`, with
    `public static final Codec<Animation> CODEC`
  - `public record Animation.Frame(int rgb, int rampDark, int rampLight)`. A frame parses from
    either `"#RRGGBB"` (ramp from `ModColor.deriveRamp`) or `{ "rgb": "#..", "ramp": ["#dark", "#light"] }`
    (reuse `ModColor.Ramp.CODEC`).
  - `int cycleTicks()` = `frames.size() * frametime`
  - `int rgbAt(double tick)`: the colour at that tick, wrapped modulo `cycleTicks()`. With
    `interpolate`, a per-channel linear blend between frame `i` and `i+1` (wrapping) by
    `(tick mod frametime) / frametime`, as vanilla's sprite interpolation does. Without it, frame
    `i`'s rgb.
  - `List<MapColor> mapColors()`: the distinct `ModColor.nearestMapColor(frame.rgb)` values, in
    frame order
  - `String mcmeta()`: `{"animation":{"frametime":N,"interpolate":B}}`
  - `ModColor` gains a last component, `Optional<Animation> animation`. The JSON field is
    `"animation"`, optional. Both `of(...)` factories take it as their last parameter.
- Validation (codec `DataResult.error`): at least 2 frames; `frametime` ≥ 1.

- [ ] **Step 1: Write the failing tests** in `MoreDyesGameTests`:

```java
@GameTest
public void animationParsesAndBlends(GameTestHelper helper) {
	var json = JsonParser.parseString("""
		{"id":"t","name":"T","rgb":"#FF0000","animation":{"frametime":10,"interpolate":true,
		 "frames":["#FF0000",{"rgb":"#0000FF","ramp":["#000040","#8080FF"]}]}}""");
	ModColor c = ModColor.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
	Animation a = c.animation().orElseThrow();
	helper.assertValueEqual(a.cycleTicks(), 20, "cycle");
	helper.assertValueEqual(a.rgbAt(0), 0xFF0000, "frame 0");
	helper.assertValueEqual(a.rgbAt(5), 0x7F007F, "halfway red->blue");   // truncated, like vanilla's (int) blend
	helper.assertValueEqual(a.rgbAt(10), 0x0000FF, "frame 1");
	helper.assertValueEqual(a.rgbAt(20), 0xFF0000, "wraps");
	helper.assertValueEqual(a.frames().get(1).rampDark(), 0x000040, "explicit ramp kept");
	helper.assertTrue(ModColors.get("cerise").animation().isEmpty(), "static colour has no animation");
	helper.succeed();
}

@GameTest
public void animationRejectsBadInput(GameTestHelper helper) {
	for (String bad : List.of(
			"{\"frametime\":10,\"frames\":[\"#FF0000\"]}",
			"{\"frametime\":0,\"frames\":[\"#FF0000\",\"#00FF00\"]}")) {
		helper.assertTrue(Animation.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(bad)).isError(), "accepted " + bad);
	}
	helper.succeed();
}

@GameTest
public void animationStepsWithoutInterpolate(GameTestHelper helper) {
	var a = Animation.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
			"{\"frametime\":10,\"interpolate\":false,\"frames\":[\"#FF0000\",\"#0000FF\"]}")).getOrThrow();
	helper.assertValueEqual(a.rgbAt(9), 0xFF0000, "still frame 0");
	helper.succeed();
}
```

- [ ] **Step 2: Run the game tests.** Expected: compile failure (`Animation` undefined).
- [ ] **Step 3: Implement `Animation` and the `ModColor` changes above.** Blend channels as
      `(int) (cur * (1 - f) + next * f)`, truncating like vanilla.
- [ ] **Step 4: Run the game tests.** Expected: all pass. `colors.json` is untouched, so the
      startup log lists the same two colours.
- [ ] **Step 5: Commit** `moredyes: a colour can be animated through keyframes in colors.json`

### Task 2: Animated textures in datagen, and Rainbow ships

**Files:**
- Modify: `src/main/java/metacraft/moredyes/datagen/Tex.java` (add `stack`)
- Modify: `src/main/java/metacraft/moredyes/datagen/GeneratedAssets.java` (the per-colour loop around lines 598–630, `png`, `drawnOr`)
- Modify: `src/main/resources/colors.json` (append Rainbow)
- Modify: `README.md`. One paragraph under "Adding a colour" on the optional `animation` field.
  Point to the spec values; no design essay.
- Test: `MoreDyesGameTests`

**Interfaces:**
- Consumes: `ModColor.animation()`, `Animation.frames()`, `Animation.mcmeta()`
- Produces:
  - `static Tex stack(List<Tex> frames)` in `Tex`: same width, frames top to bottom; throws
    `IllegalArgumentException` on a size mismatch
  - private `void sprite(String path, ModColor color, BiFunction<Integer, Integer, Tex> make)` in
    `GeneratedAssets`, where `make` takes `(dark, light)`:
    - static colour: `png(path, make(dark, light))`, the same bytes as today
    - animated: `png(path, Tex.stack(frames.map(f -> make(f.rampDark(), f.rampLight()))))` plus the
      raw `mcmeta()` string at `path + ".mcmeta"` (through `bytes(...)`)
    - animated, but `path` is under `textures/entity/equipment/`: static, as for a static colour
      (`make(dark, light)` with the colour's own ramp, no mcmeta). In 26.3 the equipment layer
      renderer binds those textures directly; there's no atlas for them, so a strip would draw all
      frames squashed. This covers the happy ghast harness and the llama carpet.
    - animated, with a hand-drawn file at that path (`drawnOr` / the `png` existence check):
      throw `IllegalStateException("hand-drawn <path> can't serve animated colour <id>")`

- [ ] **Step 1: Snapshot today's output.** Run datagen, then
      `cp -R src/main/generated /tmp/moredyes-gen-before`.
- [ ] **Step 2: Write the failing test:**

```java
/** Every texture of an animated colour ships as a strip of frames with its mcmeta; static ones don't. */
@GameTest
public void animatedTexturesShipAsStrips(GameTestHelper helper) {
	ModColor rainbow = animated();   // new helper: first colour with an animation, assertion failure if none
	int frames = rainbow.animation().orElseThrow().frames().size();
	for (String p : List.of("block/{c}_wool", "block/{c}_concrete", "block/{c}_stained_glass", "block/{c}_bed_head_up",
			"block/sheep/{c}_wool", "block/sheep/{c}_wool_hurt", "block/sheep/{c}_body_sheared", "block/{c}_torchflower",
			"entity/shulker/{c}", "item/{c}_dye")) {
		BufferedImage still = image(p.replace("{c}", first().id()));
		BufferedImage strip = image(p.replace("{c}", rainbow.id()));
		helper.assertValueEqual(strip.getWidth(), still.getWidth(), p + " width");
		helper.assertValueEqual(strip.getHeight(), still.getHeight() * frames, p + " height");
		helper.assertTrue(resource(p.replace("{c}", rainbow.id()) + ".png.mcmeta").contains("\"frametime\":150"), p + " mcmeta");
		helper.assertTrue(resourceOrNull(p.replace("{c}", first().id()) + ".png.mcmeta") == null, p + " static has mcmeta");
	}
	// equipment textures aren't atlas sprites: they stay one static frame even for an animated colour
	for (String p : List.of("entity/equipment/llama_body/{c}", "entity/equipment/happy_ghast_body/{c}_harness")) {
		helper.assertValueEqual(image(p.replace("{c}", rainbow.id())).getHeight(), image(p.replace("{c}", first().id())).getHeight(), p + " height");
		helper.assertTrue(resourceOrNull(p.replace("{c}", rainbow.id()) + ".png.mcmeta") == null, p + " has mcmeta");
	}
	helper.succeed();
}
```

  The helpers `image` / `resource` / `resourceOrNull` read
  `/assets/moredyes/textures/<p>.png[.mcmeta]` from the classpath (`MoreDyes.class.getResourceAsStream`).

- [ ] **Step 3: Run the game tests.** Expected: FAIL (`animated()` finds no colour).
- [ ] **Step 4: Implement.**
  - Append Rainbow to `colors.json`.
  - Add `Tex.stack` and `sprite`.
  - Route every per-colour texture write through `sprite`: the `sources.forEach` recolour, the
    sheared body and its hurt, the wool hurt, and the torchflower petals. Derived ops (`composite`,
    `hurt`, `recolourPetals`) run inside `make`, per frame.
  - After the loop, log each animated colour's total generated texture bytes
    (`MoreDyes.LOGGER.info`).
- [ ] **Step 5: Run datagen, then check static output is byte-identical:**
      `diff -r /tmp/moredyes-gen-before src/main/generated | grep -v rainbow`.
      Expected: no lines except `Only in`/changed entries naming rainbow, plus the shared
      lang/tag/recipe files that list colours (inspect those: the only change should be added
      rainbow entries).
- [ ] **Step 6: Run the game tests.** Expected: all pass, including the whole existing suite,
      now with three colours. In the server log, check the `Looks` decision line for
      `STAINED_GLASS_PANE`. If it says display rather than donor, stop and report it (Global
      Constraints).
- [ ] **Step 7: Commit** `moredyes: Rainbow, a colour that drifts through the rainbow every three minutes`

### Task 3: Animated banner patterns

**Files:**
- Modify: `src/main/java/metacraft/moredyes/banner/BannerPatterns.java` (`generateTextures` ~line 244, next to `multiply` ~line 295)
- Test: `MoreDyesGameTests`

**Interfaces:**
- Consumes: `Animation.frames()`, `Animation.mcmeta()`
- Produces: `public static byte[] multiplyFrames(byte[] png, int[] rgbs) throws IOException`.
  `multiply` for each rgb, stacked top to bottom into one PNG. `generateTextures` uses it for an
  animated colour (the frames' `rgb`s) and adds `<same path>.mcmeta` via
  `builder.addStringData(path + ".mcmeta", animation.mcmeta())`. Static colours: unchanged.

- [ ] **Step 1: Write the failing test** `bannerMasksStackPerFrame`. Build a 2×2 opaque white PNG
      in the test, call `multiplyFrames(png, new int[]{0xFF0000, 0x00FF00})`, and assert a 2×4
      result whose top rows are `0xFFFF0000` and bottom rows `0xFF00FF00`.
- [ ] **Step 2: Run the game tests.** Expected: compile failure.
- [ ] **Step 3: Implement as above.**
- [ ] **Step 4: Run the game tests.** Expected: pass. The server log line "generated N banner
      pattern textures (0 without a mask)" still shows 0 without a mask.
- [ ] **Step 5: Commit** `moredyes: Rainbow banner patterns drift like the blocks`

### Task 4: Rainbow dye overwrites armour

**Files:**
- Create: `src/main/java/metacraft/moredyes/armor/AnimatedArmor.java`
- Create: `src/main/java/metacraft/moredyes/mixin/DyeRecipeMixin.java` (add to `moredyes.mixins.json`)
- Modify: `src/main/java/metacraft/moredyes/recipe/ModDyeRecipe.java` (`matches`, `assemble`)
- Modify: `src/main/java/metacraft/moredyes/MoreDyes.java` (call `AnimatedArmor.init()` after `ModRecipes.init()`)
- Test: `MoreDyesGameTests`

**Interfaces:**
- Consumes: `ModColor.animation()`, `ModColors.getOrNull`, `DyeStacks.mix`, `DyeStacks.armorRgb`
- Produces (in `AnimatedArmor`):
  - `public static final DataComponentType<String> ANIMATED_DYE`, id `moredyes:animated_dye`,
    persistent `Codec.STRING`, registered like `Cushions.ITEM_COLOR`
  - `public static Optional<ModColor> animatedColour(ItemStack stack)`. The marker counts only if
    the stack has `DYED_COLOR`, the id resolves to a colour with an animation, and
    `dyed_color.rgb() & 0xFFFFFF == colour.rgb()`. Otherwise it's empty (a stale marker), which
    also covers cauldron washing (vanilla removes `DYED_COLOR`), so no cauldron mixin is needed.
    This is a plan call against the spec's "must clear the marker": washing leaves an inert
    marker behind instead of removing it. Players see the same thing.
  - `public static void init()`
- Recipe rules (the spec's overwrite rule):
  - `ModDyeRecipe.matches`: a grid holding an animated dye matches only as the target plus exactly
    that one dye. It also matches a target with a valid marker plus vanilla-only dyes.
  - `assemble`:
    - animated dye: `DYED_COLOR` = colour `rgb`, set `ANIMATED_DYE` = id, and ignore the target's
      old colour;
    - target with a valid marker: `DyeStacks.mix(null, rgbs)`, remove the marker;
    - otherwise as today.
  - `DyeRecipeMixin`: vanilla `DyeRecipe.matches(CraftingInput, Level)` returns false when any
    stack in the grid has a valid marker, so ours handles it.

- [ ] **Step 1: Write the failing tests:**

```java
@GameTest public void rainbowDyeOverwritesArmour(GameTestHelper helper) {
	ItemStack blue = new ItemStack(Items.LEATHER_CHESTPLATE); blue.set(DataComponents.DYED_COLOR, new DyedItemColor(0x0000FF));
	ItemStack out = craft(helper, grid(blue, new ItemStack(ModContent.dye(animated()))));
	helper.assertValueEqual(out.get(DataComponents.DYED_COLOR).rgb() & 0xFFFFFF, animated().rgb(), "overwritten colour");
	helper.assertValueEqual(AnimatedArmor.animatedColour(out), Optional.of(animated()), "marked");
	helper.succeed();
}
@GameTest public void rainbowDyeDoesNotMix(GameTestHelper helper) {
	ItemStack rainbow = new ItemStack(ModContent.dye(animated()));
	helper.assertTrue(craft(helper, grid(new ItemStack(Items.LEATHER_BOOTS), rainbow.copy(), new ItemStack(Items.DYE.red()))).isEmpty(), "mixed with vanilla red");
	helper.assertTrue(craft(helper, grid(new ItemStack(Items.LEATHER_BOOTS), rainbow.copy(), new ItemStack(ModContent.dye(first())))).isEmpty(), "mixed with ours");
	helper.succeed();
}
@GameTest public void normalDyeEndsTheRainbow(GameTestHelper helper) {
	ItemStack rb = craft(helper, grid(new ItemStack(Items.LEATHER_HELMET), new ItemStack(ModContent.dye(animated()))));
	for (ItemStack dye : List.of(new ItemStack(Items.DYE.red()), new ItemStack(ModContent.dye(first())))) {
		ItemStack out = craft(helper, grid(rb.copy(), dye));
		helper.assertValueEqual(out.get(DataComponents.DYED_COLOR), DyeStacks.mix(null, List.of(DyeStacks.armorRgb(dye))), "plain colour from " + dye);
		helper.assertTrue(AnimatedArmor.animatedColour(out).isEmpty(), "still animated after " + dye);
	}
	helper.succeed();
}
@GameTest public void staleMarkersArePlain(GameTestHelper helper) {
	ItemStack rb = craft(helper, grid(new ItemStack(Items.LEATHER_LEGGINGS), new ItemStack(ModContent.dye(animated()))));
	ItemStack washed = rb.copy(); washed.remove(DataComponents.DYED_COLOR);
	ItemStack unknown = rb.copy(); unknown.set(AnimatedArmor.ANIMATED_DYE, "no_such_colour");
	ItemStack staticId = rb.copy(); staticId.set(AnimatedArmor.ANIMATED_DYE, first().id());
	for (ItemStack s : List.of(washed, unknown, staticId)) helper.assertTrue(AnimatedArmor.animatedColour(s).isEmpty(), "stale marker counted: " + s);
	helper.succeed();
}
```

- [ ] **Step 2: Run the game tests.** Expected: compile failure.
- [ ] **Step 3: Implement as above.**
- [ ] **Step 4: Run the game tests.** Expected: pass, and the existing armour/firework dye tests
      still pass.
- [ ] **Step 5: Commit** `moredyes: Rainbow dye turns leather, horse and wolf armour to Rainbow, and nothing mixes into it`

### Task 5: Armour fades on each player's clock

**Files:**
- Create: `src/main/java/metacraft/moredyes/armor/PackClock.java`
- Create: `src/main/java/metacraft/moredyes/mixin/ServerCommonPacketListenerImplMixin.java` (add to `moredyes.mixins.json`)
- Modify: `src/main/java/metacraft/moredyes/armor/AnimatedArmor.java`
- Test: `MoreDyesGameTests`

**Interfaces:**
- Consumes: `AnimatedArmor.animatedColour`, `Animation.rgbAt`,
  `PolymerItemUtils.ITEM_MODIFICATION_EVENT` (`modifyItem(ItemStack original, ItemStack client, PacketContext ctx)`),
  `PolymerItemUtils.CONTEXT_ITEM_CHECK` (`BiPredicate<ItemInstance, PacketContext>`),
  `PolymerResourcePackUtils.getMainUuid()`
- Produces:
  - `PackClock`, backed by a `ConcurrentHashMap<UUID, Long>`. The packet can arrive in the
    configuration phase, off the server thread.
    - `static void loaded(UUID profile, long millis)` (the latest call wins)
    - `static double ticksSince(UUID profile, long nowMillis)`: `(now - loaded) / 50.0`, or `0` if
      nothing is recorded
    - `static void forget(UUID profile)`, called on `ServerPlayConnectionEvents.DISCONNECT`
  - The mixin injects at the head of `handleResourcePackResponse(ServerboundResourcePackPacket)`.
    When `action() == SUCCESSFULLY_LOADED` and `id().equals(getMainUuid())`, it calls
    `PackClock.loaded(playerProfile().id(), System.currentTimeMillis())`.
  - In `AnimatedArmor`:
    - `static int viewerRgb(ModColor colour, UUID viewer, long nowMillis)` =
      `animation.rgbAt(PackClock.ticksSince(viewer, nowMillis))`
    - The `ITEM_MODIFICATION_EVENT` handler sets the client copy's `DYED_COLOR` to `viewerRgb`
      for the context's player, and never touches `original`. `CONTEXT_ITEM_CHECK` returns true
      for stacks with a valid marker.
    - `static Set<LivingEntity> wearers()` (weak set), maintained from
      `ServerEntityEvents.EQUIPMENT_CHANGE` (added when any slot holds a valid-marked stack,
      removed when none does) and `ServerEntityEvents.ENTITY_UNLOAD`
    - Refresh on `ServerTickEvents.END_SERVER_TICK` when `server.getTickCount() % 20 == 0`. For
      each wearer:
      - send a `ClientboundSetEquipmentPacket` of its marked slots to tracking players
        (`ServerLevel.getChunkSource().sendToTrackingPlayers`);
      - if the wearer is a `ServerPlayer`, also force `ClientboundContainerSetSlotPacket`s for
        those slots of its `inventoryMenu`. `broadcastChanges` skips unchanged stacks, so it
        won't do this.

- [ ] **Step 1: Write the failing tests:**

```java
@GameTest public void armourFollowsTheViewersClock(GameTestHelper helper) {
	ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
	ItemStack rb = craft(helper, grid(new ItemStack(Items.LEATHER_CHESTPLATE), new ItemStack(ModContent.dye(animated()))));
	Animation a = animated().animation().orElseThrow();
	long now = System.currentTimeMillis();
	PackClock.loaded(viewer.getUUID(), now - 75 * 50);          // mid-keyframe 0
	int early = clientRgb(helper, viewer, rb);
	PackClock.loaded(viewer.getUUID(), now - 1875 * 50);        // later load replaces the earlier one; mid-keyframe 12
	int late = clientRgb(helper, viewer, rb);
	helper.assertTrue(near(early, a.rgbAt(75)) && near(late, a.rgbAt(1875)), "phases " + hex(early) + " " + hex(late));
	helper.assertValueEqual(rb.get(DataComponents.DYED_COLOR).rgb() & 0xFFFFFF, animated().rgb(), "server stack untouched");
	helper.succeed();
}
@GameTest public void viewerWithoutPackSeesTheFirstKeyframe(GameTestHelper helper) {
	ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
	PackClock.forget(viewer.getUUID());
	ItemStack rb = craft(helper, grid(new ItemStack(Items.LEATHER_BOOTS), new ItemStack(ModContent.dye(animated()))));
	helper.assertValueEqual(clientRgb(helper, viewer, rb), animated().animation().orElseThrow().rgbAt(0), "no clock");
	helper.succeed();
}
@GameTest public void armourStandWearersComeAndGo(GameTestHelper helper) {
	var stand = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(1, 2, 1));
	ItemStack rb = craft(helper, grid(new ItemStack(Items.LEATHER_CHESTPLATE), new ItemStack(ModContent.dye(animated()))));
	stand.setItemSlot(EquipmentSlot.CHEST, rb);
	// EQUIPMENT_CHANGE fires from the entity's tick, not from setItemSlot: give it a couple of ticks
	helper.runAfterDelay(2, () -> {
		helper.assertTrue(AnimatedArmor.wearers().contains(stand), "stand not tracked");
		stand.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		helper.runAfterDelay(2, () -> {
			helper.assertFalse(AnimatedArmor.wearers().contains(stand), "stand still tracked");
			helper.succeed();
		});
	});
}
```

  `clientRgb` = `PolymerItemUtils.getPolymerItemStack(stack, viewer.connection.getPacketContext(), registryAccess)`'s
  `DYED_COLOR` rgb & `0xFFFFFF`. `near` = every channel within 3. A tick of test runtime moves a
  channel by at most about 1.

- [ ] **Step 2: Run the game tests.** Expected: compile failure.
- [ ] **Step 3: Implement as above.**
- [ ] **Step 4: Run the game tests.** Expected: pass.
- [ ] **Step 5: Commit** `moredyes: Rainbow armour drifts in step with the Rainbow blocks each player sees`

### Task 6: Rainbow paints random pixels on canvases

**Files:**
- Modify: `src/main/java/metacraft/moredyes/compat/polydecorations/mixin/CanvasEntityMixin.java` (`moredyes$ourDyesPaint`)
- Test: `MoreDyesGameTests` (the `Canvas` helper class and `canvasTakesOurDyes`)

**Interfaces:**
- Consumes: `Animation.mapColors()`
- Behaviour: for a dye whose colour has an animation, return
  `CanvasColor.from(<uniformly random element of mapColors()>, MapColor.Brightness.NORMAL)`, drawn
  fresh on every call. Static colours are unchanged. Use `ThreadLocalRandom`; there's no world
  random in that static method.

- [ ] **Step 1: Write the failing test.** Add `Canvas.paintsRandomKeyframes(helper, animated())`,
      called from `canvasTakesOurDyes` behind the same `isModLoaded("polydecorations")` guard. It
      calls `CanvasEntity.getColor(new ItemStack(ModContent.dye(colour)))` 200 times and asserts:
      - every result is in `mapColors()` mapped through `CanvasColor.from(m, NORMAL)`;
      - at least 5 distinct results appear.
- [ ] **Step 2: Run the game tests.** Expected: FAIL. Today every call returns the single
      `mapColor()`.
- [ ] **Step 3: Implement as above.**
- [ ] **Step 4: Run the game tests.** Expected: pass.
- [ ] **Step 5: Commit** `moredyes: Rainbow dye paints a canvas in random rainbow pixels`

### Task 7: Client screenshot check (cloud)

**Files:**
- Modify: `src/gametest/java/metacraft/moredyes/clienttest/MoreDyesClientTests.java`

**Interfaces:**
- Consumes: the client test harness already in that file (Polymer-hello and pack-auto-accept mixins)

- [ ] **Step 1: Add a client test `rainbowDrifts`.**
  - Set up a row in front of the camera:
    - Rainbow wool, concrete and stained glass;
    - a Rainbow shulker box item in an item frame;
    - a banner with a Rainbow pattern;
    - an armour stand in Rainbow leather;
    - a llama with a Rainbow carpet;
    - a happy ghast with a Rainbow harness.
  - Take a screenshot, wait 900 ticks (45 s, a quarter cycle), and take another.
  - Per region, compute the mean hue in each shot.
  - Assert the wool region's hue moved by at least 20°.
  - Log the hue change for every other region, without asserting. Those regions are the spec's
    "needs confirming" atlases. The llama carpet and the ghast harness are expected to stay still,
    since they're static by design.
- [ ] **Step 2: Run it in a cloud session with environment `Minecraft`**
      (`./gradlew :mods:moredyes:runDatagen :mods:moredyes:runClientGameTest`), never locally.
      Expected: pass. Collect both screenshots and the logged hue changes.
- [ ] **Step 3: Report.** List each region that didn't change (likely candidates: the shulker
      item, banner, harness, llama carpet). Those are findings for Edvin, per the spec: don't
      paper over them.
- [ ] **Step 4: Commit** `moredyes: a client screenshot test watches Rainbow drift`

### Task 8 (follow-up, after the cloud run): rename to RGB, RGB stripes on the harness and llama carpet

Edvin, 2026-10-20: the colour is **RGB** (id `rgb`, name `"RGB"`), not Rainbow. The fade stays the
same: 24 hues × 150 ticks, like RGB lighting. Equipment textures can't animate, so they get static
red / green / blue stripes.

- `colors.json`: rename the entry `rainbow` → `rgb`, `"Rainbow"` → `"RGB"`. Nothing has shipped,
  so a rename is fine; the "never delete, mark retired" rule only starts once worlds exist. Update
  the README paragraph and any comment or test message that says Rainbow. Tests find the colour
  through `animated()`, not by id.
- `Animation` gains an optional `"stripes"` list (hex colours). Default: 3 keyframes sampled
  evenly from `frames`. The RGB entry sets `["#FF0000", "#00FF00", "#0000FF"]`.
- In `GeneratedAssets.sprite`, for an animated colour under `textures/entity/equipment/`: recolour
  in horizontal bands. Split the texture's rows into `stripes.size()` equal bands; each band uses
  the stripe colour's derived ramp. A hand-drawn file at that path wins, and is allowed here
  because it's static anyway.
- Test: extend `animatedTexturesShipAsStrips`. The RGB harness and llama textures keep the static
  height, have no mcmeta, and the top, middle and bottom bands differ in hue.
- Check the look in the cloud client screenshot (llama + happy ghast). If texture-row bands read
  badly on the model, band per model face instead, or hand-draw.
- Before fast-forwarding onto PR #17, reword this branch's commit messages from Rainbow to RGB.
  The branch isn't on the PR yet, so rewriting above be89e352 is fine.
