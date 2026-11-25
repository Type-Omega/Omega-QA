package com.typeomega.qa.compat.emi;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

import com.typeomega.lib.test.SweepResult;
import com.typeomega.qa.dsl.OmegaTestHelper;

import java.util.Set;

/**
 * {@code ModList}-guarded EMI audit helper checking custom categories and gated recipe visibility when EMI client
 * runtime is active.
 */
public final class EmiAuditHelper {

    private EmiAuditHelper() {}

    public static SweepResult auditRecipes(GameTestHelper helper, String namespace,
                                           Set<ResourceLocation> forbiddenRecipeIds) {
        OmegaTestHelper oh = OmegaTestHelper.wrap(helper);
        oh.assumeModLoaded("emi");
        oh.assumeTrue(FMLEnvironment.dist == Dist.CLIENT, "EMI recipe manager is client-only");
        return EmiAuditBridge.auditRecipes(namespace, forbiddenRecipeIds);
    }
}
