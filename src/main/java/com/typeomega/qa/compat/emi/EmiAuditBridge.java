package com.typeomega.qa.compat.emi;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import com.typeomega.lib.test.SweepResult;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;

import java.util.Set;

/** EMI-only implementation bridge isolated behind {@link EmiAuditHelper}. */
final class EmiAuditBridge {

    private EmiAuditBridge() {}

    static SweepResult auditRecipes(String namespace, Set<ResourceLocation> forbiddenRecipeIds) {
        SweepResult result = new SweepResult("EMI recipes for " + namespace);
        EmiRecipeManager manager = EmiApi.getRecipeManager();
        if (manager == null) {
            result.fail("EMI recipe manager is not initialized");
            return result;
        }
        for (EmiRecipe recipe : manager.getRecipes()) {
            ResourceLocation id = recipe.getId();
            if (id == null || !namespace.equals(id.getNamespace())) continue;
            result.checked();
            if (forbiddenRecipeIds.contains(id)) {
                result.fail(id + ": gated/removed recipe still visible in EMI");
            }
            for (EmiIngredient input : recipe.getInputs()) {
                if (input.isEmpty()) continue;
                boolean allBarrier = !input.getEmiStacks().isEmpty() &&
                        input.getEmiStacks().stream().allMatch(s -> s.getItemStack().is(Items.BARRIER));
                if (allBarrier) {
                    result.fail(id + ": EMI input resolves only to BARRIER");
                }
            }
            for (EmiStack output : recipe.getOutputs()) {
                if (!output.isEmpty() && output.getItemStack().is(Items.BARRIER)) {
                    result.fail(id + ": EMI output resolves to BARRIER");
                }
            }
        }
        return result;
    }
}
