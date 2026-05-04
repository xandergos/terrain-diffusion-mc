package com.github.xandergos.terraindiffusionmc.client.datagen;

import com.github.xandergos.terraindiffusionmc.block.ModBlocks;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.block.Block;
import net.minecraft.data.DataOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.DataWriter;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.github.xandergos.terraindiffusionmc.TerrainDiffusionMc.MOD_ID;

/**
 * Generates every JSON asset/data file required by {@link com.github.xandergos.terraindiffusionmc.block.LayeredBlock}.
 *
 * <p>The block registry remains the source of truth. Adding a new layer block in {@link ModBlocks}
 * automatically generates blockstates, block models, item models, item definitions and loot tables
 * on the next {@code runDatagen} pass.</p>
 */
public final class TerrainDiffusionLayerDataProvider implements DataProvider {

    private static final String[] SIDE_NAMES = {"north", "south", "west", "east"};

    private final FabricDataOutput output;

    public TerrainDiffusionLayerDataProvider(FabricDataOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(DataWriter writer) {
        List<CompletableFuture<?>> writes = new ArrayList<>();

        DataOutput.PathResolver blockstates = output.getResolver(DataOutput.OutputType.RESOURCE_PACK, "blockstates");
        DataOutput.PathResolver blockModels = output.getResolver(DataOutput.OutputType.RESOURCE_PACK, "models/block");
        DataOutput.PathResolver itemModels = output.getResolver(DataOutput.OutputType.RESOURCE_PACK, "models/item");
        DataOutput.PathResolver itemDefinitions = output.getResolver(DataOutput.OutputType.RESOURCE_PACK, "items");
        DataOutput.PathResolver lootTables = output.getResolver(DataOutput.OutputType.DATA_PACK, "loot_table/blocks");

        for (ModBlocks.LayeredBlockInfo layer : ModBlocks.getLayeredBlocks()) {
            Identifier blockId = layer.blockId();
            String name = layer.name();
            String texture = vanillaBlockTexture(layer.fullBlock());

            writes.add(write(writer, blockstates.resolveJson(blockId), blockstateJson(name)));
            writes.add(write(writer, itemModels.resolveJson(blockId), itemModelJson(name)));
            writes.add(write(writer, itemDefinitions.resolveJson(blockId), itemDefinitionJson(name)));
            writes.add(write(writer, lootTables.resolveJson(blockId), lootTableJson(blockId)));

            for (int layers = 1; layers <= 7; layers++) {
                writes.add(write(writer, blockModels.resolveJson(layerModelId(name, layers, false)), layerModelJson(texture, layers, false)));
                writes.add(write(writer, blockModels.resolveJson(layerModelId(name, layers, true)), layerModelJson(texture, layers, true)));
            }
        }

        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Terrain Diffusion layered block assets";
    }

    private static CompletableFuture<?> write(DataWriter writer, Path path, JsonObject json) {
        return DataProvider.writeToPath(writer, json, path);
    }

    private static Identifier layerModelId(String name, int layers, boolean snowy) {
        return Identifier.of(MOD_ID, name + "_layer/layer" + layers + (snowy ? "_snowy" : ""));
    }

    private static JsonObject blockstateJson(String name) {
        JsonObject root = new JsonObject();
        JsonObject variants = new JsonObject();

        for (int layers = 1; layers <= 7; layers++) {
            for (boolean snowy : new boolean[]{false, true}) {
                for (boolean waterlogged : new boolean[]{false, true}) {
                    JsonObject variant = new JsonObject();
                    variant.addProperty("model", MOD_ID + ":block/" + name + "_layer/layer" + layers + (snowy ? "_snowy" : ""));
                    variants.add("layers=" + layers + ",snowy=" + snowy + ",waterlogged=" + waterlogged, variant);
                }
            }
        }

        root.add("variants", variants);
        return root;
    }

    private static JsonObject layerModelJson(String texture, int layers, boolean snowy) {
        int height = layers * 2;
        JsonObject root = new JsonObject();
        root.addProperty("parent", "minecraft:block/block");
        root.add("textures", textures(texture, snowy));

        JsonArray elements = new JsonArray();
        elements.add(layerElement(0, height, "#all", true, 16 - height));
        if (snowy) {
            elements.add(layerElement(height, Math.min(16, height + 2), "#snow", false, 14));
        }
        root.add("elements", elements);
        root.add("display", display());
        return root;
    }

    private static JsonObject textures(String texture, boolean snowy) {
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", "#all");
        if (snowy) {
            textures.addProperty("snow", "minecraft:block/snow");
        }
        textures.addProperty("all", texture);
        return textures;
    }

    private static JsonObject layerElement(int fromY, int toY, String texture, boolean cullSides, int sideUvMinY) {
        JsonObject element = new JsonObject();
        element.add("from", array(0, fromY, 0));
        element.add("to", array(16, toY, 16));

        JsonObject faces = new JsonObject();

        JsonObject down = face(texture);
        if (cullSides) {
            down.addProperty("cullface", "down");
        }
        faces.add("down", down);
        JsonObject up = face(texture);
        if (!cullSides && toY >= 16) {
            up.addProperty("cullface", "up");
        }
        faces.add("up", up);

        for (String side : SIDE_NAMES) {
            JsonObject sideFace = face(texture);
            if (cullSides) {
                sideFace.addProperty("cullface", side);
            }
            sideFace.add("uv", array(0, sideUvMinY, 16, 16));
            faces.add(side, sideFace);
        }

        element.add("faces", faces);
        return element;
    }

    private static JsonObject face(String texture) {
        JsonObject face = new JsonObject();
        face.addProperty("texture", texture);
        return face;
    }

    private static JsonObject itemModelJson(String name) {
        JsonObject root = new JsonObject();
        root.addProperty("parent", MOD_ID + ":block/" + name + "_layer/layer1");
        return root;
    }

    private static JsonObject itemDefinitionJson(String name) {
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", MOD_ID + ":block/" + name + "_layer/layer1");

        JsonObject root = new JsonObject();
        root.add("model", model);
        return root;
    }

    private static JsonObject lootTableJson(Identifier blockId) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", blockId.toString());

        JsonArray entries = new JsonArray();
        entries.add(entry);

        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        pool.add("entries", entries);

        JsonArray pools = new JsonArray();
        pools.add(pool);

        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:block");
        root.add("pools", pools);
        return root;
    }

    private static JsonObject display() {
        JsonObject display = new JsonObject();
        display.add("gui", displayTransform(array(30, 225, 0), array(0, 0, 0), array(0.625, 0.625, 0.625)));
        display.add("ground", displayTransform(null, array(0, 3, 0), array(0.25, 0.25, 0.25)));
        display.add("fixed", displayTransform(null, null, array(0.5, 0.5, 0.5)));
        display.add("thirdperson_righthand", displayTransform(array(75, 45, 0), array(0, 2.5, 0), array(0.375, 0.375, 0.375)));
        display.add("firstperson_righthand", displayTransform(array(0, 45, 0), null, array(0.4, 0.4, 0.4)));
        display.add("firstperson_lefthand", displayTransform(array(0, 225, 0), null, array(0.4, 0.4, 0.4)));
        return display;
    }

    private static JsonObject displayTransform(JsonArray rotation, JsonArray translation, JsonArray scale) {
        JsonObject transform = new JsonObject();
        if (rotation != null) {
            transform.add("rotation", rotation);
        }
        if (translation != null) {
            transform.add("translation", translation);
        }
        if (scale != null) {
            transform.add("scale", scale);
        }
        return transform;
    }

    private static JsonArray array(Number... values) {
        JsonArray array = new JsonArray();
        for (Number value : values) {
            array.add(value);
        }
        return array;
    }

    private static String vanillaBlockTexture(Block block) {
        Identifier id = Registries.BLOCK.getId(block);
        return id.getNamespace() + ":block/" + id.getPath();
    }
}
