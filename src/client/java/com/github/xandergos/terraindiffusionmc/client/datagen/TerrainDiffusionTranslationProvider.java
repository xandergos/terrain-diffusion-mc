package com.github.xandergos.terraindiffusionmc.client.datagen;

import com.github.xandergos.terraindiffusionmc.TerrainDiffusionMc;
import com.github.xandergos.terraindiffusionmc.block.ModBlocks;
import com.github.xandergos.terraindiffusionmc.block.ModBlocks.LayeredBlockInfo;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Generates every translations.
 */
public final class TerrainDiffusionTranslationProvider extends FabricLanguageProvider {

    public TerrainDiffusionTranslationProvider(
            FabricDataOutput output,
            CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup
    ) {
        super(output, "en_us", registryLookup);
    }

    @Override
    public void generateTranslations(
            RegistryWrapper.WrapperLookup registryLookup,
            TranslationBuilder builder
    ) {
        addBaseTranslations(builder);
        addLayerBlockTranslations(builder);
    }

    private static void addBaseTranslations(TranslationBuilder builder) {
        String modId = TerrainDiffusionMc.MOD_ID;

        builder.add("generator." + modId + ".terrain_diffusion", "Terrain Diffusion");
        builder.add(modId + ".world_settings.title", "Terrain Settings");
        builder.add(modId + ".world_settings.open_button", "Terrain Settings");
        builder.add(modId + ".world_settings.scale", "World Scale");
        builder.add(modId + ".world_settings.world_scale_description", "Set World Scale for this world.");
        builder.add(modId + ".world_settings.scale_error", "Scale must be an integer >= 1");
    }

    private static void addLayerBlockTranslations(TranslationBuilder builder) {
        for (LayeredBlockInfo layer : ModBlocks.getLayeredBlocks()) {
            Identifier id = layer.blockId();
            String displayName = toEnglishName(layer.name()) + " Layer";

            builder.add("block." + id.getNamespace() + "." + id.getPath(), displayName);
            builder.add("item." + id.getNamespace() + "." + id.getPath(), displayName);
        }
    }

    private static String toEnglishName(String idPath) {
        String[] parts = idPath.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            if (result.length() > 0) {
                result.append(' ');
            }

            result.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                result.append(part.substring(1));
            }
        }

        return result.toString();
    }
}