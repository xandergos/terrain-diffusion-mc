# Terrain Diffusion Fabric Mod

This is a Minecraft Fabric mod integrating [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion).
The mod works purely server-side and can be used on multiplayer servers. In v2, the mod is **self-contained**: it runs ML inference locally via ONNX. The released version utilizes CUDA for GPU acceleration.

## Requirements

- Minecraft with [Fabric](https://fabricmc.net/) and the [Fabric API Mod](https://modrinth.com/mod/fabric-api) installed.
- An NVIDIA GPU with CUDA is strongly recommended. CPU inference is supported but slow (see [Configuration)](#configuration).

**If you have an NVIDIA GPU**:
- Download CUDA 12.x (Not 13): https://developer.nvidia.com/cuda-toolkit-archive
- Download cuDNN 9.x: https://developer.nvidia.com/cudnn

## Installation

1. Download the mod jar from the [releases](https://github.com/xandergos/terrain-diffusion-mc/releases) page and place it in your Minecraft `mods/` folder. Make sure the Minecraft version matches.
2. Launch Minecraft and create a **Terrain Diffusion** world.

## Creating a World

When creating a world, select the **Terrain Diffusion** world type. Click **Customize** to set the `World Scale` (see [Per-world settings](#per-world-settings) below).

## Exploring the World

The mod includes a built-in terrain explorer web UI. Run the `/td-explore` command in-game; it will print a clickable link (e.g. `http://localhost:19801`) that opens an interactive map in your browser. Click the map on the left to open a "detailed view". Click the detailed view to get coordinates in the bottom left. You can also filter for certain climates.

Use the explorer to scout continents, mountains, islands, and other interesting terrain before venturing out in Minecraft.

## Configuration

Edit `config/terrain-diffusion-mc.properties` (created automatically on first launch):

# Terrain Diffusion MC configuration

# Inference device: "cpu", "gpu", or "auto" (try GPU first then fall back to CPU).
# Defaults to "gpu" so startup fails loudly if CUDA is expected but not detected.
# Set to "cpu" explicitly if you do not have a CUDA-capable GPU.
inference.device=gpu

# Offload inactive models from VRAM between pipeline stages.
# Keeps peak VRAM to ~1.5-2 GB. Set to false if you have ~2.5+ GB free for slightly
# faster generation.
inference.offload_models=true

# Port for the local terrain explorer web UI (/td-explore).
explorer.port=19801
```

### Per-world settings

For Terrain Diffusion worlds, click **Customize** in world creation and set:

- `World Scale` (integer `1..6`)

This value is saved with the world save and affects:

- block-to-meter mapping (`scale=1` => `30m/block`, `scale=2` => `15m/block`, etc.)
- world max height for newly created worlds (assumes tallest point is 10000 real-world meters)
- pre-registered worldgen variants for scales `1..6`
- 2 is recommended to start, or 1 for smaller worlds

## Building from Source

1. Clone this repository.
2. Download the ONNX model files from [HuggingFace](https://huggingface.co/xandergos/terrain-diffusion-30m-onnx). Only the `.onnx` files are needed (`coarse_model.onnx`, `base_model.onnx`, `decoder_model.onnx`). Place them in `src/main/resources/onnx/`.
3. Build with Gradle:

```
./gradlew build
```

The build step runs an ONNX optimization pass before packaging the models into the jar. The released jar bundles `onnxruntime_gpu` (CUDA). ONNX Runtime also supports other execution providers (DirectML, TensorRT, ROCm, etc.). See the [ORT provider documentation](https://onnxruntime.ai/docs/execution-providers/) if you want to build with a different backend.
