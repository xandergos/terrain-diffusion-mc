package com.github.xandergos.terraindiffusionmc.debug.river;

/**
 * Hydrology tuning by biome. The terrain decides where water can go; this profile decides
 * how much persistent water exists there and how freely mature rivers can meander.
 */
public record TerrainBiomeHydrologyProfile(
        float runoffCoefficient,
        float evaporationMultiplier,
        float sourceMultiplier,
        float lakePersistence,
        float meanderMultiplier,
        boolean ocean
) {
    public static TerrainBiomeHydrologyProfile forBiome(short biomeId) {
        return switch (biomeId) {
            // Ocean sinks. They receive rivers, but do not seed inland river discharge.
            case 41, 44, 46, 48 -> new TerrainBiomeHydrologyProfile(0.00F, 0.70F, 0.00F, 2.20F, 0.10F, true);

            // Wet lowlands and tropical biomes.
            case 6 -> new TerrainBiomeHydrologyProfile(0.38F, 0.70F, 1.30F, 2.30F, 1.55F, false);
            case 23 -> new TerrainBiomeHydrologyProfile(0.78F, 0.82F, 1.45F, 1.35F, 1.25F, false);

            // Forest and taiga keep good permanent runoff.
            case 8, 108 -> new TerrainBiomeHydrologyProfile(0.62F, 0.88F, 1.12F, 1.15F, 1.00F, false);
            case 15, 115 -> new TerrainBiomeHydrologyProfile(0.58F, 0.78F, 1.15F, 1.10F, 0.82F, false);
            case 16, 116 -> new TerrainBiomeHydrologyProfile(0.64F, 0.66F, 1.35F, 1.25F, 0.62F, false);

            // Snow and highland biomes favor springs/snowmelt but resist wide meanders.
            case 3 -> new TerrainBiomeHydrologyProfile(0.56F, 0.66F, 1.18F, 1.10F, 0.72F, false);
            case 29, 31 -> new TerrainBiomeHydrologyProfile(0.52F, 0.78F, 1.25F, 1.05F, 0.72F, false);
            case 32, 33 -> new TerrainBiomeHydrologyProfile(0.70F, 0.58F, 1.65F, 1.30F, 0.24F, false);
            case 35, 19 -> new TerrainBiomeHydrologyProfile(0.42F, 1.05F, 0.95F, 0.62F, 0.18F, false);

            // Dry biomes mostly allow imported rivers; local permanent sources are rare.
            case 5 -> new TerrainBiomeHydrologyProfile(0.08F, 2.70F, 0.04F, 0.15F, 0.45F, false);
            case 26 -> new TerrainBiomeHydrologyProfile(0.14F, 2.20F, 0.08F, 0.20F, 0.35F, false);
            case 17 -> new TerrainBiomeHydrologyProfile(0.28F, 1.45F, 0.35F, 0.42F, 0.82F, false);

            // Default plains-like behavior.
            case 1 -> new TerrainBiomeHydrologyProfile(0.54F, 1.00F, 0.86F, 0.90F, 1.05F, false);
            default -> new TerrainBiomeHydrologyProfile(0.50F, 1.00F, 0.85F, 0.85F, 0.85F, false);
        };
    }
}
