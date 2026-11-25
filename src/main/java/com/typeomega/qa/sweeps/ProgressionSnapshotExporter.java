package com.typeomega.qa.sweeps;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.Element;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.generator.VeinGenerator;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.typeomega.qa.OmegaQA;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

/**
 * Exports live server registries ({@code items.txt}, {@code fluids.txt}, {@code elements.tsv},
 * {@code machines.jsonl}, {@code worldgen.jsonl}, {@code recipes.jsonl.gz}) in the exact format expected by
 * {@code reach.py} and {@code questgen.py}.
 */
public final class ProgressionSnapshotExporter {

    private ProgressionSnapshotExporter() {}

    public static void export(MinecraftServer server, Path dir) {
        try {
            exportInternal(server, dir);
            OmegaQA.LOGGER.info("Omega-QA exported runtime snapshot to {}", dir.toAbsolutePath());
        } catch (IOException | RuntimeException e) {
            OmegaQA.LOGGER.error("Omega-QA runtime snapshot export failed at {}", dir.toAbsolutePath(), e);
        }
    }

    private static void exportInternal(MinecraftServer server, Path dir) throws IOException {
        Files.createDirectories(dir);
        RegistryAccess access = server.registryAccess();

        try (var gzOut = new GZIPOutputStream(Files.newOutputStream(dir.resolve("recipes.jsonl.gz")));
                var writer = new BufferedWriter(new OutputStreamWriter(gzOut, StandardCharsets.UTF_8))) {
            for (Recipe<?> recipe : server.getRecipeManager().getRecipes()) {
                JsonObject json;
                try {
                    json = recipe instanceof GTRecipe gt ? gtRecipe(gt) : plainRecipe(recipe, access);
                } catch (RuntimeException e) {
                    continue;
                }
                writer.write(json.toString());
                writer.newLine();
            }
        }

        try (BufferedWriter out = Files.newBufferedWriter(dir.resolve("machines.jsonl"), StandardCharsets.UTF_8)) {
            for (MachineDefinition definition : GTRegistries.MACHINES.values()) {
                ResourceLocation item = ForgeRegistries.ITEMS.getKey(definition.getItem());
                if (item == null) continue;
                JsonObject json = new JsonObject();
                json.addProperty("item", item.toString());
                json.addProperty("tier", definition.getTier());
                json.addProperty("multiblock", definition instanceof MultiblockMachineDefinition);
                JsonArray types = new JsonArray();
                for (GTRecipeType type : definition.getRecipeTypes()) {
                    if (type != null) types.add(type.registryName.toString());
                }
                json.add("types", types);
                out.write(json.toString());
                out.newLine();
            }
        }

        try (BufferedWriter out = Files.newBufferedWriter(dir.resolve("worldgen.jsonl"), StandardCharsets.UTF_8)) {
            for (var entry : GTRegistries.ORE_VEINS.entries()) {
                GTOreDefinition vein = entry.getValue();
                JsonObject json = new JsonObject();
                json.addProperty("vein", entry.getKey().toString());
                json.add("dimensions", dimensions(vein.dimensionFilter(),
                        vein.layer() == null ? Set.of() : vein.layer().getLevels()));
                JsonArray materials = new JsonArray();
                JsonArray blocks = new JsonArray();
                if (vein.veinGenerator() != null) {
                    for (VeinGenerator.VeinEntry veinEntry : vein.veinGenerator().getAllEntries()) {
                        veinEntry.vein().ifLeft(state -> {
                            ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
                            if (key != null) blocks.add(key.toString());
                        }).ifRight(mat -> materials.add(mat.getResourceLocation().toString()));
                    }
                }
                json.add("materials", materials);
                json.add("blocks", blocks);
                out.write(json.toString());
                out.newLine();
            }
            for (var entry : GTRegistries.BEDROCK_FLUID_DEFINITIONS.entries()) {
                BedrockFluidDefinition definition = entry.getValue();
                JsonObject json = new JsonObject();
                json.addProperty("bedrock_fluid", entry.getKey().toString());
                ResourceLocation fluid = definition.getStoredFluid() == null ? null :
                        ForgeRegistries.FLUIDS.getKey(definition.getStoredFluid().get());
                json.addProperty("fluid", fluid == null ? "" : fluid.toString());
                json.add("dimensions", dimensions(definition.getDimensionFilter(), Set.of()));
                out.write(json.toString());
                out.newLine();
            }
        }

        Files.write(dir.resolve("items.txt"),
                new TreeSet<>(ForgeRegistries.ITEMS.getKeys().stream().map(ResourceLocation::toString).toList()));
        Files.write(dir.resolve("fluids.txt"),
                new TreeSet<>(ForgeRegistries.FLUIDS.getKeys().stream().map(ResourceLocation::toString).toList()));
        writeElementsTsv(dir.resolve("elements.tsv"));
    }

    private static void writeElementsTsv(Path path) throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (Material material : GTCEuAPI.materialManager.getRegisteredMaterials()) {
            Element element = material.getElement();
            if (element == null) continue;
            ResourceLocation materialId = material.getResourceLocation();
            ResourceLocation bucket = ResourceLocation.tryBuild(materialId.getNamespace(),
                    materialId.getPath() + "_bucket");
            rows.add(new String[] {
                    String.valueOf(element.protons()),
                    element.symbol(),
                    element.name(),
                    materialId.toString(),
                    itemId(ChemicalHelper.get(TagPrefix.ingot, material)),
                    itemId(ChemicalHelper.get(TagPrefix.dust, material)),
                    itemId(ChemicalHelper.get(TagPrefix.gem, material)),
                    bucket != null && ForgeRegistries.ITEMS.containsKey(bucket) ? bucket.toString() : ""
            });
        }
        rows.sort(Comparator.comparingLong((String[] row) -> Long.parseLong(row[0])).thenComparing(row -> row[3]));
        List<String> lines = new ArrayList<>(rows.size() + 1);
        lines.add("protons\tsymbol\tname\tmaterial\tingot\tdust\tgem\tbucket");
        for (String[] row : rows) lines.add(String.join("\t", row));
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private static String itemId(ItemStack stack) {
        if (stack.isEmpty()) return "";
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static JsonObject plainRecipe(Recipe<?> recipe, RegistryAccess access) {
        JsonObject json = new JsonObject();
        json.addProperty("id", recipe.getId().toString());
        ResourceLocation type = ForgeRegistries.RECIPE_TYPES.getKey(recipe.getType());
        json.addProperty("type", type == null ? "unknown" : type.toString());
        JsonArray in = new JsonArray();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty()) in.add(itemIngredient(ingredient, 0, 0));
        }
        json.add("in", in);
        JsonArray out = new JsonArray();
        ItemStack result = recipe.getResultItem(access);
        if (result != null && !result.isEmpty()) out.add(itemStack(result));
        json.add("out", out);
        return json;
    }

    private static JsonObject gtRecipe(GTRecipe recipe) {
        JsonObject json = new JsonObject();
        json.addProperty("id", recipe.getId().toString());
        json.addProperty("type", recipe.recipeType.registryName.toString());
        json.addProperty("duration", recipe.duration);
        json.add("in", contents(recipe.inputs));
        json.add("out", contents(recipe.outputs));
        json.add("tick_in", contents(recipe.tickInputs));
        json.add("tick_out", contents(recipe.tickOutputs));
        JsonArray conditions = new JsonArray();
        for (RecipeCondition<?> condition : recipe.conditions) {
            try {
                conditions.add(condition.serialize());
            } catch (RuntimeException e) {
                JsonObject unknown = new JsonObject();
                unknown.addProperty("type", condition.getClass().getName());
                conditions.add(unknown);
            }
        }
        json.add("conditions", conditions);
        return json;
    }

    private static JsonArray contents(Map<RecipeCapability<?>, List<Content>> map) {
        JsonArray array = new JsonArray();
        for (var entry : map.entrySet()) {
            for (Content content : entry.getValue()) {
                Object value = content.content;
                if (entry.getKey() == ItemRecipeCapability.CAP && value instanceof Ingredient ingredient) {
                    array.add(itemIngredient(ingredient, content.chance, content.maxChance));
                } else if (entry.getKey() == FluidRecipeCapability.CAP && value instanceof FluidIngredient fluid) {
                    JsonObject json = new JsonObject();
                    JsonArray ids = new JsonArray();
                    int amount = 0;
                    for (FluidStack stack : fluid.getStacks()) {
                        ResourceLocation key = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
                        if (key != null && !contains(ids, key.toString())) ids.add(key.toString());
                        amount = Math.max(amount, stack.getAmount());
                    }
                    json.add("fluid", ids);
                    json.addProperty("mb", amount);
                    chance(json, content.chance, content.maxChance);
                    array.add(json);
                } else if (entry.getKey() == EURecipeCapability.CAP && value instanceof EnergyStack energy) {
                    JsonObject json = new JsonObject();
                    json.addProperty("eu", energy.voltage());
                    json.addProperty("amps", energy.amperage());
                    array.add(json);
                } else {
                    JsonObject json = new JsonObject();
                    json.addProperty("cap", entry.getKey().name);
                    array.add(json);
                }
            }
        }
        return array;
    }

    private static JsonObject itemIngredient(Ingredient ingredient, int chance, int maxChance) {
        JsonObject json = new JsonObject();
        Ingredient inner = SizedIngredient.getInner(ingredient);
        if (inner instanceof IntCircuitIngredient circuit) {
            json.addProperty("circuit", circuit.toJson().getAsJsonObject().get("configuration").getAsInt());
            return json;
        }
        JsonArray ids = new JsonArray();
        int count = 1;
        for (ItemStack stack : ingredient.getItems()) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (key != null && !contains(ids, key.toString())) ids.add(key.toString());
            count = Math.max(count, stack.getCount());
        }
        json.add("item", ids);
        json.addProperty("n", count);
        chance(json, chance, maxChance);
        return json;
    }

    private static JsonObject itemStack(ItemStack stack) {
        JsonObject json = new JsonObject();
        JsonArray ids = new JsonArray();
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key != null) ids.add(key.toString());
        json.add("item", ids);
        json.addProperty("n", stack.getCount());
        return json;
    }

    private static JsonArray dimensions(@Nullable Set<ResourceKey<Level>> filter, Set<ResourceLocation> levels) {
        Set<String> names = new TreeSet<>();
        if (filter != null) filter.forEach(key -> names.add(key.location().toString()));
        levels.forEach(level -> names.add(level.toString()));
        JsonArray array = new JsonArray();
        names.forEach(array::add);
        return array;
    }

    private static void chance(JsonObject json, int chance, int maxChance) {
        if (maxChance > 0 && chance != maxChance) {
            json.addProperty("chance", chance);
            json.addProperty("max_chance", maxChance);
        }
    }

    private static boolean contains(JsonArray array, String value) {
        for (var element : array) {
            if (element.getAsString().equals(value)) return true;
        }
        return false;
    }
}
