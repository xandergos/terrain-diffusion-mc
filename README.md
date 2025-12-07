# Terrain Diffusion Fabric Mod

This is a Minecraft Fabric mod for integrating [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion). 
The mod works purely server-side so can be used on servers if desired. The mod requires the Terrain Diffusion Minecraft API to be running in the backgroumd. See instructions below.

**This is a research preview**. I cannot guarantee continued maintanence of the mod, but I am happy to support future mod developers.

You need [Python](https://www.python.org/downloads/) and [git](https://git-scm.com/) installed.

## Installation

1. Install the Terrain Diffusion backend:

```
git clone https://github.com/xandergos/terrain-diffusion
cd terrain-diffusion
pip install -r requirements.txt
```

2. For the best performance, install PyTorch with CUDA if you have an NVIDIA GPU.

```
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu121
```

## Using Terrain Diffusion

Terrain Diffusion has two main tools:

1. **mc-api** generates the actual world for Minecraft and writes a file called `world.h5`. This file stores cached terrain plus all parameters you used.

2. **explorer** is a separate viewer for exploring the same world, but it cannot open `world.h5` directly because HDF5 only allows one writer at a time. Instead, explorer needs a temporary file plus exactly the same parameters you used with mc-api, including the seed.

If you forget the seed, it was printed in the mc-api console when you ran it.

Explorer then opens a two-panel GUI. Click anywhere on the left panel to zoom into an area. The exact coordinates you clicked appear in the console.

## Generating a World for Minecraft

Run the API:
```
python -m terrain_diffusion mc-api
```

The defaults already give realistic terrain. For my favorite setting, which is a good mix between realism and intensity, try:
```
python -m terrain_diffusion mc-api --kwarg coarse_pooling=2
```
My favorite find so far uses these settings with `--seed 599419348` at x=1000, z=28500.

The actual Fabric mod is available in the [releases](https://github.com/xandergos/terrain-diffusion-mc/releases) section. Make sure the Minecraft version's match up.

If you know how to code in Python, you can modify Terrain Diffusion's source code at `terrain_diffusion/inference/minecraft_api.py` 
to directly change how the world/biomes are generated.

## Previewing the World

Use explorer to scout continents, mountains, islands, and interesting terrain before entering Minecraft.

Since `world.h5` is locked for writing by `mc-api`, explorer uses its own temporary file. To ensure you see the same world, you must pass the exact same arguments and seed you used for `mc-api`.

Example:
```
python -m terrain_diffusion explorer --hdf5-file TEMP  --kwarg coarse_pooling=2 --seed 599419348
```
Explorer opens a GUI with two panels. The left panel shows a global view. Click anywhere to zoom in. The console prints the coordinates you clicked.

## Configuration

There are some configuration options you can play with, if you want:

```
# Terrain Diffusion MC configuration

# This is the value that the terrain height is scaled by. 20 means that one block in Minecraft is 20 meters tall in real life.
height_converter.resolution=20

# Resolution of the heightmap API endpoint. One of 90, 45, 22, or 11. This controls the horizontal scale of the world.
heightmap_api.endpoint=22

# ADVANCED
# Gamma and c of terrain (Mod applies elev -> ((elev + c)^gamma - c^gamma) before scaling down by `height_converter.resolution`)
# I tried experimenting with this to boost low-elevation relief, but didn't really see any improvements. Maybe I missed something.
# Note that this changes the scale of the elevation, so `height_converter.resolution` will need to be changed accordingly (usually to a much smaller value).
height_converter.gamma=1.0
height_converter.c=30.0
```
