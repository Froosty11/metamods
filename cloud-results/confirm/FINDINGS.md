# ovvar confirm results
Branch edvin/ovvar-enchantments @ 90978b4820e0cf6c46893663d32cf4894e1fa2cd. Datagen (ovvar, kultur, moredyes) and ovvar:patcherJar built OK.

| Run | Attempts | rc | Duration | Result |
|---|---|---|---|---|
| A ovvar | 2 (attempt1/ + top-level) | 1, 1 | 2m47s, 1m53s | FAIL both: `AssertionError: Timeout loading world` (attempt1 at OvvarClientTests:75 connect; attempt2 at OvvarDanseClientTests:145 connect). Server logged "Server empty for 60 seconds" while client sat on ConnectScreen. |
| B kultur | 1 | 0 | 1m25s | PASS |
| C kultur_plain | 1 | 0 | 1m16s | PASS |

- No `[ovvar-clienttest]` lines were emitted in either attempt, so Punkphöz offer count, everyChapterLineup, punkphozShows, mob ovvar and enchantment checks were NOT confirmed (never reached). Only attempt1 has 3 screenshots (after, after_gesture_end, after_frack).
- Loom (B, Brewery+moredyes): black_dye 35 selectable server / 35 client, 3 patterns without item checked; 20 of 20 pattern items checked both sides. cerise_dye: same 35/35, 20 of 20. No per-pattern failures. (No separate vanilla-control line printed.)
- Loom (C, plain): "More Dyes is not loaded, vanilla dye only"; black_dye 35/35, 20 of 20.
- `chapter banners: 20 patterns in 4 shots` in both B and C.
- items.png-atlas: kultur runs 1024x512; ovvar attempt1 6x 4096x2048 (NOT 2048x2048); ovvar attempt2 died before creating atlases. 
- B has harmless NoSuchFileException stderr noise for brewery resource paths under build/run.
- Other noise: OpenAL device failure, Realms/sessionserver timeouts (sandbox).
