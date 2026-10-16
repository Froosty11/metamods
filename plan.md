# Kultur drinks without Brewery — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Without Brewery, Kultur's four drinks come back with PolymITer's brewing-stand recipes. They feed a Brewery-style alcohol meter whose stages come in steps of 10, and players see an Inebriated effect, which is Luck re-skinned by the pack.

**Architecture:** A new package `metacraft.kultur.drinks`, switched on only when `brewery` isn't loaded:
- pure meter maths (`Alcohol`, unit-tested)
- per-player storage (a Fabric data attachment)
- a server tick handler that applies the steps
- Polymer potions with brewing recipes and a Nyckeln item
- a drink hook that applies each drink's effects and the blackout
- a Polymer mob effect, `kultur:inebriated`, shown to clients as Luck

The Brewery JSONs and Brewery tests stay as they are for the Brewery case.

**Tech Stack:**
- Minecraft 26.3 (Mojang mappings), Java 25
- Fabric Loader 0.19.5, Fabric API 0.161.0+26.3
- Polymer 0.18.4+26.3 (`polymer-core`, `polymer-resource-pack`, `polymer-autohost`)
- Fabric game tests and client game tests; JUnit for `src/test`

**Spec:** `docs/superpowers/specs/2026-10-16-kultur-drinks-without-brewery-design.md` (read it first; this plan argues from it).

## Global Constraints

- **Branch:** `edvin/kultur-drinks`, cut from `origin/edvin/kultur` (tip `2582eda2`).
  - Push it to `origin` (Froosty11/metamods) only, never `upstream`.
  - Open no PR.
- **Commits:**
  - No `docs/` files.
  - No `Co-Authored-By` or other AI attribution lines.
  - Commit messages follow the module's style, e.g. `metacraft-kultur: …` in plain sentences.
- **Mod id and namespace:** the mod id is `metacraft-kultur` (`Kultur.MOD_ID`). Registry and asset namespace is `kultur` (`Kultur.NAMESPACE`).
- **The switch:** everything new is active only when `!FabricLoader.getInstance().isModLoaded("brewery")`. With Brewery loaded, behaviour is byte-for-byte today's.
- **Vanilla clients:** no client mod. Everything goes through Polymer and the auto-served pack.
- **Copy, exactly:**
  - Drink names: "Alcoholic Beverage", "Spiken Patch Drink", "Släggan Patch Drink", "Nyckeln Patch Drink".
  - Effect name: "Inebriated" (en_us), "Berusad" (sv_se).
  - Blackout line: "You wake up with a splitting headache..." in bold `#FF0F0F`.
- **Meter numbers:**
  - Drinks add: alcohol 20, spiken 30, slaggan 35, nyckeln 0.
  - Drain 0.012 per tick.
  - Steps at >0 / 30 / 40 / 50 / 60 / 70 / 80 → Inebriated I–VII.
  - Blackout at ≥ 100 after a drink, which resets the meter to 30.
  - Bread −3, milk bucket −10.
- **Code style:** tabs, the existing Javadoc voice (short plain sentences saying *why*), Mojang names.
- **Where things run:** Gradle builds and Minecraft runs happen in this cloud environment, never on the user's machine.

## Review Focus

1. **Riding, gliding, creative flight, spectator:** a drunk player in a boat, on a horse, gliding with an elytra, flying in creative or in spectator is not shoved. The stagger only applies to a player on their own feet, as in Brewery, where a push on a vehicle does nothing useful. Test: `staggerSkipsRidingAndFlying` in Task 3.
2. **The blackout in the Nether or the End:** the teleport finds a safe spot in the *same* dimension (not inside the Nether roof, not over the End void). After a bounded number of tries it gives up and the player stays put; the rest of the blackout still happens. Test: `blackoutInTheNetherStaysInTheNether` in Task 4.
3. **A server restart mid-drunk:** the meter is restored from the player's save, and Inebriated, with its countdown, is back within a second of joining. Test: `meterSurvivesSaveAndLoad` in Task 2.
4. **Sobering past zero:** bread or milk at a low level never makes the meter negative. Eating while sober does nothing. Test: `breadAndMilkSober` in Task 4.
5. **Death while drunk:** the respawned player has a zero meter, no Inebriated and no step effects. Test: `deathClearsTheMeter` in Task 2.

---

### Task 1: The switch, and game tests without Brewery

**Files:**
- Create: `mods/metacraft-kultur/src/main/java/metacraft/kultur/drinks/KulturDrinks.java`
- Create: `mods/metacraft-kultur/src/main/java/metacraft/kultur/gametest/KulturVanillaDrinkTests.java`
- Modify: `mods/metacraft-kultur/build.gradle` (the `runtimeOnly("maven.modrinth:brewery:…")` line)
- Modify: `mods/metacraft-kultur/src/main/java/metacraft/kultur/Kultur.java` (`onInitialize`)
- Modify: `mods/metacraft-kultur/src/main/resources/fabric.mod.json` (`fabric-gametest` entrypoints)

**Interfaces:**
- Produces:
  - `KulturDrinks.active(): boolean`, true when Brewery isn't loaded.
  - `KulturDrinks.register(): void`, which calls each later task's `register()` and is a no-op when not active.
  - `KulturVanillaDrinkTests`, where later tasks add their `@GameTest`s. Each test starts with `if (!KulturDrinks.active()) return succeed;`, mirroring `KulturDrinkTests.breweryHere`.

- [ ] **Step 1: Build switch.** In `build.gradle`, add the Brewery `runtimeOnly` only when `!project.hasProperty('kulturNoBrewery')`. `compileOnly` and `gametestCompileOnly` stay.
- [ ] **Step 2: Failing test.** Write `drinksAreActiveWithoutBrewery`, asserting `KulturDrinks.active()` and `KulturDrinks.registered()`, which is true once `register()` has run in active mode. Register the class in `fabric.mod.json`.
- [ ] **Step 3: Run it and see it fail.**
  `JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:metacraft-kultur:runServer -PkulturNoBrewery`
  Expected: it fails to compile, or the test fails.
- [ ] **Step 4: Implement** `KulturDrinks`, and call `KulturDrinks.register()` from `Kultur.onInitialize` after `ModContent.register`.
- [ ] **Step 5: Run both modes.**
  - The Step 3 command: every test passes.
  - The same command without `-PkulturNoBrewery`: today's Brewery tests pass, and the new test returns early.
- [ ] **Step 6: Commit.**
  `metacraft-kultur: the chapter drinks get a mode of their own when Brewery is not on the server`

### Task 2: The meter — maths, storage, persistence

**Files:**
- Create: `…/drinks/Alcohol.java` (pure maths; no Minecraft types)
- Create: `…/drinks/Drunkenness.java` (the attachment)
- Test: `mods/metacraft-kultur/src/test/java/metacraft/kultur/AlcoholTest.java`
- Test: `KulturVanillaDrinkTests`

**Interfaces:**
- Produces, in `Alcohol` (all `static`):
  - `double DRAIN_PER_TICK = 0.012`, `double BLACKOUT_AT = 100`, `double AFTER_BLACKOUT = 30`
  - `double afterDrink(double alcohol, double value)`, which returns `max(alcohol + value, value)`
  - `double afterTicks(double alcohol, int ticks)`, floored at 0
  - `int step(double alcohol)`: 0 when ≤ 0; 1 when < 30; 2 at 30; 3 at 40; 4 at 50; 5 at 60; 6 at 70; 7 at ≥ 80
  - `boolean blacksOut(double alcoholAfterDrink)`, true at ≥ 100
  - `int ticksUntilSober(double alcohol)`, which returns `ceil(alcohol / DRAIN_PER_TICK)`
  - `double staggerLength(double alcohol, double rand)`, which returns `min((0.3 + rand / 10) * ((alcohol - 40) / 50) * 0.7, 0.2)`
  - `int drinkAmplifier(double alcohol)`, which returns `clamp(floor((alcohol - 30) / 40), 0, 1)`
  - `double sobered(double alcohol, double by)`: `alcohol` when ≤ 0, else `max(alcohol - by, 0)`
- Produces, in `Drunkenness`:
  - `double get(ServerPlayer)`, `void set(ServerPlayer, double)`, `void drink(ServerPlayer, double value)`
  - storage is an attachment `kultur:alcohol`: a `Double`, persistent, *not* copied on death, copied on a live respawn.

- [ ] **Step 1: Failing unit tests** in `AlcoholTest`:
  - `step(0)==0`, `step(0.1)==1`, `step(29.9)==1`, `step(30)==2`, `step(50)==4`, `step(80)==7`, `step(99)==7`
  - `afterDrink(-5, 20)==20`, `afterDrink(70, 35)==105`
  - `blacksOut(100)`, `!blacksOut(99.9)`
  - `afterTicks(1, 100)==0`; `afterTicks(60, 1200)` is within 1e-9 of `45.6`
  - `ticksUntilSober(12)==1000`
  - `staggerLength(50, 0)` within 1e-9 of `0.042`; `staggerLength(90, 1)==0.2`
  - `drinkAmplifier(60)==0`, `drinkAmplifier(70)==1`, `drinkAmplifier(200)==1`
  - `sobered(2, 3)==0`, `sobered(0, 3)==0`, `sobered(-1, 3)==-1`
- [ ] **Step 2:** `./gradlew :mods:metacraft-kultur:test --tests metacraft.kultur.AlcoholTest`. Expected: FAIL (the class is missing).
- [ ] **Step 3: Implement** `Alcohol`.
- [ ] **Step 4:** the same command passes.
- [ ] **Step 5: Failing game tests** in `KulturVanillaDrinkTests`:
  - `meterSurvivesSaveAndLoad`: set 55 on a mock player, save it to NBT/`ValueOutput` and load it into a fresh player, and assert 55. Use the same mock-player approach `KulturDrinkTests` uses.
  - `deathClearsTheMeter`: set 55, kill, respawn (`restoreFrom(old, false)`), and assert 0.
  - `liveRespawnKeepsTheMeter`: `restoreFrom(old, true)` keeps 55.
- [ ] **Step 6: Implement** `Drunkenness` with Fabric's `AttachmentRegistry` (`persistent(Codec.DOUBLE)`, and copy on respawn only when alive). Register it from `KulturDrinks.register()`.
- [ ] **Step 7:** both game-test modes pass, as in Task 1 Step 5.
- [ ] **Step 8: Commit.**
  `metacraft-kultur: a player's alcohol, kept like Brewery keeps it`

### Task 3: The steps — tick, effects, stagger, poisoning, Inebriated

**Files:**
- Create: `…/drinks/DrunkTicker.java` (server tick, players only)
- Create: `…/drinks/InebriatedEffect.java` (`MobEffect` + `PolymerMobEffect`, replaced by `MobEffects.LUCK`)
- Create: `…/drinks/KulturDamageTypes.java` (`ResourceKey<DamageType> ALCOHOL_POISONING`)
- Create: `mods/metacraft-kultur/src/main/resources/data/kultur/damage_type/alcohol_poisoning.json`. Copy Brewery's (`data/brewery/damage_type/alcohol_poisoning.json` in Patbox/brewery) with message id `kultur.alcohol_poisoning`, plus the death-message lang keys in `assets/kultur/lang/en_us.json`.
- Test: `KulturVanillaDrinkTests`

**Interfaces:**
- Consumes: `Alcohol`, `Drunkenness`.
- Produces:
  - `KulturDrinks.INEBRIATED: Holder<MobEffect>` (`kultur:inebriated`)
  - `DrunkTicker.tick(ServerPlayer)`, which tests may call directly

**Behaviour** (spec, "The meter" and "Inebriated"):
- Every tick, drain the meter.
- Rates count on `player.tickCount`: 4 s = 80 ticks, 0.2 s = 4 ticks, 16 s = 320 ticks.
- Step effects:
  - step ≥ 2: weakness 0
  - step ≥ 3: slowness 0
  - step ≥ 4: the stagger, every 4 ticks
  - step ≥ 5: nausea 0 for 32 s, every 16 s
  - step ≥ 6: 1 poisoning damage every 4 s, only while health > 1, with damage capped so health stays ≥ 1
  - step ≥ 7: darkness 0 for 16 s, every 16 s
- Every step effect shows its icon and particles off. Its duration is its rate plus 40 ticks of slack, so the icon never lapses.
- Inebriated is refreshed every 20 ticks and whenever the meter changes:
  - amplifier `step − 1`
  - duration `ticksUntilSober`
  - icon shown
  - removed when the meter hits 0
- Stagger: direction `(rand − 0.5, rand/2 − 0.49, rand − 0.5)`, normalised to `Alcohol.staggerLength`, added to the delta movement. Then set `hurtMarked = true`, so the player's client gets it.
- Skip the stagger when the player is riding, gliding, flying (`getAbilities().flying`) or a spectator.

- [ ] **Step 1: Failing game tests:**
  - `stepEffectsAtTheirLevels`: set 35, tick 80, and assert weakness without slowness. Set 45, tick 80, and assert both. Set 65, tick 320, and assert nausea. Set 85, tick 320, and assert darkness. Every one must be `isVisible()` with `showIcon()`.
  - `inebriatedShowsTheStep`: set 55, tick 20, and assert Inebriated amplifier 3 (IV) and duration within 20 ticks of `ticksUntilSober(55)`. Set 0, tick 20, and assert it's gone.
  - `staggerMovesThePlayer`: a survival player standing on stone at 75; over 40 ticks the horizontal position changes and `hurtMarked` was set at least once (record it in the tick).
  - `staggerSkipsRidingAndFlying`: at 75, a player in a boat and a creative player with `flying=true` keep their delta movement zero from the stagger.
  - `poisoningNeverKills`: at 75 with health 3, tick 400. Assert alive, health ≥ 1, and that the last damage source was `kultur:alcohol_poisoning`.
  - `inebriatedLooksLikeLuck`: `((PolymerMobEffect) INEBRIATED.value()).getPolymerReplacement(...)` is `MobEffects.LUCK.value()`.
  - `stepEffectsDoNotFlicker`: at 45, tick 240, and assert slowness was present on every tick.
- [ ] **Step 2:** run without Brewery; expected FAIL.
- [ ] **Step 3: Implement** the three files. Register the effect and the `ServerTickEvents.END_SERVER_TICK` player loop from `KulturDrinks.register()`.
- [ ] **Step 4:** both modes pass.
- [ ] **Step 5: Commit.**
  `metacraft-kultur: Brewery's drunkenness in steps of ten, and Inebriated`

### Task 4: The drinks — potions, recipes, Nyckeln, drinking, blackout, sobering

**Files:**
- Create: `…/drinks/DrinkKind.java`, an enum with `ALCOHOL("alcohol", 20)`, `SPIKEN("spiken", 30)`, `SLAGGAN("slaggan", 35)` and `NYCKELN("nyckeln", 0)`. It has `id()` and `alcohol()`, plus `String displayName()` returning the copy in Global Constraints.
- Create: `…/drinks/DrinkPotions.java`. It registers three `SimplePolymerPotion`s, `kultur:alcohol`, `kultur:spiken` and `kultur:slaggan`, with **no** effects. It also registers the brewing recipes and has `Holder<Potion> potion(DrinkKind)` and `DrinkKind kindOf(ItemStack)`, which returns null when a stack is none of ours.
- Create: `…/drinks/NyckelnItem.java`, a Polymer item (`kultur:nyckeln`) with a drink consumable, `stacksTo(1)` and a glass bottle remainder. Its recipe goes in `data/kultur/recipe/nyckeln.json`: shapeless, apple plus a potion whose `potion_contents` is `minecraft:water` (use Fabric's components custom ingredient). The recipe is loaded only without Brewery: a `fabric:load_conditions` `not mod_loaded brewery`.
- Create: `…/drinks/DrinkEffects.java` with `void onDrink(ServerPlayer, DrinkKind)`.
- Test: `KulturVanillaDrinkTests`

**Interfaces:**
- Consumes: `Alcohol`, `Drunkenness`, `DrunkTicker` (refresh Inebriated at once after a drink), `KulturDamageTypes`.
- Produces: `DrinkPotions.potion(DrinkKind)`, `DrinkPotions.kindOf(ItemStack)`, `KulturDrinks.NYCKELN: Item`.

**Behaviour** (spec, "Recipes" and "Each drink's own effects"):
- Brewing, on 26.3 Fabric's potion-brewing builder event:
  - water + potato → alcohol
  - alcohol + sweet berries → spiken
  - alcohol + suspicious stew → slaggan
- Drinking a *drinkable* `minecraft:potion` of ours (not splash, not lingering) or Nyckeln calls `onDrink`. Detect it at consume finish, through whatever hook 26.3 offers best: an `ItemStack.finishUsingItem`/`Consumable` mixin gated by the inverse of `KulturMixinPlugin`'s rule, or a Fabric event if one exists.
- `onDrink` does the following in order:
  - `Drunkenness.drink`
  - if `Alcohol.blacksOut`: the blackout, as in the spec's list. Up to 16 random tries within ±250 blocks in the same level, accepting only a spot whose feet and head blocks are free, with a solid block below and inside the world border. In the Nether, y must be below 127. Otherwise the player stays put.
  - else the drink's own effects, 200 s (4000 ticks), amplifier `Alcohol.drinkAmplifier`:
    - Alcoholic Beverage and Spiken: slowness *or* speed, 50/50.
    - Släggan: 75% slowness, plus strength 0 if alcohol ≥ 60; otherwise speed, plus weakness 0 if alcohol ≥ 60.
    - Nyckeln: nothing.
- Sobering: finishing bread lowers the meter by 3 and finishing a milk bucket by 10, through `Alcohol.sobered` (same hook).
- Client look, via `PolymerItemUtils.ITEM_MODIFICATION_EVENT`:
  - A potion stack of ours gets `ITEM_MODEL` `kultur:drink/<id>`, and `ITEM_NAME` the display name. Splash and lingering get "Splash …" and "Lingering …".
  - Nyckeln is disguised as `minecraft:potion` with model `kultur:drink/nyckeln`.
- Creative: add the four drinks to Kultur's Polymer creative tab (`ModContent.registerCreativeTab` builds it only when there are pattern items, and there always are). Expose a hook there instead of a second tab.

- [ ] **Step 1: Failing game tests.**
  - `eachBrewingRecipeMakesItsDrink`: check through `level.potionBrewing()`:
    - `hasMix(water, POTATO)`, and `mix(POTATO, water)` gives alcohol
    - alcohol + `SWEET_BERRIES` gives spiken
    - alcohol + `SUSPICIOUS_STEW` gives slaggan
    - `mix(POTATO, awkward)` is not ours
  - `nyckelnCraftsOnlyFromWater`: the recipe manager finds Nyckeln for apple + water bottle, and nothing of ours for apple + a healing potion.
  - `drinkingAddsAlcohol`: drink spiken via `finishUsingItem` and assert the meter is 30 and Inebriated I. Drink alcohol and assert 50.
  - `spikenGivesSpeedOrSlowness`: after one spiken, exactly one of speed or slowness, 4000 ticks, amplifier 0. At 80 before the drink (110 → blackout): skip. At 45 before (75 after), amplifier 1.
  - `slagganStrengthOrWeaknessWhenDrunk`: at 30 before (65 after), slowness with strength or speed with weakness. At 0 before, neither strength nor weakness.
  - `nyckelnIsSober`: the meter stays 0 and no effects.
  - `splashAndLingeringAffectNobody`: throw a splash spiken at a player and tick 20. Assert meter 0 and no Inebriated.
  - `blackoutAt100`: at 70, drink spiken. Assert the meter is 30, the player moved ≤ 250 blocks on x and z, blindness for 10 s, no other effects, the last damage was `kultur:alcohol_poisoning`, and the action-bar packet text. Capture it the way `KulturDrinkTests` does for the Brewery blackout.
  - `blackoutInTheNetherStaysInTheNether`: the same in a Nether test level, or by moving the player to `Level.NETHER`. Assert still in the Nether at y < 127.
  - `breadAndMilkSober`: at 20, eat bread and assert 17. Drink milk and assert 7. Milk again and assert 0. Bread at 0 stays 0.
- [ ] **Step 2:** run without Brewery; expected FAIL.
- [ ] **Step 3: Implement** the four files and the recipe JSON, and register them from `KulturDrinks.register()`.
- [ ] **Step 4:** both modes pass. With Brewery, assert in an existing Brewery test that `kultur:spiken` isn't in `BuiltInRegistries.POTION` and that no Nyckeln recipe is loaded.
- [ ] **Step 5: Commit.**
  `metacraft-kultur: without Brewery, PolymITer's drinks from the brewing stand, with their effects and the blackout`

### Task 5: The pack — Luck becomes Inebriated

**Files:**
- Create: `mods/metacraft-kultur/src/main/resources/assets/minecraft/textures/mob_effect/luck.png`, copied from Froosty11/PolymITer `src/main/resources/assets/minecraft/textures/mob_effect/luck.png` (`gh api repos/Froosty11/PolymITer/contents/<path> --jq .content | base64 -d`).
- Create: `mods/metacraft-kultur/src/main/resources/assets/minecraft/lang/en_us.json` with `effect.minecraft.luck` = "Inebriated" and `potion.potency.6` = "VII".
- Create: `mods/metacraft-kultur/src/main/resources/assets/minecraft/lang/sv_se.json` with `effect.minecraft.luck` = "Berusad" and `potion.potency.6` = "VII".
- Test: `KulturGameTests` (the existing "every asset in the jar" test)

**Behaviour:** Polymer's built pack must contain these keys *and* any other mod's keys in the same files. Find out how Polymer 0.18.4 treats two mods' `assets/minecraft/lang/en_us.json` (read its `ResourcePackBuilder`/`DefaultRPBuilder`).
- If it merges JSON lang files: nothing more to do.
- If it doesn't: write the keys into the pack through Polymer's API instead (a `RESOURCE_PACK_CREATION_EVENT` / `addData`-style hook that merges into whatever is there), and drop the static files.
- These assets ship in both modes; the pack doesn't know about Brewery. Accepted: Inebriated never appears with Brewery on, and Luck renamed is harmless.

- [ ] **Step 1: Failing test** `packRenamesLuck`: build the Polymer pack in-process, as the existing pack-contents test does. Assert that `assets/minecraft/textures/mob_effect/luck.png` is our PNG (same bytes). Assert that `assets/minecraft/lang/en_us.json` has `effect.minecraft.luck` = "Inebriated" and `potion.potency.6` = "VII", and that `sv_se.json` has "Berusad".
- [ ] **Step 2:** expected FAIL.
- [ ] **Step 3: Implement** per the merge finding.
- [ ] **Step 4:** both modes pass. Also build the pack with `mods:rivals-paint` on the classpath, if it's in this branch, and check that its `en_us` keys survive. If it's not in this branch, add a test-only second lang source that proves the merge.
- [ ] **Step 5: Commit.**
  `metacraft-kultur: the pack shows Luck as Inebriated (Berusad), with PolymITer's icon`

### Task 6: Screenshots and README

**Files:**
- Modify: `mods/metacraft-kultur/src/gametest/java/metacraft/kultur/clienttest/KulturClientTests.java`
- Modify: `mods/metacraft-kultur/README.md` ("The drinks" section)

- [ ] **Step 1: Client test** `drinksWithoutBrewery`, which runs only without Brewery (the client test task honours `-PkulturNoBrewery` too). Screenshots:
  - `drinks_vanilla_bottles.png`: the four drinks in item frames, or in the hotbar with one held.
  - `drinks_vanilla_inebriated.png`: the inventory screen at alcohol 55, showing Inebriated IV with the new icon and name, plus weakness and slowness icons.
- [ ] **Step 2:** `./gradlew :mods:metacraft-kultur:runClientGameTest -PkulturNoBrewery`. **Look at both screenshots.** The bottles use the drink models, not a plain potion. The effect reads "Inebriated IV" with PolymITer's icon.
- [ ] **Step 3: README.** Rewrite "The drinks" to describe both modes:
  - With Brewery: today's text.
  - Without: the recipe table, the step table and the blackout, from the spec.
  - How to run the tests without Brewery (`-PkulturNoBrewery`).
  - Remove "Brewery is not a dependency: without it nothing reads them" and replace it with the two-mode explanation.
- [ ] **Step 4: Full run.**
  - `./gradlew :mods:metacraft-kultur:build :mods:metacraft-kultur:test`
  - game tests in both modes
  - the client test in both modes

  All pass.
- [ ] **Step 5: Commit, then push.**
  - Commit: `metacraft-kultur: screenshots and README for the drinks without Brewery`
  - Push: `git push origin edvin/kultur-drinks`
