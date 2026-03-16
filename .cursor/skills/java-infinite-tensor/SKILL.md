---
name: java-infinite-tensor
description: Java reimplementation of the infinite-tensor Python library for the terrain-diffusion-mc mod. Use when writing or editing code in the infinitetensor/ package, porting WorldPipeline logic from Python, or working with InfiniteTensor, MemoryTileStore, TensorWindow, FloatTensor, TensorFunction, or BatchTensorFunction.
---

# Java Infinite Tensor

Java port of [infinite-tensor](https://github.com/xandergos/infinite-tensor) living in `com.github.xandergos.terraindiffusionmc.infinitetensor`.  Only the **direct** cache strategy is implemented (no HDF5 / indirect tiles).

## Python → Java mapping

| Python | Java |
|--------|------|
| `TensorWindow(size, stride, offset)` | `new TensorWindow(int[] size, int[] stride, int[] offset)` |
| `tile_store.get_or_create(id, shape, f, window)` | `store.getOrCreate(id, Integer[] shape, TensorFunction f, TensorWindow window, ...)` |
| `tile_store.get_or_create(..., batch_size=N)` | `store.getOrCreateBatched(..., batchSize)` |
| `tensor[i1:i2, j1:j2]` | `tensor.getSlice(int[] start, int[] end)` |
| `torch.Tensor` (CPU) | `FloatTensor` |
| `MemoryTileStore()` | `new MemoryTileStore()` |
| `cache_method='direct', cache_limit=N` | `cacheLimitBytes` (pass `Long.MAX_VALUE` for unlimited) |

## Creating tensors

```java
MemoryTileStore store = new MemoryTileStore();

// Non-batched (equivalent to Python f(ctx, *args))
TensorWindow outWindow = new TensorWindow(new int[]{7, 64, 64}, new int[]{7, 48, 48});
InfiniteTensor t = store.getOrCreate(
    "my_tensor",
    new Integer[]{7, null, null},         // null = infinite dim
    (windowIndex, args) -> computeWindow(windowIndex, args),
    outWindow,
    new InfiniteTensor[]{dep},            // upstream deps (empty array if none)
    new TensorWindow[]{depWindow},
    100L * 1024 * 1024                    // 100 MB cache limit
);

// Batched (equivalent to Python f(ctxs, list_of_dep_tensors))
InfiniteTensor bt = store.getOrCreateBatched(
    "batched_tensor",
    new Integer[]{6, null, null},
    (windowIndices, args) -> computeBatch(windowIndices, args),
    outWindow,
    new InfiniteTensor[]{dep},
    new TensorWindow[]{depWindow},
    100L * 1024 * 1024,
    4                                     // batch size
);
```

## Slicing

```java
// Python: result = tensor[0:7, i1:i2, j1:j2]
FloatTensor result = tensor.getSlice(
    new int[]{0, i1, j1},
    new int[]{7, i2, j2}
);
// result.data is row-major float[]; result.shape = {7, i2-i1, j2-j1}
```

## FloatTensor layout

Row-major (C order). Access element `[c, y, x]`:
```java
float v = tensor.data[c * tensor.strides[0] + y * tensor.strides[1] + x];
```

`byteSize()` returns `data.length * 4L` (used for cache accounting).

## TensorWindow math

For window index `w[]`, the covered pixel range in dimension `d` is:
```
[w[d] * stride[d] + offset[d],  w[d] * stride[d] + offset[d] + size[d])
```

Negative offsets are valid (e.g. `offset=(0,-1,-1)` for the coarse conditioning window in `WorldPipeline`).

## Key constraints

1. **Direct cache only** — no tile accumulation; each window output is stored whole and summed on read. Overlapping windows (stride < size) are automatically summed via `FloatTensor.addFrom`.
2. **Deterministic functions** — outputs are cached as if pure; do not use randomness that isn't seeded by `windowIndex`.
3. **Output shape must match `outputWindow.size` exactly** — validated at runtime.
4. **No thread safety** — `MemoryTileStore` and `InfiniteTensor` are not synchronized.

## WorldPipeline port notes

The Python `WorldPipeline` uses `caching_strategy='direct'` with `cache_limit=100MB`.  The three tensor stages map as:

| Python tensor | Java id | Shape | Batched |
|---------------|---------|-------|---------|
| `self.coarse` | `"base_coarse_map"` | `(7, ∞, ∞)` | No |
| `self.latents` | `"init_latent_map"` / `"step_latent_map_N"` | `(6, ∞, ∞)` | Yes |
| `self.residual` | `"init_residual_map"` | `(2, ∞, ∞)` | No |

Python source: `/home/agos/dev/terrain-diffusion/terrain_diffusion/inference/world_pipeline.py`  
Python library: `/home/agos/dev/infinite-tensor/infinite_tensor/`
