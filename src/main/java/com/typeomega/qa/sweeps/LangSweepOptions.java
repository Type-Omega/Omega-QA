package com.typeomega.qa.sweeps;

import java.util.List;

/** Configuration for {@link OmegaSweeps#namesInAllLanguages(String, LangSweepOptions)}. */
public record LangSweepOptions(
                               boolean includeMaterials,
                               boolean includeDeposits,
                               boolean includeTooltips,
                               boolean checkPlaceholderParity,
                               List<String> extraKeyPrefixes) {

    public LangSweepOptions {
        extraKeyPrefixes = extraKeyPrefixes == null ? List.of() : List.copyOf(extraKeyPrefixes);
    }

    public static LangSweepOptions defaults() {
        return new LangSweepOptions(false, false, true, true, List.of());
    }

    public static LangSweepOptions strictPack() {
        return new LangSweepOptions(true, true, true, true, List.of());
    }
}
