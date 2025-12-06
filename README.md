# Terrain Diffusion Fabric Mod

This is a Minecraft Fabric mod for integrating [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion). 
The mod works purely server-side so can be used on servers if desired. The mod requires the Terrain Diffusion Minecraft API to be running in the backgroumd. See instructions below.

**This is a research preview**. I cannot guarantee continued maintanence of the mod, but I am happy to support future mod developers.

## Installation

1. Begin by installing the Terrain Diffusion backend:

```
git clone https://github.com/xandergos/terrain-diffusion
cd terrain-diffusion
pip install -r requirements.txt
```

2. Optionally (but strongly recommended), install PyTorch with CUDA if you have an NVIDIA GPU available. Make sure you have recent NVIDIA drivers installed.

```
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu121
```

3. Now run the Terrain Diffusion API.

```
python -m terrain_diffusion mc-api
```

That's it. The actual Fabric mod is available in the [releases](https://github.com/xandergos/terrain-diffusion-mc/releases) section. Make sure the Minecraft version's match up.

If you know how to code in Python, you can modify Terrain Diffusion's source code at `terrain_diffusion/inference/minecraft_api.py` 
to directly change how the world/biomes are generated.

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
