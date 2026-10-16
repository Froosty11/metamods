# Kultur drinks without Brewery — design

2026-10-16. Module `mods/metacraft-kultur` (mod id `metacraft-kultur`, namespace `kultur`), Minecraft
26.3, Fabric + Polymer, vanilla clients.

## Why

The server's mod polls are not voting Patbox's Brewery in. Kultur's four chapter drinks exist today
only as Brewery data (`data/kultur/brewery_drinks/*.json`), so without Brewery they cannot be made at
all. The user wants them back the way PolymITer (Froosty11/PolymITer, 1.21.10, the mod Kultur
succeeds) made them — the vanilla brewing stand — but with **Brewery's drunkenness**: the alcohol
meter and its thresholds, above all the stagger ("the cute little steps") while walking. Players see
an **Inebriated** effect for as long as they have alcohol in them; the resource pack turns vanilla's
Luck into Inebriated.

## Goals

1. Without Brewery on the server, the four drinks are brewable and drinkable with PolymITer's recipes.
2. Drinking feeds a per-player alcohol meter that behaves like Brewery's (same rises, drain,
   effects, sobering foods, persistence), with its stages compressed into steps of 10 below the blackout.
3. Each drink keeps its own effects, as Kultur's Brewery drinks have them today.
4. A visible Inebriated effect the whole time the meter is above zero, its timer counting down to sober.
5. With Brewery on the server, nothing changes from today: Brewery's drinks, Brewery's meter, none of
   this code active, no double recipes.

## Non-goals

- Brewery's quality and barrel age (there is one quality, see below), its game rules
  (`alcohol_value_multiplier` etc.), cauldron cooking, distilling.
- Drunkenness from anything but Kultur's drinks. Vanilla potions are untouched.
- The guidebook's "IT's drinks" page for this mode and dropping Brewery from `deploy/edvin*.txt`:
  separate follow-ups on other branches (booklet is PR #19; deploy lives on `split-deploy`/`edvin-test`).

## Anti-goals

- **No hidden effects.** (The user: "no hidden extras".) Brewery hides the icons of its threshold
  effects; here every effect a player is under shows its icon.
- Splashing or lingering a drink must not make *someone else* drunk. Only drinking counts.
- Nothing that needs a client mod; a vanilla client with the auto-served pack sees everything.
- Icons that flicker: an effect re-applied on a rate must outlast its rate so its icon and timer
  stay steady.

## Where it switches on

Everything below is active only when `FabricLoader.isModLoaded("brewery")` is false — registrations,
recipes, tick handler, any mixins (the existing `KulturMixinPlugin` applies its mixins only *with*
Brewery; anything new needs the inverse). The Brewery drink JSONs stay as they are for the Brewery case.

## Recipes (PolymITer's)

| drink | made by | alcohol |
| --- | --- | --- |
| Alcoholic Beverage (`alcohol`) | brewing stand: water bottle + potato | 20 |
| Spiken Patch Drink (`spiken`) | brewing stand: Alcoholic Beverage + sweet berries | 30 |
| Släggan Patch Drink (`slaggan`) | brewing stand: Alcoholic Beverage + suspicious stew | 35 |
| Nyckeln Patch Drink (`nyckeln`) | crafting, shapeless: apple + **water bottle** | 0 |

PolymITer's Nyckeln took "apple + any potion", which turned a Potion of Healing into Nyckeln; it
takes a water bottle only. Alcohol values are the ones in today's Brewery JSONs.

The three brewed drinks are potions (registered `Potion`s, Polymer-safe, so the brewing stand and a
vanilla client handle them; PolymITer used `SimplePolymerPotion`). The potions carry **no** mob
effects of their own; drinking is handled by Kultur (below), which is also why a splash or lingering
version does nothing. Nyckeln is a Polymer item with a drink consumable, as PolymITer had it.

On the client every drink shows its existing bottle model `kultur:drink/<id>`
(`assets/kultur/items/drink/`, already in the module) and its proper name ("Spiken Patch Drink", not
"Potion of …" or "Uncraftable Potion"); the splash/lingering versions get sensible names too.

## The meter (Brewery's, compressed into steps of 10)

Ported from Brewery 0.17's `AlcoholManager` and `data/brewery/brewery_effects.json`
(github.com/Patbox/brewery), with one change the user asked for: Brewery's stages sit at 50–140,
above where Kultur's drinks can take anyone (the blackout at 100 resets you to 30, and no drink adds
more than 35). So **each Brewery stage comes one step of 10 after the last, all below the
blackout**, and the Inebriated effect's level shows which step you are on.

Brewery's numbers depend on a drink's quality; vanilla potions have no quality, so quality is fixed
at 10 (every Kultur drink's `base_quality_value`). That makes Brewery's quality offset a constant,
so it is dropped: everything reads one number, `alcohol`.

- **Level.** `alcohol` per player. Drinking adds the drink's value: `alcohol = max(alcohol + v, v)`.
- **Drain.** While `alcohol > 0`, it falls by `0.0012 × 10 = 0.012` per tick (≈14.4 a minute).
- **Steps**, every rate counted on the player's tick count, as Brewery. Each step keeps the ones below it.

| alcohol | Inebriated | every | adds (Brewery's stage) |
| --- | --- | --- | --- |
| > 0 | I | — | nothing; tipsy |
| 30 | II | 4 s | weakness 0 |
| 40 | III | 4 s | slowness 0 |
| 50 | IV | 0.2 s | **the stagger**: a velocity push in direction `(rand − 0.5, rand/2 − 0.49, rand − 0.5)` normalised to length `min((0.3 + rand/10) × ((alcohol − 40) / 50) × 0.7, 0.2)` — Brewery's formula moved down so the little steps are already felt at 50 and reach Brewery's cap near 90 |
| 60 | V | 16 s | nausea 0, 32 s |
| 70 | VI | 4 s | alcohol poisoning: 1 damage, type `kultur:alcohol_poisoning` |
| 80 | VII | 16 s | darkness 0, 16 s |
| ≥ 100 after a drink | — | — | the blackout (below) |

  Builder's notes:
  - Brewery gives the stage effects a duration equal to their rate (4 s every 4 s) and hides them.
    Here they show their icons, so give them enough slack over the rate that they never lapse
    between refreshes (the builder picks the slack).
  - A player's velocity change only reaches their own client if it is sent (`hurtMarked` or an
    explicit motion packet). The stagger must be visible on a real client, not just on the server's
    entity.
  - **Poisoning never kills (my call, not the user's).** Brewery's formula,
    `(level − 110) / 10 + 1` every 4 s, is meant for a band players rarely reach. Here the band is
    three drinks away and drains slowly: draining from 90 to 70 takes about 80 s. So it is 1 damage
    every 4 s, and it never takes a player below 1 health (half a heart). Armour and resistance work
    as the damage type says (copy Brewery's `alcohol_poisoning` damage type).
- **Sobering.** Eating bread lowers `alcohol` by 3, drinking a milk bucket by 10 (Brewery's
  `alcohol_reduction_items`), only while `alcohol > 0`.
- **Persistence.** Saved with the player; survives logging out and changing dimension; dying clears
  it (Brewery copies it only on a live respawn, `restoreFrom(…, alive=true)`).
- **Who.** Players only. (Brewery ticks every living entity; nothing but a player drinks here.)

## Each drink's own effects

Today's Brewery JSONs, evaluated at quality 10, with the level read *after* the drink is added (as
Brewery does: `drink()` runs before the consumption effects):

- **Alcoholic Beverage, Spiken:** at random, slowness *or* speed for `60 + 14 × 10 = 200` s,
  amplifier `clamp(floor((alcohol − 30) / 40), 0, 1)`.
- **Släggan:** three times in four, slowness (amplifier as above) and, when `alcohol ≥ 60`, strength
  0; else speed (as above) and, when `alcohol ≥ 60`, weakness 0; both for 200 s.
- **Nyckeln:** nothing; it is the sober one.
- **The blackout** (all three alcoholic drinks), when `alcohol ≥ 100` after drinking:
  - teleport to a random safe spot up to 250 blocks away (Brewery's `teleport_random` with distance
    500, i.e. ±250). It must not fail or loop forever, as PolymITer's `while(true)` could.
  - clear all effects
  - 4 damage of `kultur:alcohol_poisoning` (this hit *may* kill, as in today's drinks)
  - the action bar "You wake up with a splitting headache..." in bold `#FF0F0F`
  - blindness for 10 s
  - `alcohol` set to 30

  For scale: three Spiken (90) is step VII; the fourth blacks you out. Three Släggan (105) black you out.

## Inebriated

- A Polymer mob effect `kultur:inebriated` that vanilla clients receive as `minecraft:luck`
  (`PolymerStatusEffect.getPolymerReplacement`, as PolymITer did). It does nothing itself; the
  steps' effects are separate, visible effects.
- The player has it whenever `alcohol > 0`. Its duration is the time left until sober
  (`alcohol / 0.012` ticks), refreshed often enough that the countdown reads right after each drink,
  bread or milk. It goes away when the meter reaches zero. Milk clears it like any effect; it comes
  back on the next refresh if alcohol is left.
- Its amplifier is the step, I–VII, per the table. Vanilla only has potency names up to VI
  (`potion.potency.0`–`5`), so the pack adds `potion.potency.6` = "VII".
- **Resource pack:**
  - Kultur's Polymer pack replaces `assets/minecraft/textures/mob_effect/luck.png` with PolymITer's
    icon (in Froosty11/PolymITer at `src/main/resources/assets/minecraft/textures/mob_effect/luck.png`).
  - It sets `effect.minecraft.luck` to "Inebriated" (en_us) and "Berusad" (sv_se).
  - Vanilla's own Luck (a creative-only potion) will show as Inebriated too; accepted.
- The pack is built by Polymer from every mod's assets. A `assets/minecraft/lang/<lang>.json` from
  Kultur must *add* its keys, not replace another mod's file of the same name (rivals-paint ships an
  `assets/minecraft/lang/en_us.json`). Verify how Polymer merges lang files and do whatever it takes
  for both to survive.
- In the minecraft namespace the pack adds only that icon and those lang keys.

## Tests

- The existing Brewery tests (`KulturDrinkTests`) skip without Brewery; the new ones skip *with* it.
  The dev and test runtime currently always has Brewery (`runtimeOnly`), so add a way to run the game
  tests without it (a Gradle property or a second test task — builder's call) and run both.
- Server game tests without Brewery: each brewing recipe produces its drink and nothing else
  (water + potato is not otherwise a recipe); Nyckeln crafts from apple + water bottle and not from
  apple + another potion; drinking adds the right alcohol; drain per tick; each step's effect at its
  level, visible (icon shown), and Inebriated's level matching the step; the stagger moves the player and is sent to the client; poisoning
  damages with `kultur:alcohol_poisoning` and stops at 1 health; bread and milk sober; the blackout at `alcohol ≥ 100`; Nyckeln
  adds nothing; splash and lingering versions affect nobody; Inebriated present with a countdown,
  gone when sober; the meter survives save/load and is cleared by death.
- The client screenshot test (`KulturClientTests`) photographs, without Brewery: the four bottles in
  hand or in a frame, and the inventory screen with Inebriated showing its new icon and name. Look at
  the screenshots.
- Builds, game tests and the client test run in a cloud agent, not on the user's machine.

## Delivery

Branch `edvin/kultur-drinks` off `edvin/kultur` (upstream PR #16 stays untouched). No `docs/` files
and no AI attribution in commits on it (upstream rule). Update `mods/metacraft-kultur/README.md`'s
drinks section to describe both modes. The branch is pushed to `origin` (the Froosty11 fork) only; no upstream push and no PR without the user's go.

## Left to the builder

How the meter is stored and ticked (Fabric data attachments + server tick events are preferred over
mixins where they do the job), how drinking is detected, how the brewing recipes are registered on
26.3's Fabric API, how names and models reach the client, the icon slack, and the test-without-Brewery
mechanism.

## Calls made while writing this (not the user's)

- Poisoning is 1 damage every 4 s and never kills; the blackout's 4 damage still may.
- The stagger's formula is moved down so it starts at 50, not just its threshold.
- Each step adds level-0 effects (weakness, slowness); Brewery's growing amplifiers are dropped, as the
  steps now carry the growth.
- Brewery's quality offset is dropped (quality is fixed, so it was a constant).
- No migration of Brewery-made drinks in an existing world: the server has not run Brewery.
