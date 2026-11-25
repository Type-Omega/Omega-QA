package com.typeomega.qa.spi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Shared model for Wand selection bounds and semantic labels stored in {@code .labels.json} sidecars. */
public record WandSelectionData(
                                @Nullable BlockPos pos1,
                                @Nullable BlockPos pos2,
                                String exportNamespace,
                                String exportName,
                                List<LabelEntry> labels) {

    public WandSelectionData {
        exportNamespace = exportNamespace == null ? "" : exportNamespace;
        exportName = exportName == null ? "" : exportName;
        labels = labels == null ? List.of() : List.copyOf(labels);
    }

    public record LabelEntry(
                             String name,
                             String role,
                             BlockPos relativePos,
                             @Nullable Direction facing) {}
}
