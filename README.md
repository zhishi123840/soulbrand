# soulbrand (附魂 / Fu Hun)

A Minecraft **1.20.1 / Fabric** mod: imbue a weapon or a piece of armour once,
then every kill feeds it soul. The higher the soul gauge, the harder it hits --
with no ceiling.

- mod id: `soulbrand` / display name: `附魂`
- loader: Fabric Loader `0.16.10`, yarn `1.20.1+build.10`, Fabric API `0.92.12+1.20.1`
- JDK 17, Gradle wrapper 8.10, Loom

## The loop

1. Kill mobs -> they drop **残魂碎晶 / soul fragments**.
2. Put the gear in the **附魂台 / soul altar** GUI, press *brand*: costs
   **3 fragments + 55 experience points** (points, not levels -- your level is
   never reduced). All checks pass before anything is consumed, and a failed
   attempt takes nothing.
3. Keep wearing/wielding it. Every kill adds soul to the main-hand weapon
   **and** to all four branded armour pieces you are wearing:
   `+2` normal mob, `+5` "hard" mob (a `Monster` that is not zombie / skeleton /
   spider / creeper), plus `+1` per consecutive kill within 100 ticks, capped at
   `+4`.
4. Soul is **uncapped and only goes up**. Every 100 soul is one tier:
   `0-99 = I`, `100-199 = II`, `200-299 = III`, `300-399 = IV`, ...
   Per tier: **+1.0 attack** (before armour reduction), **+1.5 armour-piercing**
   (after armour reduction, ignores armour), **+0.5 armour** per branded armour
   piece.
5. Feed soul directly: put a branded item plus fragments in an **anvil** -- each
   fragment is `+10` soul and the transaction costs no experience levels.
   Shift-clicking the output injects *all* fragments at once.
6. **净魂粉 / soul cleanser** (craft: 3 fragments) resets one branded item's
   soul to 0 -- via the altar button, or by right-clicking it while holding the
   target in your other hand. It only clears soul; the brand itself is
   permanent.
7. **生命魂晶 / life crystal** (craft: 8 fragments + 1 echo shard) is a pure
   gain: **+1 heart (2 points) of max health each**, up to `+40` total.

There is no backlash. Soul never eats your health -- an early draft of this
mod had a "overflowing soul costs maximum health" mechanic, and it was cut
before 1.0. Nothing in the shipped code touches player health except life
crystals, which only add to it.

## Numbers that matter

| Thing | Value |
|---|---|
| Soul per tier | 100, tiers uncapped |
| Attack / armour-pierce / armour per tier | +1.0 / +1.5 / +0.5 |
| Armour-piercing clamp | at most 3x the pre-armour damage |
| Soul per kill | +2 normal, +5 hard mob, combo up to +4 |
| Fragment drop rates | wither skeleton 100% · creeper / enderman 50% · zombie / skeleton / spider 25% |
| Fortune interaction | +1 guaranteed fragment per Fortune level (extra drop, not a probability bump) |
| Brand cost | 3 fragments + 55 XP points |
| Anvil injection | +10 soul per fragment, free of XP levels |

Arrows carry a **snapshot** of the bow's soul at the moment you shoot, so
switching slots mid-flight does not change the damage. Attribution counts melee
and player-shot projectiles only -- pets, iron golems and other mods' AoE
do not feed your gauge. Killing another **player** feeds nothing, and neither
does anything while you are in creative mode.

The `soul_brand` enchantment itself is a marker (`BREAKABLE`, all six slots,
`VERY_RARE`, max level 5) that is never offered by the enchanting table, never
on books and never randomly selected. Its level is kept in sync with the soul
value as a `short`, so it survives past level 127 and shows up in JEI / Jade /
the equipment panel -- `EnchantmentHelper.fromNbt` does not clamp stored levels
against `getMaxLevel()`, which is why an uncapped tier still renders.

## Config

`config/soulbrand.json`, six fields, written with defaults on first run:
`fragmentDropMultiplier`, `soulGainMultiplier`, `penetrationMultiplier`,
`attackPowerMultiplier`, `armorBonusMultiplier`, `brandXpCost`. A malformed file
falls back to defaults **without overwriting your file**, so you can fix it.

Drop-rate multiplier semantics: expected = base chance x multiplier, the integer
part is guaranteed and the fraction is rolled once. So `2.5` on a 25% drop means
0 guaranteed + a 62.5% chance of 1 -- not "25% x 2.5 capped at 100%".

## Build

```sh
./gradlew build
```

Artifact lands in `build/libs/soulbrand-<version>.jar`. Drop it into
`.minecraft/mods` together with Fabric Loader and Fabric API.

## What is in the repo

Mod sources only: `src/`, the Gradle wrapper, `build.gradle`,
`settings.gradle`, `gradle.properties`.

Everything else from development is git-ignored -- build output, run
directories, local backups, the ad-hoc `*.txt` build logs, and the one-off
asset scripts under `tools/` (Blockbench model conversion, texture painting).
Those scripts are throwaway generators, not a dependency of the build.

`gradle.properties` intentionally does **not** pin `org.gradle.java.home`; set
JAVA_HOME to a JDK 17, or pass `-Dorg.gradle.java.home=...` if your default
differs.
