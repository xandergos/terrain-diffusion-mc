<img width="2522" height="723" alt="dramatic_range_sillouhette" src="https://github.com/user-attachments/assets/bafb07a9-5957-499f-85d5-26e7eb90ad7b" />

# Terrain Diffusion Fabric Mod

This is a Minecraft Fabric mod integrating [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion).

## Requirements

- Minecraft with [Fabric](https://fabricmc.net/) and the [Fabric API Mod](https://modrinth.com/mod/fabric-api) installed
- A directX 12 and directml capable GPU is strongly recommended. CPU inference works but is slow (see Configuration below to use the CPU).
- VRAM needed: 1.5GB
- At least 4GB of RAM allocated to minecraft

## Usage

1. Download the mod jar from [Releases](https://github.com/xandergos/terrain-diffusion-mc/releases) for your Minecraft version and place it in your Minecraft `mods/` folder. Make sure the Minecraft version matches.
2. Launch Minecraft, at least once online to download the models (~2.5GB).
3. Create a world, and select the **Terrain Diffusion** world type. Click **Customize** to set the `World Scale` (see [Per-world settings](#per-world-settings) below).

## Exploring the World

The mod includes a built-in terrain explorer web UI. Run the `/td-explore` command in-game; it will print a clickable link (e.g. `http://localhost:19801`) that opens an interactive map in your browser. Click the map on the left to open a "detailed view". Click the detailed view to get coordinates in the bottom left. You can also filter for certain climates.

Use the explorer to scout continents, mountains, islands, and other interesting terrain before venturing out in Minecraft.

## Configuration

Edit `config/terrain-diffusion-mc.properties` (created automatically on first launch):

```
# Terrain Diffusion MC configuration

# Inference device: "cpu", "gpu", or "auto" (try GPU first then fall back to CPU).
# Defaults to "gpu" so startup fails loudly if CUDA is expected but not detected.
# Set to "cpu" explicitly if you do not have a CUDA-capable GPU.
inference.device=gpu

# Offload inactive models from VRAM between pipeline stages.
# Keeps peak VRAM to ~1.5-2 GB. Set to false if you have ~2.5+ GB free for slightly
# faster generation.
inference.offload_models=true

# Validate SHA-256 for pre-existing files in .minecraft/terrain-diffusion-models.
# Set to false if you want to provide custom models/config files without hash checks.
validate_model=true

# Port for the local terrain explorer web UI (/td-explore).
explorer.port=19801
```

### Per-world settings

For Terrain Diffusion worlds, click **Customize** in world creation and set:

- `World Scale` (integer `1..6`)

This value is saved with the world save and affects:

- how many real-world meters each block represents (`scale=1` => `30m/block`, `scale=2` => `15m/block`, etc.)
- world max height for newly created worlds (assumes tallest point is 10000 real-world meters)
- 2 is recommended for a good balance of scale and playability. Use 1 for smaller, more compressed worlds.

## Common Issues
**A dynamic link library (DLL) initialization routine failed**

This can happen for some older Java versions. Please update to the most recent version of Java 21 or higher. The [latest Microsoft OpenJDK 21](https://learn.microsoft.com/en-us/java/openjdk/download) version is known to work.

**LoadLibrary failed with error 126**

This is typically due to an improper CUDA or cuDNN installation. Things to check:
- The appropriate CUDA folder is in PATH, and the folder contains `cudart64_12.dll`
- The appropriate cuDNN folder is in PATH, and the folder contains `cudnn64_9.dll`
- CUDA version is 12.x
- cuDNN version is 9.x

**If your issue is still not resolved, please [raise it here](https://github.com/xandergos/terrain-diffusion-mc/issues/new).**

## Building from Source

1. Clone this repository.
2. Build with Gradle (online connection required during build to fetch the pinned model manifest metadata):

```
./gradlew build
```

At runtime, the mod downloads required model assets from the pinned Hugging Face commit on first launch into `.minecraft/terrain-diffusion-models` and verifies SHA-256 checksums before use. The released jar bundles `onnxruntime_gpu` (CUDA). ONNX Runtime also supports other execution providers (DirectML, TensorRT, ROCm, etc.). See the [ORT provider documentation](https://onnxruntime.ai/docs/execution-providers/) if you want to build with a different backend.

## Note For Mod Developers

While modifying the AI terrain itself is quite complex, the integration with Minecraft biomes is extremely simple. The model outputs elevation + 4 climate variables, and this is converted to Minecraft biomes with hand-written rules. This is the most immediate way to improve the quality of the terrain and is relatively easy, but takes time to get realistic. The entire biome classifier is [only 250 lines](https://github.com/xandergos/terrain-diffusion-mc/blob/master/src/main/java/com/github/xandergos/terraindiffusionmc/pipeline/BiomeClassifier.java).

The terrain diversity far outpaces the biome diversity and there's a real opportunity to close that gap. I'm hoping someone goes crazy with it.