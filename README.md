# Simple HUD Mod

A very small Minecraft Forge mod for **1.20.1** that shows:

- XYZ coordinates
- Current biome
- Facing direction
- Whether hostile mobs can spawn on the block you are currently looking at

## Toggle Key

- Default key: **H**
- Action: Toggle the HUD on/off

## Compatibility

- Minecraft: `1.20.1`
- Forge: `47.4.10+`
- Java: `17`

## How spawnability is checked

The HUD reports "Hostile Spawn: YES" when the looked-at block meets a simple hostile-spawn surface check:

- Top surface is sturdy
- Two blocks above are clear of collision and fluid
- Light level at spawn position is low enough (<= 7)

This is intended as a practical indicator, not an exact replacement for every vanilla mob-specific rule.

## Build and Run

```bash
./gradlew runClient
./gradlew build
```

Built jar output:

- `build/libs/simplehud-1.0.0.jar`
