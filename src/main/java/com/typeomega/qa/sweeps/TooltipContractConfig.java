package com.typeomega.qa.sweeps;

import org.jetbrains.annotations.Nullable;

/** Contract rules for {@link OmegaSweeps#multiblockTooltipsContract(String, String, TooltipContractConfig)}. */
public record TooltipContractConfig(
                                    int minFactoryLines,
                                    int minMegaLines,
                                    @Nullable String megaMarkerKey,
                                    boolean requireHumor,
                                    boolean forbidStructureAndPollution,
                                    boolean simulateEmiModAppend) {

    public static TooltipContractConfig typeOmegaDefault() {
        return new TooltipContractConfig(4, 9, "typeomega.tooltip.mega.phases", true, true, true);
    }
}
