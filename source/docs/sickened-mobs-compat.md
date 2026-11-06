# Sickened Mobs compatibility

MCA optionally integrates with the `sickened_mobs` mod. All compatibility behavior, including hostile-golem overrides and MultiGolem conversion suppression, is inactive when Sickened Mobs is absent. Normal MCA and MultiGolem behavior is preserved. Only cleanup of saved sickness state remains active after removal. The optional mod's classes are not required to load MCA on its own.

- Adds `mca:sickened_person_spawn_egg` only when Sickened Mobs is installed. It spawns `mca:sickened_person`, a purple MCA zombie person. The permanent MCA entity IDs remain loadable without the optional mod and fall back to ordinary zombie people. Direct use, dispensers and spawners retain the sickness tag.
- The external `sickened_mobs:sickened_villager_spawn_egg` remains a different, unchanged item spawning its vanilla-style `sickmob:sickened_villager`. MCA does not redirect that egg.
- Golden Apple Stew and Withered Flesh are the existing `sickened_mobs:golden_apple_stew` and `sickened_mobs:withered_flesh` items; no duplicates are registered.
- Sickness has its own tracked infection percentage and editor slider. MCA people progress toward conversion using MCA's existing infection progression, rather than the external mod's instant death conversion. Conversion retains MCA identity, appearance and inventory.
- Sickness and zombie bites share MCA’s infection setting, configured bite chance, guard immunity and level-based resistance. Sickness announces existing illness dialogue once when infection starts; repeated exposure does not repeat that announcement.
- Wither damage, Wither Skeletons, the Wither, and sickened attackers can start sickness. Ordinary zombies can start a separate opposing infection in fully sickened zombies. Pending sickness and ordinary zombie infection can coexist; neither rewrites the other's pending value.
- Purple names and gradually purple skin distinguish sickness from ordinary green zombie infection. Sickness tint takes visual precedence if both infections are present.
- Gifting the existing Golden Apple Stew cures pending sickness, consumes one stew in Survival, and returns one bowl. It does not cure a separate pending zombie infection. Fully transformed mobs retain Sickened Mobs' timed stew cure.
- Fully sickened MCA people drop the existing Withered Flesh item through normal mob-loot handling.
- When Sickened Mobs is removed, its items and MCA's conditional sickened-person egg are no longer registered. The Person egg therefore has no Creative or `/give` entry without the optional mod. MCA clears saved sickness and cure state when affected mobs next tick. Fully transformed people remain valid ordinary MCA zombie villagers; no optional entity ID can disappear from the save.

Hostile golems remain enemies of MCA villagers and ordinary golems: `sickmob:sickened_iron_golem` and MultiGolem's zombie variant (`minecraft:zombie_golem`, with registered-ID and variant-attachment recognition). Guards recognize them as enemies, civilians recognize them as threats, friendly-golem damage immunity does not suppress their hits, and corrupted golems do not perform MCA police arrests. MultiGolem's instant villager/trader conversion handler is disabled, allowing the ordinary attack-damage path to run. This does not change attacks by actual zombies.

Validation: `python3 tools/validate_sickened_compat.py` (65 checks) and `python3 tools/validate_hostile_golem_compat.py` (22 checks) exercises the production lifecycle, cure, conversion, item reuse, uninstall and palette methods against lightweight fixtures. It does not launch Minecraft or prove visual behavior in-game.

On restart without Sickened Mobs, the conditional MCA egg is not registered. Minecraft inventory reload clears old slots and skips item entries whose IDs cannot decode; its Creative and command lists likewise have no entry for that ID. The editor skips both the sickness control and its vertical spacing. Unconverted sickness patients lose sickness state; full sickened people retain or fall back to the existing zombie-person type. Pending sickness qualifies for infirmary admission while the mod is installed, and transformed sickened people already use the ordinary zombie-person infirmary path.

The editor uses the short “Sickness” label and the same slider style as Infection. Scroll the right-hand Misc panel with the mouse wheel or its arrow buttons. The separate Person egg uses MCA’s existing egg sprite with a purple item tint; it does not copy or reference Sickened Mobs’ texture.

Sickened people use the existing zombie speech garbling: advanced pending sickness crosses the same babbling threshold, and fully sickened people retain it independently of their separate zombie infection level. The ordinary randomized word replacement remains unchanged.

## Zombie behavior restoration — 2026-11-01

Both MCA sickened entity IDs construct ZombieVillagerEntityMCA with monster attributes and zombie renderers in both model settings. Fully tagged living people from the former implementation migrate on their next server tick. Pending living infections still follow MCA's staged rules. Sickness conversion preserves UUID, MCA appearance/family data and inventory. Without Sickened Mobs, pending infections clear and fully sickened types become ordinary zombie people.

On Hard difficulty, opposing-faction lethal hits follow Sickened Mobs' death-conversion rule for MCA zombie people: sickened/wither attacks turn an ordinary zombie person sickened; ordinary zombie/skeleton/undead attacks turn a sickened person into an ordinary zombie person. Same-faction hits and unrelated damage still kill normally. Nonlethal opposing undead attacks use MCA's existing random infection roll and progression. Successful faction changes restore health and retain identity/inventory. All behavior stays inside MCA's optional compatibility layer; the external mod is unchanged.

Current focused checks: `tools/validate_sickened_factions.py` executes the production death-conversion helper (34 checks); `tools/validate_entity_renderers.py` checks all registered renderers (64 checks). The older broad sickness fixture above is stale following separate sickened ID/cough changes and is not claimed passing. No Minecraft launch or live gameplay validation.
