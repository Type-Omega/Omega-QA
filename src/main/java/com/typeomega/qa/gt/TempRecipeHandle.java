package com.typeomega.qa.gt;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

import com.typeomega.qa.OmegaQA;
import com.typeomega.qa.dsl.OmegaTestHelper;
import com.typeomega.qa.log.EventCategory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Safely stages temporary {@link GTRecipe} entries alongside all live recipes of a {@link GTRecipeType} and
 * guarantees restoration in {@code afterTest} (avoiding GTCEu's empty-lookup trap).
 */
public final class TempRecipeHandle implements AutoCloseable {

    private static final Map<GTRecipeType, TempRecipeHandle> ACTIVE_STAGING = new ConcurrentHashMap<>();

    private final OmegaTestHelper helper;
    private final GTRecipeType recipeType;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private TempRecipeHandle(OmegaTestHelper helper, GTRecipeType recipeType) {
        this.helper = helper;
        this.recipeType = recipeType;
    }

    public static TempRecipeHandle stage(GameTestHelper helper, GTRecipeType type, GTRecipe... extra) {
        return stage(OmegaTestHelper.wrap(helper), type, extra);
    }

    public static TempRecipeHandle stage(OmegaTestHelper helper, GTRecipeType type, GTRecipe... extra) {
        Objects.requireNonNull(type, "type");
        TempRecipeHandle handle = new TempRecipeHandle(helper, type);
        TempRecipeHandle previous = ACTIVE_STAGING.put(type, handle);
        if (previous != null && !previous.closed.get()) {
            String warn = "Concurrent TempRecipeHandle staging on " + type.registryName +
                    " detected (previous test " + previous.helper.session().testId() + ")";
            OmegaQA.LOGGER.warn(warn);
            helper.log(EventCategory.RECIPE, warn);
        }
        stageWithRealRecipes(type, extra);
        helper.log(EventCategory.RECIPE,
                "Staged " + extra.length + " temp recipe(s) on " + type.registryName);
        helper.afterTest(handle::close);
        return handle;
    }

    /** Re-stages every real {@link GTRecipe} from {@code RecipeManager} plus optional {@code extra} recipes. */
    public static void stageWithRealRecipes(GTRecipeType type, GTRecipe... extra) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("Cannot stage recipes without a running MinecraftServer");
        }
        // EN: completeStaging() wipes the RecipeDB; re-add every real recipe first so later tests keep working.
        // PT: completeStaging() limpa o RecipeDB; re-adicionamos todas as receitas reais para não quebrar outros
        // testes.
        // ZH: completeStaging() 会清空 RecipeDB；先重新添加所有真实配方，确保后续测试不受影响。
        type.getAdditionHandler().beginStaging();
        for (GTRecipe recipe : server.getRecipeManager().getAllRecipesFor(type)) {
            type.getAdditionHandler().addStaging(recipe);
        }
        if (extra != null) {
            for (GTRecipe recipe : extra) {
                if (recipe != null) type.getAdditionHandler().addStaging(recipe);
            }
        }
        type.getAdditionHandler().completeStaging();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        ACTIVE_STAGING.remove(recipeType, this);
        stageWithRealRecipes(recipeType);
        helper.log(EventCategory.RECIPE, "Restored real recipes on " + recipeType.registryName);
    }
}
