# Kitagawa Codex Addons

### Hollows landmarks, stash, and testers

- Use `/kca map set mines_of_divan 633 82 412` to set a landmark for this lobby. Press Tab after `/kca map set ` to see all supported names. A manual pin takes priority over chat and Skyblocker discoveries.
- Use `/kca map share` to send all known Hollows landmarks to party chat with their original X, Y, and Z. Party members can read the message with any client. KCA clients also pick up the named coordinates.
- `/kca stash` shows the latest item and material counts reported by SkyBlock. Enable or disable its HUD under Nucleus → HUD & alerts. With your inventory open, click **[Edit stash]** below its count. Drag the text to move it, scroll over it to resize, and use the two controls to choose which counts and areas to display. Click **[Done]** to finish. A question mark means the current count is unknown.
- Under Nucleus → HUD & alerts, enable Low tools alert and Mole check on tool reminder to see your pet status when restocking. Enable SkyBlock’s Pet tab widget to identify your active pet before a summon or swap.
- To award a pink **BETA** badge, edit `src/main/resources/assets/kca/beta-testers.txt` before building. Enter one Minecraft Java username or hyphenated UUID per line. Save the file, run `.\build.ps1` in the project folder, and distribute the updated JAR to viewers. General → Appearance → Beta badges controls visibility on each viewer’s client.

KCA is a client-side Fabric mod for Minecraft 26.2. Open its skill tree with `/kca` or the configure button in Mod Menu. Select Nucleus, General, Dungeons, Chat (fourth branch) or Kuudra. Type a setting in the search bar, or search from chat with `/kca secrets color`. Select a result to open its page. General has two home-screen shortcuts, Achievements and Move all HUD. Select the CN Gambling leaf on the Nucleus branch to open its animation settings directly.

## Party reports and Nucleus settings in 1.11.0

- `!ping` measures one round trip to the connected Minecraft server. `!pb` or `!cnpb` shares the Nucleus PB. IQ's Kuudra record is available through `!kpb` or `!kuudrapb`. `!lowest` shares the last completed dungeon's lowest non-Mage counts.
- Open **Dungeons → Secrets and rooms → Secrets message → Fewest** to customize the summary and enable either kick prompt. Among known non-Mages, KCA compares 65% of each player's share of secrets and 35% of their share of rooms. An incomplete result or tied lowest score breaks the streak. A separate prompt tracks four consecutive blood-door times over 25 seconds with the same sole Mage. Mort's map line starts the clock. The door announcement gives the team's time. Clicking the local prompt sends a party kick. With General's test commands enabled, `/kca test lowest` shows a local sample.
- `/kca nucleus pb`, `times`, `runs`, `count`, `lobbyStats`, `safe`, `syncRuns`, `skip` and `stop` are local commands. Explicit requests still show feedback with Quiet chat on.
- Under **Nucleus → Speedruns**, select checkpoints for the next run. With Place off the final enabled checkpoint finishes it. With all checkpoints off, placing five crystals finishes it. Use the Skip split button, `/kca nucleus skip`, or bind **Skip Nucleus split** in Controls when an objective sticks. The timer keeps elapsed time and removes the skipped checkpoint from that run. Run counter and Crystal checklist can be toggled separately.
- **CN Gambling → Drops** controls frequency and the game per drop. **Animate every bundle** chooses one featured reward per completed bundle, including a generic bundle reveal when there is no featured item. Choose **Random vault** for Crystal or Trial, or **Random game** for all games. Auto ending follows the actual selection. **Style** has separate Wheel and Vaults tabs. Timing continues to control speed.

## Install

1. Close Minecraft and put `KCA-1.13.12-mc26.2.jar` in your active 26.2 Fabric mods folder.
2. Replace your previous KCA JAR. Move any standalone Purple Pad Timer or Mado Nucleus Utilities JAR out of the active folder. KCA uses the existing Nucleus, dungeon and timer settings. Other Noamm features still require Noamm. If you keep Noamm and see two dungeon reports, switch off one chat printer.
3. Use Java 25+, Fabric Loader 0.19.5+, and Fabric API 0.161.0+ for 26.2. Mod Menu is optional.

## Party chat commands

- `!p <full name>`, `!inv` and `!invite` invite a named player. `!k <prefix>` kicks one unambiguous party member.
- `!reinv`, `!reinvite` and `!rpt` share one KCA switch. A teammate running KCA remembers the requester for 60 seconds and invites them five seconds after their confirmed departure. `!kpt` separately invites the last confirmed kick.
- `!holding`, `!show` and `!showitem` post the held item’s name to party chat. For Hypixel’s clickable item link, use its separate `/show hand` command.
- In **Dungeons → Secrets and rooms**, **Fewest** prints the lowest non-Mage rooms and secrets, including ties. LOCAL is the default for new settings. Select PARTY to share the summary.

Open the fourth branch or use `/kca chat`. Switch off the other mods’ incoming party-command listeners and enable **Party replies** in KCA. The listener starts off on installation. Anyone in the party can send a trigger, including players using vanilla or a different mod. Party management actions use your account’s normal server permissions.

- **Commands** lets you search by alias or provider. Select a command to see its usage, provider, aliases and switch. In party chat, `!help 2` lists another page and `!help chim` explains a command.
- **Routing** accepts comma-separated trigger prefixes and incoming channel labels. Keep `!` for other mods’ usual spelling. KCA sends party replies with `/pchat` by default, even with a Skyblocker `/pc` shortcut. It reads incoming Hypixel party messages before display formatting.
- `!k <prefix>` and `!pt <prefix>` check the current Hypixel party list on demand. An exact name wins, and a unique case-insensitive prefix resolves to the full username. Multiple matches ask for more letters. Use `!ptme` by itself to transfer to the sender. Bare `!pt` shows usage.
- After a confirmed kick, any remaining party member can type `!warp` and then `!kpt` (also `!invlast`) to invite the last kicked player back. KCA listens for the server removal message even for manual kicks. The invite goes through the responding client’s normal party permissions.
- Coordinates, party management and floor/tier queues work directly. Source-backed commands such as `!chims` read the responding player’s installed mod. Keep trackers running and individual Odin/Noamm features enabled if you use those handlers. A missing provider produces a private notice. In Routing, `!profit` can use IQ (Kuudra) or SBO (Diana). Auto prefers IQ. `!profits` explicitly uses SBO.
- `!all inv` and `!allinv` toggle All Invite. The optional 60-second reset starts when Hypixel confirms your client enabled the setting. A subsequent state change or disconnect cancels it.

### Shortcuts and local testing

Open the editor directly with `/kca shortcuts`. Skysoft inventory buttons also use these mappings. A button configured with `ep` runs the same shortcut as typing `/ep`. Leave **KCA shortcuts** and the shortcut groups you use on. Your party reply switch is independent.

| Shortcut group | When it runs | Example |
| --- | --- | --- |
| Commands | The whole command matches what you typed. | `/pc` switches to party chat. |
| Command arguments | A matching word expands and the rest of your command stays in place. | `/m Alex hi` becomes `/msg Alex hi`. |
| Key bindings | A key or combination is pressed during gameplay. | Choose New shortcut, record the keys, and enter a command. |

KCA saves to `config/kca/shortcuts.json` using Skyblocker’s `commands`, `commandArgs` and optional `keyBindings` objects. First launch reads the profile’s Skyblocker file if present. **Import Skyblocker** adds missing mappings and preserves existing KCA edits. The Void warp shortcut is `/void`. The `/calc` shortcut still opens Skyblocker’s calculator. Turning off KCA shortcuts hands control back to Skyblocker if enabled.

Enable **General → Test commands**, then open **Chat → Tester**. Try a party trigger, a full incoming line, or a slash shortcut. Simple replies and supported tracker values use local data. Actions such as `!warp` and `/pd` show the proposed command. Commands delegated to an external action handler identify its provider. Testing works while the live party listener is switched off.

## Nucleus

Select **Spreadsheet** at the bottom of a Nucleus settings page to open the linked Google Sheet.

Open Nucleus from the Divan's Alloy branch or with `/kca nucleus`. The Nucleus sections share one settings window. Select a sidebar entry, or use the arrows below the list on shorter windows. Nucleus features are compiled into KCA using the 26.1 source for version 2.0.0. History, config paths and keybind IDs carry over.

| Section | Features |
| --- | --- |
| Tool Finder | Metal Detector chest locator, detector sounds, scavenged tool and set rates. |
| HUD & alerts · NPC highlights | Marks King Yolkar, Professor Robot, the four Keepers and Door Guardians when spotted. Clear markers forgets their recorded locations. |
| HUD & alerts · Alerts | Wrong-pet, low-tool and bad-lobby alerts. Bal timer and spawn status, Bal pet drop animation, lobby day, and Terror armor stack display. |
| Customization | Menu themes, themed wheel effects and seasonal effects. |
| Waypoints · Jungle Temple | Automatic Door Guardian or manual placement, waypoint colors, connecting lines, through-wall rendering and range dimming. |
| Waypoints · Custom waypoints | Place and manage your own per-lobby waypoints. |
| Gamble | Bundle wheel, preview drop, speeds and odds. Use Random to choose eligible games. Enable gameplay HUD and gambling animations to see previews. |
| Sounds | Mixer, objective sound, custom files, individual trigger switches and wheel sound packs on one page. |
| Speedruns | Safe warp mode, automatic activation after a configurable number of runs, Nucleus timer, crystal splits, run history, personal bests and lobby run summaries. |

**Start distance** under **Speedruns** adds 0–64 blocks beyond the original Nucleus start area. Enter the start area first, then leave by more than this distance to trigger the timer. Zero retains the original timing.

## Kuudra

Select the Kuudra branch or use `/kca kuudra` to open the supplied Kitagawa illustration. Click the illustration to see its creator credit.

## Dungeons

- **Purple Pad Timer** retains the F7/M7 Storm countdown, Simple Mode, preview, and drag-to-move and resize editor. Use `/purplepad help` for commands.
- Open **Dungeons → Secrets and rooms**. **Hide Blade split recap** silences Blade Addons’ final splits chat summary. It starts off. **Display** shows the party during a run. **GUI settings → Simple** shows only your secret and room counts on one line. Select **Move / resize**, drag the preview, and use the scroll wheel or the + and − buttons to resize. Save or press Enter to apply. Choose **Secrets color** and **Rooms color** independently. Escape cancels. The preview works outside dungeons. **Chat print** cycles through LOCAL, PARTY and OFF. LOCAL prints one [KCA] row per tracked player even when Quiet chat is on. PARTY shares the rows at a limited rate. Leaving the run shows pending rows locally. **Lowest** cycles through OFF, LOCAL and PARTY. It reports the fewest total credited rooms (solo plus shared) and fewest run secrets separately, excludes every Mage and lists ties. Missing classes or values make the result partial. When Noamm supplies its end-of-run rows, KCA uses those counts and shows one copy while Chat print is on. The toggles work independently, and both start enabled.
- **Secrets message** changes the wording on the counter and in the final chat report. For example, enter `{name} found {secrets} secrets in {rooms} rooms`. The editable default is `{name} | Secrets: {secrets} | Rooms: {rooms} | Deaths: {deaths}`. You can also use `{solo}`, `{shared}` and `{deaths}`. Check the preview, then select **Save**. **Default** followed by **Save** restores the original wording.
- **General → Achievements** includes three single-run dungeon milestones for your own totals. Find 10 secrets, clear 5 rooms solo, or share 3 room clears in one completed run.
- A line like `Archer: 3-5 Rooms | 9 Secrets` means three rooms cleared alone and two more with another player present. Rooms are credited from the first observed map checkmark and player positions. Clears missed by your client can be absent from the report.
- KCA reads `/secretcount` during the countdown, at the start, at boss entry and on the score screen. If the party disbands or warps before the final response, the recap can use the boss-entry reading. The live display refreshes about every 30 seconds during the clear. Rooms update approximately every quarter second. A question mark means that KCA lacks a matching pair of samples.
- For teammate totals unavailable from the game command, KCA can query the official Hypixel player API when the launcher inherits a personal key in `KCA_HYPIXEL_API_KEY`. Set it in your environment before starting OneClient. The key goes to api.hypixel.net. Cached API totals can lag behind the run. Teammate coverage and end results can differ from Noamm's service.

## Achievement milestones

Open **General → Achievements**. Cycle **All achievements** to filter by Dungeons, Nucleus or General. Dungeon achievements show their goals and are grouped by runs, secrets, rooms, floors, Master Mode and Storm. Other branches also support Hidden and Hinted filters. Hidden cards conceal their title and goal until earned or revealed. One hint refreshes daily at 00:00 UTC. The header shows when the next hint is available. Version 1.5.6 adds 42 milestones.

| Goal | Thresholds |
| --- | --- |
| Finish a tracked dungeon run on each floor | F1–F7 and M1–M7, one achievement per floor |
| Total tracked dungeon runs | 5, 25, 100, 250, 500 |
| Total confirmed personal dungeon secrets | 25, 100, 500, 1,000, 5,000 |
| Total solo room clears | 10, 50, 150, 500, 1,000 |
| Total shared room clears | 10, 50, 150, 500, 1,000 |
| Crystal Nucleus runs | 5, 25, 100, 250 |
| Crystal Nucleus best time | 8:00, 4:00, 3:00, 1:45 or faster |

New dungeon totals begin with the first tracked run in this version. Enable Display or Chat print in Secrets and rooms for tracking. Floor credit uses the floor named in the end-of-run report. Room credit uses observed map clears. Secret credit requires a confirmed personal start and end count. KCA checks existing saved Nucleus totals and best times on startup.

## GUI settings

General uses black panels with sakura-pink accents. Dungeons and Nucleus use their selected accent over cool blue or warm brown shadows respectively. Change either accent in General. With Menu FX off, each branch keeps a static tinted background.

Open **General → GUI settings** or the footer shortcut on a HUD feature page. Choose **Purple Pad Timer**, **Secrets and rooms**, or **Nucleus text HUDs**, then select a style. **Simple** draws text only, **KCA** uses an adjustable dark backdrop, and **Purple Pad Fancy** uses a colored pixel frame. The preview updates immediately. The Nucleus choice applies to speedruns, alerts, scavenger tracking, lobby day, Bal and Terror stacks. Special effects such as the gambling wheel retain their own visuals. The original timer settings window applies its timer draft when you press **Save**.

## Global settings and commands

- **General** contains the KCA master switch, quiet chat, test commands, gameplay HUD visibility, menu effects and the two branch palettes. **Achievements** tracks Nucleus milestones, dungeon runs, solo rooms, secrets, the Storm timer and HUD placement. Dungeons also has a countdown color selector. The master switch leaves settings accessible.
- **/kca <setting name>** searches from chat. **/kca settings**, **/kca nucleus** and **/kca dungeons** open their respective pages.
- **/kca hud** opens the shared HUD editor. All nine movable overlays appear together, including disabled ones. Click a preview to select it and reveal its switches in the footer. Drag the selected overlay to move it, or scroll over an overlay to resize it. The arrows let you select overlapping previews. Alerts has separate switches for wrong pet, low tools and bad lobby. Bal has separate timer and detector switches.
- **/kca reload** rereads saved timer, counter and Nucleus settings and the Nucleus run store.
- **/kca test timer**, **/kca test secrets**, and **/kca test report** preview the countdown, open the counter preview, or print sample output locally. Enable **Test commands** first. The timer preview also needs the timer enabled.

## Command reference

| Command | Action |
| --- | --- |
| `/kca`, `/kca help` | Open the skill tree or print command help. |
| `/kca settings`, `/kca dungeons`, `/kca nucleus` | Open General, Dungeons or Nucleus. |
| `/kca search`, `/kca search <words>`, `/kca <setting name>` | Open global search or search for a setting. |
| `/kca hud`, `/kca reload` | Move and resize overlays or reload saved KCA settings and Nucleus run data. |
| `/kca waypoint`, `/kca waypoint mode` | Open Jungle Temple settings at Waypoint mode. |
| `/kca waypoint rightguardian` | Place the three right-guardian offsets once from your feet inside the Jungle Temple. Stand on the higher-X guardian's block. |
| `/kca set waypoint` | Place the three offsets from your feet using the preset selected in settings. Both placement shortcuts work in either mode and preserve the saved preset. |
| `/kca nucleus times`, `/kca nucleus runs` | Print Nucleus run history. |
| `/kca nucleus stop`, `/kca nucleus lobbyStats` | Reset the current speedrun or print lobby totals. |
| `/kca nucleus safe`, `/kca nucleus syncRuns` | Toggle safe warp mode or sync the official run count from the Tiered Achievements menu. |
| `/kca disableTabwarn` | Dismiss future tab warnings. |
| `/purplepad`, `/purplepad gui`, `/purplepad help` | Open timer settings or print timer help. |
| `/purplepad on`, `/purplepad off` | Enable or disable the timer. |
| `/purplepad simple on`, `/purplepad simple off` | Select Simple or Purple Pad Fancy. |
| `/purplepad position <x> <y>` | Set GUI pixel coordinates. X ranges from −1 to 10000 and Y from 0 to 10000. Use −1 for horizontal centering. |
| `/purplepad scale <value>`, `/purplepad reload` | Set timer scale from 0.5 to 6 or reload its saved configuration. |

In `/kca settings`, turn on **KCA** and **Test commands**. The switch takes effect in the current world. Type `/kca test` for the list. Explicit test replies appear locally with **Quiet chat** enabled.

| Test command | Action |
| --- | --- |
| `/kca test timer`, `/purplepad preview` | Preview the countdown for 2.8 seconds. The timer and Game HUD must be enabled to see it. |
| `/kca test secrets`, `/kca test report` | Open the sample counter editor or print sample results in local chat. |
| `/kca test debug` | Print detected area, waypoints, timers, pet and tracker state. |
| `/kca test unlock` | Unlock all Nucleus themes for this session. |
| `/kca test season <season>` | Preview a seasonal theme. Choices are `none`, `winter`, `christmas`, `new_year`, `valentine`, `st_patrick`, `easter`, `anniversary` and `halloween`. Use `auto` to follow the calendar again. |

## Jungle Temple offsets

Open **Nucleus → Waypoints → Jungle Temple** or search for **guardian**. **Waypoint mode** selects automatic detection of the higher-X Door Guardian or manual placement from your current block. In Manual mode, stand at the intended origin inside the temple and choose **Set now** or use the capture hotkey. **Offsets** chooses the original first point (+3,+8,+63) or the reference first point (+4,+10,+65). Both presets use (+29,−32,+65) and (+29,−32,+48) for points two and three. Switching presets clears the old temple markers.

## Artwork

Each fresh home-screen opening selects one of the three home artworks, including the Kamen Rider portrait. Each appears once per shuffled rotation, with no consecutive repeats. Selecting the portrait opens the complete image in Credits with its dimensions and available attribution. The Kamen Rider and glasses portraits use the artists’ original 2000 × 2925 and 1545 × 1545 images. Resizing the menu or returning from a branch keeps that selection. The Kuudra artwork stays on its rose-red branch. Your earlier Marin portrait is the mod icon. The main menu displays large sakura-pink KCA initials in the Minecraft font. To use your own logo, add `assets/kca/textures/logo.png` to an enabled resource pack. A transparent 4:1 image, such as 160 by 40 pixels, fits the reserved space. The menu checks for it when it opens. The General branch uses Precursor Gear from the Hypixel SkyBlock pack. Nucleus uses the pack's Divan's Alloy and Dungeons uses the looshyblock BETA v0.5 Hyperion.

## Performance

Tool Finder searches for the Jade anchor across multiple client ticks. Each slice visits at most 12 chunk sections and checks up to 1,024 cells or barrier-walk steps. Nearby chest searches skip sections without chest blocks. Bal discovery polls every five ticks while absent and follows the loaded body every tick. The scavenger HUD refreshes its text four times per second. Dungeon map calibration retries on changed map data, and the achievement list reuses its layout between updates.

## Build and verification

Unzip the source and run `pwsh -File ./build.ps1` with a JDK 25 toolchain on PATH. The script uses the installed OneClient libraries and Mod Menu compile-time API. Use `-OneClientRoot`, `-MinecraftJar`, `-FabricApiJar` or `-ModMenuJar` to override paths. It compiles, runs timer, HUD geometry and dungeon report tests, and packages the JAR in the build directory.

The dungeon tests cover solo and shared room credit, repeated checkmarks, previously cleared maps, and missing or reset secret counters. The menu, global search, master switch, test-command switch, counter scale persistence, and shared HUD editor were also checked in an isolated Minecraft 26.2 client. Live Hypixel parity and FPS gains have not been measured.

General offers Full, Subtle and Off for Menu FX. The General achievements include using global search, changing Menu FX, opening Credits and editing the HUD. In Nucleus, open HUD & alerts for the Alerts and NPC highlights subtabs.

In **Nucleus → Speedruns**, Personal best shows your fastest saved time and its cumulative splits. Run history marks matching times with a pink trophy. In **Nucleus → CN Gambling → Timing**, switch off Opening sequence to spin immediately. With the opening enabled, Power and luck rolls controls just the number reveals. Gold pointer mount restores the large disc and Wheel sparkles controls the glints on rare slices.

### Nucleus vault and dungeon milestones

Under Nucleus → CN Gambling, select **Animation – Trial vault** and choose **Test animation** to preview five crystal locks opening onto your reward. Wheel and Trial vault share the final rare-drop celebration. The wheel uses a small rose peg by default. Enable **Original wheel arrow** for the orange flapper.

General → Achievements → Dungeons includes personal total-secret milestones up to 100,000 and run-time targets for F1–F7 and M1–M7. KCA reads the time from the server’s boss defeat line on tracked runs. The Dungeons report and HUD can use their existing settings, and no splits mod is required.

The Gambling animation volume slider is available in both Animation and Sounds. Set it to 0% to mute previews and real reveals, or 100% for normal volume. Final reveal volume controls the ending as a percentage of that level and defaults to 25%. CN Gambling offers Wheel, Trial vault, Crystal vault and Briefcase slots. Ending – Auto pairs each game with its own reveal. You can also choose any ending independently. Crystal vault has five evenly spaced sockets around the arch. Topaz turns in the crown. Crystals fly in from beyond the screen with colored trails, turn upright in the stone, and light the large crystal clusters on the arch. Topaz dives into a loop before rising to the crown. The pink Trial vault loops every key, opens its top shutter, and ejects up to two other recognized bundle drops and then the selected reward before the finale. Test animation uses example loot for the first pops. Style → Vaults includes separate controls for Vault key loops, Vault light trails, and Vault lock sounds. The gate slides open on a chamber filled with gold and ore. The Divan’s Fragment, Jaderald, Gemstone Mixture, Flawless Ruby and Precious Pearl on the blocks and cart use the original Hypixel server-pack textures. The fragment uses the pack’s animation frames. Minecraft provides the lantern, pickaxe and block textures. Briefcase slots uses six independent item reels, three on either side of a brass lock. After the sixth match the latch clicks, the lid folds back over the black poker mat, and the reward lifts out of the tray. Pair either game with its Auto ending to see the full sequence. The final reveal uses the original pink-gold Divan's Alloy. **Cool trial vault accents** changes the Trial vault trim from KCA pink to lavender and ice blue. Timing groups Opening speed, Game speed, Final reveal speed and the quick-spin hotkey. Press the bound key during the game to advance to the last second of its motion. The vault key sequences take 4.2 seconds and the briefcase takes three seconds at normal speed, with a themed finale of up to 4.8 seconds. Opening sequence can be disabled. Titles lets you edit the roll verdicts and win banners. The former Legendary Win tier defaults to RARE DROP!. Guide covers the reveal and how to add a game or ending in the source.

### Extending CN Gambling in the source

1. Start in `src/main/java/com/nucleus/client/JackpotDetector.java`. Its real-drop path selects the `DropType` from received loot. The preview picker enters the same animation through `JackpotAnimation.startPreview`.
2. Add a game to `NucleusConfig.Game`. In `JackpotAnimation.start`, choose the duration and any per-run random values once. Render the game in the `Stage.WHEEL` switch in `JackpotHud.draw`. `BriefcaseHud` shows how the progress value drives the reels at any frame rate. Place sound cues in the tick path and call `playJackpot` once when each cue is crossed.
3. Add a finale to `NucleusConfig.Ending` and supply its Auto pairing in `JackpotAnimation.start`. Draw the surroundings in `JackpotHud.drawMaxWin`. The reward comes from `fireTotem`, the coin value from `WinRollup`, and the title from `NucleusConfig.gambleTitle`. `VaultHud.eject` draws the top shutter and loot. The themed Alloy burst is in `VaultHud.alloyBurst`.
4. Build with `build.ps1` and Java 25. In a test profile, select a fixed Preview drop and try the Classic, Trial vault, Crystal vault and Briefcase slots endings. Check a low-value reward, Alloy and Jade Dye, then repeat with Opening sequence off. Test both with your resource pack and with Minecraft's base textures.

## Export selected branches

Double-click **Export KCA.cmd** in the project folder. Check Nucleus, General, Dungeons, Chat commands, or Kuudra. General includes its settings, appearance, achievements, and item rarity together.

Choose **Export together** for one installable jar with the selected branches. Choose **Export separately** for one standalone jar per selected branch. Every edition uses the same KCA mod ID. Install one edition per Minecraft instance. To give someone two branches together, send a combined export.

In PowerShell, `./build.ps1 -Branches Nucleus,General` creates that combination under `build`. You can also supply `-Destination` with a full jar path. Running `./build.ps1` produces the full edition.
