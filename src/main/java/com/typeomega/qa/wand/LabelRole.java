package com.typeomega.qa.wand;

import java.util.Locale;

/**
 * Standardized semantic roles for labeled positions inside an Omega-QA structure template.
 */
public enum LabelRole {

    CONTROLLER("controller", 0xFF22D3EE),
    ENERGY_HATCH("energy_hatch", 0xFFFACC15),
    DYNAMO_HATCH("dynamo_hatch", 0xFFF97316),
    INPUT_BUS("input_bus", 0xFF38BDF8),
    OUTPUT_BUS("output_bus", 0xFF4ADE80),
    INPUT_HATCH("input_hatch", 0xFF60A5FA),
    OUTPUT_HATCH("output_hatch", 0xFF34D399),
    MAINTENANCE_HATCH("maintenance_hatch", 0xFFFBBF24),
    MUFFLER_HATCH("muffler_hatch", 0xFFA1A1AA),
    ME_BUS("me_bus", 0xFFC084FC),
    CUSTOM("custom", 0xFFE2E8F0);

    private final String id;
    private final int colorRgb;

    LabelRole(String id, int colorRgb) {
        this.id = id;
        this.colorRgb = colorRgb;
    }

    public String id() {
        return id;
    }

    public int colorRgb() {
        return colorRgb;
    }

    public String translationKey() {
        return "omegaqa.role." + id;
    }

    public LabelRole next() {
        LabelRole[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static LabelRole fromId(String raw) {
        if (raw == null || raw.isBlank()) return CUSTOM;
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        for (LabelRole role : values()) {
            if (role.id.equals(norm)) return role;
        }
        return CUSTOM;
    }
}
