# Terrain Diffusion Fabric Mod

This is a Minecraft Fabric mod for integrating [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion). 
The mod works purely server-side so can be used on servers if desired. The mod requires the Terrain Diffusion Minecraft API to be running in the background. See instructions below.

This is a research preview. I cannot guarantee continued maintanence of the mod, but I am happy to support mod developers.

## Requirements:
- Minecraft with [Fabric](https://fabricmc.net/) and the [Fabric API Mod](https://modrinth.com/mod/fabric-api) installed.
- Python and Git

## Installation

1. Install the mod

The Fabric mod is available in the [releases](https://github.com/xandergos/terrain-diffusion-mc/releases) section. Make sure the Minecraft version's match up. Place it in your Minecraft mods folder.

2. Install the Terrain Diffusion backend

You need [Python](https://www.python.org/downloads/) and [git](https://git-scm.com/) installed. Then run:

```
git clone https://github.com/xandergos/terrain-diffusion
cd terrain-diffusion
pip install -r requirements.txt
```

3. For the best performance, install PyTorch with CUDA if you have an NVIDIA GPU.

```
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu121
```

4. See "Generating a World for Minecraft" below. After that you are good to go.

## Using Terrain Diffusion

Terrain Diffusion has two main tools:

1. **mc-api** generates the actual world for Minecraft.

2. **explorer** is a separate viewer for exploring worlds.

You will need to ensure you use the same options for both, so the worlds match up. If you need the seed, it is printed in the console after running either command.

## Generating a World for Minecraft

Run the API:
```
python -m terrain_diffusion mc-api xandergos/terrain-diffusion-30m --seed <YOUR_SEED>
```

30m is recommended for gameplay (practically and visually). If you want to replicate the paper:
```
python -m terrain_diffusion mc-api xandergos/terrain-diffusion-90m --seed <YOUR_SEED>
```

Note that because of the new resolution you will need to update the mod config.

## Previewing the World

Use explorer to scout continents, mountains, islands, and interesting terrain before entering Minecraft. The input arguments should be exactly the same used for `mc-api`.

Example:
```
python -m terrain_diffusion explore xandergos/terrain-diffusion-30m --seed <YOUR_SEED>
```

Explorer opens a GUI with two panels. The left panel shows a global view. Click anywhere to zoom in. The console prints the coordinates you clicked at various resolutions.
Note that Minecraft uses a flipped coordinate system, so you will need to flip the coordinates in the console.

## Configuration

There are two configuration scopes:

1. Global mod config file (`config/terrain-diffusion-mc.properties`)
2. Per-world Terrain Diffusion settings (set in world creation UI)

```
# Terrain Diffusion MC configuration

# Inference device: "cpu", "gpu", or "auto" (try GPU first then fall back to CPU).
inference.device=auto

# Offload inactive models from VRAM between pipeline stages.
inference.offload_models=true

# Port for the local terrain explorer web UI (/td-explore).
explorer.port=19842

```

### Per-world settings

For Terrain Diffusion worlds, click **Customize** in world creation and set:

- `World Scale` (integer `>= 1`)

This value is saved in that world save and affects:

- block-to-meter mapping (`scale=1` => `30m/block`, `scale=2` => `15m/block`, etc.)
- world max height for newly created worlds (computed from the max pipeline height assumption of `10000m`)
