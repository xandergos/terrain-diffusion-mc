# Terrain Diffusion Fabric Mod

This is a Minecraft Fabric mod for integrating [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion). 
The mod works purely server-side so can be used on servers if desired. The mod requires the Terrain Diffusion Minecraft API to be running in the backgroumd. See instructions below.

**This is a research preview**. I cannot guarantee continued maintanence of the mod, but I am happy to support future mod developers.

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

## Using Terrain Diffusion

Terrain Diffusion has two main tools:

1. **mc-api** generates the actual world for Minecraft.

2. **explorer** is a separate viewer for exploring worlds.

You will need to ensure you use the same options for both, so the worlds match up. If you need the seed, it is printed in the console.

Explorer then opens a two-panel GUI. Click anywhere on the left panel to zoom into an area. The exact coordinates you clicked appear in the console.

## Generating a World for Minecraft

Run the API:
```
python -m terrain_diffusion mc-api --seed <YOUR_SEED>
```

The defaults already give realistic terrain. For more variation, I recommend trying:
```
python -m terrain_diffusion mc-api --seed <YOUR_SEED> --kwarg coarse_pooling=2
```

## Previewing the World

Use explorer to scout continents, mountains, islands, and interesting terrain before entering Minecraft. The input arguments should be exactly the same used for `mc-api`.

Example:
```
python -m terrain_diffusion explorer --seed <YOUR_SEED> --kwarg coarse_pooling=2
```

Explorer opens a GUI with two panels. The left panel shows a global view. Click anywhere to zoom in. The console prints the coordinates you clicked at various resolutions.
Note that Minecraft uses a flipped coordinate system, so you will need to flip the coordinates in the console.

## Configuration

There are some configuration options you can play with, if you want:

```
# Terrain Diffusion MC configuration

# This is the value that the terrain height is scaled by. 20 means that one block in Minecraft is 20 meters tall in real life.
height_converter.resolution=20

# Resolution of the heightmap API endpoint. One of 90, 45, 22, or 11. This controls the horizontal scale of the world.
heightmap_api.endpoint=22

# Base URL of the heightmap API server.
heightmap_api.url=http://localhost:8000

# ADVANCED
# Gamma and c of terrain (Mod applies elev -> ((elev + c)^gamma - c^gamma) before scaling down by `height_converter.resolution`)
# I tried experimenting with this to boost low-elevation relief, but didn't really see any improvements. Maybe I missed something. Note that this changes the scale of the elevation, so `height_converter.resolution`
# will need to be changed accordingly (usually to a much smaller value).
height_converter.gamma=1.0
height_converter.c=30.0
```
