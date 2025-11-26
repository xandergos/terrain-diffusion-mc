package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;

public class HeightConverter {
    private static final float RESOLUTION = TerrainDiffusionConfig.heightConverterResolution();
    private static final float GAMMA = TerrainDiffusionConfig.heightConverterGamma();
    private static final float C = TerrainDiffusionConfig.heightConverterC();
    private static final int SEA_LEVEL = 63;
    
    public static int convertToMinecraftHeight(short meters) {
        int baseY;
        
        if (meters >= 0) {
            double transformed = Math.pow(meters + C, GAMMA) - Math.pow(C, GAMMA);
            baseY = (int) (transformed / RESOLUTION);
        } else {
            baseY = (int) (-Math.sqrt(Math.abs(meters) + 10) + Math.sqrt(10.0)) - 1;
        }
        
        return baseY + SEA_LEVEL;
    }
}

