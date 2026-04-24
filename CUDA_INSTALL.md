# CUDA Setup (for the `-cuda` build)

This guide is only needed for the `-cuda` build of Terrain Diffusion MC. If you downloaded the `-windows` build, no setup is required.

> **On Linux?** Follow the [Official ONNX Runtime instructions](https://onnxruntime.ai/docs/install/#cuda-and-cudnn). The required CUDA and cuDNN versions are the same as below.

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

## Troubleshooting

**LoadLibrary failed with error 126**

This is typically due to an improper CUDA or cuDNN installation. Things to check:

- The appropriate CUDA folder is in PATH, and the folder contains `cudart64_12.dll`
- The appropriate cuDNN folder is in PATH, and the folder contains `cudnn64_9.dll`
- CUDA version is 12.x
- cuDNN version is 9.x