# Attribution and license

Mado (Madobrick) created the original Nucleus wheel animation and final drop reveal. The original jackpot textures are bundled unchanged. The personal build includes the Hypixel item sprites and animation metadata used by the original drop models, sourced from the installed server pack, for display when the pack is unavailable.

The personal KCA build includes unchanged item sprites from the user's installed Hypixel+ 0.25.0c pack by ic22487. The Crystal vault uses its five crystal sprites. The briefcase, vaults and final reveal use the pack's loot sprites, including its Divan's Alloy ingot. Minecraft supplies the block textures. The KCA pink Trial vault uses palette variants of Minecraft�s vault_front_off, vault_front_on and vault_front_ejecting textures. These assets retain their respective authors' rights.

Purple Pad Timer is a modified, standalone Java adaptation of the PurplePadTimer feature and server-ping hook from Synnerz/devonian 1.34.9. It is not an official Devonian release.

Original authors: Synnerz/devonian contributors.

Upstream repository: https://github.com/Synnerz/devonian

Original source paths (26.1 branch):

- src/main/kotlin/com/github/synnerz/devonian/features/dungeons/f7/PurplePadTimer.kt
- src/main/java/com/github/synnerz/devonian/mixin/ClientCommonPacketListenerImplMixin.java

Changes: isolated the 56-server-tick countdown; replaced Devonian's feature, event, stage and HUD frameworks with a small Fabric initializer, two packet mixins and a timer state machine; added JSON configuration, preview/position/scale commands, phase/world reset handling and behavioral tests. Version 1.1.0 adds a SkyBlock-inspired settings screen with draft edits, a local animated preview, and a help command. Version 1.2.0 adds Simple Mode, an optional pixel-card HUD, a shared HUD renderer, and a drag/resize layout editor with geometry checks. There are no Devonian networking services or other gameplay features.

Distributed under GNU GPL version 3; see LICENSE. Complete corresponding source and build instructions accompany the binary in the source ZIP. Minecraft, Fabric API and launcher libraries are external build/runtime dependencies and are not bundled.

Version 1.2.1 corrects decorated-HUD border scaling, background compositing, text spacing and editor handle size.

Kitagawa Codex Addons 1.3.0 adds a section menu and a client-side dungeon report. The report's room range and secret-count presentation follow the Clear Info feature in the supplied NoammAddons 1.3.2 release. KCA implements the tracker separately.

KCA 1.4.0 incorporates and adapts Madobrick's Nucleus Utilities 2.0.0 source for Minecraft 26.1 (CC0-1.0). Credit and upstream release: https://github.com/Madobrick/Mado-s-Nucleus-Utilities/releases/tag/2.0.0. KCA integrates its feature code, assets and existing data formats, supplies category-specific settings pages, and ships the upstream JLayer audio dependency. The source ZIP contains the adapted source.

The mod portrait is a crop of the Marin Kitagawa image supplied for this personal build. Divan's Alloy uses the Hypixel resource-pack texture. Hyperion uses the supplied profile's looshyblock BETA v0.5 texture. The code license does not grant redistribution rights to third-party artwork.

KCA 1.5.0 adds shared section navigation and HUD placement, search, configurable colors, optional reference offsets, test commands and reduced work during HUD rendering and unchanged map updates.


KCA 1.5.1 adds General, shared achievements and HUD appearance settings, animated menu nodes, softer branch accents and setting-name search from chat.

KCA 1.5.2 adds direct temple waypoint commands, uses Hypixel's original Precursor Gear texture for General, and adds blue and lavender accents to Dungeons.


KCA 1.5.3 consolidates Nucleus settings, replaces the legacy command names, migrates saved appearance preferences, and lets the shared HUD editor select and toggle individual previews.

The Credits screen uses the complete supplied Marin Kitagawa fan art (kita gawa.jpeg), with its original signature intact.

The Kuudra branch displays the complete user-supplied kitagawa.jpg illustration, including its signature.

The home rotation includes the seated Marin portrait, the glasses illustration and the Kamen Rider illustration. Kuudra has a dedicated image.

The approved Kamen Rider illustration is by Piniacotta (@piniacottaalt). Source: https://x.com/piniacottaalt/status/2107317478800122321 . The original image is 2000 × 2925. The Kuudra illustration is by LuziusL474 (DeviantArt work df5qfs2). The school portrait uses Meowfeis’s signed 1545 × 1545 original at https://meowfeis.tumblr.com/post/796641408783187968 . Authorship of the seated portrait has not been confirmed.

The pink personal-best marker uses the Google Material Design Icons emoji_events glyph, tinted for KCA. Apache License 2.0 accompanies the JAR in MATERIAL-ICONS-LICENSE.txt. Source: https://github.com/google/material-design-icons .

The black card-suit table in Briefcase slots uses the supplied CASINO TEXTURE.jpg, converted to PNG for Minecraft.

The item_background_square.png and item_background_circular.png sprites are from Skyblocker 6.11.0 by the Skyblocker contributors (https://github.com/SkyblockerMod/Skyblocker). See LICENSE_skyblocker.

## Gallery additions in 1.13.9

- Pink sunglasses portrait by Midnite Blue Draws. [Artist’s post](https://www.artstation.com/artwork/AlXb2z)
- Park bench illustration signed ARDEN. [Artist’s portfolio](https://ardenlolo.artstation.com/projects/o29zrw)
- Color portrait and companion line drawing by Takko (@_takk00). [Original carousel](https://www.instagram.com/p/DaRPqV8EdaT/)
- White-haired uniform portrait signed @shirrori. [Artist’s profile](https://ko-fi.com/shirrori)

The Jade Dye and Quick Claw reward heads use their original [Jade Dye skin](https://textures.minecraft.net/texture/8b215ff671f111e98d17a473fda8e5e9d0425ccd6e79426654ccb55f3f0cc652) and [Quick Claw skin](https://textures.minecraft.net/texture/6312a5a12ecb24d6852db388e6a34721cc67f522ccde7e824b9f75e95036ac93). The legacy Quick Claw image is padded to fit the player-head model.
