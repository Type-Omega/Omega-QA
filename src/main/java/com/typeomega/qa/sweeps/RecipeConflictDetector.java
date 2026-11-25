package com.typeomega.qa.sweeps;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import com.typeomega.lib.test.SweepResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Detects colliding {@link GTRecipe} lookup signatures (identical inputs, programmed circuit, and conditions) within
 * a {@link GTRecipeType}.
 */
public final class RecipeConflictDetector {

    private RecipeConflictDetector() {}

    public static SweepResult detect(RecipeManager manager, String namespace) {
        SweepResult result = new SweepResult(namespace + " recipe lookup conflicts");
        Map<GTRecipeType, Map<String, GTRecipe>> seenByType = new HashMap<>();

        for (Recipe<?> raw : manager.getRecipes()) {
            if (!(raw instanceof GTRecipe gt) || !namespace.equals(gt.getId().getNamespace())) continue;
            result.checked();
            String signature = signature(gt);
            Map<String, GTRecipe> bySig = seenByType.computeIfAbsent(gt.recipeType, t -> new HashMap<>());
            GTRecipe previous = bySig.putIfAbsent(signature, gt);
            if (previous != null && !previous.getId().equals(gt.getId())) {
                result.fail(gt.recipeType.registryName + ": " + previous.getId() + " collides with " + gt.getId() +
                        " (" + signature + ")");
            }
        }
        return result;
    }

    static String signature(GTRecipe recipe) {
        List<String> items = new ArrayList<>();
        int circuit = -1;
        for (Content c : recipe.getInputContents(ItemRecipeCapability.CAP)) {
            Ingredient ing = ItemRecipeCapability.CAP.of(c.content);
            Ingredient inner = SizedIngredient.getInner(ing);
            if (inner instanceof IntCircuitIngredient circuitIng) {
                var json = circuitIng.toJson().getAsJsonObject();
                circuit = json.has("configuration") ? json.get("configuration").getAsInt() : 0;
            } else {
                TreeSet<String> ids = new TreeSet<>();
                int count = 0;
                for (ItemStack stack : ing.getItems()) {
                    ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (key != null) ids.add(key.toString());
                    count = Math.max(count, stack.getCount());
                }
                items.add(ids + "*" + count);
            }
        }
        Collections.sort(items);

        List<String> fluids = new ArrayList<>();
        for (Content c : recipe.getInputContents(FluidRecipeCapability.CAP)) {
            FluidIngredient fluid = FluidRecipeCapability.CAP.of(c.content);
            TreeSet<String> ids = new TreeSet<>();
            int mb = 0;
            for (FluidStack stack : fluid.getStacks()) {
                ResourceLocation key = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
                if (key != null) ids.add(key.toString());
                mb = Math.max(mb, stack.getAmount());
            }
            fluids.add(ids + "*" + mb);
        }
        Collections.sort(fluids);

        List<String> conds = new ArrayList<>();
        for (RecipeCondition<?> cond : recipe.conditions) {
            try {
                conds.add(cond.serialize().toString());
            } catch (RuntimeException e) {
                conds.add(cond.getClass().getSimpleName());
            }
        }
        Collections.sort(conds);

        return "circuit=" + circuit + "|items=" + items + "|fluids=" + fluids + "|cond=" + conds;
    }
}
