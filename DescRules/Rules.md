# Rules
## Commands
### commandAutosave
- **Description:** Enables `/autosave` command to query information about the autosave and execute commands relative to the autosave.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandBlockInfo
- **Description:** Enables `/blockinfo` command, also enables gray carpet placement action if 'carpets' rule is turned on as well.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandCameramode
- **Description:** Enables `/c` and `/s` commands to quickly switch between spectator and server modes, `/c` and `/s` commands are available to all players regardless of their permission levels.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandChunk
- **Description:** Enables `/chunk` command, chunk info command.
- **Type:** `boolean`
- **Default value:** `false`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandCluster
- **Description:** Enables `/cluster` command.
- **Type:** `boolean`
- **Default value:** `false`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS, VASTECH

### commandDistance
- **Description:** Enables `/distance` command to measure in game distance between points, also enables brown carpet placement action if 'carpets' rule is turned on as well.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandEntityInfo
- **Description:** Enables `/entityinfo` command, also enables yellow carpet placement action if 'carpets' rule is turned on as well.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### CommandEntityTask
- **Description:** Enables `/entityTask` command.
- **Type:** `boolean`
- **Default value:** `false`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS, VASTECH

### commandFill13
- **Description:** Enables `/fill13` command, which is a `/fill` with grammar of 1.13.
- **Type:** `boolean`
- **Default value:** `false`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS, VASTECH

### commandFillBiome
- **Description:** Enables `/fillbiome` command to change the biome of an area.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandGrow
- **Description:** Enables `/grow` command for growing plants.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandLagspike
- **Description:** Enables `/lagspike` for manually induced lag spikes.
- **Type:** `boolean`
- **Default value:** `false`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandLazyChunkBehavior
- **Description:** Enables `/lazychunkbehavior` command.
- **Type:** `boolean`
- **Default value:** `false`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandLight
- **Description:** Enables `/light` for changing light levels.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandLoadedChunks
- **Description:** Enables `/loadedChunks` command, get information of the loaded chunks hashmap.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandLog
- **Description:** Enables `/log` command to monitor events in the game via chat and overlays.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandPerimeterInfo
- **Description:** Enables `/perimeterinfo` command that scans the area around the block for potential spawnable spots.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandPing
- **Description:** Enables /ping for players to get their ping.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandPlayer
- **Description:** Enables `/player` command to control/spawn players.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandProfile
- **Description:** Enables `/profile` command to profile lag.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandPublicScoreboard
- **Description:** Allows the use of scoreboardPublic, a lower permition level score display.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandRepopulate
- **Description:** Enables `/repopulate` command to repopulate given chunk.
- **Type:** `boolean`
- **Default value:** `false`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandRNG
- **Description:** Enables `/rng` command to manipulate and query rng.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandSetblock13
- **Description:** Enables `/setblock13` command, which is a /setblock with grammar of 1.13.
- **Type:** `boolean`
- **Default value:** `false`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS, VASTECH

### commandSpawn
- **Description:** Enables `/spawn` command for spawn tracking.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandStructure
- **Description:** Enables `/structure` to manage NBT structures used by structure blocks.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandTick
- **Description:** Enables `/tick` command to control game speed.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandUnload
- **Description:** Enables `/unload` command to inspect chunk unloading order.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

### commandWaypoint
- **Description:** Enables `/waypoint` for saving coordinates.
- **Type:** `boolean`
- **Default value:** `true`
- **Allowed options:** `true`, `false`
- **Categories:** COMMANDS

## Ints

### blockEventRange
- **Description:** Changes the range for block events to be sent to clients to this value. May be useful when testing flying machines or tree farms.
- **Type:** `int`
- **Default value:** `64`
- **Suggested options:** `64`
- **Categories:** OPTIMIZATIONS

### combinePotionDuration
- **Description:** Combines the duration of potions when drinking out of a bottle. The combined duration capped by this carpet rule in gameticks.
- **Type:** `int`
- **Default value:** `0`
- **Suggested options:** `0`, `18000`, `36000`, `72000`
- **Categories:** FEATURE

### fillLimit
- **Description:** Customizable fill/clone volume limit.
- **Type:** `int`
- **Default value:** `32768`
- **Creative default value:** `500000`
- **Suggested options:** `32768`, `250000`, `1000000`
- **Categories:** CREATIVE

### limitITTupdates
- **Description:** A limiter for updates happening on the main thread to prevent crashes on instant tile tick.
- **Type:** `int`
- **Default value:** `0`
- **Suggested options:** `0`, `1000000`, `10000000`
- **Categories:** FIX

### maxEntityCollisions
- **Description:** Customizable maximal entity collision limits, `0` for no limits.
- **Type:** `int`
- **Default value:** `0`
- **Suggested options:** `0`, `10`, `20`
- **Categories:** 

### pushLimit
- **Description:** Customize Toolbar....
- **Type:** `int`
- **Default value:** `12`
- **Suggested options:** `10`, `12`, `14`, `100`
- **Categories:** CREATIVE

### railPowerLimit
- **Description:** Customizable powered rail power range.
- **Type:** `int`
- **Default value:** `9`
- **Suggested options:** `9`, `15`, `30`
- **Categories:** CREATIVE

### scoreboardDelta
- **Description:** Scoreboard displays changes over time, specified in seconds, Set to `0` to disable Scoreboard delta display.
- **Type:** `int`
- **Default value:** `0`
- **Suggested options:** `0`, `60`, `600`, `3600`
- **Categories:** EXPERIMENTAL

### sleepingPercentage
- **Description:** The percentage of required sleeping players to skip the night, use values from 0 to 100, 100 for default (all players needed.
- **Type:** `int`
- **Default value:** `100`
- **Suggested options:** `0`, `10`, `50`, `100`
- **Categories:** EXPERIMENTAL

### structureBlockLimit
- **Description:** Changes the structure block dimension limit.
- **Type:** `int`
- **Default value:** `32`
- **Suggested options:** `32`, `50`, `200`, `1000`
- **Categories:** CREATIVE

### tileTickLimit
- **Description:** Customizable tile tick limit, `-1` for no limit.
- **Type:** `int`
- **Default value:** `65536`
- **Suggested options:** `1000`, `65536`, `1000000`
- **Categories:** SURVIVAL

### tntFuseLength
- **Description:** Changes default tnt fuse.
- **Type:** `int`
- **Default value:** `80`
- **Suggested options:** `70`, `80`, `100`
- **Categories:** CREATIVE

### viewDistance
- **Description:** Changes the view distance of the server, Set to `0` to not override the value in server settings.
- **Type:** `int`
- **Default value:** `0`
- **Suggested options:** `0`, `12`, `16`, `32`, `64`
- **Categories:** 

## Decimals

### hardcodeTNTangle
- **Description:** Sets the horizontal random angle on TNT for debugging of TNT contraptions, set to `-1` for default behaviour.
- **Type:** `double`
- **Default value:** `-1`
- **Suggested options:** `-1`
- **Categories:** TNT

### tntRandomRange
- **Description:** Sets the tnt random explosion range to a fixed value, set to `-1` for default behaviour.
- **Type:** `double`
- **Default value:** `-1`
- **Suggested options:** `-1`
- **Categories:** TNT

## Strings & Enums

### 
- **Description:** .
- **Type:** `double`
- **Default value:** ``
- **Suggested options:** ``
- **Categories:** 

### 
- **Description:** .
- **Type:** `double`
- **Default value:** ``
- **Suggested options:** ``
- **Categories:** 
