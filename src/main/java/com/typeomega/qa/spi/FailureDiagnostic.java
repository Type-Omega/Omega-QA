package com.typeomega.qa.spi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/**
 * Visual and structured failure diagnostic emitted by {@code MultiblockRig} / {@code OmegaTestHelper} and consumed by
 * {@link FailureOverlaySink}.
 */
public record FailureDiagnostic(
                                String testId,
                                BlockPos cellOrigin,
                                Vec3i cellSize,
                                @Nullable BlockPos failPos,
                                @Nullable BlockState expectedState,
                                @Nullable BlockState actualState,
                                String message,
                                TestStatus status) {

    public enum TestStatus {
        RUNNING,
        PASSED,
        FAILED,
        ERROR,
        TIMED_OUT,
        SKIPPED
    }
}
