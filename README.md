# Minecraft Comes Alive Remastered
![Interaction](https://media.forgecdn.net/attachments/1945/262/minecraft-logo-2013-1-3-png.png)
[![Also on CurseForge](https://img.shields.io/badge/Also%20on-CurseForge-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/minecraft-comes-alive-remastered)
[![Made with Java](https://img.shields.io/badge/Made%20with-Java-ED8B00?style=for-the-badge&logo=coffeescript&logoColor=white)](https://www.java.com)


Bring life to those boring villages but with some tweaks!
*   Build your way to kingship by helping to improve the village!
*   Replaces the big noses with player-like NPCs!
*   Interact with the community through gifting and conversations!
*   You can build relationships with the townsfolk, and possibly settle down!
*   Watch your children grow!
*   Marry your fellow players when in multiplayer!
*   Enable chat AI integration (/mca chatAI default) to talk freely with your villagers!
*   Enable TTS voices to let villagers speak! (it sounds pretty robotic, will be fixed later on)
*   Create, share and use custom clothes from a built-in skin library with thousands of available assets!
*   Use Villager Editor in Creative mode to edit villagers or cheat!

## Interact with villagers!
Villagers share the entity ID `mca:person`; gender remains part of each person's
saved data. The creative menu has one **Person Spawn Egg** (`mca:person_spawn_egg`).
The old male/female entity IDs remain compatible with existing saves and commands,
but construct a Person and save under the new ID. Existing male/female spawn eggs
remain usable and are hidden from the creative menu. Zombie villagers likewise
share `mca:zombie_person`, with one **Zombie Person Spawn Egg**
(`mca:zombie_person_spawn_egg`) and compatible legacy IDs. Infection and curing
preserve gender while using the unified entity types.

People keep processing remembered doors after their walking path finishes or is
cancelled. Doors wait for the doorway to clear before closing, and powered doors
are left open. This works with both the standard and smarter door-opening modes.

![Interaction](https://media.forgecdn.net/attachments/1945/204/b4264769-jpg.jpg)

## Manage entire villages!
![Management](https://media.forgecdn.net/attachments/2011/931/2026-10-04_20-45-38-png.png)

With Auto Scan enabled, villagers report nearby beds and workstations and the
rooms they occupy every 600 game ticks (about 30 seconds). The door-containing
ground room anchors the building (the lowest valid door-containing room when
there are entrances on multiple levels): discovered rooms below it become Basement 1,
Basement 2, and so on; rooms above become Floor 2, Floor 3, and so on. Upper rooms
and basements do not require doors. Rooms still need to pass the normal enclosure,
size, and building-type checks. Discovery is gradual as villagers explore.
Add Building searches the connected interior immediately and registers valid
rooms on the discovered levels using the same floor rules.

Blueprint → Rules → Path Block lets you search for and choose the village's road
material. Dirt Path is the default. Residents first try a route along that block,
with access to buildings, ladders, and short connections at either end. If no
road route reaches their destination, they fall back to ordinary travel. Guards
and archers can leave the roads to patrol and respond to threats.

Guards give a first warning and a final warning before attacking on the third
hit. A hit that kills a villager triggers immediate retaliation, even before
either warning. Alerts reach nearby guards within 32 blocks.

## Edit villagers!
![Editor](https://media.forgecdn.net/attachments/1945/205/e53a5a5-jpg.jpg)


## Building

Use the named Gradle wrapper from this repository or the `source` folder:

```sh
./buildmca                    # Fabric + NeoForge for all three versions
./buildmca --version 26.1.2   # Only Minecraft 26.1.2
./buildmca --version 26.2     # Only Minecraft 26.2
./buildmca --version 26.3     # Only Minecraft 26.3
```

Additional Gradle options are forwarded, for example `./buildmca --version 26.3 --refresh-dependencies`. Jars are published to `source/fabric/build/libs` and `source/neoforge/build/libs`. `gradlew` remains available for normal Gradle tasks.

To build MCA Remastered, a recommended machine is a Mac or a Linux machine (For Solaris users, I do not know, but it might work depending on your setup). This guide is targeted at UNIX and UNIX-like setups.
### Modifying
Modifying the assets and logic requires you to go to these following paths:
* Assets, data, icon, and mixin list: `/common/src/main/resources/`
* Logic: `/common/src/main/java/com/ancientkira/mca/`
### Details
Once you have finished modifying the contents, head over to gradle.properties and change the mod details to a new name for your fork and new details, but it is mandatory to keep the credits and add your name in there. (e.g. Minecraft Comes Alive Remake).
### Baking
Once you have finished modifying, go to the `source` folder and type:
```zsh
./gradlew clean build
```
And the build will start. 
This builds Fabric and NeoForge for Minecraft 26.1.2, 26.2, and 26.3. All three versions
are kept together in each loader's `build/libs/` folder.

The 26.3 target uses Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, NeoForm
26.3-1, and NeoForge 26.3.0.69-beta. Its generated compatibility sources keep
Minecraft API changes separate from the shared gameplay code.

If Fabric compilation reports a missing `EntityFluidExtension` after changing
Minecraft or Fabric API versions, refresh Loom's cached dependencies from `source/`:
```zsh
./gradlew build --refresh-dependencies
```
Then use the normal build command again. See [Fabric's cache troubleshooting guide](https://docs.fabricmc.net/develop/loom/).

### Output
The outputs are in the following directory:
* NeoForge: `source/neoforge/build/libs/`
* Fabric: `source/fabric/build/libs/`
* Common: `source/common/build/libs/`

Pick the base file to distribute. The `sources` (source code) and `javadoc` (documentation) files are optional.

The multi-version build is built into Gradle; no separate script is needed.

**Enjoy your build!**

## Patreon
Donations coming out soon! Stay tuned!

## Credits
*   Open-Source Base: [MCA Reborn](https://www.curseforge.com/minecraft/mc-mods/minecraft-comes-alive-reborn)

## Notes
It uses the same library for hairs and clothing as MCA Reborn. Also available on [Modrinth](https://modrinth.com/mod/mca-remastered) (note: it is still waiting to be reviewed). Since updates can be under review, to get them as soon as they're released, get them on [GitHub](https://github.com/AncientKira21/mcarem)!

## Donation Warning
If you try using the old method where you support MCAR's devs just to get a premium tier for AI or get your name mentioned by villagers, it WILL NOT WORK due to unfair rules. Just delete the mod and use MCAR instead if you love those devs too much. MCArem and MCAR are like Apple and Samsung, they share displays, but they're rivals, but instead of displays, they share the AI and library. If you still want to support us, try donating. It keeps things fair while you get me a boba (I'm not a fan of coffee!).  But doesn't mean you can't support them. Support them and me if you want. It's up to you, the choice is yours!

## Compatibilities
MCA is usually compatible with every mod, except when it comes to recognizing items (e.g. gifting).

Following mods have the required resource packs included and are therefore fully compatible:

- Farmer's Delight
- Atmospheric
- Autumity
- Berry Good
- Buzzier Bees
- Environmental
- Neopolitan
- Upgrade Aquatic
- [Player2](https://player2.game/)

## Credits
- [Cleora](https://www.planetminecraft.com/member/cleora/)
- AncientKira
- Luke100000
- WildBamaBoy
- SheWolfDeadly
- ntzrmtthihu777
- ko2fan
- Akjosch
- Innectic
- Sollace
- CDAGaming
- And many more!

Licensed under the [GNU General Public License v3.0](LICENSE).

### Pillagers and marriage proposal mail

Vanilla pillagers are converted to adult `mca:person` villagers with the `mca:pillager` profession. Pillagers remain hostile, use crossbows, attack players, villagers and iron golems, and are opposed by guards and golems. Conversion keeps their name, UUID, health, equipment, passengers and active raid membership. Raid waves and health bars include converted Pillagers; captain banners and ominous bottle drops are retained. The `mca:pillager_spawn_egg` creates an adult person with this profession, including through dispensers and spawners. Disable conversion with `overwriteOriginalPillagers` if desired.

Happy adult people with the required marriage affection can send a named marriage proposal through the existing mail inbox. They respect attraction, family relationships and existing partners. The letter offers clickable **Yes** and **No** responses. Yes marries both parties after checking eligibility again; No dismisses the proposal. Proposals survive saving, expire after seven Minecraft days, and cannot be accepted twice or by another player. Visit an unloaded sender before accepting. Eligible nearby people have a 25% chance per Minecraft day to propose, with a seven-day cooldown after sending; configure `villagerMarriageProposalChance` or disable `enableVillagerMarriageProposals`. The existing mail setting also applies.

### MCA Vindicators, Evokers and Witches

`mca:vindicator`, `mca:evoker` and `mca:witch` are hostile professions on the shared `mca:person` entity, each with its own spawn egg. Vanilla counterparts convert automatically, including during raids, and retain UUIDs, names, health, effects, equipment and raid membership. Individual conversion switches are `overwriteOriginalVindicators`, `overwriteOriginalEvokers` and `overwriteOriginalWitches`.

Vindicators wield iron axes. Evokers use timed fang spells and summon owner-linked, limited-lifetime Vexes, with a limit of six nearby owned Vexes. Witches drink defensive potions, throw offensive splash potions at players or attackers, and support injured members of their own active raid with healing or regeneration. Witches do not initiate attacks on civilian villagers. Spellcasters use distance and spell/potion cooldowns instead of the guard melee routine. Guards, archers and golems recognize all these professions as enemies. Evokers retain totem drops, Witches retain their ingredient loot and magic resistance, and Vindicators retain emerald loot. All four professions remain part of vanilla raid wave counts and health bars; their profession eggs also preserve their jobs in ordinary spawners.

### Raider professions in Peaceful mode

Hostile mobs seek ordinary MCA people and vanilla villagers as attack targets, using their native attack behavior. They ignore MCA Pillagers, Vindicators, Evokers and Witches as attack targets. This also rejects retaliation targets and clears existing mob or brain attack targets. Guards and golems can still oppose hostile raider professions outside Peaceful mode.

In Peaceful mode these people remain in the world and keep their saved professions, but use ordinary unemployed villager routines, seek homes, socialize and stop attacking or casting. Guards and golems no longer treat them as enemies. Difficulty changes clear combat state and refresh their routines once; returning to Easy, Normal or Hard restores their raider behavior. Entering Peaceful mode removes their previous raid membership.

### Raider friendship, families and editor tools

All raider professions appear in family trees, civil records and revival searches like other people; only the Blueprint **Villagers** list hides them. Real family links remain available, and raiders never choose their relatives or partners as attack targets. Unknown players are initially neutral. A positive interaction with an ordinary villager marks the player as a villager friend and makes uncontacted raiders hostile. While a raider's interaction menu is open, all MCA raiders pause their attacks on that player, including Johnny; they remain hostile to other players. Closing the menu ends the pause. A positive interaction befriends only that raider, and a negative interaction makes that raider hostile again even after earlier positive hearts. Attacks can still provoke retaliation. Raiders never convert automatically from friendship. Choose **Redeem from Raider** in Talk to ask them to become an ordinary villager. It uses the adventurer **Ask to stay** acceptance chances, repeat-request memory and village-space checks. Acceptance retains their UUID, family and friendship while ending their raid membership. Raiders stand their ground in combat instead of fleeing, with no panic animation, panic screams or hurt/badly-hurt dialogue. Guards and archers can catch raiders: pursuit limits their speed. Iron golems still damage raiders normally.

Villagers do not lose mood or mourn when a raider dies, but family and spouse records still update. Raiders only grieve deaths of their own relatives or spouse. Raiders remain eligible for revival. The Villager Editor's **Misc** tab includes Pillager, Vindicator, Evoker and Witch professions. Naming a Vindicator **Johnny** through the editor or a name tag makes it attack living non-raiders, including animals, hostile mobs, relatives and Survival players. Vanilla raiders and people with raider professions are spared; Creative/Spectator players, players talking to a raider or befriended by that Vindicator and Peaceful mode remain protected.

Two shapeless recipes unlock when you obtain the Book of Supporters: **Book of Supporters + Staff of Life → Villager Reviver**, and **Book of Supporters + Feather → Villager Editor**.

### MCA wandering traders

Inn traders and former cultist villagers use the `mca:wandering_trader` profession. They remain MCA people with their names, personalities and relationships, and offer normal Minecraft wandering-trader trades. Existing cultist villagers migrate on loading and discard their old cultist trade offers. The Misc editor tab exposes Wandering Trader.

### Cleric rescue

Guards and archers at 25% health or lower call for a cleric. The nearest available healthy cleric within 32 blocks answers, approaches and throws beneficial splash potions: strong healing for critical wounds, regeneration or healing for recovery, and fire resistance when the defender is burning. Throws have a three-second cooldown. At 85% health, once burning ends, the cleric resumes their normal work schedule. Rescue assignments also end if the patient dies, moves out of range, changes jobs, or cannot be reached within 30 seconds.
