# Cordlink

Cordlink is a Windows-only Fabric mod that bridges Discord voice with Minecraft positional audio.

It links a Discord account to a Minecraft account, injects a native audio bridge into Discord, and places remote voice in 3D space based on in-game player positions.

## Requirements

- Minecraft 1.21.3
- Fabric Loader 0.16.0 or newer
- Fabric API
- Java 21
- Windows
- Discord desktop client

## How It Works

1. Link your Discord account to your Minecraft account through the companion service.
2. Open the Cordlink screen in Minecraft and press `Sync`.
3. The mod extracts the bundled native files and connects Discord voice to the in-game positional audio system.
4. Leave and rejoin the voice channel after `Sync` so the voice session is reinitialized correctly.

## Notes

- The mod uses bundled native components and shared memory.
- The current implementation is Windows-only.
- Voice positioning depends on successful account linking and active player position updates.

## Build

```bash
./gradlew build
```

Output:

`build/libs/cordlink-1.0.0.jar`

## License

This project is distributed as `All Rights Reserved` unless explicitly stated otherwise.

Bundled third-party components remain under their own licenses.
