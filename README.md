# Terrain Diffusion Fabric Mod

This is a Minecraft Fabric mod integrating [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion).

## Which version should I use?

Four builds are available on the [Releases](https://github.com/xandergos/terrain-diffusion-mc/releases) page:


| Build                     | Supports                    | Setup required                          |
| ------------------------- | --------------------------- | --------------------------------------- |
| **Windows** (recommended) | Windows with any modern GPU | None                                    |
| **CUDA**                  | NVIDIA GPUs                 | [CUDA + cuDNN install](CUDA_INSTALL.md) |
| **MIGraphX**              | Linux with modern AMD GPUs  | ROCm + MIGraphX                         |
| **CPU (Slow)**            | Everything                  | None                                    |


Use the `-cuda` build only if you are on Linux, or have an NVIDIA GPU and prefer CUDA (may improve performance).

## Requirements

- Minecraft with [Fabric](https://fabricmc.net/) and the [Fabric API Mod](https://modrinth.com/mod/fabric-api) installed
- Windows with a GPU, Linux with an NVIDIA GPU (CUDA), or Linux with a modern AMD GPU (MIGraphX) is strongly recommended. CPU inference works but is very slow.
- VRAM (GPU RAM) needed: 1.5GB
- RAM needed: 2.5GB (May need to increase Minecraft's RAM allocation)

## Usage

**If using CUDA build:** First see [CUDA_INSTALL.md](CUDA_INSTALL.md).

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
# "gpu" uses DirectML on the -windows build, CUDA on the -cuda build,
# or MIGraphX on the -migraphx build.
# Defaults to "gpu" so startup fails loudly if GPU inference is expected but not detected.
# Set to "cpu" if you do not have a supported GPU.
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
- Lower values put more stress on the GPU (Terrain Diffusion runs more often), while higher values put more stress on the CPU (larger world height). Most modern GPUs will be bottlenecked by the CPU around scale 2 or 3.

## Common Issues

**A dynamic link library (DLL) initialization routine failed**

This can happen for some older Java versions. Please update to the most recent version of Java 21 or higher. The [latest Microsoft OpenJDK 21](https://learn.microsoft.com/en-us/java/openjdk/download) version is known to work.

**LoadLibrary failed with error 126** *(CUDA build only)*

This is typically due to an improper CUDA or cuDNN installation. See [CUDA_INSTALL.md](CUDA_INSTALL.md) for troubleshooting steps.

**java.lang.IllegalStateException: Failed to load terrain-diffusion models**

This typically indicates an "out of memory" error (the logs should show this as well).
Terrain Diffusion's models take up about 2.5GB of RAM, so make sure to allocate enough RAM to account for this.

**If your issue is still not resolved, please [raise it here](https://github.com/xandergos/terrain-diffusion-mc/issues/new).**

## Building from Source

An internet connection is required during the build to fetch the pinned model manifest metadata from Hugging Face.

The `-windows` build requires `libs/onnxruntime-dml.jar`, which is provided as part of the repo. See [Building onnxruntime with DirectML](#building-onnxruntime-with-directml) to build from source. 
The `-migraphx` build requires `libs/onnxruntime-migraphx.jar`, which is built locally from ONNX Runtime + MIGraphX.

Build for Windows (DirectML):
```
./gradlew build -PuseDml=true
```

Build for CUDA:
```
./gradlew build -PuseCuda=true
```

Build for MIGraphX:
```
./gradlew build -PuseMigraphX=true
```

Build for CPU:
```
./gradlew build -PuseCpu=true
```

Build all:
```
./gradelw buildAll
```

### Building onnxruntime with DirectML

**Requirements**

- [Windows 10 SDK (10.0.17134.0)](https://developer.microsoft.com/en-us/windows/downloads/sdk-archive/index-legacy) — for Windows 10 version 1803 or newer
- Visual Studio 2017 toolchain — install *Desktop development with C++* from the VS Installer
- Visual Studio 2022 toolchain — same as above
- Python 3.10+: [https://python.org/](https://python.org/)
- CMake 3.28 or higher

Keep both VS toolchains up to date. Full details at the [ONNX Runtime build docs](https://onnxruntime.ai/docs/build/inferencing.html) and the [DirectML EP requirements](https://onnxruntime.ai/docs/execution-providers/DirectML-ExecutionProvider.html#build).

**Steps**

Run all commands from the **Developer Command Prompt for VS 2022**.

```
git clone --recursive https://github.com/Microsoft/onnxruntime.git
cd onnxruntime
.\build.bat --config RelWithDebInfo --build_shared_lib --parallel --compile_no_warning_as_error --skip_submodule_sync --use_dml --build_java --build
```

The built jar appears in `java/build/`. Rename it to `onnxruntime-dml.jar` and place it in `libs/` in this repository.

### Building onnxruntime with MIGraphX

**Requirements**

- Linux host with ROCm installed (example path: `/opt/rocm`)
- MIGraphX libraries available through the ROCm install
- Python 3.10+
- CMake 3.28+
- GCC/G++ 14 (or a toolchain compatible with your ONNX Runtime checkout)

See the ONNX Runtime build docs and the MIGraphX EP docs for platform-specific details:

- [ONNX Runtime build docs](https://onnxruntime.ai/docs/build/inferencing.html)
- [MIGraphX EP docs](https://onnxruntime.ai/docs/execution-providers/MIGraphX-ExecutionProvider.html)

**Steps**

Run from the ONNX Runtime repo root:

```bash
python3 tools/ci_build/build.py \
	--build_dir build/Linux \
	--config Release \
	--use_migraphx \
	--migraphx_home /opt/rocm \
	--build_java \
	--parallel \
	--skip_tests \
	--compile_no_warning_as_error \
	--cmake_extra_defines \
		CMAKE_C_COMPILER=gcc-14 \
		CMAKE_CXX_COMPILER=g++-14 \
		CMAKE_HIP_COMPILER=/opt/rocm/llvm/bin/clang++ \
		CMAKE_POLICY_VERSION_MINIMUM=3.5 \
		FETCHCONTENT_TRY_FIND_PACKAGE_MODE=NEVER \
		re2_FOUND=FALSE \
		ABSL_PROPAGATE_CXX_STD=ON
```

The Java build produces:

- `java/build/libs/onnxruntime-1.20.0.jar`
- `build/Linux/Release/libonnxruntime_providers_shared.so`
- `build/Linux/Release/libonnxruntime_providers_migraphx.so`

Create the local jar by adding the provider libraries into the standard ONNX Runtime native path:

```bash
cp java/build/libs/onnxruntime-1.20.0.jar /path/to/terrain-diffusion-mc/libs/onnxruntime-migraphx.jar

tmpdir=$(mktemp -d)
mkdir -p "$tmpdir/ai/onnxruntime/native/linux-x64"
cp build/Linux/Release/libonnxruntime_providers_shared.so "$tmpdir/ai/onnxruntime/native/linux-x64/"
cp build/Linux/Release/libonnxruntime_providers_migraphx.so "$tmpdir/ai/onnxruntime/native/linux-x64/"

(cd "$tmpdir" && jar uf /path/to/terrain-diffusion-mc/libs/onnxruntime-migraphx.jar \
	ai/onnxruntime/native/linux-x64/libonnxruntime_providers_shared.so \
	ai/onnxruntime/native/linux-x64/libonnxruntime_providers_migraphx.so)

rm -rf "$tmpdir"
```

This final `onnxruntime-migraphx.jar` should contain:

- `ai/onnxruntime/native/linux-x64/libonnxruntime.so`
- `ai/onnxruntime/native/linux-x64/libonnxruntime4j_jni.so`
- `ai/onnxruntime/native/linux-x64/libonnxruntime_providers_shared.so`
- `ai/onnxruntime/native/linux-x64/libonnxruntime_providers_migraphx.so`

Developer note for Java bindings

- If your ONNX Runtime Java binding does not already expose a MIGraphX provider helper on the session (and the 1.20.* builds won't), add the following methods to the Java session class (this project uses `OrtSession` in java/src/main/java/ai/onnxruntime/OrtSession.java):

```java
public void addMIGraphX() throws OrtException {
	addMIGraphX(0);
}

public void addMIGraphX(int deviceNum) throws OrtException {
	checkClosed();
	addMIGraphX(OnnxRuntime.ortApiHandle, nativeHandle, deviceNum);
}

private native void addMIGraphX(long apiHandle, long nativeHandle, int deviceNum)
				throws OrtException;
```

- Also ensure the core loader exposes the MIGraphX provider constant and extraction helper in `OnnxRuntime` (java/src/main/java/ai/onnxruntime/OnnxRuntime.java):

```java
static final String ONNXRUNTIME_LIBRARY_MIGRAPHX_NAME = "onnxruntime_providers_migraphx";
static boolean extractMIGraphX() {
		return extractProviderLibrary(ONNXRUNTIME_LIBRARY_MIGRAPHX_NAME);
}
// call extractMIGraphX() from init() before loading the core native library (line ~165)
```

These changes allow the Java API to request registration of the MIGraphX execution provider at runtime (similar to CUDA/DirectML/TensorRT helpers).



## Note For Mod Developers

While modifying the AI terrain itself is quite complex, the integration with Minecraft biomes is extremely simple. The model outputs elevation + 4 climate variables, and this is converted to Minecraft biomes with hand-written rules. This is the most immediate way to improve the quality of the terrain and is relatively easy, but takes time to get realistic. The entire biome classifier is [only 250 lines](https://github.com/xandergos/terrain-diffusion-mc/blob/master/src/main/java/com/github/xandergos/terraindiffusionmc/pipeline/BiomeClassifier.java).

The terrain diversity far outpaces the biome diversity and there's a real opportunity to close that gap. I'm hoping someone goes crazy with it.
