# Terrain Diffusion MC, multi-loader migration

This archive starts from `MultiLoader-Template-1.21.11` and preserves the original project names, packages, filenames and identifiers.

## What was preserved

- `mod_id`: `terrain-diffusion-mc`
- Java package: `com.github.xandergos.terraindiffusionmc`
- Original Fabric source filenames and class names
- Original resource namespace: `assets/terrain-diffusion-mc` and `data/terrain-diffusion-mc`
- Original build variant properties: `useCuda`, `useDml`, `useCpu`, `buildCuda`, `buildDml`, `buildCpu`, `buildAll`
- Original local DirectML jar under `libs/onnxruntime-dml.jar`

## Current split

- `fabric/`: contains the original mod implementation with its Fabric/Yarn code intact.
- `common/`: intentionally left empty except template build support. Moving code here requires a Yarn-to-Mojang/Parchment mapping migration.
- `forge/` and `neoforge/`: loader entrypoint stubs using the same package and class name. They are present so the template structure stays valid, but they do not yet run the terrain pipeline.

## Why this is not a finished functional Forge/NeoForge port

The source mod is Fabric/Yarn. The receiver template compiles shared code using Mojang/Parchment mappings for cross-loader compatibility. The original implementation imports Yarn names such as `net.minecraft.registry.Registry`, `net.minecraft.util.Identifier`, `net.minecraft.server.world.ServerWorld`, and Fabric-only APIs such as `FabricLoader`, `CommandRegistrationCallback`, `ServerLifecycleEvents`, and `ServerWorldEvents`.

Putting those files directly in `common/` would break Forge/NeoForge compilation. They must be ported to Mojang/Parchment names and loader-specific services before the Forge and NeoForge modules can be functional.

## Next required porting work

1. Move loader-neutral classes from `fabric/src/main/java` into `common/src/main/java` only after converting Yarn mappings to Mojang/Parchment mappings.
2. Replace Fabric-only services with a platform abstraction, especially:
   - config directory lookup currently using `FabricLoader.getInstance().getConfigDir()`;
   - game directory lookup currently using `FabricLoader.getInstance().getGameDir()`;
   - server lifecycle callbacks;
   - world load callbacks;
   - command registration.
3. Recreate the init hooks in `fabric/`, `forge/`, and `neoforge/` so each loader calls the same common bootstrap methods.
4. Port mixin target names and method selectors for Forge/NeoForge where required.
5. Verify whether the hyphenated mod id `terrain-diffusion-mc` is accepted by the Forge/NeoForge versions targeted here. It was preserved because renaming it would violate the constraint.
