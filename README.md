# ParCool! — 1.12.2 backport

An **unofficial** backport of [ParCool!](https://github.com/alRex-U/ParCool) to Minecraft **1.12.2** / Forge.

> ⚠️ **This is not the original project and is not affiliated with its author.**
> Do not report problems with this backport to the original author. All credit for the
> design, gameplay and assets belongs to **alRex-U**. See [NOTICE](NOTICE) and [LICENSE](LICENSE).

ParCool adds parkour movement to Minecraft: wall running, sliding, vaulting, dodging,
cliff hanging, crawling, ziplines, a stamina system, and more.

---

## Requirements

| | |
|---|---|
| Minecraft | **1.12.2** |
| Forge | **14.23.5.2847** or newer |
| [Aqua Acrobatics](https://github.com/Fuzss/aquaacrobatics) | **1.15.4+ — required** (hard dependency: pose / bounding-box / swimming API) |
| [Patchouli](https://github.com/VazkiiMods/Patchouli) | optional (in-game guide book; not ported yet) |

Tested on **Cleanroom 0.6.13-alpha** with Forge 14.23.5.2864 and Aqua Acrobatics 1.15.4.

If Aqua Acrobatics is missing, Forge shows its standard "missing dependency" screen.

## Installation

1. Drop the jar into `.minecraft/mods/`.
2. Drop **Aqua Acrobatics 1.15.4+** into `mods/` as well — ParCool will not start without it.
3. Launch the game.

### ⚠️ One setting to check on first run

Aqua's `Movement.enableToggleCrawling` **must stay `true`** — ParCool's crawling depends on it.
But its default key is **C**, which collides with ParCool's own crawl key.
**Rebind or clear Aqua's "toggle crawl" key** in *Options → Controls*; do **not** disable the
config option itself. ParCool prints a warning with the same instructions at startup if it
detects the problem.

## Usage

* Bind ParCool's keys in *Options → Controls* (search for `ParCool`).
* Enable/disable individual actions and animations, stamina behaviour, HUD style and
  camera animations in **Mods → ParCool → Config**.
  Changes are written to `config/parcool/parcool.cfg` — note that `used_stamina` only takes
  effect after re-entering the world.
* Stamina can be ParCool's own bar or the vanilla hunger bar (`used_stamina`).

### Server-side commands

Require permission level 2 unless noted. `/parcool limitation get …` is available to everyone.

```
/parcool limitation get    global                       # dump the effective server limitation
/parcool limitation get    <group>:<name> of <player>
/parcool limitation set    <group>:<name> of <players> <item> <value>
/parcool limitation set    individual    of <players> <item> <value>
/parcool limitation enable|disable <group>:<name> of <players>
/parcool limitation delete <group>:<name>
/parcool zipline set <x1> <y1> <z1> <x2> <y2> <z2> [<nbt>]
```

`<item>` is one of `to_default` / `boolean <name>` / `integer <name>` / `reals <name>` /
`possibility <Action>` / `least_stamina_consumption <Action>`.

> **`limitation_imposed` notes:** the `[Server.*]` section of the config is **ignored** unless
> `limitation_imposed` is enabled. That is intentional (same behaviour as upstream): by default
> players get the most advantageous values, and admins opt in to imposing limits — either with
> that switch or per-player with `/parcool limitation`.

## Building from source

```bash
# 1. Gradle needs a JDK 25+ JVM (RetroFuturaGradle 2.0.2 is compiled for it);
#    the mod itself still targets Java 8.
# 2. Machine-specific JDK paths are NOT committed. Copy the template first:
cp gradle.properties.example gradle.properties
#    then edit the "local toolchain" section at the bottom of gradle.properties

./gradlew build
# → build/libs/ParCool-1.12.2-3.4.3.3.jar
```

## Status

Feature complete for single-player and dedicated servers. Not yet ported: the
**Patchouli guide book** contents. Compatibility hardening (OptiFine, shaders) and
release packaging are still in progress.

Detailed porting notes — what differs between 1.16.5 and 1.12.2 and why — are in
[`docs/`](docs/) (written in Chinese).

## Public API

`com.alrex.parcool.api.*` mirrors the upstream API surface (stamina, action events,
animation hooks, limitation control). See [`docs/`](docs/) for the 1.12.2 specifics.

## License

**GNU Lesser General Public License v3.0** — see [LICENSE](LICENSE).

This work is a derivative of ParCool! and therefore **cannot be relicensed** under
different terms. Because it is distributed under the LGPL, the corresponding source must
remain available — that is what this repository is for.

### Third-party

* **ParCool!** by alRex-U — LGPL-3.0 — the original work this is ported from.
* **Aqua Acrobatics** by Fuzs — released into the public domain (Unlicense) — required
  dependency; no source code from it is copied into this project.
* **Patchouli** by Vazkii — optional dependency, not redistributed.

Minecraft and Minecraft Forge are not redistributed here.
