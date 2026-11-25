package com.typeomega.qa.visual;

import com.typeomega.qa.spi.FailureDiagnostic;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side overlay state store populated by {@code FailureOverlaySyncPacket} and {@code ReportScreen}.
 */
public final class ClientOverlayStore {

    private static final Map<String, FailureDiagnostic> DIAGNOSTICS = new ConcurrentHashMap<>();
    private static volatile boolean showFailureBoxes = true;
    private static volatile @Nullable String activeLabelName;
    private static volatile WandEditorTool activeTool = WandEditorTool.MOVE;

    private ClientOverlayStore() {}

    public static void putDiagnostic(FailureDiagnostic diagnostic) {
        if (diagnostic != null && diagnostic.testId() != null) {
            DIAGNOSTICS.put(diagnostic.testId(), diagnostic);
        }
    }

    public static void removeDiagnostic(String testId) {
        if (testId != null) DIAGNOSTICS.remove(testId);
    }

    public static void clearAll() {
        DIAGNOSTICS.clear();
    }

    public static Collection<FailureDiagnostic> diagnostics() {
        return DIAGNOSTICS.values();
    }

    public static boolean showFailureBoxes() {
        return showFailureBoxes;
    }

    public static void setShowFailureBoxes(boolean show) {
        showFailureBoxes = show;
    }

    public static @Nullable String activeLabelName() {
        return activeLabelName;
    }

    public static void setActiveLabelName(@Nullable String name) {
        activeLabelName = name;
    }

    public static WandEditorTool activeTool() {
        return activeTool;
    }

    public static void setActiveTool(WandEditorTool tool) {
        activeTool = tool == null ? WandEditorTool.MOVE : tool;
    }
}
