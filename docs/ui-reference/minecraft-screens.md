# Minecraft Java Edition - screen reference

Source: Perplexity deep research (Oct 2026), citing minecraft.wiki (Menu_screen/Java_Edition, Options, Font,
Select_World, World_Options, Game_rule), minecraft.net 1.20.2/1.20.5/1.21/1.21.11/26.3 release notes and
decompiled 1.8.9 sources (GuiButton, GuiIngameMenu, ScaledResolution). Layouts below are the 1.8-era classic
values this project follows; later versions differ (noted).

## GUI scaling (ScaledResolution)
- Auto: largest integer scale s >= 1 with floor(W/s) >= 320 and floor(H/s) >= 240; manual choice caps it.
- Logical size = ceil(W/s) x ceil(H/s). Everything (buttons, text, spacing) is laid out in these logical pixels.

## Widgets
- Button 200x20 (wide), 150x20 / 98x20 (half), 20x20 (icon). Horizontal gap 4, vertical row pitch 24.
- Button text colors: normal #E0E0E0, disabled #A0A0A0, hovered #FFFFA0, drawn centered with shadow.
- Slider: same 200x20 button with a 8x20 knob; label "Name: value".
- Text field: 200x20, 1px border, black fill, white text, blinking cursor.
- Background: tiled dirt (options_background, darkened 0x40 tint) in 1.8; dimmed blurred panorama in 1.20.5+.
  In-game menus: translucent dark gradient (#C0101010 -> #D0101010).

## Font
- ascii.png: 128x128, 16x16 grid of 8x8 cells, variable advance (rightmost lit column + 1, space = 4), line height 9.
- Shadow: +1 logical pixel right/down, color = text color * 0.25 (white -> #3F3F3F).
- Title/splash yellow #FFFF00; default text white #FFFFFF; hint gray #A0A0A0; label dark #404040 on panels.

## Screens (title -> where Done/Cancel returns)
| Screen | Widgets | Returns |
|---|---|---|
| Title ("Minecraft") | Singleplayer, Multiplayer, Minecraft Realms (200x20 @ 24px pitch); Options... and Quit Game (98x20, 4px gap); language + accessibility icon buttons; logo, yellow splash, panorama | - |
| Select World | search/list of worlds (name, folder, date, mode), Play Selected World, Create New World, Edit, Delete, Re-Create, Cancel | Title |
| Create New World | Game tab: World Name, Game Mode, Difficulty, Allow Cheats; World tab: World Type, Seed, Generate Structures, Bonus Chest; More tab: Data Packs, Game Rules; Create New World, Cancel | Select World |
| Edit World | name field, Reset Icon, Open Folder, Make Backup, Open Backups Folder, Optimize World, Done | Select World |
| Delete World confirm | warning text, Delete, Cancel | Select World |
| Multiplayer | server list, Join Server, Direct Connect, Add Server, Edit, Delete, Refresh, Back | Title |
| Add Server / Direct Connect | Server Name/Address fields, Done/Join, Cancel | Multiplayer |
| Realms | contextual list/invites | Title |
| Options... | FOV slider, Difficulty, Skin Customization, Music & Sounds, Video Settings, Controls, Language, Chat Settings, Resource Packs, Accessibility Settings, Online, Telemetry Data, Credits & Attribution, Done | Title or Pause |
| Video Settings | Graphics, Render Distance, Simulation Distance, Smooth Lighting, Max Framerate, VSync, View Bobbing, GUI Scale, Fullscreen, Brightness, Clouds, Particles, Mipmap Levels, Entity Shadows, Done | Options |
| Controls | Mouse Settings, Key Binds, Auto-Jump, Sneak/Sprint toggles, Done | Options |
| Key Binds | scrollable list of bindings (label + key button), Reset All, Done | Controls |
| Mouse Settings | Sensitivity, Invert Mouse, Scroll Sensitivity, Discrete Scrolling, Done | Controls |
| Music & Sounds | Master Volume + per-category sliders (Music, Jukebox/Note Blocks, Weather, Blocks, Hostile, Friendly, Players, Ambient/Environment, Voice), Subtitles, Done | Options |
| Language | scrollable list, Done | Options |
| Chat / Accessibility / Skin Customization / Online / Telemetry / Credits | toggles + Done | Options |
| Resource Packs | Available/Selected lists, Open Pack Folder, Done | Options |
| Game Menu (pause) | Back to Game, Advancements, Statistics, Options..., Save and Quit to Title (+ Open to LAN pre-26.3); 200x20 rows @ 24px, 98x20 pairs | game |
| Advancements / Statistics | tab canvas / General-Items-Mobs tables, Done | Pause |
| Death ("You died!") | score line, Respawn, Title Screen | game / Title |
| Loading | "Saving level...", "Loading terrain..." status text | automatic |
| Container screens | inventory, crafting table, furnace, chest... slot-grid artwork | game |

## Notes
- 1.20.2+ splits widgets.png into per-sprite files; this project draws widgets procedurally either way.
- Realms, Multiplayer, Telemetry and Resource Packs are not implemented in this clone (no backing feature).
