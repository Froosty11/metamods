# Cloud confirm 2 findings
Base: origin/edvin/ovvar-enchantments 90978b48 + merge of origin/edvin/resource-packs-delayed-push (contains cfbea0f0); clean merge; merged HEAD 0fdd9407. Datagen + patcherJar OK.

| run | task | rc | time (UTC) | result |
|---|---|---|---|---|
| ovvar1 | :mods:ovvar:runClientGameTest | 1 | 13:25:22-13:28:29 | FAIL (OvvarEnchantmentClientTests) |
| ovvar2 | same | 1 | 13:28:29-13:31:27 | FAIL (identical) |
| kultur | :mods:metacraft-kultur:runClientGameTest | 0 | 13:31:27-13:32:38 | PASS |

## Failure (both ovvar runs, deterministic)
`java.lang.AssertionError: the curse is not red: {color=white,!italic}` at OvvarEnchantmentClientTests.java:86 (called from runTest:78). Tooltip line for "Curse of the Spilled Drink" has colour white instead of red. This is the LAST client test; everything before it passed (no exceptions/crash before it). Not a resource-pack race issue.

## Per-connect (every `Connecting to localhost`, both runs identical pattern)
All 10 ovvar connects (5 per run) happened BEFORE `Resource pack created successfully!` (1-3 s earlier; each connect is a fresh server). In all 10 the combo pack f47fd2e2 was loaded (ResourceManager reload listing server/.../f47fd2e2-5232-34f8-a0f9-cce50237351a, 2-3 s after connect) and `logged in` followed. 0 failed joins. 0 `Timeout loading world` in any log. Kultur: 1 connect, same pattern, joined.
(Run 1 connects: ports 25600,25599,25606,25602,25607; joins at 13:26:16, :59, 13:27:36, 13:28:06, :23. Run 2: 13:29:14, :57, 13:30:34, 13:31:04, :22.)

## Punkphöz
offers=5 both runs; changed pixels=61777; after hurt+leash 61777; trader llama leash and still connected (lost connection only at normal test end).

## [ovvar-clienttest] lines (both runs): combo pack applied server pack eae61945..., client/server legs+chest air/null, shader patcher url, instant mannequin dyes, lineup colours (see logs). Chapter lineup mean-vs-want within ±48 except none flagged; e.g. it_kisel mean [66,114,141] want [87,115,130]; media [47,45,50] want [20,20,20].
[kultur-clienttest]: loom 35 patterns selectable server/client, 20/20 pattern items checked (black_dye, cerise_dye), 20 chapter banners in 4 shots, drinks screenshot. PASS.
Screenshots: shots/ovvar1 (20), shots/ovvar2 (20), shots/kultur (8).

Human review: Punkphöz model after hit and leash; chapter lineup colours; mobs wearing ovvar; the curse tooltip colour.
