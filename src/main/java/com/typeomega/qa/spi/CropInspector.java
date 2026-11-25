package com.typeomega.qa.spi;

import net.minecraft.world.level.block.state.BlockState;

/** SPI for inspecting custom crops (e.g. Agronomica) without creating a compile/runtime dependency. */
public interface CropInspector {

    boolean isCropBlock(BlockState state);

    int growthStage(BlockState state);

    default int maxGrowthStage(BlockState state) {
        return -1;
    }
}
