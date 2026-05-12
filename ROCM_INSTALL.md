# ROCm Setup (for the `-rocm` build)

This guide is only needed for the `-rocm` build of Terrain Diffusion MC. It uses AMD's ROCm runtime to run terrain inference on a supported AMD GPU under Linux.

If you have an NVIDIA card, use the `-cuda` build instead. If you have an AMD card on Windows, use the `-windows` build (DirectML).

---

## Supported hardware

The ROCm build is a Linux-only variant that targets AMD GPUs and APUs supported by ROCm 6.4 or newer. Verified to work on:

- **Strix Halo / Ryzen AI Max+ 395** (gfx1151) — see the *Strix Halo notes* below
- ROCm-supported discrete cards: RX 7000 / Radeon W7000 series (gfx110x), Radeon Instinct MI200 / MI300 series (gfx90a / gfx942)

ROCm-supported but not personally verified for this mod yet: RDNA2 (gfx103x), CDNA1 (gfx908), CDNA2 (gfx90a).

---

## Linux install

### Step 1: Install ROCm 6.4 or 7.x system packages

Pick the path that matches your distribution.

**Ubuntu 22.04 / 24.04 (recommended):**
```bash
# Follow AMD's official guide for the latest version pin:
# https://rocm.docs.amd.com/projects/install-on-linux/en/latest/
wget https://repo.radeon.com/amdgpu-install/6.4.4/ubuntu/$(lsb_release -cs)/amdgpu-install_6.4.60404-1_all.deb
sudo apt install ./amdgpu-install_6.4.60404-1_all.deb
sudo apt update
sudo amdgpu-install --usecase=rocm
```

**Fedora 41 / 42 / 43:**
ROCm ships in the official repos.
```bash
sudo dnf install rocm-core rocm-runtime miopen hipblas hipfft rocblas roctracer
```

**Arch / Manjaro:**
```bash
sudo pacman -S rocm-hip-sdk rocm-opencl-sdk
```

Verify ROCm is installed by running `rocminfo`. The output should list your GPU under `Agent N` with `Name:` matching your architecture (e.g. `gfx1151`, `gfx1100`, `gfx942`).

### Step 2: Add your user to the `render` and `video` groups

The ONNX Runtime ROCm provider opens `/dev/kfd` and `/dev/dri/renderD*`, which are owned by `render`/`video` on most distros.
```bash
sudo usermod -aG render,video $USER
# log out and back in for the group change to take effect
```

If you're running Minecraft as a different user (e.g. a dedicated `minecraft` system user for a server), apply the change there instead and restart the relevant service.

### Step 3 (Strix Halo only): set `HSA_OVERRIDE_GFX_VERSION`

ROCm 6.4.x does not ship compiled MIOpen kernels for gfx1151. The override tells ROCm to treat the iGPU as gfx1100, which has working kernels and produces correct output for the diffusion models the mod uses.

Before launching Minecraft:
```bash
export HSA_OVERRIDE_GFX_VERSION=11.0.0
```

Then launch your Minecraft launcher from the same terminal session.

> **Prism Launcher / MultiMC users:** set this per-instance under **Edit → Settings → Environment Variables**: `HSA_OVERRIDE_GFX_VERSION` = `11.0.0`. This is the cleanest option; you don't need to remember the export.

> **Dedicated-server / systemd users:** put it in your unit file's `Environment=` directive, e.g.
> ```
> [Service]
> Environment=HSA_OVERRIDE_GFX_VERSION=11.0.0
> ```

If ROCm 7.x or later adds first-class gfx1151 support, this override becomes optional. Until then, the JVM will fail to load `libonnxruntime_providers_rocm.so` without it.

For RDNA3 (gfx110x) cards: the override is **not** needed, MIOpen ships kernels for those.

### Step 4: install the mod

Drop `terrain-diffusion-mc-<version>-rocm+<mc-version>.jar` into your `mods/` folder. Launch Minecraft. The first launch will download the ~2.5 GB ONNX models from Hugging Face into your `<minecraft-dir>/terrain-diffusion-models/` folder.

If everything is wired up correctly, your log should contain:
```
Terrain diffusion inference: ROCm
```

If you see `CPU` or `CoreML` instead, scroll back for the failure reason — see *Troubleshooting* below.

---

## Strix Halo / Ryzen AI Max+ notes

Strix Halo (Ryzen AI Max+ 395) ships with a Radeon 8060S iGPU built on the gfx1151 microarchitecture, which is RDNA3.5 — a small extension of RDNA3 (gfx1100). ROCm 6.4.x's MIOpen kernel binaries don't yet include gfx1151 targets, so kernels need to be JIT-compiled or routed to gfx1100. The `HSA_OVERRIDE_GFX_VERSION=11.0.0` override is the simplest way; everything in the terrain-diffusion pipeline is supported by gfx1100 kernels.

**Memory configuration**: Strix Halo's iGPU uses unified memory shared with the CPU. By default Linux allocates only a few GB to the GPU; if you plan to run other ML workloads alongside the mod you may want to carve out a larger GTT region via `amdgpu.gttsize=NNN` on the kernel command line. The mod itself only needs ~1.5–2 GB of GPU memory, so the default is fine for the mod.

**Performance expectations**: in our benchmarks, the ROCm build on Strix Halo achieves roughly 2000 chunks/second of terrain generation at world-scale 6 — a ~7× speedup over the CPU build on the same machine. See the project's benchmark notes for the methodology.

---

## Troubleshooting

**Log contains `Failed to load library libonnxruntime_providers_rocm.so` and falls back to CPU**

The most common cause on Strix Halo is the missing `HSA_OVERRIDE_GFX_VERSION` — see *Step 3* above. On RDNA2/RDNA3 discrete cards the most common cause is that the `render` group permissions haven't been applied yet (log out/in after `usermod`).

**Log contains `librocm_smi64-... cannot open shared object file`**

This means the mod's classpath extraction step didn't run. Make sure you have the `-rocm` build of the mod, not the `-cpu` or `-cuda` build, and that the jar is in your `mods/` folder (not extracted/repacked).

**Log shows `Some nodes were not assigned to the preferred execution providers`**

This is a benign warning from ONNX Runtime when a few graph nodes (typically shape ops) get pinned to the CPU EP for efficiency. Terrain inference still runs on ROCm; ignore.

**`ROCm not available: OrtException - This binary was not compiled with ROCM support.`**

This message means the JVM loaded the wrong ONNX Runtime native library — usually because you have a CPU-build mod jar in `mods/` alongside the ROCm-build jar, and Fabric loaded the CPU one first. Remove the CPU jar and restart.

**Inference produces garbage terrain / `nan` heights**

Re-check the GPU architecture override — using `HSA_OVERRIDE_GFX_VERSION=10.3.0` on a gfx1151 chip silently corrupts some MIOpen kernels. The correct override for gfx1151 is `11.0.0`. RDNA1 / Vega / older architectures are not supported regardless of override.

**Build error: `Permission denied` opening `/dev/kfd`**

The user running the JVM is not in the `render` group. See *Step 2*.

---

## How the ROCm build is packaged

This is for people building from source or curious about how it works.

The `-rocm` build bundles an ONNX Runtime jar (`libs/onnxruntime-rocm.jar`) that combines:

- ONNX Runtime 1.21.0 Java bindings from Maven Central (`com.microsoft.onnxruntime:onnxruntime:1.21.0`)
- The Java/JNI shared library `libonnxruntime4j_jni.so`, recompiled from the upstream sources with `-DUSE_ROCM=1` so that `addROCM()` resolves to the real C API instead of throwing
- The ROCm-built `libonnxruntime.so.1.21.0` + `libonnxruntime_providers_rocm.so` + `libonnxruntime_providers_shared.so` from AMD's official Python wheel
- The auditwheel-bundled `librocm_smi64-*.so.7.7.60404` that the ROCm provider depends on at runtime

At mod init, `OnnxModel.preloadBundledRocmSmiOnce()` extracts the `librocm_smi64-*.so` from the classpath to a temp directory and `System.load()`s it, so that when libonnxruntime_providers_rocm.so later dlopen()s it by SONAME, ld.so finds it in its loaded-libraries cache.

The hybrid jar is reproducible — there's a build script that pulls the upstream Maven jar, the AMD Python wheel, and rebuilds the JNI shim against pinned versions. See the `/opt/td-phase3/ort-rocm-jar/` workspace in [the original benchmark repository][bench] for the exact recipe.

[bench]: https://github.com/anthropics/claude-code/issues  <!-- TODO: point at the actual repo once we publish the benchmark workspace -->
