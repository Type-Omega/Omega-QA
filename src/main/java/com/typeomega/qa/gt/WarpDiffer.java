package com.typeomega.qa.gt;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import net.minecraft.core.BlockPos;

import com.typeomega.qa.log.EventCategory;
import com.typeomega.qa.log.EventLog;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Observes state transitions during {@link TimeWarpRunner} simulation and emits structured recipe, maintenance, and
 * structure lifecycle entries into {@link EventLog}.
 */
public final class WarpDiffer {

    private static final class Snapshot {

        @Nullable
        String recipeId;
        int lastMilestone = -1;
        boolean wasWorking;
        boolean wasFormed = true;
        boolean hadMaintenanceProblems;
    }

    private final Map<BlockPos, Snapshot> states = new HashMap<>();

    public void sample(long warpTick, MetaMachine machine, EventLog log) {
        Snapshot snap = states.computeIfAbsent(machine.getPos(), p -> new Snapshot());
        String machineId = machine.getDefinition().getId().toString();

        if (machine instanceof MultiblockControllerMachine ctrl) {
            boolean formed = ctrl.isFormed();
            if (snap.wasFormed && !formed) {
                log.record(warpTick, EventCategory.STRUCTURE, "StructureDeformed: " + machineId + " at " +
                        ctrl.getPos());
            }
            snap.wasFormed = formed;

            boolean hasProblem = false;
            for (IMultiPart part : ctrl.getParts()) {
                if (part instanceof IMaintenanceMachine maint && maint.hasMaintenanceProblems()) {
                    hasProblem = true;
                    break;
                }
            }
            if (!snap.hadMaintenanceProblems && hasProblem) {
                log.record(warpTick, EventCategory.STRUCTURE, "MaintenanceIssueAppeared: " + machineId);
            }
            snap.hadMaintenanceProblems = hasProblem;
        }

        if (machine instanceof IRecipeLogicMachine rlm) {
            RecipeLogic logic = rlm.getRecipeLogic();
            GTRecipe recipe = logic.getLastRecipe();
            String currentRecipeId = recipe == null ? null : recipe.getId().toString();
            boolean working = logic.isWorking();
            int max = Math.max(1, logic.getMaxProgress());
            int pct = (int) Math.min(100, Math.round(logic.getProgressPercent() * 100.0));

            if (working && (!snap.wasWorking || (currentRecipeId != null && !currentRecipeId.equals(snap.recipeId)))) {
                long eut = recipe != null ? recipe.getInputEUt().getTotalEU() : 0L;
                log.record(warpTick, EventCategory.RECIPE,
                        "RecipeStarted: " + currentRecipeId + " eut=" + eut + " duration=" + max);
                snap.lastMilestone = 0;
            } else if (working) {
                int milestone = (pct / 25) * 25;
                if (milestone >= 25 && milestone < 100 && milestone > snap.lastMilestone) {
                    snap.lastMilestone = milestone;
                    log.record(warpTick, EventCategory.RECIPE,
                            "RecipeProgressed: " + currentRecipeId + " " + milestone + "%");
                }
            } else if (snap.wasWorking) {
                if (logic.isIdle() && logic.getProgress() == 0) {
                    log.record(warpTick, EventCategory.RECIPE, "RecipeFinished: " + snap.recipeId);
                } else {
                    log.record(warpTick, EventCategory.RECIPE,
                            "RecipeAborted: " + snap.recipeId + " status=" +
                                    (logic.isWaiting() ? "waiting" : "stopped"));
                }
                snap.lastMilestone = -1;
            }

            snap.wasWorking = working;
            snap.recipeId = currentRecipeId;
        }
    }
}
