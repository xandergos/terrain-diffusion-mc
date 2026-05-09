# CUDA Setup (for the `-cuda` build)

This guide is only needed for the `-cuda` build of Terrain Diffusion MC. If you downloaded the `-windows` build, no setup is required.

## Installation Steps (Windows)

#### Step 1: Install CUDA 12

Go to the [CUDA Toolkit Archive](https://developer.nvidia.com/cuda-toolkit-archive) and download any **12.x** version.

> ⚠️ Do not install version 13 — it isn't supported yet.

#### Step 2: Install cuDNN 9

Go to the [cuDNN download page](https://developer.nvidia.com/cudnn) and download any **9.x** version.

#### Step 3: Add CUDA to PATH

After installing, find your CUDA `bin` folder containing `cudart64_12.dll`. It should look like this:

`C:\Program Files\NVIDIA GPU Computing Toolkit\CUDA\v12.9\bin`

The version number (e.g. `v12.9`) may differ slightly, that's fine. Before copying the path, confirm the folder contains a file named `cudart64_12.dll`.

Then add this path to your system PATH. ([How do I edit PATH on Windows?](https://www.architectryan.com/2018/03/17/add-to-the-path-on-windows-10/))

> Ensure that you **edit** the system **path** or **PATH** variable, adding this **folder** to the list, as shown in the link above. Do **not** create a new variable.

#### Step 4: Add cuDNN to PATH

Find the cuDNN folder containing `cudnn64_9.dll`. It should look something like this:

`C:\Program Files\NVIDIA\CUDNN\v9.x\bin\12.x\x64`

`9.x` and `12.x` should be your cuDNN and CUDA version respectively. Confirm the folder contains `cudnn64_9.dll`, then add it to PATH the same way.

#### Step 5: Restart your PC

You may need to restart your PC for PATH changes to take effect. Once you're back, you're all set.


---

## Linux

The mod requires **CUDA 12.x** and **cuDNN 9.x**. The approach differs depending on your distro.

> ⚠️ **Do not install CUDA 13** — it is not supported yet. If `nvidia-smi` shows "CUDA Version: 13.x", that is just your driver's maximum supported version, not an installed toolkit. You still need to install CUDA 12.x separately.

### Option A: Ubuntu / Debian (Recommended)

This is the simplest path. NVIDIA provides native `.deb` packages.

#### Step 1: Install CUDA 12

Go to the [CUDA Toolkit Archive](https://developer.nvidia.com/cuda-toolkit-archive), select your Ubuntu version, and follow the `deb (network)` instructions. Make sure you pick a **12.x** version.

#### Step 2: Install cuDNN 9

Go to the [cuDNN download page](https://developer.nvidia.com/cudnn-downloads), select:
- Linux → x86_64 → Ubuntu → your version → **Deb**
- Select **CUDA 12** and download

Install with:
```bash
sudo dpkg -i cudnn-local-repo-*.deb
sudo apt-get update
sudo apt-get install libcudnn9-cuda-12
```
#### Step 3: Set LD_LIBRARY_PATH

Before launching Minecraft, set the library path in your terminal:

```bash
export LD_LIBRARY_PATH=/usr/lib/x86_64-linux-gnu
```

Then launch your Minecraft launcher from the same terminal session.

> **Prism Launcher / MultiMC users:** You can set this per-instance instead. Go to instance → **Edit** → **Settings** → **Environment Variables** and add `LD_LIBRARY_PATH` = `/usr/lib/x86_64-linux-gnu`. This way you don't need the terminal export every time.

Launch the game. Done.

---

### Option B: Arch Linux (and Arch-based distros like EndeavourOS, Manjaro)

Arch does not package CUDA 12.x and 13.x separately in the official repos, so the cleanest approach for users who need to keep another CUDA version is to install CUDA 12.x via the `.run` file into an isolated directory.

#### Step 1: Download CUDA 12.x runfile

Go to the [CUDA Toolkit Archive](https://developer.nvidia.com/cuda-toolkit-archive) and download any distro's **12.x** version as a **Linux runfile (local)**. CUDA 12.0 is known to work.

#### Step 2: Install CUDA 12 into an isolated directory

> ℹ️ This does **not** touch your existing CUDA installation or GPU drivers.

Arch's libxml2 ships under a different versioned name than the runfile expects. Create a symlink first:
```bash
sudo ln -s /usr/lib/libxml2.so /usr/lib/libxml2.so.2
```

Then install:
```bash
sudo LD_LIBRARY_PATH=/usr/lib:$LD_LIBRARY_PATH sh cuda_12.x.x_*.run \
  --silent \
  --toolkit \
  --toolkitpath=/usr/local/cuda-12.0 \
  --no-opengl-libs \
  --override
```

The `--override` flag bypasses a gcc version check that fails on Arch. The three "no version information available" warnings that appear are harmless.

Verify it worked:
```bash
ls /usr/local/cuda-12.0/lib64/libcudart.so*
# Should show: libcudart.so.12
```

#### Step 3: Download and install cuDNN 9.x

Go to the [cuDNN download page](https://developer.nvidia.com/cudnn-downloads) and select:
- Linux → x86_64 → **Tarball** → **CUDA 12** → **Full**

> ⚠️ Make sure you select **CUDA 12**, not CUDA 13. The filename should contain `cuda12`.

Extract and copy into your CUDA 12 directory:
```bash
tar -xf cudnn-linux-x86_64-9.*.tar.xz
sudo cp cudnn-linux-x86_64-9.*_cuda12-archive/include/cudnn*.h /usr/local/cuda-12.0/include/
sudo cp -P cudnn-linux-x86_64-9.*_cuda12-archive/lib/libcudnn* /usr/local/cuda-12.0/lib64/
sudo chmod a+r /usr/local/cuda-12.0/lib64/libcudnn*
```

Verify:
```bash
ls /usr/local/cuda-12.0/lib64/libcudnn.so*
# Should show: libcudnn.so.9
```

#### Step 4: Set LD_LIBRARY_PATH

Before launching Minecraft, set the library path in your terminal:

```bash
export LD_LIBRARY_PATH=/usr/local/cuda-12.0/lib64
```

Then launch your Minecraft launcher from the same terminal session.

> **Prism Launcher / MultiMC users:** You can set this per-instance instead. Go to instance → **Edit** → **Settings** → **Environment Variables** and add `LD_LIBRARY_PATH` = `/usr/local/cuda-12.0/lib64`. This tells the JVM exactly where to find the CUDA and cuDNN libraries without affecting anything else on your system.

Launch the game. Done.

---

## Troubleshooting

**LoadLibrary failed with error 126**

This is typically due to an improper CUDA or cuDNN installation. Things to check:

- The appropriate CUDA folder is in PATH, and the folder contains `cudart64_12.dll`
- The appropriate cuDNN folder is in PATH, and the folder contains `cudnn64_9.dll`
- CUDA version is 12.x
- cuDNN version is 9.x
