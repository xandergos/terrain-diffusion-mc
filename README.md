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

There are some configuration options you can play with, if you want:

```
# Terrain Diffusion MC configuration

# This is the native resolution for height conversion (Resolution before `scale` is applied)
# If you are using a 30m resolution model, this should be 30 for real life slopes.
# You can adjust the value down to increase terrain height or up to decrease it.
height_converter.resolution=30

# Scale factor for terrain upsampling. 1 = 1 block per API pixel, 2 = 2 blocks per API pixel, 4 = 4 blocks per API pixel, etc.
# Example: If you are using a 30m resolution API, then 1 = 30m resolution, 2 = 15m resolution, 4 = 7.5m resolution, etc.
heightmap_api.scale=2

# Noise scale factor for terrain at large slopes (default: 1.0)
# mainly affects how "rough" cliffs or steep slopes are
heightmap_api.noise=1.0

# Base URL of the heightmap API server.
heightmap_api.url=http://localhost:8000

# Base URL of the heightmap API server.
heightmap_api.url=http://localhost:8000

# ADVANCED
# Gamma and c of terrain (Mod applies elev -> ((elev + c)^gamma - c^gamma) before scaling down by `height_converter.resolution`)
# I tried experimenting with this to boost low-elevation relief, but didn't really see any improvements. Maybe I missed something. Note that this changes the scale of the elevation, so `height_converter.resolution`
# will need to be changed accordingly (usually to a much smaller value).
height_converter.gamma=1.0
height_converter.c=30.0
```
