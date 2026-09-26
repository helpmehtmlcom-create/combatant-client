# Client Commands

Combatant client commands start with `@` and are handled locally instead of being sent to the server. Use `@help` in chat to browse the commands available in the current installation.

| Command | Aliases | Usage | Description | Requirement |
| --- | --- | --- | --- | --- |
| `@addons` | — | `@addons [list\|scan\|enable\|disable] [id]` | Lists, scans, enables, or disables client addons. | — |
| `@bind` | `@binds`, `@keybind` | `@bind <module> <key\|combo\|none>` or `@bind list` | Changes module key binds and lists existing binds. | — |
| `@clearchat` | `@cls`, `@wipe` | `@clearchat` | Clears local client chat history. | — |
| `@config` | `@cfg`, `@settings` | `@config [save\|load\|path]` | Saves, reloads, or locates the active client config. | — |
| `@enemy` | `@enemies`, `@e` | `@enemy [list\|add\|remove\|toggle\|clear] [player]` | Manages the client enemy list. | — |
| `@friend` | `@friends`, `@f` | `@friend [list\|add\|remove\|toggle\|clear] [player]` | Manages the client friend list. | — |
| `@hclip` | `@hc` | `@hclip <blocks>` | Teleports you horizontally forward or backward. | — |
| `@help` | `@commands`, `@cmds` | `@help [page\|command]` | Shows every available client command with clickable pages. | — |
| `@hide` | `@modulevisibility`, `@showmodule` | `@hide <module> [on\|off\|toggle]` | Hides or shows a module in ModuleList. | — |
| `@iris` | `@shaderpack` | `@iris` | Shows Iris and shader compatibility diagnostics. | Iris |
| `@modules` | `@modulelist`, `@mods` | `@modules [all\|enabled\|combat\|movement\|player\|visuals\|misc] [page]` | Lists modules by state or category. | — |
| `@ping` | `@latency` | `@ping` | Shows the current server latency. | Multiplayer server |
| `@runtime` | `@panic`, `@jarreplace` | `@runtime [status\|panic\|resume\|jar\|source]` | Shows or changes the client runtime and panic state. | — |
| `@rawchat` | `@raw`, `@sendchat` | `@rawchat <message>` | Sends a raw chat message to the server without prefix interception. | Multiplayer server |
| `@toggle` | `@t` | `@toggle <module> [on\|off\|toggle]` | Enables, disables, or toggles a module. | — |
| `@tps` | `@servertps` | `@tps` | Shows estimated server ticks per second. | Multiplayer server |
| `@username` | `@name`, `@ign`, `@whoami` | `@username` | Shows the current Minecraft username and UUID. | — |
| `@vclip` | `@vc`, `@clip` | `@vclip <blocks>` | Teleports you vertically up or down. | — |
| `@xaero` | `@xmark`, `@xwp`, `@waypoint`, `@marker` | `@xaero here [name...]`; `@xaero [add] <x\|~> <y\|~> <z\|~> [name...]`; `@xaero [del] <name>` | Adds, lists, or deletes Xaero's waypoints. | Xaero's Minimap or World Map |
Command and argument suggestions appear in chat while typing. Commands supplied by addons may extend this list.
