# ovvar cloud confirm 3
Branch: origin/edvin/ovvar-enchantments @ 4644e67f2a58579f089ce8dccbd456dd9380f369 (contains 4644e67f), merged cleanly with origin/edvin/resource-packs-delayed-push -> 098b832881e2dddbb9218544c5a3526e2ab137d1.
Datagen (ovvar, metacraft-kultur, moredyes) and :mods:ovvar:patcherJar succeeded (Maven 429s needed several retries).

| run | rc | start-end (UTC) | result |
|---|---|---|---|
| ovvar1 | 0 | 13:58:21-14:02:12 (3m51s) | BUILD SUCCESSFUL |
| ovvar2 | 0 | 14:02:12-14:05:56 (3m44s) | BUILD SUCCESSFUL |

- Per-class PASS/FAIL is not printed in the logs; rc 0 / BUILD SUCCESSFUL means all client game tests passed.
- `Timeout loading world`: 0 in both runs.
- Connects: 6 `Connecting to localhost` per run (ports 25600,25599,25606,25602,25607,25601); each followed by `Tester ... logged in` (6/6 both runs).
- Punkphöz: `punkphoz offers=5` in both runs; changed pixels 61777 (and after hurt/leash 61777).
- 52 `[ovvar-clienttest]` lines per run (see logs); lineup colour checks, instant mannequins dyes, shader patcher URL, server pack applied all logged.
- Exceptions: only `IllegalStateException: Failed to open OpenAL device` (no audio device; non-fatal), Realms auth warning, and normal "Tester lost connection: Disconnected" at each test end. No crash, no unexpected disconnect.
- Warnings: Punkphöz pool leaves out kultur:*_banner_pattern ("isn't registered") in ovvar-only test env.
- Screenshots (23 per run): shots/ovvarN/ — see 0017-0019 punkphoz, 0020_enchanted_ovve_front, 0021_mob_ovve_front, 0022_mob_frack_front, 0011-0016 chapter lineups.
