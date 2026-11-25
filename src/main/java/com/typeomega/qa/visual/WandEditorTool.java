package com.typeomega.qa.visual;

import com.typeomega.qa.wand.QaWandTexts;

/**
 * Active manipulation tool in the Omega QA Wand visual editor.
 */
public enum WandEditorTool {

    MOVE(QaWandTexts.EDITOR_TOOL_MOVE.key(), 0xFF38BDF8),
    RESIZE(QaWandTexts.EDITOR_TOOL_RESIZE.key(), 0xFFFACC15),
    MOVE_LABEL(QaWandTexts.EDITOR_TOOL_MOVE_LABEL.key(), 0xFF4ADE80);

    private final String translationKey;
    private final int accentRgb;

    WandEditorTool(String translationKey, int accentRgb) {
        this.translationKey = translationKey;
        this.accentRgb = accentRgb;
    }

    public String translationKey() {
        return translationKey;
    }

    public int accentRgb() {
        return accentRgb;
    }

    public WandEditorTool next() {
        WandEditorTool[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
