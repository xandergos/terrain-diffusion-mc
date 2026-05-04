package com.github.xandergos.terraindiffusionmc.client;

import com.github.xandergos.terraindiffusionmc.client.datagen.TerrainDiffusionLayerDataProvider;
import com.github.xandergos.terraindiffusionmc.client.datagen.TerrainDiffusionTranslationProvider;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public class TerrainDiffusionMcDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();

        pack.addProvider(TerrainDiffusionLayerDataProvider::new);
        pack.addProvider(TerrainDiffusionTranslationProvider::new);
    }
}
